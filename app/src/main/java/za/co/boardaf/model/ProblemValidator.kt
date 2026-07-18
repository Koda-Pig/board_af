package za.co.boardaf.model

enum class IssueSeverity { ERROR, WARNING }

enum class IssueCode(val severity: IssueSeverity) {
    NAME_MISSING(IssueSeverity.ERROR),
    NO_START(IssueSeverity.ERROR),
    TOO_MANY_STARTS(IssueSeverity.ERROR),
    START_NOT_HAND_CAPABLE(IssueSeverity.ERROR),
    START_IN_KICKBOARD(IssueSeverity.ERROR),
    NO_FINISH(IssueSeverity.ERROR),
    TOO_MANY_FINISHES(IssueSeverity.ERROR),
    FINISH_NOT_HAND_CAPABLE(IssueSeverity.ERROR),
    FINISH_IN_KICKBOARD(IssueSeverity.ERROR),
    DUPLICATE_ASSIGNMENT(IssueSeverity.ERROR),
    REGULAR_NOT_HAND_CAPABLE(IssueSeverity.ERROR),
    FOOT_NOT_FOOT_CAPABLE(IssueSeverity.ERROR),
    CAMPUS_WITH_FOOT_MARKS(IssueSeverity.ERROR),
    OPEN_KICKBOARD_WITHOUT_KICKBOARD(IssueSeverity.ERROR),
    UNKNOWN_HOLD(IssueSeverity.ERROR),
    START_RULE_MISMATCH(IssueSeverity.ERROR),
    FINISH_RULE_MISMATCH(IssueSeverity.ERROR),
    FOOT_MARKS_IGNORED_BY_RULE(IssueSeverity.WARNING),
    KICKBOARD_FOOT_REDUNDANT(IssueSeverity.WARNING),
}

data class ProblemIssue(
    val code: IssueCode,
    val holdId: String? = null,
    val message: String,
) {
    val severity: IssueSeverity get() = code.severity
}

object ProblemValidator {

