package za.co.boardaf.ui

import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.Problem
import za.co.boardaf.model.PublicationState

/**
 * Orderings offered by the problem library.
 *
 * "Newest"/"Oldest" ride on the library's own order rather than on a stored
 * timestamp. A [Problem] has no created-at field, and `BoardViewModel` keeps the
 * list newest-first — both a freshly set problem and one newly adopted from the
 * cloud are prepended. Adding a timestamp to the record would change every
 * problem's canonical sync encoding, so the next sync would read as a
 * library-wide edit; that is far too much risk to take on for a listing option.
 */
enum class ProblemSort(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    GRADE_EASIEST("Easiest first"),
    GRADE_HARDEST("Hardest first"),
    ANGLE_FLATTEST("Flattest first"),
    ANGLE_STEEPEST("Steepest first"),
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    SETTER("Setter A–Z"),
    HOLDS_FEWEST("Fewest holds"),
    HOLDS_MOST("Most holds"),
    ;

    companion object {
        val DEFAULT = NEWEST
    }
}

/**
 * Orders [problems], which must arrive in library order (newest first). Every
 * comparator here is stable, so problems that tie keep that order rather than
 * shuffling between recompositions — grade and angle in particular tie a lot.
 */
fun sortProblems(problems: List<Problem>, sort: ProblemSort): List<Problem> = when (sort) {
    ProblemSort.NEWEST -> problems
    ProblemSort.OLDEST -> problems.reversed()
    // Ordinal, never the label: in the V-scale several French grades share one
    // label (F6A and F6A+ are both "V3"), so a label sort would tie grades that
    // differ and reorder the library when the display system changes.
    ProblemSort.GRADE_EASIEST -> problems.sortedBy { it.grade.ordinal }
    ProblemSort.GRADE_HARDEST -> problems.sortedByDescending { it.grade.ordinal }
    ProblemSort.ANGLE_FLATTEST -> problems.sortedBy { it.angleDegrees }
    ProblemSort.ANGLE_STEEPEST -> problems.sortedByDescending { it.angleDegrees }
    ProblemSort.NAME_ASC -> problems.sortedWith(byText(descending = false) { it.name })
    ProblemSort.NAME_DESC -> problems.sortedWith(byText(descending = true) { it.name })
    ProblemSort.SETTER -> problems.sortedWith(byText(descending = false) { it.setter })
    ProblemSort.HOLDS_FEWEST -> problems.sortedBy { it.assignments.size }
    ProblemSort.HOLDS_MOST -> problems.sortedByDescending { it.assignments.size }
}

/**
 * Case-insensitive text ordering that keeps blanks last in *both* directions.
 * An untitled draft has no name to file under, and letting the reverse order
 * float those to the top would bury every named problem behind them.
 */
private fun byText(descending: Boolean, selector: (Problem) -> String): Comparator<Problem> {
    val order = if (descending) {
        String.CASE_INSENSITIVE_ORDER.reversed()
    } else {
        String.CASE_INSENSITIVE_ORDER
    }
    return compareBy(nullsLast(order)) { problem: Problem -> selector(problem).trim().ifBlank { null } }
}

/**
 * The listing the problem library is showing: which problems pass its filters,
 * and in what order.
 *
 * This lives in `BoardUiState` rather than in `ProblemsScreen` because swiping
 * between problems on the board has to walk the *same* list. Held only by the
 * screen, the board fell back to raw library order and swiped straight into
 * problems the filters were hiding.
 *
 * The filters are the chip labels themselves rather than typed values: the
 * options are derived from the library (setters, angles) or from the current
 * grade system, so a label is the only thing all five have in common.
 */
data class ProblemLibraryQuery(
    val text: String = "",
    val sort: ProblemSort = ProblemSort.DEFAULT,
    val status: String = STATUS_ACTIVE,
    val grade: String = ANY,
    val angle: String = ANY,
    val feetRule: String = ANY,
    val setter: String = ANY,
) {
    /** Whether the listing is narrower than the library's default view. */
    val isFiltering: Boolean
        get() = status != STATUS_ACTIVE ||
            grade != ANY ||
            angle != ANY ||
            feetRule != ANY ||
            setter != ANY ||
            text.isNotBlank()

    fun matches(problem: Problem, gradeSystem: GradeSystem): Boolean {
        val statusOk = when (status) {
            STATUS_ACTIVE -> problem.publicationState != PublicationState.ARCHIVED
            ANY -> true
            else -> problem.publicationState.label == status
        }
        return statusOk &&
            (grade == ANY || problem.grade.label(gradeSystem) == grade) &&
            (angle == ANY || angleLabel(problem.angleDegrees) == angle) &&
            (feetRule == ANY || problem.feetRule.label == feetRule) &&
            (setter == ANY || problem.setter == setter) &&
            problemDisplayName(problem.name).contains(text, ignoreCase = true)
    }

    /**
     * The problems the library lists, filtered and ordered exactly as rendered.
     * [problems] must arrive in library order (newest first), as [sortProblems] requires.
     */
    fun visible(problems: List<Problem>, gradeSystem: GradeSystem): List<Problem> =
        sortProblems(problems.filter { matches(it, gradeSystem) }, sort)

    /** Clears every filter but keeps the search text and the chosen ordering. */
    fun cleared(): ProblemLibraryQuery =
        copy(status = STATUS_ACTIVE, grade = ANY, angle = ANY, feetRule = ANY, setter = ANY)

    companion object {
        /** The "no restriction" option every filter row offers. */
        const val ANY = "All"

        /** Default status: everything that hasn't been archived. */
        const val STATUS_ACTIVE = "Active"

        fun angleLabel(degrees: Int): String = "$degrees°"
    }
}
