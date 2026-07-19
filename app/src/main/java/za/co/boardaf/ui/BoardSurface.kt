package za.co.boardaf.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import za.co.boardaf.R
import za.co.boardaf.model.BoardTransform
import za.co.boardaf.model.BoardTransforms
import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.ConfiguredHold
import za.co.boardaf.model.HoldCapability
import za.co.boardaf.model.PixelPoint
import za.co.boardaf.model.PixelSize
import za.co.boardaf.model.ProblemAssignment
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.ui.theme.Gold
import za.co.boardaf.ui.theme.Sky

enum class BoardDisplayMode {
    /** Climbing: only the problem's markers, no editing affordances. */
    VIEW,

    /** Setting: all holds tappable, zone boundary visible. */
    SET,

    /** Setup: holds display zone/capability and taps correct classification. */
    CONFIGURE,
}

@Composable
fun BoardSurface(
    board: ConfiguredBoard,
    assignments: List<ProblemAssignment>,
    mode: BoardDisplayMode,
    onHoldClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val assignmentsById = assignments.associateBy { it.holdId }
    var transform by remember(mode) { mutableStateOf(BoardTransform.IDENTITY) }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .shadow(12.dp, RoundedCornerShape(18.dp))
            .background(Color(0xFF273338), RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp)),
    ) {
        val containerSize = PixelSize(
            width = constraints.maxWidth.toFloat(),
            height = constraints.maxHeight.toFloat(),
        )
        val touchTarget = 44.dp
        val touchTargetPx = with(density) { touchTarget.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mode) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val alreadyConsumed = event.changes.any { it.isConsumed }
                            val pressedCount = event.changes.count { it.pressed }
                            if (!alreadyConsumed && (pressedCount > 1 || transform.scale > 1f)) {
                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid()
                                if ((zoom != 1f || pan != Offset.Zero) && centroid.isSpecified) {
                                    transform = BoardTransforms.applyGesture(
                                        current = transform,
                                        containerSize = PixelSize(
                                            size.width.toFloat(),
                                            size.height.toFloat(),
                                        ),
                                        centroid = PixelPoint(centroid.x, centroid.y),
                                        panX = pan.x,
                                        panY = pan.y,
                                        zoom = zoom,
                                    )
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(mode) {
                    detectTapGestures(onDoubleTap = { transform = BoardTransforms.reset() })
                },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.offsetX
                        translationY = transform.offsetY
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) {
                Image(
                    painter = painterResource(R.drawable.home_board),
                    contentDescription = "${board.name} photo",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp)),
                )
                if (mode != BoardDisplayMode.VIEW && board.hasKickboard) {
                    KickboardOverlay(
                        boundaryY = board.kickboardTopY,
                        prominent = mode == BoardDisplayMode.CONFIGURE,
                    )
                }
            }

            board.holds.forEach { hold ->
                val assignment = assignmentsById[hold.id]
                val visible = when (mode) {
                    BoardDisplayMode.VIEW -> assignment != null
                    BoardDisplayMode.SET, BoardDisplayMode.CONFIGURE -> true
                }
                if (visible) {
                    val anchor = BoardTransforms.anchor(hold.point, containerSize, transform)
                    val onScreen = anchor.x > -touchTargetPx && anchor.y > -touchTargetPx &&
                        anchor.x < containerSize.width + touchTargetPx &&
                        anchor.y < containerSize.height + touchTargetPx
                    if (onScreen) {
                        HoldTarget(
                            hold = hold,
                            role = assignment?.role,
                            mode = mode,
                            onClick = { onHoldClick(hold.id) },
                            modifier = Modifier.offset {
                                IntOffset(
                                    (anchor.x - touchTargetPx / 2f).roundToInt(),
                                    (anchor.y - touchTargetPx / 2f).roundToInt(),
                                )
                            },
                        )
                    }
                }
            }

            if (!transform.isIdentity) {
                Surface(
                    color = Color(0xB3273338),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp),
                ) {
                    IconButton(onClick = { transform = BoardTransforms.reset() }) {
                        Icon(
                            Icons.Rounded.FitScreen,
                            contentDescription = "Reset zoom to fit",
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KickboardOverlay(boundaryY: Float, prominent: Boolean) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height * boundaryY
            drawRect(
                color = Gold.copy(alpha = if (prominent) 0.10f else 0.06f),
                topLeft = Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - y),
            )
            drawLine(
                color = Gold.copy(alpha = if (prominent) 0.95f else 0.6f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }
        if (prominent) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "KICKBOARD",
                    color = Gold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .offset(y = maxHeight * boundaryY)
                        .padding(start = 10.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HoldTarget(
    hold: ConfiguredHold,
    role: ProblemHoldRole?,
    mode: BoardDisplayMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val assignmentText = role?.let { "assigned ${it.label}" } ?: "unassigned"
    val overridden = hold.capability != hold.zone.defaultCapability
    val description = buildString {
        append(hold.id)
        append(", ")
        append(hold.zone.label)
        append(", ")
        append(hold.capability.label)
        if (overridden && mode == BoardDisplayMode.CONFIGURE) append(" (corrected)")
        if (mode != BoardDisplayMode.CONFIGURE) {
            append(", ")
            append(assignmentText)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .semantics { contentDescription = description }
            .clickable(enabled = mode != BoardDisplayMode.VIEW, onClick = onClick),
    ) {
        when {
            mode == BoardDisplayMode.CONFIGURE -> CapabilityDot(hold = hold, overridden = overridden)
            role != null -> ProblemMarker(
                role = role,
                size = if (role == ProblemHoldRole.FOOT_ONLY) 27.dp else 33.dp,
            )
            else -> UnassignedHint(capability = hold.capability)
        }
    }
}

@Composable
private fun UnassignedHint(capability: HoldCapability) {
    Canvas(modifier = Modifier.size(26.dp)) {
        drawCircle(
            color = Color(0x55273338),
            radius = size.minDimension / 2 - 4.dp.toPx(),
        )
        when (capability) {
            HoldCapability.HAND_AND_FOOT -> drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = size.minDimension / 2 - 4.dp.toPx(),
                style = Stroke(width = 1.dp.toPx()),
            )
            HoldCapability.FOOT_ONLY -> drawCircle(
                color = Gold.copy(alpha = 0.9f),
                radius = size.minDimension / 2 - 4.dp.toPx(),
                style = Stroke(
                    width = 1.2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                ),
            )
        }
    }
}

@Composable
private fun CapabilityDot(hold: ConfiguredHold, overridden: Boolean) {
    Canvas(modifier = Modifier.size(28.dp)) {
        drawCircle(color = Color(0x66101410), radius = size.minDimension / 2)
        when (hold.capability) {
            HoldCapability.HAND_AND_FOOT -> drawCircle(
                color = Sky.copy(alpha = 0.9f),
                radius = size.minDimension / 2 - 5.dp.toPx(),
            )
            HoldCapability.FOOT_ONLY -> drawCircle(
                color = Gold.copy(alpha = 0.95f),
                radius = size.minDimension / 2 - 4.dp.toPx(),
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                ),
            )
        }
        if (overridden) {
            drawCircle(
                color = Color.White,
                radius = size.minDimension / 2 - 1.5.dp.toPx(),
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
        if (hold.zone == BoardZoneType.KICKBOARD && hold.capability == HoldCapability.HAND_AND_FOOT) {
            drawCircle(
                color = Sky,
                radius = size.minDimension / 2 - 2.dp.toPx(),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))),
            )
        }
    }
}
