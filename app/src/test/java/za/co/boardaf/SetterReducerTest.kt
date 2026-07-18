package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.DraftProblem
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.setter.GuidedStep
import za.co.boardaf.setter.SetterMode
import za.co.boardaf.setter.SetterReducer
import za.co.boardaf.setter.SetterState

class SetterReducerTest {

    private val board = ConfiguredBoard.from(BoardSetup.default())

    private fun quickState() = SetterReducer.start(DraftProblem(), SetterMode.QUICK)

    private fun SetterState.roleOf(holdId: String): ProblemHoldRole? =
        draft.assignments.firstOrNull { it.holdId == holdId }?.role

    @Test
    fun `tapping adds removes and replaces assignments`() {
        var state = SetterReducer.selectRole(quickState(), ProblemHoldRole.REGULAR)

        state = SetterReducer.tapHold(state, "h20", board).state
        assertEquals(ProblemHoldRole.REGULAR, state.roleOf("h20"))

        state = SetterReducer.selectRole(state, ProblemHoldRole.START)
        state = SetterReducer.tapHold(state, "h20", board).state
        assertEquals(ProblemHoldRole.START, state.roleOf("h20"))

        state = SetterReducer.tapHold(state, "h20", board).state
        assertNull(state.roleOf("h20"))
    }

    @Test
    fun `hand roles are rejected on foot-only kicker holds without changing the draft`() {
        listOf(ProblemHoldRole.START, ProblemHoldRole.REGULAR, ProblemHoldRole.FINISH).forEach { role ->
            val state = SetterReducer.selectRole(quickState(), role)
            val result = SetterReducer.tapHold(state, "h43", board)

            assertNotNull("$role should be rejected", result.rejection)
            assertTrue(result.rejection!!.offerFootInstead)
            assertEquals(state.draft, result.state.draft)
            assertTrue(result.state.undoStack.isEmpty())
        }
    }

    @Test
    fun `foot-only role is accepted on kicker holds`() {
        val state = SetterReducer.selectRole(quickState(), ProblemHoldRole.FOOT_ONLY)
        val result = SetterReducer.tapHold(state, "h43", board)

        assertNull(result.rejection)
        assertEquals(ProblemHoldRole.FOOT_ONLY, result.state.roleOf("h43"))
    }

    @Test
    fun `a corrected kicker hold accepts hand roles again`() {
        val corrected = ConfiguredBoard.from(BoardSetup.default().withCapabilityToggled("h43"))
        val state = SetterReducer.selectRole(quickState(), ProblemHoldRole.REGULAR)

        val result = SetterReducer.tapHold(state, "h43", corrected)

        assertNull(result.rejection)
        assertEquals(ProblemHoldRole.REGULAR, result.state.roleOf("h43"))
    }

    @Test
    fun `mark as foot instead assigns the rejected hold`() {
        val state = SetterReducer.selectRole(quickState(), ProblemHoldRole.START)
        val rejected = SetterReducer.tapHold(state, "h43", board)
        val marked = SetterReducer.markFootInstead(rejected.state, "h43")

        assertEquals(ProblemHoldRole.FOOT_ONLY, marked.roleOf("h43"))
    }

    @Test
    fun `undo restores both the assignment and its previous role`() {
        var state = SetterReducer.selectRole(quickState(), ProblemHoldRole.REGULAR)
        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.selectRole(state, ProblemHoldRole.START)
        state = SetterReducer.tapHold(state, "h20", board).state
        assertEquals(ProblemHoldRole.START, state.roleOf("h20"))

        // Undoing the role replacement brings back the hold with its previous role,
        // while the palette selection stays where the setter put it.
        state = SetterReducer.undo(state)
        assertEquals(ProblemHoldRole.REGULAR, state.roleOf("h20"))
        assertEquals(ProblemHoldRole.START, state.activeRole)

        state = SetterReducer.redo(state)
        assertEquals(ProblemHoldRole.START, state.roleOf("h20"))

        state = SetterReducer.undo(state)
        state = SetterReducer.undo(state)
        assertNull(state.roleOf("h20"))
    }

    @Test
    fun `clear empties the wall and stays undoable`() {
        var state = SetterReducer.selectRole(quickState(), ProblemHoldRole.REGULAR)
        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.tapHold(state, "h21", board).state

        state = SetterReducer.clearAssignments(state)
        assertTrue(state.draft.assignments.isEmpty())

        state = SetterReducer.undo(state)
        assertEquals(2, state.draft.assignments.size)
    }

    @Test
    fun `history is bounded`() {
        var state = SetterReducer.selectRole(quickState(), ProblemHoldRole.REGULAR)
        val holdIds = board.holds.map { it.id }.filter { it < "h37" }
        repeat(SetterReducer.HISTORY_LIMIT + 10) { index ->
            state = SetterReducer.tapHold(state, holdIds[index % holdIds.size], board).state
        }

        assertTrue(state.undoStack.size <= SetterReducer.HISTORY_LIMIT)
    }

    @Test
    fun `a new action clears the redo stack`() {
        var state = SetterReducer.selectRole(quickState(), ProblemHoldRole.REGULAR)
        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.undo(state)
        assertTrue(state.canRedo)

        state = SetterReducer.tapHold(state, "h21", board).state
        assertFalse(state.canRedo)
    }

    @Test
    fun `switching modes keeps the draft`() {
        var state = SetterReducer.selectRole(quickState(), ProblemHoldRole.REGULAR)
        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.setFeetRule(state, FeetRule.CAMPUS)

        val guided = SetterReducer.setMode(state, SetterMode.GUIDED)

        assertEquals(state.draft.assignments, guided.draft.assignments)
        assertEquals(FeetRule.CAMPUS, guided.draft.feetRule)
        assertEquals(SetterMode.GUIDED, guided.mode)
    }

    @Test
    fun `guided steps drive the active role`() {
        var state = SetterReducer.start(DraftProblem(), SetterMode.GUIDED)
        assertEquals(GuidedStep.FEET_RULE, state.guidedStep)

        state = SetterReducer.nextStep(state)
        assertEquals(GuidedStep.START, state.guidedStep)
        assertEquals(ProblemHoldRole.START, state.activeRole)

        state = SetterReducer.nextStep(state)
        assertEquals(ProblemHoldRole.FINISH, state.activeRole)

        state = SetterReducer.nextStep(state)
        assertEquals(GuidedStep.MOVEMENT, state.guidedStep)
        assertEquals(ProblemHoldRole.REGULAR, state.activeRole)

        state = SetterReducer.previousStep(state)
        assertEquals(GuidedStep.FINISH, state.guidedStep)
        assertEquals(ProblemHoldRole.FINISH, state.activeRole)
    }
}
