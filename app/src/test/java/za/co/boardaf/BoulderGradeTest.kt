package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.DraftProblem
import za.co.boardaf.model.GradeSystem

class BoulderGradeTest {
    @Test
    fun `new problem defaults to French six A equivalent`() {
        val draft = DraftProblem()

        assertEquals(BoulderGrade.F6A, draft.grade)
        assertEquals("6A", draft.grade.label(GradeSystem.FRENCH))
    }

    @Test
    fun `persisted V grades migrate to canonical grades`() {
        assertEquals(BoulderGrade.F4, BoulderGrade.fromPersisted("V0"))
        assertEquals(BoulderGrade.F6B, BoulderGrade.fromPersisted("V4"))
        assertEquals(BoulderGrade.F7B, BoulderGrade.fromPersisted("V8"))
    }

    @Test
    fun `French labels resolve to the same canonical grades`() {
        assertEquals(BoulderGrade.F5_PLUS, BoulderGrade.fromPersisted("5+"))
        assertEquals(BoulderGrade.F6C, BoulderGrade.fromPersisted("6C"))
        assertEquals(BoulderGrade.F7A_PLUS, BoulderGrade.fromPersisted("7A+"))
    }

    @Test
    fun `French scale preserves plus grades while V scale has one option per grade`() {
        assertEquals(13, BoulderGrade.options(GradeSystem.FRENCH).size)
        assertEquals(9, BoulderGrade.options(GradeSystem.V_SCALE).size)
        assertEquals("V3", BoulderGrade.F6A_PLUS.label(GradeSystem.V_SCALE))
    }

    @Test
    fun `grade system defaults to French`() {
        assertEquals(GradeSystem.FRENCH, BoardUiState().gradeSystem)
    }
}
