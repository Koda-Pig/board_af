package za.co.boardaf.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import za.co.boardaf.BoardUiState
import za.co.boardaf.model.IssueSeverity
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.setter.SetterMode
import za.co.boardaf.ui.theme.Forest
import kotlinx.coroutines.launch
import za.co.boardaf.ui.theme.Moss
import za.co.boardaf.ui.theme.Coral

/** The board sheet's resting height before its content has been measured. */
private val BOARD_SHEET_PEEK_HEIGHT = 100.dp

/**
 * Air under the feet-rule banner in the resting sheet. Less than the gap to the
 * next row, so the peek ends on a clean edge instead of slicing through that row.
 */
private val BOARD_SHEET_PEEK_MARGIN = 8.dp

/**
 * Height of [SheetHandle]. The setter's peek is this plus the measured strip, so
 * the strip shows in full at any font scale.
 */
private val SHEET_HANDLE_HEIGHT = 24.dp

/**
 * Outlined buttons in this Material version default to grey (onSurfaceVariant)
 * text, which read as disabled next to the green text buttons. Every enabled
 * outlined button in the app uses this instead.
 */
@Composable
internal fun primaryOutlinedColors() =
    ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)

internal fun problemDisplayName(name: String): String = name.ifBlank { "Untitled draft" }

