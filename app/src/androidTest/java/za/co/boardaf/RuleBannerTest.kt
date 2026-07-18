package za.co.boardaf

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.FinishRule
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.StartRule
import za.co.boardaf.ui.FeetRuleBanner
import za.co.boardaf.ui.MarkerLegend
import za.co.boardaf.ui.StartFinishExplanation
import za.co.boardaf.ui.theme.BoardAfTheme

@RunWith(AndroidJUnit4::class)
class RuleBannerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feetRuleBannerShowsRuleAndMarkedFeetCount() {
        composeRule.setContent {
            BoardAfTheme {
                FeetRuleBanner(feetRule = FeetRule.MARKED_ONLY, footMarkCount = 2)
            }
        }

        composeRule.onNodeWithText("Marked feet only · 2 marked feet").assertExists()
        composeRule.onNodeWithText(FeetRule.MARKED_ONLY.description).assertExists()
    }

    @Test
    fun campusBannerSkipsFootCount() {
        composeRule.setContent {
            BoardAfTheme {
                FeetRuleBanner(feetRule = FeetRule.CAMPUS, footMarkCount = 3)
            }
        }

        composeRule.onNodeWithText("Campus").assertExists()
        composeRule.onNodeWithText(FeetRule.CAMPUS.description).assertExists()
    }

    @Test
    fun startFinishExplanationUsesPlainLanguage()  {
        composeRule.setContent {
            BoardAfTheme {
                StartFinishExplanation(
                    startRule = StartRule.SPLIT_TWO,
                    finishRule = FinishRule.CONTROL_TWO,
                )
            }
        }

        composeRule.onNodeWithText(StartRule.SPLIT_TWO.description).assertExists()
        composeRule.onNodeWithText(FinishRule.CONTROL_TWO.description).assertExists()
    }

    @Test
    fun legendListsEveryRoleWithItsGlyph() {
        composeRule.setContent {
            BoardAfTheme {
                MarkerLegend()
            }
        }

        ProblemHoldRole.entries.forEach { role ->
            composeRule.onNodeWithText(role.label).assertExists()
        }
    }
}