    fun validate(spec: ProblemSpec, board: ConfiguredBoard): List<ProblemIssue> {
        val issues = mutableListOf<ProblemIssue>()

        if (spec.name.isBlank()) {
            issues += ProblemIssue(IssueCode.NAME_MISSING, message = "Give the problem a name.")
        }

        spec.assignments
            .groupBy { it.holdId }
            .filterValues { it.size > 1 }
            .keys
            .forEach { holdId ->
                issues += ProblemIssue(
                    IssueCode.DUPLICATE_ASSIGNMENT,
                    holdId,
                    "$holdId has more than one role. Keep a single role per hold.",
                )
            }

        val known = mutableListOf<Pair<ProblemAssignment, ConfiguredHold>>()
        spec.assignments.forEach { assignment ->
            val hold = board.holdsById[assignment.holdId]
            if (hold == null) {
                issues += ProblemIssue(
                    IssueCode.UNKNOWN_HOLD,
                    assignment.holdId,
                    "${assignment.holdId} is not on this board. Remove the assignment.",
                )
            } else {
                known += assignment to hold
            }
        }

        val starts = known.filter { it.first.role == ProblemHoldRole.START }
        val finishes = known.filter { it.first.role == ProblemHoldRole.FINISH }
        val startCount = spec.assignments.count { it.role == ProblemHoldRole.START }
        val finishCount = spec.assignments.count { it.role == ProblemHoldRole.FINISH }

        when {
            startCount == 0 -> issues += ProblemIssue(IssueCode.NO_START, message = "Mark one or two start holds.")
            startCount > 2 -> issues += ProblemIssue(IssueCode.TOO_MANY_STARTS, message = "Use at most two start holds.")
            spec.startRule == StartRule.MATCH_ONE && startCount == 2 || spec.startRule == StartRule.SPLIT_TWO && startCount == 1 ->
                issues += ProblemIssue(
                    IssueCode.START_RULE_MISMATCH,
                    message = "The start rule says ${spec.startRule.label.lowercase()} but $startCount start hold(s) are marked.",
                )
        }
        when {
            finishCount == 0 -> issues += ProblemIssue(IssueCode.NO_FINISH, message = "Mark one or two finish holds.")
            finishCount > 2 -> issues += ProblemIssue(IssueCode.TOO_MANY_FINISHES, message = "Use at most two finish holds.")
            spec.finishRule == FinishRule.MATCH_ONE && finishCount == 2 || spec.finishRule == FinishRule.CONTROL_TWO && finishCount == 1 ->
                issues += ProblemIssue(
                    IssueCode.FINISH_RULE_MISMATCH,
                    message = "The finish rule says ${spec.finishRule.label.lowercase()} but $finishCount finish hold(s) are marked.",
                )
        }

        starts.forEach { (assignment, hold) ->
            if (!hold.capability.allowsHands) {
                issues += ProblemIssue(
                    IssueCode.START_NOT_HAND_CAPABLE,
                    assignment.holdId,
                    "${assignment.holdId} is foot-only. Move the start to a hand-capable hold.",
                )
            }
            if (hold.zone == BoardZoneType.KICKBOARD) {
                issues += ProblemIssue(
                    IssueCode.START_IN_KICKBOARD,
                    assignment.holdId,
                    "${assignment.holdId} is on the kickboard. Move the start onto the main board.",
                )
            }
        }
        finishes.forEach { (assignment, hold) ->
            if (!hold.capability.allowsHands) {
                issues += ProblemIssue(
                    IssueCode.FINISH_NOT_HAND_CAPABLE,
                    assignment.holdId,
                    "${assignment.holdId} is foot-only. Move the finish to a hand-capable hold.",
                )
            }
            if (hold.zone == BoardZoneType.KICKBOARD) {
                issues += ProblemIssue(
                    IssueCode.FINISH_IN_KICKBOARD,
                    assignment.holdId,
                    "${assignment.holdId} is on the kickboard. Move the finish onto the main board.",
                )
            }
        }

        known.forEach { (assignment, hold) ->
            when (assignment.role) {
                ProblemHoldRole.REGULAR -> if (!hold.capability.allowsHands) {
                    issues += ProblemIssue(
                        IssueCode.REGULAR_NOT_HAND_CAPABLE,
                        assignment.holdId,
                        "${assignment.holdId} is foot-only. Mark it Foot only, or pick another hold.",
                    )
                }
                ProblemHoldRole.FOOT_ONLY -> if (!hold.capability.allowsFeet) {
                    issues += ProblemIssue(
                        IssueCode.FOOT_NOT_FOOT_CAPABLE,
                        assignment.holdId,
                        "${assignment.holdId} cannot be used with feet. Remove the foot mark.",
                    )
                }
                else -> Unit
            }
        }

        val footMarks = known.filter { it.first.role == ProblemHoldRole.FOOT_ONLY }
        when (spec.feetRule) {
            FeetRule.CAMPUS -> footMarks.forEach { (assignment, _) ->
                issues += ProblemIssue(
                    IssueCode.CAMPUS_WITH_FOOT_MARKS,
                    assignment.holdId,
                    "Campus problems have no feet. Remove the foot mark on ${assignment.holdId} or change the feet rule.",
                )
            }
            FeetRule.ANY_FEET, FeetRule.FEET_FOLLOW_MARKED -> if (footMarks.isNotEmpty()) {
                issues += ProblemIssue(
                    IssueCode.FOOT_MARKS_IGNORED_BY_RULE,
                    message = "Foot-only marks have no effect under “${spec.feetRule.label}”.",
                )
            }
            FeetRule.OPEN_KICKBOARD -> footMarks
                .filter { it.second.zone == BoardZoneType.KICKBOARD }
                .forEach { (assignment, _) ->
                    issues += ProblemIssue(
                        IssueCode.KICKBOARD_FOOT_REDUNDANT,
                        assignment.holdId,
                        "The kickboard is already open for feet; the mark on ${assignment.holdId} is redundant.",
                    )
                }
            FeetRule.MARKED_ONLY -> Unit
        }

        if (spec.feetRule == FeetRule.OPEN_KICKBOARD && !board.hasKickboard) {
            issues += ProblemIssue(
                IssueCode.OPEN_KICKBOARD_WITHOUT_KICKBOARD,
                message = "This board has no kickboard. Choose another feet rule.",
            )
        }

        return issues
    }

    fun validate(problem: Problem, board: ConfiguredBoard): List<ProblemIssue> =
        validate(problem.toSpec(), board)

    fun hasErrors(issues: List<ProblemIssue>): Boolean =
        issues.any { it.severity == IssueSeverity.ERROR }

    /** Publishing requires valid data plus an explicit successful-forerun confirmation. */
    fun canPublish(issues: List<ProblemIssue>, forerunConfirmed: Boolean): Boolean =
        !hasErrors(issues) && forerunConfirmed

    /** Lifecycle for problems that already exist (migration, board reconfiguration, edits). */
    fun resolveState(current: PublicationState, issues: List<ProblemIssue>): PublicationState = when {
        !hasErrors(issues) -> current
        current == PublicationState.PUBLISHED || current == PublicationState.BENCHMARK -> PublicationState.NEEDS_REVIEW
        current == PublicationState.ARCHIVED -> PublicationState.ARCHIVED
        current == PublicationState.DRAFT -> PublicationState.DRAFT
        else -> PublicationState.NEEDS_REVIEW
    }
}
