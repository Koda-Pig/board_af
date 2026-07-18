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
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.FinishRule
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.HoldCapability
import za.co.boardaf.model.HoldClassification
import za.co.boardaf.model.Problem
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

        return DecodeResult.Success(
            snapshot = LibrarySnapshot(
                setup = setup,
                problems = problems,
                gradeSystem = gradeSystem,
                setterMode = setterMode,
                unreadable = unreadable,
            ),
            issues = issues,
        )
    }

    private fun encodeSetup(setup: BoardSetup): JsonObject = buildJsonObject {
        put("kickboardEnabled", setup.kickboardEnabled)
        put("kickboardTopY", setup.kickboardTopY.toDouble())
        val confirmedAt = setup.confirmedAt
        if (confirmedAt != null) put("confirmedAt", confirmedAt) else put("confirmedAt", JsonNull)
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
    }

    private fun decodeSetup(json: JsonObject): BoardSetup {
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
        )
    }

    fun encodeProblem(problem: Problem): JsonObject = buildJsonObject {
        put("id", problem.id)
        put("name", problem.name)
        put("grade", problem.grade.name)
        put("accent", problem.accent.name)
        put("setter", problem.setter)
        put("note", problem.note)
        put("tags", JsonArray(problem.tags.map { JsonPrimitive(it) }))
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
            note = json["note"]?.jsonPrimitive?.content.orEmpty(),
            tags = (json["tags"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(),
            feetRule = parseEnum<FeetRule>(json.require("feetRule").jsonPrimitive.content),
            startRule = parseEnum<StartRule>(json.require("startRule").jsonPrimitive.content),
            finishRule = parseEnum<FinishRule>(json.require("finishRule").jsonPrimitive.content),
            publicationState = parseEnum<PublicationState>(json.require("state").jsonPrimitive.content),
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

    private fun JsonObject.require(key: String): JsonElement =
        this[key]?.takeIf { it !is JsonNull } ?: error("Missing field: $key")

    private inline fun <reified T : Enum<T>> parseEnum(value: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: error("Unknown ${T::class.simpleName}: $value")
}