internal fun holdCountLabel(count: Int): String =
    if (count == 1) "1 hold" else "$count holds"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: BoardUiState,
    actions: BoardActions,
    contentPadding: PaddingValues,
    snackbarHostState: SnackbarHostState,
) {
    val problem = state.selectedProblem
    val activeAssignments = if (state.isSetting) state.setter.draft.assignments else problem?.assignments.orEmpty()
    val surfaceMode = if (state.isSetting) BoardDisplayMode.SET else BoardDisplayMode.VIEW
    // Feet rule and details & review render no role palette, so leaving their holds
    // tappable would assign whatever role happened to be active last. Quick set has
    // no steps: taps toggle membership, so holds stay live for the whole session.
    val holdsEnabled = !state.isSetting ||
        state.setter.mode == SetterMode.QUICK ||
        state.setter.guidedStep.roleForStep != null

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        val wide = maxWidth >= 840.dp
        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        if (!state.isSetting) BoardHeader(state = state, actions = actions, problem = problem)
                        SwipeableBoard(
                            enabled = !state.isSetting,
                            onSwipe = actions.onSelectAdjacentProblem,
                        ) {
                            BoardSurface(
                                board = state.board,
                                assignments = activeAssignments,
                                mode = surfaceMode,
                                onHoldClick = actions.onTapHold,
                                holdsEnabled = holdsEnabled,
                                photoPath = state.boardPhotoPath,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 620.dp)
                                    .aspectRatio(state.board.aspectRatio),
                            )
                        }
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .width(400.dp)
                        .fillMaxHeight(),
                ) {
                    item { SidePanel(state = state, actions = actions, problem = problem) }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        } else {
            val sheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded,
                skipHiddenState = true,
            )
            val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            var stripHeight by remember { mutableStateOf(0.dp) }
            var bannerHeight by remember { mutableStateOf(0.dp) }
            // Sized to what the peek is for — the setter's strip, or the feet rule
            // while viewing — so it shows that in full, at any font scale, and
            // nothing of the row beneath it.
            val peekHeight: Dp = when {
                state.isSetting && stripHeight > 0.dp -> SHEET_HANDLE_HEIGHT + stripHeight
                !state.isSetting && problem != null && bannerHeight > 0.dp ->
                    SHEET_HANDLE_HEIGHT + bannerHeight + BOARD_SHEET_PEEK_MARGIN
                else -> BOARD_SHEET_PEEK_HEIGHT
            }

            // The sheet follows the step: open for the steps that are all form
            // (feet rule, details), down to the strip for the steps where the board
            // is what you're working on, and down when the session ends so the
            // saved problem isn't hidden behind it.
            val wantExpanded = state.isSetting &&
                state.setter.mode == SetterMode.GUIDED &&
                state.setter.guidedStep.roleForStep == null
            LaunchedEffect(state.isSetting, state.setter.mode, state.setter.guidedStep) {
                if (wantExpanded) sheetState.expand() else sheetState.partialExpand()
            }
            // The scaffold moves its collapsed anchor when the peek changes but leaves
            // a resting sheet at the old height until dragged, so re-settle it. Only
            // a resting sheet, though: one the user has pulled open stays open when a
            // hint wraps or the next problem's feet rule runs to a second line.
            LaunchedEffect(peekHeight) {
                if (sheetState.targetValue == SheetValue.PartiallyExpanded) sheetState.partialExpand()
            }

            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = peekHeight,
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetDragHandle = { SheetHandle() },
                // The scaffold keeps this just above a resting sheet. On the setter that
                // is the gap between the board and the strip, which is a little too
                // short for a two-line snackbar: it hid the lowest row of holds. Let it
                // sink over the handle, the one part of the sheet with nothing to read.
                // An open sheet puts it at the screen's bottom edge, where there is no
                // room to sink into.
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = if (sheetState.targetValue == SheetValue.PartiallyExpanded) {
                            Modifier.offset(y = SHEET_HANDLE_HEIGHT)
                        } else {
                            Modifier
                        },
                    )
                },
                sheetContent = {
                    if (state.isSetting) {
                        val chrome = rememberSetterChromeState()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Outside the scroll so it can't be scrolled out of the peek.
                            SetterStrip(
                                state = state,
                                actions = actions,
                                chrome = chrome,
                                onShowDetails = { scope.launch { sheetState.expand() } },
                                modifier = Modifier
                                    .onSizeChanged { stripHeight = with(density) { it.height.toDp() } }
                                    .padding(horizontal = 14.dp)
                                    .padding(bottom = 10.dp),
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 14.dp)
                                    .padding(top = 12.dp, bottom = 24.dp),
                            ) {
                                SetterBody(state = state, actions = actions, chrome = chrome)
                            }
                        }
                        SetterDialogs(state = state, actions = actions, chrome = chrome)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 14.dp)
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            if (problem != null) {
                                FeetRuleBanner(
                                    feetRule = problem.feetRule,
                                    footMarkCount = problem.assignments.count { it.role == ProblemHoldRole.FOOT_ONLY },
                                    modifier = Modifier.onSizeChanged {
                                        bannerHeight = with(density) { it.height.toDp() }
                                    },
                                )
                                ProblemDetails(
                                    problem = problem,
                                    state = state,
                                    actions = actions,
                                )
                            } else {
                                Text(
                                    "No problem selected. Open Problems, or tap New to set one.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                    }
                },
            ) { sheetPadding ->
                // Width-first on the phone: the board fills the device width and the
                // column scrolls the small overflow the sheet peek leaves. Height-first
                // fitting kept everything on screen but rendered the wall too small to
                // read or tap comfortably.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(sheetPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp)
                        .padding(top = 14.dp),
                ) {
                    // While setting, the sheet's strip names the step; a second
                    // header up here only pushed the board down.
                    if (!state.isSetting) BoardHeader(state = state, actions = actions, problem = problem)
                    SwipeableBoard(
                        enabled = !state.isSetting,
                        onSwipe = actions.onSelectAdjacentProblem,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AnimatedProblemSurface(
                            state = state,
                            actions = actions,
                            surfaceMode = surfaceMode,
                            activeAssignments = activeAssignments,
                            holdsEnabled = holdsEnabled,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * Renders the board for the currently selected problem with a light slide-and-fade
 * when swiping between problems — the cue that the problem actually changed.
 * The slide direction follows the library order; selections that arrive from
 * elsewhere (or enter/exit of the setter) just crossfade.
 */
@Composable
private fun AnimatedProblemSurface(
    state: BoardUiState,
    actions: BoardActions,
    surfaceMode: BoardDisplayMode,
    activeAssignments: List<za.co.boardaf.model.ProblemAssignment>,
    holdsEnabled: Boolean,
) {
    // Same listing the swipe itself walks, so the slide direction matches the move.
    val listedProblems = state.visibleProblems
    val activeKey = if (state.isSetting) SETTER_SURFACE_KEY else state.selectedProblem?.id ?: "none"

    AnimatedContent(
        targetState = activeKey,
        transitionSpec = {
            val from = listedProblems.indexOfFirst { it.id == initialState }
            val to = listedProblems.indexOfFirst { it.id == targetState }
            val direction = if (from >= 0 && to >= 0 && from != to) {
                if (to > from) 1 else -1
            } else {
                0
            }
            if (direction == 0) {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            } else {
                (slideInHorizontally(tween(220)) { it / 4 * direction } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 4 * direction } + fadeOut(tween(120)))
            }
        },
        label = "problem-swipe",
    ) { key ->
        // Render the assignments belonging to this key, not the latest state, so the
        // outgoing board keeps its own markers during the transition.
        val assignments = when {
            key == SETTER_SURFACE_KEY -> activeAssignments
            else -> state.problems.firstOrNull { it.id == key }?.assignments
                ?: activeAssignments
        }
        BoardSurface(
            board = state.board,
            assignments = assignments,
            mode = surfaceMode,
            onHoldClick = actions.onTapHold,
            holdsEnabled = holdsEnabled,
            photoPath = state.boardPhotoPath,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(state.board.aspectRatio),
        )
    }
}

private const val SETTER_SURFACE_KEY = "setter-session"

/**
 * A slimmer drag handle than Material's 48dp one, which ate a third of the setter's
 * peek. The sheet still drags from anywhere on it, and the scaffold attaches the
 * expand/collapse accessibility actions to this slot whatever it draws.
 */
@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(width = 32.dp, height = 4.dp)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(2.dp),
            ),
    )
}

