package za.co.boardaf.setter

import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.DraftProblem
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole

/**
 * How holds are entered during a setter session. GUIDED walks the step wizard;
 * QUICK is a single canvas where taps toggle membership and roles are inferred.
 * Also persisted in snapshots/cloud docs as the preferred entry mode.
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
    val mode: SetterMode = SetterMode.GUIDED,
    val activeRole: ProblemHoldRole = ProblemHoldRole.START,
    val guidedStep: GuidedStep = GuidedStep.FEET_RULE,
    /**
     * True once the setter picked a feet rule by hand this session; quick-set
     * inference then stops adjusting it on membership changes.
     */
    val feetRuleTouched: Boolean = false,
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

    /**
     * Holds whose normalized-y differ by less than this sit on "the same row" for
     * quick-set inference. Real rows on this wall are ≥ ~0.03 apart and holds
     * within one row are ≤ ~0.005 apart.
     */
    const val ROW_TOLERANCE = 0.02f

    /**
     * New empty drafts walk the wizard from the top. Library edits and drafts with
     * enough content to persist land on details & review.
     */
    fun start(draft: DraftProblem, mode: SetterMode = SetterMode.GUIDED): SetterState {
        val entry = if (draft.editingProblemId != null || draft.hasContent) {
            GuidedStep.DETAILS
        } else {
            GuidedStep.FEET_RULE
        }
        return SetterState(
            draft = draft,
            mode = mode,
            guidedStep = entry,
            activeRole = entry.roleForStep ?: ProblemHoldRole.START,
            // An edited problem's feet rule is a past decision, not this session's
            // inference output — treat it as touched so quick taps don't rewrite it.
            feetRuleTouched = draft.editingProblemId != null || draft.hasContent,
        )
    }

    /**
     * Switching entry modes keeps the draft. Entering quick mode does NOT re-infer
     * existing roles — inference only runs on membership changes, so a line built
     * in the wizard survives the switch untouched.
     */
    fun setMode(state: SetterState, mode: SetterMode): SetterState = state.copy(mode = mode)

    fun stepSatisfied(draft: DraftProblem, step: GuidedStep): Boolean = when (step) {
        GuidedStep.FEET_RULE, GuidedStep.OTHER, GuidedStep.DETAILS -> true
        GuidedStep.START -> draft.countFor(ProblemHoldRole.START) in 1..ROLE_CAP
        GuidedStep.FINISH -> draft.countFor(ProblemHoldRole.FINISH) in 1..ROLE_CAP
    }

    /** First step whose requirement is unmet, or null once the whole line is set. */
    fun firstUnsatisfiedStep(draft: DraftProblem): GuidedStep? =
        GuidedStep.entries.firstOrNull { !stepSatisfied(draft, it) }

    /**
     * Quick set: a tap toggles hold membership and roles are re-inferred for the
     * whole set. Any hold is accepted — foot-only holds simply infer as feet — so
     * unlike the wizard there is nothing to reject.
     */
    fun quickTapHold(state: SetterState, holdId: String, board: ConfiguredBoard): TapResult {
        val currentIds = state.draft.assignments.map { it.holdId }
        val nextIds = if (holdId in currentIds) currentIds - holdId else currentIds + holdId
        val inferred = inferAssignments(nextIds, board)
        val next = withHistory(state) { inferred }
        return TapResult(
            state = if (next.feetRuleTouched) {
                next
            } else {
                next.copy(draft = next.draft.copy(feetRule = inferFeetRule(inferred)))
            },
        )
    }

    /**
     * Role inference for quick set. Foot-only holds become feet; hand-capable
     * kickboard holds can't legally start or finish, so they join the middle of
     * the line. On the main board the whole lowest row starts and the whole
     * topmost row finishes — ties are kept, never trimmed to a pair.
     */
    fun inferAssignments(holdIds: List<String>, board: ConfiguredBoard): List<ProblemAssignment> {
        val holds = holdIds.mapNotNull { board.holdsById[it] }
        val lineHolds = holds.filter {
            it.capability.allowsHands && it.zone == BoardZoneType.MAIN
        }
        val bottomY = lineHolds.maxOfOrNull { it.point.y }
        val topY = lineHolds.minOfOrNull { it.point.y }
        return holds.map { hold ->
            val role = when {
                !hold.capability.allowsHands -> ProblemHoldRole.FOOT_ONLY
                hold.zone == BoardZoneType.KICKBOARD -> ProblemHoldRole.REGULAR
                // Lowest row wins first: a one-row line is all start, not all finish.
                bottomY != null && hold.point.y >= bottomY - ROW_TOLERANCE -> ProblemHoldRole.START
                topY != null && hold.point.y <= topY + ROW_TOLERANCE -> ProblemHoldRole.FINISH
                else -> ProblemHoldRole.REGULAR
            }
            ProblemAssignment(hold.id, role)
        }
    }

    /** Foot marks in the set mean they matter; otherwise feet just follow the line. */
    fun inferFeetRule(assignments: List<ProblemAssignment>): FeetRule =
        if (assignments.any { it.role == ProblemHoldRole.FOOT_ONLY }) {
            FeetRule.MARKED_ONLY
        } else {
            FeetRule.FEET_FOLLOW_MARKED
        }

    fun tapHold(state: SetterState, holdId: String, board: ConfiguredBoard): TapResult {
        // Feet rule and details & review assign no role and render no palette, so a
        // tap there would land on whatever role happened to be active — START on a
        // library edit — and reject foot-only holds for a reason the setter can't see.
        if (state.guidedStep.roleForStep == null) {
            return TapResult(
                state,
                notice = "Holds are locked on ${state.guidedStep.title}. " +
                    "Go back to ${GuidedStep.OTHER.title} to change them.",
            )
        }

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
                    message = "That hold is foot-only — it can't be a ${role.label.lowercase()} hold.",
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
                    message = "That hold can't be used with feet.",
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
            notice = existing?.let { "Hold changed from ${it.role.label} to ${role.label}." },
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
        return reinferFeetAfterHistoryJump(
            state.copy(
                draft = state.draft.copy(assignments = entry),
                undoStack = state.undoStack.dropLast(1),
                redoStack = state.redoStack + listOf(state.draft.assignments),
            ),
        )
    }

    fun redo(state: SetterState): SetterState {
        val entry = state.redoStack.lastOrNull() ?: return state
        return reinferFeetAfterHistoryJump(
            state.copy(
                draft = state.draft.copy(assignments = entry),
                redoStack = state.redoStack.dropLast(1),
                undoStack = state.undoStack + listOf(state.draft.assignments),
            ),
        )
    }

    /**
     * History entries hold the assignments (roles included) but not the feet rule,
     * so a quick-set undo/redo re-infers feet from the restored set. Roles need no
     * re-inference: each snapshot already matches its own membership.
     */
    private fun reinferFeetAfterHistoryJump(state: SetterState): SetterState =
        if (state.mode == SetterMode.QUICK && !state.feetRuleTouched) {
            state.copy(draft = state.draft.copy(feetRule = inferFeetRule(state.draft.assignments)))
        } else {
            state
        }

    fun selectRole(state: SetterState, role: ProblemHoldRole): SetterState =
        state.copy(activeRole = role)

    /** Campus has no feet: switching to it drops existing foot marks (undoable). */
    fun setFeetRule(state: SetterState, feetRule: FeetRule): SetterState {
        val next = state.copy(
            draft = state.draft.copy(feetRule = feetRule),
            feetRuleTouched = true,
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
