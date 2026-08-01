package za.co.boardaf

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.boardaf.data.SnapshotCodec
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.Problem
import za.co.boardaf.model.PublicationState

/** Libraries written before benchmarks were dropped must still load. */
class RetiredBenchmarkStateCodecTest {

    @Test
    fun `a persisted BENCHMARK state decodes as published`() {
        val record = SnapshotCodec.encodeProblem(
            Problem(
                id = "p",
                name = "P",
                grade = BoulderGrade.F6A,
                accent = Accent.SKY,
                setter = "You",
                publicationState = PublicationState.PUBLISHED,
            ),
        ).toMutableMap()
        record["state"] = JsonPrimitive("BENCHMARK")

        val decoded = SnapshotCodec.decodeProblem(JsonObject(record))

        assertEquals(PublicationState.PUBLISHED, decoded.publicationState)
    }
}
