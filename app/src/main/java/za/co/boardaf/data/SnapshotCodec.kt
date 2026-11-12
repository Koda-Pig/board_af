package za.co.boardaf.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardPhoto
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.FinishRule
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.HoldCapability
import za.co.boardaf.model.HoldClassification
import za.co.boardaf.model.HoldDefinition
import za.co.boardaf.model.NormalizedPoint
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAngle
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.PublicationState
import za.co.boardaf.model.StartRule
import za.co.boardaf.setter.SetterMode

/**
 * Versioned v2 snapshot codec. Problems decode record-by-record so one malformed
 * entry never takes the rest of the library down with it.
 */
object SnapshotCodec {
    const val VERSION = 2

    sealed interface DecodeResult {
        data class Success(
            val snapshot: LibrarySnapshot,
            val issues: List<StorageIssue>,
        ) : DecodeResult

        data class Corrupt(val error: String) : DecodeResult
    }

    fun encode(snapshot: LibrarySnapshot): String {
        val root = buildJsonObject {
            put("version", VERSION)
            put("board", encodeSetup(snapshot.setup))
            put(
                "settings",
                buildJsonObject {
                    put("gradeSystem", snapshot.gradeSystem.name)
                    put("setterMode", snapshot.setterMode.name)
                },
            )
            put("problems", JsonArray(snapshot.problems.map { encodeProblem(it) }))
            put(
                "deletedProblems",
                JsonArray(snapshot.deletedProblemIds.sorted().map { JsonPrimitive(it) }),
            )
            put(
                // Distinct from "deletedProblems", which is the id list above.
                "deletedProblemRecords",
                JsonArray(
                    snapshot.deletedProblems.map { record ->
                        buildJsonObject {
                            put("deletedAt", record.deletedAt)
                            put("problem", encodeProblem(record.problem))
                        }
                    },
                ),
            )
            put(
                "unreadable",
                JsonArray(
                    snapshot.unreadable.map { record ->
                        buildJsonObject {
                            put("raw", record.raw)
                            put("error", record.error)
                            put("source", record.source)
                        }
                    },
                ),
            )
        }
        return root.toString()
    }

    fun decode(raw: String): DecodeResult {
        val root = runCatching { Json.parseToJsonElement(raw).jsonObject }
            .getOrElse { return DecodeResult.Corrupt("Snapshot is not valid JSON: ${it.message}") }

        val version = root["version"]?.jsonPrimitive?.longOrNull?.toInt()
        if (version != VERSION) {
            return DecodeResult.Corrupt("Unsupported snapshot version: $version")
        }

        val issues = mutableListOf<StorageIssue>()

        val setup = runCatching { decodeSetup(root.require("board").jsonObject) }
            .getOrElse {
                issues += StorageIssue(
                    StorageIssueCode.SNAPSHOT_RECORD_UNREADABLE,
                    "Board setup couldn't be read; the default classification was restored.",
                )
                BoardSetup.default()
            }

        val settings = root["settings"] as? JsonObject
        val gradeSystem = settings?.get("gradeSystem")?.jsonPrimitive?.content
            ?.let { name -> GradeSystem.entries.firstOrNull { it.name == name } }
            ?: GradeSystem.FRENCH
        val setterMode = settings?.get("setterMode")?.jsonPrimitive?.content
            ?.let { name -> SetterMode.entries.firstOrNull { it.name == name } }
            ?: SetterMode.GUIDED

        val problems = mutableListOf<Problem>()
        val unreadable = mutableListOf<UnreadableRecord>()
        val problemElements = runCatching { root.require("problems").jsonArray }
            .getOrElse { return DecodeResult.Corrupt("Snapshot problem list is unreadable: ${it.message}") }
        problemElements.forEach { element ->
            runCatching { decodeProblem(element.jsonObject) }
                .onSuccess { problems += it }
                .onFailure { failure ->
                    unreadable += UnreadableRecord(
                        raw = element.toString(),
                        error = failure.message ?: failure.toString(),
                        source = UnreadableRecord.SOURCE_V2,
                    )
                }
        }
        if (unreadable.isNotEmpty()) {
            issues += StorageIssue(
                StorageIssueCode.SNAPSHOT_RECORD_UNREADABLE,
                "${unreadable.size} problem record(s) couldn't be read and were kept for recovery.",
            )
        }

        (root["unreadable"] as? JsonArray)?.forEach { element ->
            runCatching {
                val record = element.jsonObject
                UnreadableRecord(
                    raw = record.require("raw").jsonPrimitive.content,
                    error = record["error"]?.jsonPrimitive?.content.orEmpty(),
                    source = record["source"]?.jsonPrimitive?.content ?: UnreadableRecord.SOURCE_V2,
                )
            }.onSuccess { unreadable += it }
        }

        // Additive since v2; absent in older snapshots, which is an empty set.
        val deletedProblemIds = (root["deletedProblems"] as? JsonArray)
            ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
            ?.toSet()
            .orEmpty()

        // Additive; absent in older snapshots, which means nothing is recoverable.
        // A record that won't decode is skipped rather than failing the load: it
        // only costs the ability to restore one already-deleted problem.
        val deletedProblems = (root["deletedProblemRecords"] as? JsonArray)
            ?.mapNotNull { element ->
                runCatching {
                    val record = element.jsonObject
                    DeletedProblem(
                        problem = decodeProblem(record.require("problem").jsonObject),
                        deletedAt = record.require("deletedAt").jsonPrimitive.longOrNull
                            ?: error("deletedAt is not a number"),
                    )
                }.getOrNull()
            }
            ?.filter { it.problem.id in deletedProblemIds }
            .orEmpty()

        return DecodeResult.Success(
            snapshot = LibrarySnapshot(
                setup = setup,
                problems = problems,
                gradeSystem = gradeSystem,
                setterMode = setterMode,
                unreadable = unreadable,
                deletedProblemIds = deletedProblemIds,
                deletedProblems = deletedProblems,
            ),
            issues = issues,
        )
    }

