package za.co.boardaf.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

private val BoardTransformSaver = listSaver<BoardTransform, Float>(
    save = { listOf(it.scale, it.offsetX, it.offsetY) },
    restore = { BoardTransform(scale = it[0], offsetX = it[1], offsetY = it[2]) },
)

enum class BoardDisplayMode {
    /** Climbing: only the problem's markers, no editing affordances. */
    VIEW,

    /** Setting: all holds tappable, zone boundary visible. */
    SET,

    /** Setup: holds display zone/capability and taps correct classification. */
    CONFIGURE,

    /**
     * Setup, re-mapping: every hold is draggable onto where it actually is in the
     * photo. Taps select rather than classify, so one gesture never means two
     * things while the setter is lining markers up.
     */
    REMAP,
}

@Composable
fun BoardSurface(
    board: ConfiguredBoard,
    assignments: List<ProblemAssignment>,
    mode: BoardDisplayMode,
    onHoldClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** Setting steps that assign no role show the board but must not take taps. */
    holdsEnabled: Boolean = true,
    /** The setter's own board photo; null renders the bundled one. */
    photoPath: String? = null,
    /** Highlighted in [BoardDisplayMode.REMAP] so Setup can act on one hold. */
    selectedHoldId: String? = null,
    /** Fired once per completed drag with the hold's new normalized center. */
    onHoldDragged: (String, Float, Float) -> Unit = { _, _, _ -> },
    /**
     * Draws the kickboard line here instead of at the board's saved boundary, so
     * Setup's slider can show where the line is going before the drag commits.
     * Only the line moves: hold classification still follows the saved boundary.
     */
    kickboardBoundaryPreview: Float? = null,
) {
    val assignmentsById = assignments.associateBy { it.holdId }
    var transform by rememberSaveable(mode, stateSaver = BoardTransformSaver) {
        mutableStateOf(BoardTransform.IDENTITY)
    }
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
        val touchTarget = 48.dp
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
                BoardBackground(
                    photoPath = photoPath,
                    contentDescription = "${board.name} photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp)),
                )
                if (mode != BoardDisplayMode.VIEW && board.hasKickboard) {
                    KickboardOverlay(
                        boundaryY = kickboardBoundaryPreview ?: board.kickboardTopY,
                        prominent = mode == BoardDisplayMode.CONFIGURE,
                    )
                }
            }

            board.holds.forEach { hold ->
                // Keyed so the per-hold drag state below belongs to the hold and not
                // to a list position that shifts when one is added or removed.
                key(hold.id) {
                    val assignment = assignmentsById[hold.id]
                    val visible = when (mode) {
                        BoardDisplayMode.VIEW -> assignment != null
                        // Locked setting steps read as review: show the line, not the
                        // pick-me hints on holds that can't be tapped right now.
                        BoardDisplayMode.SET -> holdsEnabled || assignment != null
                        BoardDisplayMode.CONFIGURE, BoardDisplayMode.REMAP -> true
                    }
                    if (visible) {
                        val anchor = BoardTransforms.anchor(hold.point, containerSize, transform)
                        // A hold in flight follows the finger from local state and is
                        // committed once, on release: writing every frame would
                        // re-validate the whole library and save a snapshot per event.
                        var dragDelta by remember { mutableStateOf(Offset.Zero) }
                        val liveX = anchor.x + dragDelta.x
                        val liveY = anchor.y + dragDelta.y
                        val onScreen = liveX > -touchTargetPx && liveY > -touchTargetPx &&
                            liveX < containerSize.width + touchTargetPx &&
                            liveY < containerSize.height + touchTargetPx
                        if (onScreen) {
                            // Read through the latest composition: a pinch part-way
                            // through a drag changes the mapping, and the commit has
                            // to use the transform in force when the finger lifts.
                            val commitDrag by rememberUpdatedState<(Offset) -> Unit> { delta ->
                                val point = BoardTransforms.normalize(
                                    PixelPoint(anchor.x + delta.x, anchor.y + delta.y),
                                    containerSize,
                                    transform,
                                )
                                onHoldDragged(hold.id, point.x, point.y)
                            }
                            val dragModifier = if (mode == BoardDisplayMode.REMAP) {
                                Modifier.pointerInput(hold.id) {
                                    detectDragGestures(
                                        onDragStart = { dragDelta = Offset.Zero },
                                        onDragCancel = { dragDelta = Offset.Zero },
                                        onDragEnd = {
                                            commitDrag(dragDelta)
                                            dragDelta = Offset.Zero
                                        },
                                        // Consuming is what keeps the board's own pan
                                        // and the Setup list's scroll out of the drag.
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragDelta += amount
                                        },
                                    )
                                }
                            } else {
                                Modifier
                            }
                            HoldTarget(
                                hold = hold,
                                role = assignment?.role,
                                mode = mode,
                                enabled = holdsEnabled,
                                selected = hold.id == selectedHoldId,
                                onClick = { onHoldClick(hold.id) },
                                gestureModifier = dragModifier,
                                modifier = Modifier.offset {
                                    IntOffset(
                                        (liveX - touchTargetPx / 2f).roundToInt(),
                                        (liveY - touchTargetPx / 2f).roundToInt(),
                                    )
                                },
                            )
                        }
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

/**
 * The setter's own photo of the wall when there is one, otherwise the bundled
 * board. `FillBounds` is deliberate on both: hold centers are normalized to the
 * frame, and callers size the frame with [za.co.boardaf.model.ConfiguredBoard.aspectRatio],
 * so the image fills it without distorting.
 */
@Composable
private fun BoardBackground(
    photoPath: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    when (val captured = rememberCapturedBoard(photoPath)) {
        is CapturedBoard.Ready -> Image(
            bitmap = captured.image,
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            modifier = modifier,
        )
        // Hold the dark container rather than flashing the bundled wall the
        // setter has just replaced; the decode is a single frame or two.
        CapturedBoard.Loading -> Box(modifier)
        CapturedBoard.None -> Image(
            painter = painterResource(R.drawable.home_board),
            contentDescription = contentDescription,
            contentScale = ContentScale.FillBounds,
            modifier = modifier,
        )
    }
}

private sealed interface CapturedBoard {
    /** No photo, or one that could not be decoded: the bundled board applies. */
    data object None : CapturedBoard
    data object Loading : CapturedBoard
    data class Ready(val image: ImageBitmap) : CapturedBoard
}

@Composable
private fun rememberCapturedBoard(photoPath: String?): CapturedBoard = produceState<CapturedBoard>(
    initialValue = if (photoPath == null) CapturedBoard.None else CapturedBoard.Loading,
    photoPath,
) {
    val path = photoPath
    value = if (path == null) {
        CapturedBoard.None
    } else {
        // Decoding a multi-megapixel JPEG on the main thread drops frames; the
        // store already caps captures, so one background decode is enough.
        withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        }?.let { CapturedBoard.Ready(it) } ?: CapturedBoard.None
    }
}.value

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
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    /**
     * Applied last so it sits inside [clickable] in the pointer chain: the drag
     * then sees each event first and, by consuming it, cancels the click.
     */
    gestureModifier: Modifier = Modifier,
) {
    val assignmentText = role?.let { "assigned ${it.label}" } ?: "unassigned"
    val overridden = hold.capability != hold.zone.defaultCapability
    val configuring = mode == BoardDisplayMode.CONFIGURE || mode == BoardDisplayMode.REMAP
    val description = buildString {
        append(hold.id)
        append(", ")
        append(hold.zone.label)
        append(", ")
        append(hold.capability.label)
        if (overridden && configuring) append(" (corrected)")
        if (!configuring) {
            append(", ")
            append(assignmentText)
        }
        if (mode == BoardDisplayMode.REMAP) {
            append(", draggable")
            if (selected) append(", selected")
        }
    }

    // Climb view is read-only, so the modifier is omitted rather than disabled:
    // clickable(enabled = false) still publishes an OnClick action, which makes a
    // screen reader announce all 43 holds as disabled buttons.
    val interactive = enabled && mode != BoardDisplayMode.VIEW

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = description }
            .then(if (interactive) Modifier.clickable(onClick = onClick) else Modifier)
            .then(gestureModifier),
    ) {
        when {
            mode == BoardDisplayMode.REMAP -> RemapDot(selected = selected)
            mode == BoardDisplayMode.CONFIGURE -> CapabilityDot(hold = hold, overridden = overridden)
            role != null -> ProblemMarker(
                role = role,
                size = if (role == ProblemHoldRole.FOOT_ONLY) 27.dp else 33.dp,
            )
            else -> UnassignedHint(capability = hold.capability)
        }
    }
}

