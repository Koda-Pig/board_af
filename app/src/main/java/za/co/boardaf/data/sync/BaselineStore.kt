package za.co.boardaf.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Device-local record of the last content each side agreed on, keyed per account. */
interface BaselineStore {
    suspend fun read(accountId: String): SyncBaselines
    suspend fun write(accountId: String, baselines: SyncBaselines)
}

object BaselineCodec {
    fun encode(baselines: SyncBaselines): String = buildJsonObject {
        baselines.board?.let { put("board", it) }
        put(
            "problems",
            buildJsonObject {
                baselines.problems.forEach { (id, encoded) -> put(id, encoded) }
            },
        )
    }.toString()

    fun decode(raw: String?): SyncBaselines {
        if (raw == null) return SyncBaselines()
        return runCatching {
            val root = Json.parseToJsonElement(raw).jsonObject
            SyncBaselines(
                board = root["board"]?.jsonPrimitive?.content,
                problems = (root["problems"] as? JsonObject)
                    ?.mapValues { (_, value) -> value.jsonPrimitive.content }
                    .orEmpty(),
            )
        }.getOrElse { SyncBaselines() }
        // A lost baseline is safe: the planner degrades to conflict copies, never data loss.
    }
}
