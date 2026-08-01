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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import za.co.boardaf.BoardUiState
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.IssueSeverity
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.setter.SetterMode
import za.co.boardaf.ui.theme.Coral
import za.co.boardaf.ui.theme.Forest
import za.co.boardaf.ui.theme.Gold
import kotlinx.coroutines.launch
import za.co.boardaf.ui.theme.Moss

internal fun problemDisplayName(name: String): String = name.ifBlank { "Untitled draft" }

internal fun holdCountLabel(count: Int): String =
    if (count == 1) "1 hold" else "$count holds"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    state: BoardUiState,
    actions: BoardActions,
    contentPadding: PaddingValues,
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
                        BoardHeader(state = state, problem = problem)
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 620.dp)
                                    .aspectRatio(BoardGeometry.IMAGE_ASPECT_RATIO),
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
        } else {
            val sheetState = rememberStandardBottomSheetState(
                initialValue = SheetValue.PartiallyExpanded,
                skipHiddenState = true,
            )
            val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
            BottomSheetScaffold(
                scaffoldState = scaffoldState,
                sheetPeekHeight = 200.dp,
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                sheetContent = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (state.isSetting) {
                            SetterPanel(state = state, actions = actions)
                        } else if (problem != null) {
                            FeetRuleBanner(
                                feetRule = problem.feetRule,
                                footMarkCount = problem.assignments.count { it.role == ProblemHoldRole.FOOT_ONLY },
                            )
                            StartFinishExplanation(
                                startRule = problem.startRule,
                                finishRule = problem.finishRule,
                            )
                            ProblemDetails(
                                problem = problem,
                                state = state,
                                actions = actions,
                            )
                        } else {
                            Text(
                                "No problem selected. Open the library or tap + to set one.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
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
                    BoardHeader(state = state, problem = problem)
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
    val activeProblems = state.problems.filter { it.publicationState != PublicationState.ARCHIVED }
    val activeKey = if (state.isSetting) SETTER_SURFACE_KEY else state.selectedProblem?.id ?: "none"

    AnimatedContent(
        targetState = activeKey,
        transitionSpec = {
            val from = activeProblems.indexOfFirst { it.id == initialState }
            val to = activeProblems.indexOfFirst { it.id == targetState }
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
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BoardGeometry.IMAGE_ASPECT_RATIO),
        )
    }
}

private const val SETTER_SURFACE_KEY = "setter-session"

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
            StartFinishExplanation(startRule = problem.startRule, finishRule = problem.finishRule)
            ProblemDetails(problem = problem, state = state, actions = actions)
        }
    }
}

@Composable
private fun BoardHeader(state: BoardUiState, problem: Problem?) {
    val issues = if (!state.isSetting && problem != null) {
        ProblemValidator.validate(problem, state.board)
    } else {
        emptyList()
    }
    val repairReason = issues
        .filter { it.severity == IssueSeverity.ERROR }
        .firstOrNull()
        ?.message

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.isSetting) "ROUTE SETTER" else "NOW VIEWING",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (state.isSetting) {
                    state.setter.draft.name.ifBlank { "Choose your holds" }
                } else {
                    problemDisplayName(problem?.name.orEmpty())
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!state.isSetting &&
                problem?.publicationState == PublicationState.NEEDS_REVIEW &&
                repairReason != null
            ) {
                Text(
                    text = repairReason,
                    color = Coral,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (!state.isSetting && problem != null) {
            StatusChip(state = problem.publicationState)
            Spacer(Modifier.width(8.dp))
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
        PublicationState.BENCHMARK ->
            Gold.copy(alpha = 0.35f) to MaterialTheme.colorScheme.onSurface
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

@Composable
private fun ProblemDetails(
    problem: Problem,
    state: BoardUiState,
    actions: BoardActions,
) {
    val issues = ProblemValidator.validate(problem, state.board)
    val hasErrors = ProblemValidator.hasErrors(issues)
    var confirmForerun by rememberSaveable(problem.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(problem.id) { mutableStateOf(false) }
    val needsRepair = problem.publicationState == PublicationState.NEEDS_REVIEW
    val displayName = problemDisplayName(problem.name)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .background(problem.accent.color(), CircleShape),
                )
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${problem.grade.label(state.gradeSystem)} at ${problem.angleDegrees}° · " +
                            "${holdCountLabel(problem.assignments.size)} · ${problem.setter}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusChip(state = problem.publicationState)
            }

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

            if (problem.note.isNotBlank()) {
                Text(problem.note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (problem.tags.isNotEmpty()) {
                Text(
                    text = problem.tags.joinToString("  ·  "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            MarkerLegend()
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            if (needsRepair) {
                Button(
                    onClick = { actions.onStartEditing(problem.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
                ) {
                    Text("Repair")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { actions.onDuplicateProblem(problem.id) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Duplicate") }
                    OutlinedButton(
                        onClick = { confirmForerun = true },
                        enabled = !hasErrors,
                        modifier = Modifier.weight(1f),
                    ) { Text("Publish…") }
                }
                TextButton(
                    onClick = { actions.onArchiveProblem(problem.id) },
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Archive") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { actions.onStartEditing(problem.id) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Edit") }
                    OutlinedButton(
                        onClick = { actions.onDuplicateProblem(problem.id) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Duplicate") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (problem.publicationState) {
                        PublicationState.DRAFT -> {
                            OutlinedButton(
                                onClick = { confirmForerun = true },
                                enabled = !hasErrors,
                                modifier = Modifier.weight(1f),
                            ) { Text("Publish…") }
                        }
                        PublicationState.PUBLISHED -> {
                            OutlinedButton(
                                onClick = { actions.onToggleBenchmark(problem.id) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Mark benchmark") }
                        }
                        PublicationState.BENCHMARK -> {
                            OutlinedButton(
                                onClick = { actions.onToggleBenchmark(problem.id) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Unmark benchmark") }
                        }
                        PublicationState.ARCHIVED -> {
                            OutlinedButton(
                                onClick = { actions.onUnarchiveProblem(problem.id) },
                                modifier = Modifier.weight(1f),
                            ) { Text("Restore") }
                        }
                        PublicationState.NEEDS_REVIEW -> Unit
                    }
                    if (problem.publicationState != PublicationState.ARCHIVED) {
                        OutlinedButton(
                            onClick = { actions.onArchiveProblem(problem.id) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Archive") }
                    }
                }
            }

            TextButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.align(Alignment.End),
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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete problem?") },
            text = {
                Text("“$displayName” will be permanently removed. This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        actions.onDeleteProblem(problem.id)
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
