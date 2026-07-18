package za.co.boardaf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import za.co.boardaf.model.Accent
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.ui.theme.BoardDark
import za.co.boardaf.ui.theme.BoardMuted
import za.co.boardaf.ui.theme.Coral
import za.co.boardaf.ui.theme.Gold
import za.co.boardaf.ui.theme.Moss
import za.co.boardaf.ui.theme.Sky

fun ProblemHoldRole.markerColor(): Color = when (this) {
    ProblemHoldRole.START -> Moss
    ProblemHoldRole.REGULAR -> Sky
    ProblemHoldRole.FOOT_ONLY -> Gold
    ProblemHoldRole.FINISH -> Coral
}

fun Accent.color(): Color = when (this) {
    Accent.SKY -> Sky
    Accent.CORAL -> Coral
    Accent.OCHRE -> Gold
    Accent.MOSS -> Moss
}

/**
 * The one semantic marker implementation shared by the board, legend, role palette
 * and previews. Every role stays distinguishable in grayscale: Start is a ring with
 * a centre dot and S badge, Regular a plain solid ring, Foot-only a dashed ring with
 * an F badge, Finish a double ring with a check.
 */
@Composable
fun ProblemMarker(
    role: ProblemHoldRole,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawMarker(role)
        }
        if (role == ProblemHoldRole.START || role == ProblemHoldRole.FOOT_ONLY) {
            val badgeSize = size * 0.42f
            val letterSize = with(LocalDensity.current) { (size * 0.26f).toSp() }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = size * 0.10f, y = -size * 0.10f)
                    .size(badgeSize)
                    .background(role.markerColor(), CircleShape),
            ) {
                Text(
                    text = if (role == ProblemHoldRole.START) "S" else "F",
                    color = BoardDark,
                    fontSize = letterSize,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = letterSize),
                )
            }
        }
    }
}

private fun DrawScope.drawMarker(role: ProblemHoldRole) {
    val color = role.markerColor()
    val radius = size.minDimension / 2f
    val ringWidth = (size.minDimension * 0.10f).coerceAtLeast(1.5f)
    val ringRadius = radius - ringWidth / 2f - 1f

    drawCircle(color = Color(0x4D14170F), radius = radius)
    drawCircle(color = color.copy(alpha = 0.10f), radius = ringRadius)

    when (role) {
        ProblemHoldRole.START -> {
            drawCircle(color = color, radius = ringRadius, style = Stroke(width = ringWidth))
            drawCircle(color = color, radius = size.minDimension * 0.12f)
        }

        ProblemHoldRole.REGULAR -> {
            drawCircle(color = color, radius = ringRadius, style = Stroke(width = ringWidth))
        }

        ProblemHoldRole.FOOT_ONLY -> {
            drawCircle(
                color = color,
                radius = ringRadius,
                style = Stroke(
                    width = ringWidth * 0.85f,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(size.minDimension * 0.16f, size.minDimension * 0.12f),
                    ),
                ),
            )
        }

        ProblemHoldRole.FINISH -> {
            drawCircle(color = color, radius = ringRadius, style = Stroke(width = ringWidth * 0.9f))
            drawCircle(
                color = color,
                radius = ringRadius - ringWidth * 1.7f,
                style = Stroke(width = ringWidth * 0.7f),
            )
            val center = Offset(size.width / 2f, size.height / 2f)
            val unit = size.minDimension / 32f
            drawLine(
                color = color,
                start = Offset(center.x - 4.5f * unit, center.y),
                end = Offset(center.x - unit, center.y + 3.5f * unit),
                strokeWidth = ringWidth * 0.8f,
            )
            drawLine(
                color = color,
                start = Offset(center.x - unit, center.y + 3.5f * unit),
                end = Offset(center.x + 5f * unit, center.y - 4f * unit),
                strokeWidth = ringWidth * 0.8f,
            )
        }
    }
}

/** Legend that renders the actual board glyphs, not colored dots. */
@Composable
fun MarkerLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        ProblemHoldRole.entries.forEachIndexed { index, role ->
            if (index > 0) Spacer(Modifier.width(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProblemMarker(role = role, size = 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = role.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = BoardMuted,
                )
            }
        }
    }
}
