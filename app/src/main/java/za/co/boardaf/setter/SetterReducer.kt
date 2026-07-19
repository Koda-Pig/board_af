package za.co.boardaf.setter

import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.DraftProblem
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole

/**
 * Retained only so v2 snapshots and cloud board docs keep round-tripping.
 * The setter session itself always runs the guided wizard now.
 */
enum class SetterMode(val label: String) {
    GUIDED("Guided"),
    QUICK("Quick"),
}

enum class GuidedStep(
    val title: String,
    val hint: String,
    /** Shown instead of the hint while this step blocks forward progress. */
    val gateHint: String? = null,
) {
    FEET_RULE("Feet rule", "Choose how feet work for the whole problem."),
    START(
        "Start holds",
        "Tap one or two start holds on the main board.",
        "Mark one or two start holds to continue.",
    ),
    OTHER("Other holds", "Fill in the line between start and finish."),
    FINISH(
        "Finish holds",
        "Tap one or two finish holds.",
        "Mark one or two finish holds to continue.",
    ),
    DETAILS("Details & review", "Name it, grade it, then save or publish."),
    ;

    val roleForStep: ProblemHoldRole?
        get() = when (this) {
            FEET_RULE, DETAILS -> null
            START -> ProblemHoldRole.START
            OTHER -> ProblemHoldRole.REGULAR
            FINISH -> ProblemHoldRole.FINISH
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
    val guidedStep: GuidedStep = GuidedStep.FEET_RULE,
    val undoStack: List<List<ProblemAssignment>> = emptyList(),
    val redoStack: List<List<ProblemAssignment>> = emptyList(),
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}

data class TapResult(
    val state: SetterState,
    val rejection: TapRejection? = null,
    /** Something changed that the setter didn't literally ask for; worth announcing. */
    val notice: String? = null,
)

/** Pure assign/change/remove/undo/redo and wizard-step behavior for the setter session. */
object SetterReducer {
    const val HISTORY_LIMIT = 50

    /** The validator's bound on start and finish holds, enforced here at tap time. */
    const val ROLE_CAP = 2

    /** New drafts walk the wizard from the top; anything with content lands on review. */
    fun start(draft: DraftProblem): SetterState {
        val entry = if (draft.hasContent) GuidedStep.DETAILS else GuidedStep.FEET_RULE
        return SetterState(
            draft = draft,
            guidedStep = entry,
            activeRole = entry.roleForStep ?: ProblemHoldRole.START,
        )
    }

    fun stepSatisfied(draft: DraftProblem, step: GuidedStep): Boolean = when (step) {
        GuidedStep.FEET_RULE, GuidedStep.OTHER, GuidedStep.DETAILS -> true
        GuidedStep.START -> draft.countFor(ProblemHoldRole.START) in 1..ROLE_CAP
        GuidedStep.FINISH -> draft.countFor(ProblemHoldRole.FINISH) in 1..ROLE_CAP
    }

    /** First step whose requirement is unmet, or null once the whole line is set. */
    fun firstUnsatisfiedStep(draft: DraftProblem): GuidedStep? =
        GuidedStep.entries.firstOrNull { !stepSatisfied(draft, it) }

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
                        state.draft.feetRule.usesFootMarks &&
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

        val capped = (role == ProblemHoldRole.START || role == ProblemHoldRole.FINISH) &&
            state.draft.countFor(role) >= ROLE_CAP
        if (capped) {
            return TapResult(
                state,
                TapRejection(
                    holdId = holdId,
                    role = role,
                    message = "Two ${role.label.lowercase()} holds max — tap a marked one to remove it first.",
                    offerFootInstead = false,
                ),
            )
        }

        return TapResult(
            state = withHistory(state) { assignments ->
                if (existing != null) {
                    assignments.map { if (it.holdId == holdId) it.copy(role = role) else it }
                } else {
                    assignments + ProblemAssignment(holdId, role)
                }
            },
            notice = existing?.let { "$holdId changed from ${it.role.label} to ${role.label}." },
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

    /** Campus has no feet: switching to it drops existing foot marks (undoable). */
    fun setFeetRule(state: SetterState, feetRule: FeetRule): SetterState {
        val next = state.copy(
            draft = state.draft.copy(feetRule = feetRule),
            activeRole = if (state.activeRole == ProblemHoldRole.FOOT_ONLY && !feetRule.usesFootMarks) {
                ProblemHoldRole.REGULAR
            } else {
                state.activeRole
            },
        )
        val hasFootMarks = next.draft.assignments.any { it.role == ProblemHoldRole.FOOT_ONLY }
        return if (feetRule == FeetRule.CAMPUS && hasFootMarks) {
            withHistory(next) { assignments -> assignments.filterNot { it.role == ProblemHoldRole.FOOT_ONLY } }
        } else {
            next
        }
    }

    /** Backward jumps are always free; forward jumps stop at the first unsatisfied step. */
    fun goToStep(state: SetterState, step: GuidedStep): SetterState {
        val forward = step.ordinal > state.guidedStep.ordinal
        val reachable = firstUnsatisfiedStep(state.draft) ?: GuidedStep.entries.last()
        if (forward && step.ordinal > reachable.ordinal) return state
        return state.copy(
            guidedStep = step,
            activeRole = step.roleForStep ?: state.activeRole,
        )
    }

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