/**
 * Re-mapping marker: a ring plus a center pip. The ring alone reads as "near the
 * hold"; the pip is what you line up with the middle of the actual hold.
 */
@Composable
private fun RemapDot(selected: Boolean) {
    val color = if (selected) Sky else Color.White.copy(alpha = 0.9f)
    Canvas(modifier = Modifier.size(if (selected) 34.dp else 26.dp)) {
        drawCircle(
            color = color,
            radius = size.minDimension / 2 - 3.dp.toPx(),
            style = Stroke(width = if (selected) 3.dp.toPx() else 2.dp.toPx()),
        )
        drawCircle(color = color, radius = 2.dp.toPx())
    }
}

@Composable
private fun UnassignedHint(capability: HoldCapability) {
    // Outline-only so the hold photo reads through while choosing.
    Canvas(modifier = Modifier.size(26.dp)) {
        val radius = size.minDimension / 2 - 4.dp.toPx()
        when (capability) {
            HoldCapability.HAND_AND_FOOT -> drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = radius,
                style = Stroke(width = 1.5.dp.toPx()),
            )
            HoldCapability.FOOT_ONLY -> drawCircle(
                color = Gold.copy(alpha = 0.95f),
                radius = radius,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                ),
            )
        }
    }
}

@Composable
private fun CapabilityDot(hold: ConfiguredHold, overridden: Boolean) {
    // Outline-only rings so Setup can visually verify the photo under each hold.
    Canvas(modifier = Modifier.size(28.dp)) {
        val radius = size.minDimension / 2 - 3.dp.toPx()
        when (hold.capability) {
            HoldCapability.HAND_AND_FOOT -> drawCircle(
                color = Sky.copy(alpha = 0.95f),
                radius = radius,
                style = Stroke(width = 2.5.dp.toPx()),
            )
            HoldCapability.FOOT_ONLY -> drawCircle(
                color = Gold.copy(alpha = 0.95f),
                radius = radius,
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