@Composable
private fun SwipeableBoard(
    enabled: Boolean,
    onSwipe: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    // Density-independent: a raw pixel threshold is ~3x stricter on a low-density
    // screen than on this one, and 80px here is only ~27dp — an accidental swipe.
    val thresholdPx = with(LocalDensity.current) { SWIPE_THRESHOLD.toPx() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.then(
            if (enabled) {
                Modifier.pointerInput(thresholdPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val delta = when {
                                offset.value > thresholdPx -> -1
                                offset.value < -thresholdPx -> 1
                                else -> 0
                            }
                            if (delta != 0) onSwipe(delta)
                            scope.launch { offset.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { _, dragAmount ->
                            // Damped so the board follows the finger without
                            // implying it will slide all the way off.
                            scope.launch { offset.snapTo(offset.value + dragAmount * 0.5f) }
                        },
                    )
                }
            } else {
                Modifier
            },
        ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer { translationX = offset.value },
            content = content,
        )
    }
}

/** Deliberate enough not to fire while steadying the phone one-handed. */
private val SWIPE_THRESHOLD = 64.dp

@Composable
private fun SidePanel(state: BoardUiState, actions: BoardActions, problem: Problem?) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (state.isSetting) {
            SetterPanel(state = state, actions = actions)
        } else if (problem != null) {
            FeetRuleBanner(
                feetRule = problem.feetRule,
                footMarkCount = problem.assignments.count { it.role == ProblemHoldRole.FOOT_ONLY },
            )
            ProblemDetails(problem = problem, state = state, actions = actions)
        }
    }
}

