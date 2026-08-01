package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * Quick-set inference: taps toggle membership, the lowest tapped row becomes the
 * start (all ties), the topmost the finish (all ties), kickboard holds become
 * feet, and everything in between is regular.
 *
 * Wall geometry used here: h36 is the lowest main-board hold, h30/h31 share a
 * row mid-wall, h01–h04 share the top row, h37–h43 are kickboard.
 */
class SetterReducerQuickSetTest {

    private val board = ConfiguredBoard.from(BoardSetup.default())

    private fun quickState() = SetterReducer.start(DraftProblem(), SetterMode.QUICK)

    private fun tap(state: SetterState, vararg holds: String): SetterState =
        holds.fold(state) { acc, id -> SetterReducer.quickTapHold(acc, id, board).state }

    private fun SetterState.roleOf(holdId: String): ProblemHoldRole? =
        draft.assignments.firstOrNull { it.holdId == holdId }?.role

    @Test
    fun `lowest tapped hold starts, topmost finishes, middle is regular`() {
        val state = tap(quickState(), "h36", "h27", "h06")

        assertEquals(ProblemHoldRole.START, state.roleOf("h36"))
        assertEquals(ProblemHoldRole.REGULAR, state.roleOf("h27"))
        assertEquals(ProblemHoldRole.FINISH, state.roleOf("h06"))
    }

    @Test
    fun `row ties all become start and all become finish`() {
        // h30 and h31 share the lowest tapped row; h03 and h04 share the top row.
        val state = tap(quickState(), "h30", "h31", "h20", "h03", "h04")

        assertEquals(ProblemHoldRole.START, state.roleOf("h30"))
        assertEquals(ProblemHoldRole.START, state.roleOf("h31"))
        assertEquals(ProblemHoldRole.REGULAR, state.roleOf("h20"))
        assertEquals(ProblemHoldRole.FINISH, state.roleOf("h03"))
        assertEquals(ProblemHoldRole.FINISH, state.roleOf("h04"))
    }

    @Test
    fun `a single-row line is all start, never all finish`() {
        val state = tap(quickState(), "h30", "h31")

        assertEquals(ProblemHoldRole.START, state.roleOf("h30"))
        assertEquals(ProblemHoldRole.START, state.roleOf("h31"))
    }

    @Test
    fun `kickboard holds become foot-only and flip the feet rule to marked only`() {
        val state = tap(quickState(), "h36", "h06", "h37")

        assertEquals(ProblemHoldRole.FOOT_ONLY, state.roleOf("h37"))
        assertEquals(FeetRule.MARKED_ONLY, state.draft.feetRule)
    }

    @Test
    fun `without foot marks the feet rule follows the marked line`() {
        val state = tap(quickState(), "h36", "h06")

        assertEquals(FeetRule.FEET_FOLLOW_MARKED, state.draft.feetRule)
    }

    @Test
    fun `a hand-picked feet rule survives later membership changes`() {
        var state = SetterReducer.setFeetRule(quickState(), FeetRule.CAMPUS)
        state = tap(state, "h37", "h36", "h06")

        assertEquals(FeetRule.CAMPUS, state.draft.feetRule)
    }

    @Test
    fun `tapping a member hold removes it and re-infers the rest`() {
        var state = tap(quickState(), "h36", "h27", "h06")
        state = tap(state, "h36")

        assertNull(state.roleOf("h36"))
        // h27 is now the lowest tapped hold, so it inherits the start.
        assertEquals(ProblemHoldRole.START, state.roleOf("h27"))
        assertEquals(ProblemHoldRole.FINISH, state.roleOf("h06"))
    }

    @Test
    fun `membership change re-infers roles set by hand in the wizard`() {
        // Mark h20 as a start in the wizard...
        var state = SetterReducer.start(DraftProblem()).copy(guidedStep = GuidedStep.START)
        state = SetterReducer.tapHold(state, "h20", board).state
        assertEquals(ProblemHoldRole.START, state.roleOf("h20"))

        // ...then switch to quick set and tap a lower hold: the whole set re-infers.
        state = SetterReducer.setMode(state, SetterMode.QUICK)
        state = tap(state, "h30")

        assertEquals(ProblemHoldRole.START, state.roleOf("h30"))
        assertEquals(ProblemHoldRole.FINISH, state.roleOf("h20"))
    }

    @Test
    fun `each tap is one undo step and undo restores matching roles`() {
        var state = tap(quickState(), "h36", "h27", "h06")
        assertTrue(state.canUndo)

        state = SetterReducer.undo(state)
        // Back to the two-hold set: h36 starts, h27 finishes, h06 gone.
        assertEquals(ProblemHoldRole.START, state.roleOf("h36"))
        assertEquals(ProblemHoldRole.FINISH, state.roleOf("h27"))
        assertNull(state.roleOf("h06"))

        state = SetterReducer.redo(state)
        assertEquals(ProblemHoldRole.REGULAR, state.roleOf("h27"))
        assertEquals(ProblemHoldRole.FINISH, state.roleOf("h06"))
    }

    @Test
    fun `undoing past a kickboard tap re-infers the feet rule`() {
        var state = tap(quickState(), "h36", "h06")
        assertEquals(FeetRule.FEET_FOLLOW_MARKED, state.draft.feetRule)

        state = tap(state, "h37")
        assertEquals(FeetRule.MARKED_ONLY, state.draft.feetRule)

        state = SetterReducer.undo(state)
        assertEquals(FeetRule.FEET_FOLLOW_MARKED, state.draft.feetRule)
    }

    @Test
    fun `editing an existing problem treats its feet rule as hand-picked`() {
        val edit = SetterReducer.start(
            DraftProblem(editingProblemId = "existing", feetRule = FeetRule.ANY_FEET),
            SetterMode.QUICK,
        )
        assertTrue(edit.feetRuleTouched)

        val state = tap(edit, "h37")
        assertEquals(FeetRule.ANY_FEET, state.draft.feetRule)
    }

    @Test
    fun `fresh quick sessions have nothing hand-picked`() {
        assertFalse(quickState().feetRuleTouched)
    }
}
