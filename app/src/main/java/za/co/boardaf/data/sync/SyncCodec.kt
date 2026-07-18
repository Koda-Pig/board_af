package za.co.boardaf.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import za.co.boardaf.data.SnapshotCodec
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.Problem
import za.co.boardaf.setter.SetterMode

/**
 * Canonical string encodings for the two sync units (one problem, one board
 * setup+settings doc). Both delegate to [SnapshotCodec] so local persistence and
 * cloud sync can never drift apart. The strings are stable for equal inputs and
 * are used both as Firestore payloads and as change-detection fingerprints.
 */
object SyncCodec {
    const val SCHEMA_VERSION = SnapshotCodec.VERSION

    fun encodeProblem(problem: Problem): String =
        SnapshotCodec.encodeProblem(problem).toString()

    fun decodeProblem(payload: String): Problem =
        SnapshotCodec.decodeProblem(Json.parseToJsonElement(payload).jsonObject)

    fun encodeBoard(setup: BoardSetup, gradeSystem: GradeSystem, setterMode: SetterMode): String =
        buildJsonObject {
            put("board", SnapshotCodec.encodeSetup(setup))
            put(
                "settings",
                buildJsonObject {
                    put("gradeSystem", gradeSystem.name)
                    put("setterMode", setterMode.name)
                },
            )
        }.toString()

    data class BoardDoc(
        val setup: BoardSetup,
        val gradeSystem: GradeSystem,
        val setterMode: SetterMode,
    )

    fun decodeBoard(payload: String): BoardDoc {
        val root = Json.parseToJsonElement(payload).jsonObject
        val setup = SnapshotCodec.decodeSetup(
            root["board"]?.jsonObject ?: error("Missing field: board"),
        )
        val settings = root["settings"]?.jsonObject ?: error("Missing field: settings")
        val gradeSystem = settings["gradeSystem"]?.jsonPrimitive?.content
            ?.let { name -> GradeSystem.entries.firstOrNull { it.name == name } }
            ?: error("Unknown gradeSystem")
        val setterMode = settings["setterMode"]?.jsonPrimitive?.content
            ?.let { name -> SetterMode.entries.firstOrNull { it.name == name } }
            ?: error("Unknown setterMode")
        return BoardDoc(setup, gradeSystem, setterMode)
    }
}
