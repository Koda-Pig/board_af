package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.boardaf.data.sync.BaselineCodec
import za.co.boardaf.data.sync.SyncBaselines
import za.co.boardaf.data.sync.SyncCodec
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.PublicationState
import za.co.boardaf.model.StartRule
import za.co.boardaf.setter.SetterMode

class SyncCodecTest {

    private val problem = Problem(
        id = "tidepool",
        name = "Tidepool",
        grade = BoulderGrade.F6B,
        accent = Accent.SKY,
        setter = "Maya",
        note = "Stay square.",
        tags = listOf("Project"),
        feetRule = FeetRule.OPEN_KICKBOARD,
        startRule = StartRule.SPLIT_TWO,
        publicationState = PublicationState.PUBLISHED,
        forerunConfirmedAt = 123L,
        assignments = listOf(
            ProblemAssignment("h01", ProblemHoldRole.START),
            ProblemAssignment("h05", ProblemHoldRole.FINISH),
        ),
    )

    @Test
    fun `problem payload roundtrips`() {
        val encoded = SyncCodec.encodeProblem(problem)
        assertEquals(problem, SyncCodec.decodeProblem(encoded))
    }

    @Test
    fun `problem encoding is stable for equal content`() {
        assertEquals(SyncCodec.encodeProblem(problem), SyncCodec.encodeProblem(problem.copy()))
    }

    @Test
    fun `board payload roundtrips setup and settings`() {
        val setup = BoardSetup.default().withKickboardEnabled(false)
        val encoded = SyncCodec.encodeBoard(setup, GradeSystem.V_SCALE, SetterMode.QUICK)
        val decoded = SyncCodec.decodeBoard(encoded)
        assertEquals(setup, decoded.setup)
        assertEquals(GradeSystem.V_SCALE, decoded.gradeSystem)
        assertEquals(SetterMode.QUICK, decoded.setterMode)
    }

    @Test
    fun `baselines roundtrip`() {
        val baselines = SyncBaselines(
            board = "board-payload",
            problems = mapOf("a" to "payload-a", "b" to "payload-b"),
        )
        assertEquals(baselines, BaselineCodec.decode(BaselineCodec.encode(baselines)))
    }

    @Test
    fun `corrupt baseline payload degrades to empty baselines`() {
        assertEquals(SyncBaselines(), BaselineCodec.decode("not json"))
        assertEquals(SyncBaselines(), BaselineCodec.decode(null))
    }
}
