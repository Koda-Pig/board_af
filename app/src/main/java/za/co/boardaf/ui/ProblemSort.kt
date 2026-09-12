package za.co.boardaf.ui

import za.co.boardaf.model.Problem

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
