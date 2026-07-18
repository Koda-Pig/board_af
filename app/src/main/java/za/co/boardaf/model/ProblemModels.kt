package za.co.boardaf.model

/** A hold's role inside one problem, independent from the hold's physical capability. */
enum class ProblemHoldRole(val label: String) {
    START("Start"),
    REGULAR("Regular"),
    FOOT_ONLY("Foot only"),
    FINISH("Finish"),
}

enum class FeetRule(val label: String, val description: String) {
    MARKED_ONLY("Marked feet only", "Feet are allowed on marked holds only."),
    OPEN_KICKBOARD("Open kickboard", "Feet on marked holds, plus any hold on the kickboard."),
    FEET_FOLLOW_MARKED("Feet follow marked", "Feet only on the marked hand holds — start, regular and finish."),
    ANY_FEET("Any feet", "Feet anywhere on the wall."),
    CAMPUS("Campus", "No feet at all."),
}

enum class StartRule(val label: String, val description: String) {
    MATCH_ONE("Match the start", "Start with both hands on the single start hold."),
    SPLIT_TWO("Two start holds", "Start with one hand on each start hold."),
}

enum class FinishRule(val label: String, val description: String) {
    MATCH_ONE("Match the finish", "Finish with both hands on the single finish hold."),
    CONTROL_TWO("Two finish holds", "Finish in control of both finish holds."),
}

enum class PublicationState(val label: String) {
    DRAFT("Draft"),
    NEEDS_REVIEW("Needs review"),
    PUBLISHED("Published"),
    BENCHMARK("Benchmark"),
    ARCHIVED("Archived"),
}

data class ProblemAssignment(
    val holdId: String,
    val role: ProblemHoldRole,
)

fun startRuleFor(assignments: List<ProblemAssignment>): StartRule =
    if (assignments.count { it.role == ProblemHoldRole.START } >= 2) StartRule.SPLIT_TWO else StartRule.MATCH_ONE

fun finishRuleFor(assignments: List<ProblemAssignment>): FinishRule =
    if (assignments.count { it.role == ProblemHoldRole.FINISH } >= 2) FinishRule.CONTROL_TWO else FinishRule.MATCH_ONE

/** What the validator sees; both saved problems and in-progress drafts reduce to this. */
data class ProblemSpec(
    val name: String,
    val feetRule: FeetRule,
    val startRule: StartRule,
    val finishRule: FinishRule,
    val assignments: List<ProblemAssignment>,
)

data class Problem(
    val id: String,
    val name: String,
    val grade: BoulderGrade,
    val accent: Accent,
    val setter: String,
    val note: String,
    val tags: List<String> = emptyList(),
    val feetRule: FeetRule = FeetRule.MARKED_ONLY,
    val startRule: StartRule = StartRule.MATCH_ONE,
    val finishRule: FinishRule = FinishRule.MATCH_ONE,
    val publicationState: PublicationState = PublicationState.DRAFT,
    val forerunConfirmedAt: Long? = null,
    val assignments: List<ProblemAssignment> = emptyList(),
) {
    fun toSpec() = ProblemSpec(name, feetRule, startRule, finishRule, assignments)
}

data class DraftProblem(
    val editingProblemId: String? = null,
    val name: String = "",
    val grade: BoulderGrade = BoulderGrade.F6A,
    val accent: Accent = Accent.SKY,
    val note: String = "",
    val tags: List<String> = emptyList(),
    val feetRule: FeetRule = FeetRule.MARKED_ONLY,
    val assignments: List<ProblemAssignment> = emptyList(),
    val baseState: PublicationState? = null,
    val setter: String = "You",
    val forerunConfirmedAt: Long? = null,
) {
    val startRule: StartRule get() = startRuleFor(assignments)
    val finishRule: FinishRule get() = finishRuleFor(assignments)

    val hasContent: Boolean
        get() = name.isNotBlank() || note.isNotBlank() || assignments.isNotEmpty()

    fun countFor(role: ProblemHoldRole): Int = assignments.count { it.role == role }

    fun toSpec() = ProblemSpec(name, feetRule, startRule, finishRule, assignments)

    fun toProblem(id: String, publicationState: PublicationState): Problem = Problem(
        id = id,
        name = name.trim(),
        grade = grade,
        accent = accent,
        setter = setter,
        note = note.trim(),
        tags = tags,
        feetRule = feetRule,
        startRule = startRule,
        finishRule = finishRule,
        publicationState = publicationState,
        forerunConfirmedAt = forerunConfirmedAt,
        assignments = assignments,
    )

    companion object {
        fun fromProblem(problem: Problem): DraftProblem = DraftProblem(
            editingProblemId = problem.id,
            name = problem.name,
            grade = problem.grade,
            accent = problem.accent,
            note = problem.note,
            tags = problem.tags,
            feetRule = problem.feetRule,
            assignments = problem.assignments,
            baseState = problem.publicationState,
            setter = problem.setter,
            forerunConfirmedAt = problem.forerunConfirmedAt,
        )

        fun duplicateOf(problem: Problem): DraftProblem = fromProblem(problem).copy(
            editingProblemId = null,
            name = "${problem.name} copy",
            baseState = null,
            forerunConfirmedAt = null,
        )
    }
}

object ProblemTags {
    val suggestions = listOf("Project", "Warm-up", "Technical", "Power")
}