    /**
     * [includePhoto] is false for the cloud-sync board document: the photo file
     * lives only on the device that took it, so syncing its metadata would leave
     * other devices pointing at a file they don't have.
     */
    fun encodeSetup(setup: BoardSetup, includePhoto: Boolean = true): JsonObject = buildJsonObject {
        put("kickboardEnabled", setup.kickboardEnabled)
        put("kickboardTopY", setup.kickboardTopY.toDouble())
        val confirmedAt = setup.confirmedAt
        if (confirmedAt != null) put("confirmedAt", confirmedAt) else put("confirmedAt", JsonNull)
        val photo = setup.photo
        if (includePhoto && photo != null) {
            put(
                "photo",
                buildJsonObject {
                    put("fileName", photo.fileName)
                    put("widthPx", photo.widthPx)
                    put("heightPx", photo.heightPx)
                    put("capturedAt", photo.capturedAt)
                },
            )
        }
        put(
            "holds",
            JsonArray(
                setup.classifications.map { (id, classification) ->
                    buildJsonObject {
                        put("id", id)
                        put("zone", classification.zone.name)
                        put("capability", classification.capability.name)
                        put("overridden", classification.overridden)
                    }
                },
            ),
        )
        // Written only once the setter has re-mapped holds. Absent means "track
        // the bundled map", which is not the same as a copy of it. Unlike the
        // photo this is included for the cloud document too: hold ids are what
        // problems reference, so the devices have to agree on which holds exist.
        val layout = setup.layout
        if (layout != null) {
            put(
                "layout",
                JsonArray(
                    layout.map { hold ->
                        buildJsonObject {
                            put("id", hold.id)
                            put("x", hold.point.x.toDouble())
                            put("y", hold.point.y.toDouble())
                        }
                    },
                ),
            )
        }
        val nextHoldNumber = setup.nextHoldNumber
        if (nextHoldNumber != null) put("nextHoldNumber", nextHoldNumber)
    }

