package za.co.boardaf.setter

import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.DraftProblem
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole

enum class SetterMode(val label: String) {
    GUIDED("Guided"),
    QUICK("Quick"),
}

enum class GuidedStep(val title: String, val hint: String) {
    FEET_RULE("Feet rule", "Choose how feet work for the whole problem."),
    START("Start holds", "Tap one or two start holds on the main board."),
    FINISH("Finish holds", "Tap one or two finish holds."),
    MOVEMENT("Regular and feet", "Fill in the line with regular holds and any foot-only marks."),
    ;

    val roleForStep: ProblemHoldRole?
        get() = when (this) {
            FEET_RULE -> null
            START -> ProblemHoldRole.START
            FINISH -> ProblemHoldRole.FINISH
            MOVEMENT -> ProblemHoldRole.REGULAR
        }
}

data class TapRejection(
    val holdId: String,
    val role: ProblemHoldRole,
    val message: String,
    val offerFootInstead: Boolean,
)

data class SetterState(
    val draft: DraftProblem = DraftProblem(),
    val activeRole: ProblemHoldRole = ProblemHoldRole.START,
    val mode: SetterMode = SetterMode.GUIDED,
    val guidedStep: GuidedStep = GuidedStep.FEET_RULE,
    val isReviewing: Boolean = false,
    val undoStack: List<List<ProblemAssignment>> = emptyList(),
    val redoStack: List<List<ProblemAssignment>> = emptyList(),
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}

data class TapResult(
    val state: SetterState,
    val rejection: TapRejection? = null,
)

/** Pure assign/change/remove/undo/redo behavior for the setter session. */
object SetterReducer {
    const val HISTORY_LIMIT = 50

    fun start(draft: DraftProblem, mode: SetterMode): SetterState = SetterState(
        draft = draft,
        mode = mode,
        activeRole = if (mode == SetterMode.GUIDED) {
            ProblemHoldRole.START
        } else {
            ProblemHoldRole.REGULAR
        },
        guidedStep = GuidedStep.FEET_RULE,
    )

    fun tapHold(state: SetterState, holdId: String, board: ConfiguredBoard): TapResult {
        val role = state.activeRole
        val existing = state.draft.assignments.firstOrNull { it.holdId == holdId }

        if (existing?.role == role) {
            return TapResult(
                withHistory(state) { assignments -> assignments.filterNot { it.holdId == holdId } },
            )
        }

        val hold = board.holdsById[holdId]
        val needsHands = role == ProblemHoldRole.START ||
            role == ProblemHoldRole.REGULAR ||
            role == ProblemHoldRole.FINISH
        if (hold != null && needsHands && !hold.capability.allowsHands) {
            return TapResult(
                state,
                TapRejection(
                    holdId = holdId,
                    role = role,
                    message = "$holdId is a foot-only ${hold.zone.label.lowercase()} hold — it can't be a ${role.label.lowercase()} hold.",
                    offerFootInstead = hold.capability.allowsFeet &&
                        existing?.role != ProblemHoldRole.FOOT_ONLY,
                ),
            )
        }
        if (hold != null && role == ProblemHoldRole.FOOT_ONLY && !hold.capability.allowsFeet) {
            return TapResult(
                state,
                TapRejection(
                    holdId = holdId,
                    role = role,
                    message = "$holdId can't be used with feet.",
                    offerFootInstead = false,
                ),
            )
        }

        return TapResult(
            withHistory(state) { assignments ->
                if (existing != null) {
                    assignments.map { if (it.holdId == holdId) it.copy(role = role) else it }
                } else {
                    assignments + ProblemAssignment(holdId, role)
                }
            },
        )
    }

    fun markFootInstead(state: SetterState, holdId: String): SetterState =
        withHistory(state) { assignments ->
            val existing = assignments.firstOrNull { it.holdId == holdId }
            if (existing != null) {
                assignments.map { if (it.holdId == holdId) it.copy(role = ProblemHoldRole.FOOT_ONLY) else it }
            } else {
                assignments + ProblemAssignment(holdId, ProblemHoldRole.FOOT_ONLY)
            }
        }

    fun clearAssignments(state: SetterState): SetterState =
        if (state.draft.assignments.isEmpty()) state else withHistory(state) { emptyList() }

    fun undo(state: SetterState): SetterState {
        val entry = state.undoStack.lastOrNull() ?: return state
        return state.copy(
            draft = state.draft.copy(assignments = entry),
            undoStack = state.undoStack.dropLast(1),
            redoStack = state.redoStack + listOf(state.draft.assignments),
        )
    }

    fun redo(state: SetterState): SetterState {
        val entry = state.redoStack.lastOrNull() ?: return state
        return state.copy(
            draft = state.draft.copy(assignments = entry),
            redoStack = state.redoStack.dropLast(1),
            undoStack = state.undoStack + listOf(state.draft.assignments),
        )
    }

    fun selectRole(state: SetterState, role: ProblemHoldRole): SetterState =
        state.copy(activeRole = role)

    fun setFeetRule(state: SetterState, feetRule: FeetRule): SetterState =
        state.copy(draft = state.draft.copy(feetRule = feetRule))

    fun setMode(state: SetterState, mode: SetterMode): SetterState =
        if (mode == state.mode) {
            state
        } else {
            state.copy(
                mode = mode,
                guidedStep = GuidedStep.FEET_RULE,
                activeRole = if (mode == SetterMode.GUIDED) ProblemHoldRole.START else state.activeRole,
            )
        }

    fun goToStep(state: SetterState, step: GuidedStep): SetterState = state.copy(
        guidedStep = step,
        activeRole = step.roleForStep ?: state.activeRole,
    )

    fun nextStep(state: SetterState): SetterState {
        val next = GuidedStep.entries.getOrNull(state.guidedStep.ordinal + 1) ?: return state
        return goToStep(state, next)
    }

    fun previousStep(state: SetterState): SetterState {
        val previous = GuidedStep.entries.getOrNull(state.guidedStep.ordinal - 1) ?: return state
        return goToStep(state, previous)
    }

    private fun withHistory(
        state: SetterState,
        transform: (List<ProblemAssignment>) -> List<ProblemAssignment>,
    ): SetterState = state.copy(
        draft = state.draft.copy(assignments = transform(state.draft.assignments)),
        undoStack = (state.undoStack + listOf(state.draft.assignments)).takeLast(HISTORY_LIMIT),
        redoStack = emptyList(),
    )
}
