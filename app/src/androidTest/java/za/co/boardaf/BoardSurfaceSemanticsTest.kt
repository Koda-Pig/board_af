package za.co.boardaf

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.ui.BoardDisplayMode
import za.co.boardaf.ui.BoardSurface
import za.co.boardaf.ui.theme.BoardAfTheme

@RunWith(AndroidJUnit4::class)
class BoardSurfaceSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val board = ConfiguredBoard.from(BoardSetup.default())
    private val assignments = listOf(
        ProblemAssignment("h34", ProblemHoldRole.START),
        ProblemAssignment("h35", ProblemHoldRole.FOOT_ONLY),
        ProblemAssignment("h04", ProblemHoldRole.FINISH),
    )

    private fun setSurface(mode: BoardDisplayMode) {
        composeRule.setContent {
            BoardAfTheme {
                BoardSurface(
                    board = board,
                    assignments = assignments,
                    mode = mode,
                    onHoldClick = {},
                )
            }
        }
    }

    @Test
    fun holdSemanticsAnnounceIdZoneCapabilityAndRole() {
        setSurface(BoardDisplayMode.SET)

        composeRule
            .onNodeWithContentDescription("h34, Main board, Hands and feet, assigned Start")
            .assertExists()
        composeRule
            .onNodeWithContentDescription("h43, Kickboard, Foot only, unassigned")
            .assertExists()
    }

    @Test
    fun interactiveTargetsStayAtLeast44dp() {
        setSurface(BoardDisplayMode.SET)

        composeRule
            .onNodeWithContentDescription("h35, Main board, Hands and feet, assigned Foot only")
            .assertWidthIsAtLeast(44.dp)
            .assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun climbViewShowsOnlyMarkedHoldsWithoutEditing() {
        setSurface(BoardDisplayMode.VIEW)

        composeRule
            .onNodeWithContentDescription("h34, Main board, Hands and feet, assigned Start")
            .assertExists()
            .assertHasNoClickAction()
        composeRule
            .onNodeWithContentDescription("h43, Kickboard, Foot only, unassigned")
            .assertDoesNotExist()
    }

    @Test
    fun settingModeMakesHoldsClickable() {
        setSurface(BoardDisplayMode.SET)

        composeRule
            .onNodeWithContentDescription("h43, Kickboard, Foot only, unassigned")
            .assertHasClickAction()
    }
}
