package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Test
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.ui.ProblemSort
import za.co.boardaf.ui.sortProblems

/**
 * The library list arrives newest-first, which is what "added" means here — see
 * [ProblemSort]. Every ordering is checked against that input order so a future
 * change to the comparators can't quietly start shuffling ties.
 */
class ProblemSortTest {

    private fun problem(
        id: String,
        name: String = id,
        grade: BoulderGrade = BoulderGrade.F6A,
        setter: String = "You",
        angleDegrees: Int = 20,
        holds: Int = 4,
    ) = Problem(
        id = id,
        name = name,
        grade = grade,
        accent = Accent.SKY,
        setter = setter,
        angleDegrees = angleDegrees,
        assignments = (1..holds).map { ProblemAssignment("h%02d".format(it), ProblemHoldRole.REGULAR) },
    )

    private fun ids(problems: List<Problem>) = problems.map { it.id }

    @Test
    fun `newest keeps library order and oldest reverses it`() {
        val library = listOf(problem("c"), problem("b"), problem("a"))

        assertEquals(listOf("c", "b", "a"), ids(sortProblems(library, ProblemSort.NEWEST)))
        assertEquals(listOf("a", "b", "c"), ids(sortProblems(library, ProblemSort.OLDEST)))
    }

    @Test
    fun `grade sorts by difficulty, not by displayed label`() {
        // The French labels sort correctly as plain strings (4, 5, 5+, 6A, 6A+, …
        // are already in ASCII order), so they prove nothing. The V-scale is where
        // a label sort falls over: F6A and F6A_PLUS both read "V3", so sorting on
        // the label would tie two different grades and the library's order would
        // shift when the setter switches display system. The ordinal is one order
        // for both.
        val library = listOf(
            problem("hard", grade = BoulderGrade.F7A),
            problem("v3-plus", grade = BoulderGrade.F6A_PLUS),
            problem("v3", grade = BoulderGrade.F6A),
            problem("easy", grade = BoulderGrade.F5_PLUS),
        )

        assertEquals(
            listOf("easy", "v3", "v3-plus", "hard"),
            ids(sortProblems(library, ProblemSort.GRADE_EASIEST)),
        )
        assertEquals(
            listOf("hard", "v3-plus", "v3", "easy"),
            ids(sortProblems(library, ProblemSort.GRADE_HARDEST)),
        )
    }

    @Test
    fun `angle sorts flattest to steepest`() {
        val library = listOf(
            problem("steep", angleDegrees = 45),
            problem("flat", angleDegrees = 0),
            problem("mid", angleDegrees = 25),
        )

        assertEquals(listOf("flat", "mid", "steep"), ids(sortProblems(library, ProblemSort.ANGLE_FLATTEST)))
        assertEquals(listOf("steep", "mid", "flat"), ids(sortProblems(library, ProblemSort.ANGLE_STEEPEST)))
    }

    @Test
    fun `name sorts case-insensitively`() {
        val library = listOf(
            problem("b", name = "bramble"),
            problem("c", name = "Chalk ghost"),
            problem("a", name = "Anvil"),
        )

        assertEquals(listOf("a", "b", "c"), ids(sortProblems(library, ProblemSort.NAME_ASC)))
        assertEquals(listOf("c", "b", "a"), ids(sortProblems(library, ProblemSort.NAME_DESC)))
    }

    @Test
    fun `untitled drafts stay last in both name directions`() {
        val library = listOf(
            problem("untitled", name = "   "),
            problem("zenith", name = "Zenith"),
            problem("anvil", name = "Anvil"),
        )

        assertEquals(listOf("anvil", "zenith", "untitled"), ids(sortProblems(library, ProblemSort.NAME_ASC)))
        assertEquals(listOf("zenith", "anvil", "untitled"), ids(sortProblems(library, ProblemSort.NAME_DESC)))
    }

    @Test
    fun `setter sorts alphabetically and keeps unattributed problems last`() {
        val library = listOf(
            problem("none", setter = ""),
            problem("maya", setter = "Maya"),
            problem("jono", setter = "jono"),
        )

        assertEquals(listOf("jono", "maya", "none"), ids(sortProblems(library, ProblemSort.SETTER)))
    }

    @Test
    fun `hold count sorts both ways`() {
        val library = listOf(
            problem("long", holds = 9),
            problem("short", holds = 3),
            problem("mid", holds = 6),
        )

        assertEquals(listOf("short", "mid", "long"), ids(sortProblems(library, ProblemSort.HOLDS_FEWEST)))
        assertEquals(listOf("long", "mid", "short"), ids(sortProblems(library, ProblemSort.HOLDS_MOST)))
    }

    @Test
    fun `ties keep the newest-first order they arrived in`() {
        val library = listOf(
            problem("newest", grade = BoulderGrade.F6A, angleDegrees = 20, holds = 5),
            problem("middle", grade = BoulderGrade.F6A, angleDegrees = 20, holds = 5),
            problem("oldest", grade = BoulderGrade.F6A, angleDegrees = 20, holds = 5),
        )
        val expected = listOf("newest", "middle", "oldest")

        // Every comparator must be stable, in both directions: a library where
        // everything shares a grade must not reshuffle when the setter sorts by it.
        listOf(
            ProblemSort.GRADE_EASIEST,
            ProblemSort.GRADE_HARDEST,
            ProblemSort.ANGLE_FLATTEST,
            ProblemSort.ANGLE_STEEPEST,
            ProblemSort.HOLDS_FEWEST,
            ProblemSort.HOLDS_MOST,
            ProblemSort.SETTER,
        ).forEach { sort ->
            assertEquals(sort.name, expected, ids(sortProblems(library, sort)))
        }
    }

    @Test
    fun `sorting never adds or drops problems`() {
        val library = listOf(
            problem("a", name = "", grade = BoulderGrade.F4, setter = "", angleDegrees = 0, holds = 0),
            problem("b", grade = BoulderGrade.F7B_PLUS, angleDegrees = 90, holds = 12),
            problem("c"),
        )

        ProblemSort.entries.forEach { sort ->
            assertEquals(sort.name, library.map { it.id }.toSet(), sortProblems(library, sort).map { it.id }.toSet())
            assertEquals(sort.name, library.size, sortProblems(library, sort).size)
        }
    }
}
