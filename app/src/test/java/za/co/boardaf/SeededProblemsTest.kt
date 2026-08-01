package za.co.boardaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState

/**
 * The seeded library is what a first-run user sees, so it has to be valid against
 * the default board. Three seeds previously started on kickboard holds, which the
 * validator rejects, and the demo library opened full of red "Needs review" cards.
 */
class SeededProblemsTest {

    private val board = ConfiguredBoard.from(BoardSetup.default())

    @Test
    fun everySeededProblemIsValidAgainstTheDefaultBoard() {
        BoardDefaults.problems.forEach { problem ->
            val issues = ProblemValidator.validate(problem, board)
            assertEquals(
                "${problem.name} has validation issues: ${issues.map { it.message }}",
                emptyList<String>(),
                issues.map { it.message },
            )
        }
    }

    @Test
    fun everySeededProblemPublishes() {
        BoardDefaults.problems.forEach { problem ->
            assertEquals(
                "${problem.name} should open as Published",
                PublicationState.PUBLISHED,
                problem.publicationState,
            )
        }
    }

    @Test
    fun noSeededHandHoldSitsOnTheKickboard() {
        BoardDefaults.problems.forEach { problem ->
            problem.assignments
                .filter { it.role != ProblemHoldRole.FOOT_ONLY }
                .forEach { assignment ->
                    val hold = board.holdsById.getValue(assignment.holdId)
                    assertTrue(
                        "${problem.name}: ${assignment.holdId} is a ${assignment.role.label} " +
                            "hold on the kickboard, which cannot be used with hands",
                        hold.zone == BoardZoneType.MAIN && hold.capability.allowsHands,
                    )
                }
        }
    }

    /**
     * Only hand holds are compared: a foot mark below the start is normal (you
     * stand on it to reach the start), so Golden hour's h35 foot under its h34
     * start is correct, not a defect.
     */
    @Test
    fun everySeededProblemStartsBelowTheRestOfItsHandHolds() {
        BoardDefaults.problems.forEach { problem ->
            val start = problem.assignments.single { it.role == ProblemHoldRole.START }
            val startY = board.holdsById.getValue(start.holdId).point.y
            problem.assignments
                .filter { it.holdId != start.holdId && it.role != ProblemHoldRole.FOOT_ONLY }
                .forEach { assignment ->
                    val y = board.holdsById.getValue(assignment.holdId).point.y
                    assertTrue(
                        "${problem.name}: start ${start.holdId} (y=$startY) is not below " +
                            "${assignment.holdId} (y=$y)",
                        startY > y,
                    )
                }
        }
    }

    @Test
    fun seededProblemsUseUniqueHolds() {
        BoardDefaults.problems.forEach { problem ->
            val ids = problem.assignments.map { it.holdId }
            assertEquals(
                "${problem.name} assigns a hold more than once",
                ids.size,
                ids.distinct().size,
            )
        }
    }
}
