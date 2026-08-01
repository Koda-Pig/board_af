package za.co.boardaf

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.boardaf.data.SnapshotCodec
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAngle

class ProblemAngleCodecTest {

    private fun problem(angle: Int) = Problem(
        id = "p",
        name = "P",
        grade = BoulderGrade.F6A,
        accent = Accent.SKY,
        setter = "You",
        angleDegrees = angle,
    )

    @Test
    fun `angle round-trips through the problem codec`() {
        val encoded = SnapshotCodec.encodeProblem(problem(angle = 45))
        val decoded = SnapshotCodec.decodeProblem(encoded)

        assertEquals(45, decoded.angleDegrees)
    }

    @Test
    fun `records without an angle decode at the default incline`() {
        val legacy = SnapshotCodec.encodeProblem(problem(angle = 45)).toMutableMap()
        legacy.remove("angle")

        val decoded = SnapshotCodec.decodeProblem(JsonObject(legacy))

        assertEquals(ProblemAngle.DEFAULT_DEGREES, decoded.angleDegrees)
    }

    @Test
    fun `out-of-range persisted angles clamp to the wall's limits`() {
        val tooSteep = SnapshotCodec.encodeProblem(problem(angle = 90)).toMutableMap()
        tooSteep["angle"] = JsonPrimitive(135)

        val decoded = SnapshotCodec.decodeProblem(JsonObject(tooSteep))

        assertEquals(ProblemAngle.MAX_DEGREES, decoded.angleDegrees)
    }
}