    fun decodeSetup(json: JsonObject): BoardSetup {
        val classifications = json.require("holds").jsonArray.associate { element ->
            val hold = element.jsonObject
            hold.require("id").jsonPrimitive.content to HoldClassification(
                zone = parseEnum<BoardZoneType>(hold.require("zone").jsonPrimitive.content),
                capability = parseEnum<HoldCapability>(hold.require("capability").jsonPrimitive.content),
                overridden = hold["overridden"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }
        return BoardSetup(
            kickboardEnabled = json.require("kickboardEnabled").jsonPrimitive.booleanOrNull
                ?: error("kickboardEnabled is not a boolean"),
            kickboardTopY = json.require("kickboardTopY").jsonPrimitive.doubleOrNull?.toFloat()
                ?: error("kickboardTopY is not a number"),
            confirmedAt = json["confirmedAt"]?.jsonPrimitive?.longOrNull,
            classifications = classifications,
            // Additive since in-app board capture landed; absent means the bundled
            // photo. A malformed record falls back to it rather than failing the
            // whole setup, which would cost the hold classifications too.
            photo = (json["photo"] as? JsonObject)?.let { runCatching { decodePhoto(it) }.getOrNull() },
            // Additive since hold re-mapping landed; absent means the bundled map.
            layout = decodeLayout(json),
            nextHoldNumber = json["nextHoldNumber"]?.jsonPrimitive?.longOrNull?.toInt(),
        )
    }

    /**
     * A hold record that won't parse is skipped rather than failing the setup: the
     * remaining holds still place every problem that doesn't use the lost one, and
     * problems that do use it surface as Needs review through the normal unknown-hold
     * check. A layout that reads as empty is treated as absent — a board with no
     * holds at all is never what the setter meant.
     */
    private fun decodeLayout(json: JsonObject): List<HoldDefinition>? =
        (json["layout"] as? JsonArray)
            ?.mapNotNull { element ->
                runCatching {
                    val hold = element.jsonObject
                    HoldDefinition(
                        id = hold.require("id").jsonPrimitive.content,
                        point = NormalizedPoint(
                            x = hold.require("x").jsonPrimitive.doubleOrNull?.toFloat()
                                ?: error("x is not a number"),
                            y = hold.require("y").jsonPrimitive.doubleOrNull?.toFloat()
                                ?: error("y is not a number"),
                        ),
                    )
                }.getOrNull()
            }
            ?.takeIf { it.isNotEmpty() }
            // Keep "tracks the bundled map" as the single representation of itself,
            // so an encode/decode round trip doesn't quietly pin the board.
            ?.takeIf { it != BoardDefaults.holds }

    private fun decodePhoto(json: JsonObject): BoardPhoto = BoardPhoto(
        fileName = json.require("fileName").jsonPrimitive.content,
        widthPx = json.require("widthPx").jsonPrimitive.longOrNull?.toInt()
            ?: error("widthPx is not a number"),
        heightPx = json.require("heightPx").jsonPrimitive.longOrNull?.toInt()
            ?: error("heightPx is not a number"),
        capturedAt = json.require("capturedAt").jsonPrimitive.longOrNull
            ?: error("capturedAt is not a number"),
    )

    fun encodeProblem(problem: Problem): JsonObject = buildJsonObject {
        put("id", problem.id)
        put("name", problem.name)
        put("grade", problem.grade.name)
        put("accent", problem.accent.name)
        put("setter", problem.setter)
        put("angle", problem.angleDegrees)
        put("feetRule", problem.feetRule.name)
        put("startRule", problem.startRule.name)
        put("finishRule", problem.finishRule.name)
        put("state", problem.publicationState.name)
        val forerunConfirmedAt = problem.forerunConfirmedAt
        if (forerunConfirmedAt != null) put("forerunConfirmedAt", forerunConfirmedAt) else put("forerunConfirmedAt", JsonNull)
        put(
            "assignments",
            JsonArray(
                problem.assignments.map { assignment ->
                    buildJsonObject {
                        put("holdId", assignment.holdId)
                        put("role", assignment.role.name)
                    }
                },
            ),
        )
    }

    fun decodeProblem(json: JsonObject): Problem {
        val gradeRaw = json.require("grade").jsonPrimitive.content
        val grade = BoulderGrade.fromPersistedOrNull(gradeRaw)
            ?: error("Unknown grade: $gradeRaw")
        return Problem(
            id = json.require("id").jsonPrimitive.content,
            name = json.require("name").jsonPrimitive.content,
            grade = grade,
            accent = parseEnum<Accent>(json.require("accent").jsonPrimitive.content),
            setter = json["setter"]?.jsonPrimitive?.content.orEmpty(),
            // Additive since angle-per-problem landed; older records were set at the default incline.
            angleDegrees = json["angle"]?.jsonPrimitive?.longOrNull?.toInt()
                ?.let { ProblemAngle.clamp(it) }
                ?: ProblemAngle.DEFAULT_DEGREES,
            feetRule = parseEnum<FeetRule>(json.require("feetRule").jsonPrimitive.content),
            startRule = parseEnum<StartRule>(json.require("startRule").jsonPrimitive.content),
            finishRule = parseEnum<FinishRule>(json.require("finishRule").jsonPrimitive.content),
            publicationState = decodeState(json.require("state").jsonPrimitive.content),
            forerunConfirmedAt = json["forerunConfirmedAt"]?.jsonPrimitive?.longOrNull,
            assignments = json.require("assignments").jsonArray.map { element ->
                val assignment = element.jsonObject
                ProblemAssignment(
                    holdId = assignment.require("holdId").jsonPrimitive.content,
                    role = parseEnum<ProblemHoldRole>(assignment.require("role").jsonPrimitive.content),
                )
            },
        )
    }

    /** "BENCHMARK" is a retired state; records written before it was dropped read back as published. */
    private fun decodeState(value: String): PublicationState =
        if (value == "BENCHMARK") PublicationState.PUBLISHED else parseEnum(value)

    private fun JsonObject.require(key: String): JsonElement =
        this[key]?.takeIf { it !is JsonNull } ?: error("Missing field: $key")

    private inline fun <reified T : Enum<T>> parseEnum(value: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: error("Unknown ${T::class.simpleName}: $value")
}
