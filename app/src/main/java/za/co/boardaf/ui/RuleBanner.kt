package za.co.boardaf.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.FinishRule
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.StartRule
import za.co.boardaf.ui.theme.BoardMuted

/** Persistent plain-language feet rule banner shown in setter, detail and climb states. */
@Composable
fun FeetRuleBanner(
    feetRule: FeetRule,
    footMarkCount: Int,
    modifier: Modifier = Modifier,
) {
    val countSuffix = when {
        feetRule == FeetRule.CAMPUS -> ""
        footMarkCount == 1 -> " · 1 marked foot"
        footMarkCount > 1 -> " · $footMarkCount marked feet"
        else -> ""
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProblemMarker(role = ProblemHoldRole.FOOT_ONLY, size = 20.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "${feetRule.label}$countSuffix",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = feetRule.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = BoardMuted,
                )
            }
        }
    }
}

/** One/two start and finish explanation for the climb view. */
@Composable
fun StartFinishExplanation(
    startRule: StartRule,
    finishRule: FinishRule,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            RuleLine(role = ProblemHoldRole.START, text = startRule.description)
            Spacer(Modifier.width(0.dp))
            RuleLine(role = ProblemHoldRole.FINISH, text = finishRule.description)
        }
    }
}

@Composable
private fun RuleLine(role: ProblemHoldRole, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 3.dp),
    ) {
        ProblemMarker(role = role, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = BoardMuted,
        )
    }
}
