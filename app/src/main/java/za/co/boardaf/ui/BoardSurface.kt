package za.co.boardaf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import za.co.boardaf.R
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.HoldRole
import za.co.boardaf.model.ProblemHold
import za.co.boardaf.ui.theme.Coral
import za.co.boardaf.ui.theme.Gold
import za.co.boardaf.ui.theme.Sky

@Composable
fun BoardSurface(
    activeHolds: List<ProblemHold>,
    isSetting: Boolean,
    onHoldClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeById = activeHolds.associateBy { it.holdId }

    BoxWithConstraints(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(18.dp))
            .background(Color(0xFF222622), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.home_board),
            contentDescription = "Home climbing board",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp)),
        )

        val touchTarget = 44.dp
        BoardDefaults.holds.forEach { hold ->
            val assignment = activeById[hold.id]
            if (isSetting || assignment != null) {
                val x = maxWidth * hold.point.x - touchTarget / 2
                val y = maxHeight * hold.point.y - touchTarget / 2
                HoldTarget(
                    id = hold.id,
                    role = assignment?.role,
                    isSetting = isSetting,
                    onClick = { onHoldClick(hold.id) },
                    modifier = Modifier.offset(x = x, y = y),
                )
            }
        }
    }
}

@Composable
private fun HoldTarget(
    id: String,
    role: HoldRole?,
    isSetting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = role?.markerColor() ?: Color.White.copy(alpha = 0.75f)
    val label = if (role == null) "$id, unassigned hold" else "$id, ${role.label} hold"

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = label }
            .clickable(enabled = isSetting, onClick = onClick),
    ) {
        Canvas(modifier = Modifier.size(if (role == HoldRole.FOOT) 27.dp else 32.dp)) {
            if (role != null) {
                drawCircle(
                    color = Color(0xB51A1D1A),
                    radius = size.minDimension / 2,
                )
                drawCircle(
                    color = ringColor.copy(alpha = 0.18f),
                    radius = size.minDimension / 2 - 2.dp.toPx(),
                )
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2 - 1.5.dp.toPx(),
                    style = Stroke(
                        width = if (role == HoldRole.FOOT) 2.dp.toPx() else 3.dp.toPx(),
                        pathEffect = if (role == HoldRole.FOOT) {
                            androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 4f))
                        } else {
                            null
                        },
                    ),
                )
                if (role == HoldRole.START) {
                    drawCircle(color = ringColor, radius = 3.5.dp.toPx())
                }
                if (role == HoldRole.FINISH) {
                    val center = Offset(size.width / 2, size.height / 2)
                    drawLine(
                        color = ringColor,
                        start = Offset(center.x - 5.dp.toPx(), center.y),
                        end = Offset(center.x - 1.dp.toPx(), center.y + 4.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                    drawLine(
                        color = ringColor,
                        start = Offset(center.x - 1.dp.toPx(), center.y + 4.dp.toPx()),
                        end = Offset(center.x + 6.dp.toPx(), center.y - 5.dp.toPx()),
                        strokeWidth = 2.5.dp.toPx(),
                    )
                }
            } else {
                drawCircle(
                    color = Color(0x55191C19),
                    radius = size.minDimension / 2 - 4.dp.toPx(),
                )
                drawCircle(
                    color = ringColor,
                    radius = size.minDimension / 2 - 4.dp.toPx(),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
        }
    }
}

fun HoldRole.markerColor(): Color = when (this) {
    HoldRole.START -> Coral
    HoldRole.HAND -> Sky
    HoldRole.FOOT -> Color(0xFFDAD8D1)
    HoldRole.FINISH -> Gold
}