@Composable
private fun BoardHeader(state: BoardUiState, actions: BoardActions, problem: Problem?) {
    val issues = if (problem != null) ProblemValidator.validate(problem, state.board) else emptyList()
    val repairReason = issues
        .filter { it.severity == IssueSeverity.ERROR }
        .firstOrNull()
        ?.message
    // Same listing the swipe walks, so the position and the arrows agree with it.
    val listed = state.visibleProblems
    val index = listed.indexOfFirst { it.id == problem?.id }
    val showPosition = index >= 0 && listed.size > 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (showPosition) "NOW VIEWING · ${index + 1} OF ${listed.size}" else "NOW VIEWING",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            // The swipe has no other visible cue; these make it discoverable and
            // give a tap alternative to it.
            if (showPosition) {
                IconButton(
                    onClick = { actions.onSelectAdjacentProblem(-1) },
                    enabled = index > 0,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous problem")
                }
                IconButton(
                    onClick = { actions.onSelectAdjacentProblem(1) },
                    enabled = index < listed.lastIndex,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next problem")
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = problemDisplayName(problem?.name.orEmpty()),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (problem?.publicationState == PublicationState.NEEDS_REVIEW && repairReason != null) {
                    Text(
                        text = repairReason,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (problem != null) {
                // Published is the normal case; only the exceptions get a chip.
                if (problem.publicationState != PublicationState.PUBLISHED) {
                    StatusChip(state = problem.publicationState)
                    Spacer(Modifier.width(8.dp))
                }
                Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = "${problem.grade.label(state.gradeSystem)} · ${problem.angleDegrees}°",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(state: PublicationState, modifier: Modifier = Modifier) {
    // Tints ride on the theme's surface colors so the chip works on both schemes;
    // hardcoded light pairs used to glare on the dark background (F28).
    val (container, content) = when (state) {
        PublicationState.DRAFT ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
        PublicationState.NEEDS_REVIEW ->
            Coral.copy(alpha = 0.22f) to MaterialTheme.colorScheme.onSurface
        PublicationState.PUBLISHED ->
            Moss.copy(alpha = 0.30f) to MaterialTheme.colorScheme.onSurface
        PublicationState.ARCHIVED ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, shape = RoundedCornerShape(7.dp), modifier = modifier) {
        Text(
            text = state.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * The rest of what a climber needs below the header — rules, legend, repair notes —
 * and the actions, weighted: one filled primary, at most one outlined secondary,
 * and the housekeeping (duplicate, archive, delete) as quiet text buttons.
 */
@Composable
private fun ProblemDetails(
    problem: Problem,
    state: BoardUiState,
    actions: BoardActions,
) {
    val issues = ProblemValidator.validate(problem, state.board)
    val hasErrors = ProblemValidator.hasErrors(issues)
    var confirmForerun by rememberSaveable(problem.id) { mutableStateOf(false) }
    val needsRepair = problem.publicationState == PublicationState.NEEDS_REVIEW
    val displayName = problemDisplayName(problem.name)
    val primaryColors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Name, grade and status are already in the header right above.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .background(problem.accent.color(), CircleShape),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = "${holdCountLabel(problem.assignments.size)} · set by ${problem.setter}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The setter sees these rules while setting; the climber needs them more.
        StartFinishExplanation(startRule = problem.startRule, finishRule = problem.finishRule)

        if (needsRepair) {
            val repairErrors = issues.filter { it.severity == IssueSeverity.ERROR }
            Surface(color = Coral.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (repairErrors.isEmpty()) {
                        Text(
                            text = "The board changed while this was published. Confirm it still climbs as set, then publish again.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Text(
                            text = "This problem needs repair before it can be published again:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        repairErrors.forEach {
                            Text("• ${it.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        MarkerLegend()
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                needsRepair -> {
                    Button(
                        onClick = { actions.onStartEditing(problem.id) },
                        modifier = Modifier.weight(1f),
                        colors = primaryColors,
                    ) { Text("Repair") }
                    if (!hasErrors) {
                        OutlinedButton(
                            colors = primaryOutlinedColors(),
                            onClick = { confirmForerun = true },
                            modifier = Modifier.weight(1f),
                        ) { Text("Publish…") }
                    }
                }
                problem.publicationState == PublicationState.ARCHIVED -> {
                    Button(
                        onClick = { actions.onUnarchiveProblem(problem.id) },
                        modifier = Modifier.weight(1f),
                        colors = primaryColors,
                    ) { Text("Restore") }
                    OutlinedButton(
                        colors = primaryOutlinedColors(),
                        onClick = { actions.onStartEditing(problem.id) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Edit") }
                }
                problem.publicationState == PublicationState.DRAFT -> {
                    Button(
                        onClick = { actions.onStartEditing(problem.id) },
                        modifier = Modifier.weight(1f),
                        colors = primaryColors,
                    ) { Text("Edit") }
                    OutlinedButton(
                        colors = primaryOutlinedColors(),
                        onClick = { confirmForerun = true },
                        enabled = !hasErrors,
                        modifier = Modifier.weight(1f),
                    ) { Text("Publish…") }
                }
                else -> Button(
                    onClick = { actions.onStartEditing(problem.id) },
                    modifier = Modifier.weight(1f),
                    colors = primaryColors,
                ) { Text("Edit") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { actions.onDuplicateProblem(problem.id) }) { Text("Duplicate") }
            if (problem.publicationState != PublicationState.ARCHIVED) {
                TextButton(onClick = { actions.onArchiveProblem(problem.id) }) { Text("Archive") }
            }
            Spacer(Modifier.weight(1f))
            // No confirm dialog: the delete is recoverable, and the snackbar offers Undo.
            TextButton(
                onClick = { actions.onDeleteProblem(problem.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete") }
        }
    }

    if (confirmForerun) {
        AlertDialog(
            onDismissRequest = { confirmForerun = false },
            title = { Text("Forerun confirmation") },
            text = {
                Text(
                    "Publishing requires a successful forerun. Have you climbed $displayName " +
                        "from start to finish exactly as set?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForerun = false
                        actions.onPublishProblem(problem.id)
                    },
                ) { Text("Yes — publish") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForerun = false }) { Text("Not yet") }
            },
        )
    }
}
