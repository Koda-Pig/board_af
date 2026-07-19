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
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.setter.GuidedStep
import za.co.boardaf.setter.SetterReducer
import za.co.boardaf.setter.SetterState

class SetterReducerTest {

    private val board = ConfiguredBoard.from(BoardSetup.default())

    private fun freshState() = SetterReducer.start(DraftProblem())

    private fun draftWithContent() = DraftProblem(
        name = "Old line",
        assignments = listOf(
            ProblemAssignment("h20", ProblemHoldRole.START),
            ProblemAssignment("h30", ProblemHoldRole.FINISH),
        ),
    )

    private fun SetterState.roleOf(holdId: String): ProblemHoldRole? =
        draft.assignments.firstOrNull { it.holdId == holdId }?.role

    // --- Tap behavior ------------------------------------------------------------

    @Test
    fun `tapping adds removes and replaces assignments`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.REGULAR)

        state = SetterReducer.tapHold(state, "h20", board).state
        assertEquals(ProblemHoldRole.REGULAR, state.roleOf("h20"))

        state = SetterReducer.selectRole(state, ProblemHoldRole.START)
        state = SetterReducer.tapHold(state, "h20", board).state
        assertEquals(ProblemHoldRole.START, state.roleOf("h20"))

        state = SetterReducer.tapHold(state, "h20", board).state
        assertNull(state.roleOf("h20"))
    }

    @Test
    fun `changing a hold's role announces it, plain adds and removes stay quiet`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.REGULAR)

        val added = SetterReducer.tapHold(state, "h20", board)
        assertNull(added.notice)
        state = SetterReducer.selectRole(added.state, ProblemHoldRole.START)

        val changed = SetterReducer.tapHold(state, "h20", board)
        assertNotNull(changed.notice)
        assertEquals(ProblemHoldRole.START, changed.state.roleOf("h20"))

        val removed = SetterReducer.tapHold(changed.state, "h20", board)
        assertNull(removed.notice)
    }

    @Test
    fun `a third start hold is rejected at tap time`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.START)
        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.tapHold(state, "h21", board).state

        val result = SetterReducer.tapHold(state, "h22", board)

        assertNotNull(result.rejection)
        assertFalse(result.rejection!!.offerFootInstead)
        assertEquals(state.draft, result.state.draft)
    }

    @Test
    fun `reassigning into a full role is also capped`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.START)
        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.tapHold(state, "h21", board).state
        state = SetterReducer.selectRole(state, ProblemHoldRole.REGULAR)
        state = SetterReducer.tapHold(state, "h22", board).state

        state = SetterReducer.selectRole(state, ProblemHoldRole.START)
        val result = SetterReducer.tapHold(state, "h22", board)

        assertNotNull(result.rejection)
        assertEquals(ProblemHoldRole.REGULAR, result.state.roleOf("h22"))
    }

    @Test
    fun `hand roles are rejected on foot-only kicker holds without changing the draft`() {
        listOf(ProblemHoldRole.START, ProblemHoldRole.REGULAR, ProblemHoldRole.FINISH).forEach { role ->
            val state = SetterReducer.selectRole(freshState(), role)
            val result = SetterReducer.tapHold(state, "h43", board)

            assertNotNull("$role should be rejected", result.rejection)
            assertTrue(result.rejection!!.offerFootInstead)
            assertEquals(state.draft, result.state.draft)
            assertTrue(result.state.undoStack.isEmpty())
        }
    }

    @Test
    fun `foot instead is not offered when the feet rule ignores foot marks`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.REGULAR)
        state = SetterReducer.setFeetRule(state, FeetRule.CAMPUS)

        val result = SetterReducer.tapHold(state, "h43", board)

        assertNotNull(result.rejection)
        assertFalse(result.rejection!!.offerFootInstead)
    }

    @Test
    fun `foot-only role is accepted on kicker holds`() {
        val state = SetterReducer.selectRole(freshState(), ProblemHoldRole.FOOT_ONLY)
        val result = SetterReducer.tapHold(state, "h43", board)

        assertNull(result.rejection)
        assertEquals(ProblemHoldRole.FOOT_ONLY, result.state.roleOf("h43"))
    }

    @Test
    fun `a corrected kicker hold accepts hand roles again`() {
        val corrected = ConfiguredBoard.from(BoardSetup.default().withCapabilityToggled("h43"))
        val state = SetterReducer.selectRole(freshState(), ProblemHoldRole.REGULAR)

        val result = SetterReducer.tapHold(state, "h43", corrected)

        assertNull(result.rejection)
        assertEquals(ProblemHoldRole.REGULAR, result.state.roleOf("h43"))
    }

    @Test
    fun `mark as foot instead assigns the rejected hold`() {
        val state = SetterReducer.selectRole(freshState(), ProblemHoldRole.START)
        val rejected = SetterReducer.tapHold(state, "h43", board)
        val marked = SetterReducer.markFootInstead(rejected.state, "h43")

        assertEquals(ProblemHoldRole.FOOT_ONLY, marked.roleOf("h43"))
    }

    // --- History -----------------------------------------------------------------

    @Test
    fun `undo restores both the assignment and its previous role`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.REGULAR)
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
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.REGULAR)
        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.tapHold(state, "h21", board).state

        state = SetterReducer.clearAssignments(state)
        assertTrue(state.draft.assignments.isEmpty())

        state = SetterReducer.undo(state)
        assertEquals(2, state.draft.assignments.size)
    }

    @Test
    fun `history is bounded`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.REGULAR)
        val holdIds = board.holds.map { it.id }.filter { it < "h37" }
        repeat(SetterReducer.HISTORY_LIMIT + 10) { index ->
            state = SetterReducer.tapHold(state, holdIds[index % holdIds.size], board).state
        }

        assertTrue(state.undoStack.size <= SetterReducer.HISTORY_LIMIT)
    }

    @Test
    fun `a new action clears the redo stack`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.REGULAR)
        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.undo(state)
        assertTrue(state.canRedo)

        state = SetterReducer.tapHold(state, "h21", board).state
        assertFalse(state.canRedo)
    }

    // --- Feet rule ---------------------------------------------------------------

    @Test
    fun `switching to campus clears foot marks and stays undoable`() {
        var state = SetterReducer.selectRole(freshState(), ProblemHoldRole.FOOT_ONLY)
        state = SetterReducer.tapHold(state, "h43", board).state
        state = SetterReducer.selectRole(state, ProblemHoldRole.REGULAR)
        state = SetterReducer.tapHold(state, "h20", board).state

        state = SetterReducer.setFeetRule(state, FeetRule.CAMPUS)

        assertEquals(FeetRule.CAMPUS, state.draft.feetRule)
        assertNull(state.roleOf("h43"))
        assertEquals(ProblemHoldRole.REGULAR, state.roleOf("h20"))

        state = SetterReducer.undo(state)
        assertEquals(ProblemHoldRole.FOOT_ONLY, state.roleOf("h43"))
    }

    @Test
    fun `an active foot-only palette falls back to regular when marks stop meaning anything`() {
        val state = SetterReducer.selectRole(freshState(), ProblemHoldRole.FOOT_ONLY)

        val anyFeet = SetterReducer.setFeetRule(state, FeetRule.ANY_FEET)

        assertEquals(ProblemHoldRole.REGULAR, anyFeet.activeRole)
    }

    // --- Wizard steps ------------------------------------------------------------

    @Test
    fun `a new session starts at the feet rule step`() {
        val state = freshState()
        assertEquals(GuidedStep.FEET_RULE, state.guidedStep)
    }

    @Test
    fun `a session with existing content lands on details and review`() {
        val state = SetterReducer.start(draftWithContent())
        assertEquals(GuidedStep.DETAILS, state.guidedStep)
    }

    @Test
    fun `the wizard walks the steps in order, gated by starts and finishes`() {
        var state = freshState()
        assertEquals(GuidedStep.FEET_RULE, state.guidedStep)

        state = SetterReducer.nextStep(state)
        assertEquals(GuidedStep.START, state.guidedStep)
        assertEquals(ProblemHoldRole.START, state.activeRole)

        val blocked = SetterReducer.nextStep(state)
        assertEquals(GuidedStep.START, blocked.guidedStep)

        state = SetterReducer.tapHold(state, "h20", board).state
        state = SetterReducer.nextStep(state)
        assertEquals(GuidedStep.OTHER, state.guidedStep)
        assertEquals(ProblemHoldRole.REGULAR, state.activeRole)

        state = SetterReducer.nextStep(state)
        assertEquals(GuidedStep.FINISH, state.guidedStep)
        assertEquals(ProblemHoldRole.FINISH, state.activeRole)

        val stuck = SetterReducer.nextStep(state)
        assertEquals(GuidedStep.FINISH, stuck.guidedStep)

        state = SetterReducer.tapHold(state, "h30", board).state
        state = SetterReducer.nextStep(state)
        assertEquals(GuidedStep.DETAILS, state.guidedStep)
    }

    @Test
    fun `forward jumps stop at the first unsatisfied step, backward jumps are free`() {
        var state = freshState()

        val jumped = SetterReducer.goToStep(state, GuidedStep.FINISH)
        assertEquals(GuidedStep.FEET_RULE, jumped.guidedStep)

        state = SetterReducer.goToStep(state, GuidedStep.START)
        assertEquals(GuidedStep.START, state.guidedStep)

        val landed = SetterReducer.start(draftWithContent())
        val back = SetterReducer.goToStep(landed, GuidedStep.FEET_RULE)
        assertEquals(GuidedStep.FEET_RULE, back.guidedStep)

        val forwardAgain = SetterReducer.goToStep(back, GuidedStep.DETAILS)
        assertEquals(GuidedStep.DETAILS, forwardAgain.guidedStep)
    }

    @Test
    fun `firstUnsatisfiedStep tracks the line as it grows`() {
        var draft = DraftProblem()
        assertEquals(GuidedStep.START, SetterReducer.firstUnsatisfiedStep(draft))

        draft = draft.copy(assignments = listOf(ProblemAssignment("h20", ProblemHoldRole.START)))
        assertEquals(GuidedStep.FINISH, SetterReducer.firstUnsatisfiedStep(draft))

        draft = draft.copy(
            assignments = draft.assignments + ProblemAssignment("h30", ProblemHoldRole.FINISH),
        )
        assertNull(SetterReducer.firstUnsatisfiedStep(draft))
    }
}
