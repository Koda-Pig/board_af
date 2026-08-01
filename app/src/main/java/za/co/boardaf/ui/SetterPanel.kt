package za.co.boardaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import za.co.boardaf.BoardUiState
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.IssueSeverity
import za.co.boardaf.model.ProblemAngle
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemIssue
import za.co.boardaf.model.ProblemTags
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.setter.GuidedStep
import za.co.boardaf.setter.SetterMode
import za.co.boardaf.setter.SetterReducer
import za.co.boardaf.ui.theme.Coral
import za.co.boardaf.ui.theme.Forest
import za.co.boardaf.ui.theme.Sage

/**
 * The setter wizard: feet rule → start holds → other holds → finish holds →
 * details & review. Forward progress is gated per step; backward is always free.
 */
@Composable
fun SetterPanel(
    state: BoardUiState,
    actions: BoardActions,
    modifier: Modifier = Modifier,
) {
    val setter = state.setter
    val draft = setter.draft
    val step = setter.guidedStep
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    // Bitmask of GuidedStep ordinals where Next was tapped while gated.
    var attemptedNextMask by rememberSaveable { mutableIntStateOf(0) }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (draft.editingProblemId == null) "NEW PROBLEM" else "EDITING DRAFT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = draft.name.ifBlank { "Set the line" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = actions.onCancelSetting) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close setter")
                }
            }

            SetterModeToggle(mode = setter.mode, onSelect = actions.onSetSetterMode)

            if (setter.mode == SetterMode.QUICK) {
                QuickSetContent(
                    state = state,
                    actions = actions,
                    onClearRequested = { confirmClear = true },
                )
            } else {
                WizardStepper(
                    state = state,
                    actions = actions,
                    hasAttemptedNext = attemptedNextMask and (1 shl step.ordinal) != 0,
                )

                if (step != GuidedStep.FEET_RULE) {
                    FeetRuleBanner(
                        feetRule = draft.feetRule,
                        footMarkCount = draft.countFor(ProblemHoldRole.FOOT_ONLY),
                    )
                }

                when (step) {
                    GuidedStep.FEET_RULE -> FeetRuleStep(state = state, actions = actions)
                    GuidedStep.START, GuidedStep.OTHER, GuidedStep.FINISH -> HoldStep(
                        state = state,
                        actions = actions,
                        onClearRequested = { confirmClear = true },
                    )
                    GuidedStep.DETAILS -> DetailsStep(state = state, actions = actions)
                }

                MarkerLegend()

                WizardNavRow(
                    state = state,
                    actions = actions,
                    onAttemptNext = {
                        attemptedNextMask = attemptedNextMask or (1 shl step.ordinal)
                    },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all holds?") },
            text = { Text("This removes all ${draft.assignments.size} hold assignments from the draft. You can undo afterwards.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        actions.onClearDraftHolds()
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Keep") }
            },
        )
    }
}

@Composable
private fun SetterModeToggle(mode: SetterMode, onSelect: (SetterMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        FilterChip(
            selected = mode == SetterMode.GUIDED,
            onClick = { onSelect(SetterMode.GUIDED) },
            label = { Text("Guided steps") },
        )
        FilterChip(
            selected = mode == SetterMode.QUICK,
            onClick = { onSelect(SetterMode.QUICK) },
            label = { Text("Quick set") },
        )
    }
}

/**
 * The single-canvas fast path: taps toggle membership, roles are inferred
 * (lowest row starts, topmost finishes, kickboard is feet), and the details
 * form sits right below — no steps, no gating. Publishing still runs the same
 * validation and forerun confirmation as the wizard.
 */
@Composable
private fun QuickSetContent(
    state: BoardUiState,
    actions: BoardActions,
    onClearRequested: () -> Unit,
) {
    val setter = state.setter
    val draft = setter.draft

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Tap the holds you want. The lowest row becomes the start, the topmost " +
                "row the finish, kickboard holds become feet. Switch to Guided steps to " +
                "fine-tune individual roles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Feet rule", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FeetRuleSelector(state = state, actions = actions)

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onUndo, enabled = setter.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = actions.onRedo, enabled = setter.canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
            }
            IconButton(
                onClick = onClearRequested,
                enabled = draft.assignments.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear all holds")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = holdCountLabel(draft.assignments.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        MarkerLegend()
        StartFinishExplanation(startRule = draft.startRule, finishRule = draft.finishRule)

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        DraftDetailsForm(state = state, actions = actions)
    }
}

@Composable
private fun WizardStepper(
    state: BoardUiState,
    actions: BoardActions,
    hasAttemptedNext: Boolean,
) {
    val setter = state.setter
    val draft = setter.draft
    val current = setter.guidedStep
    val blocking = SetterReducer.firstUnsatisfiedStep(draft)
    val reachableOrdinal = blocking?.ordinal ?: GuidedStep.entries.lastIndex
    val nextEnabled = blocking == null || blocking.ordinal > current.ordinal
    val listState = rememberLazyListState()

    LaunchedEffect(current) {
        listState.animateScrollToItem(current.ordinal)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Step ${current.ordinal + 1} of ${GuidedStep.entries.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(GuidedStep.entries) { step ->
                FilterChip(
                    selected = current == step,
                    enabled = step.ordinal <= current.ordinal || step.ordinal <= reachableOrdinal,
                    onClick = { actions.onGoToGuidedStep(step) },
                    label = { Text("${step.ordinal + 1} · ${step.title}") },
                    leadingIcon = if (step.gateHint != null && SetterReducer.stepSatisfied(draft, step)) {
                        {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "${step.title} done",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
        // DETAILS renders no Next button, so hasAttemptedNext can never be set
        // there — yet that is exactly where a user lands on an incomplete problem
        // and needs a route back to the blocking step.
        val showGate = !nextEnabled &&
            blocking != null &&
            (hasAttemptedNext || current == GuidedStep.DETAILS)
        if (showGate) {
            TextButton(
                onClick = { actions.onGoToGuidedStep(blocking) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(
                    // Name the destination: the chip for that step is often
                    // scrolled off-screen, so "go there" has to be explicit.
                    text = "${blocking.gateHint.orEmpty()} " +
                        "Go to step ${blocking.ordinal + 1} · ${blocking.title}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Coral,
                )
            }
        } else {
            Text(
                text = current.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WizardNavRow(
    state: BoardUiState,
    actions: BoardActions,
    onAttemptNext: () -> Unit,
) {
    val current = state.setter.guidedStep
    val blocking = SetterReducer.firstUnsatisfiedStep(state.setter.draft)
    val nextEnabled = blocking == null || blocking.ordinal > current.ordinal

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = actions.onGuidedBack,
            enabled = current.ordinal > 0,
        ) { Text("Back") }
        Spacer(Modifier.weight(1f))
        if (current != GuidedStep.DETAILS) {
            // Stay clickable when gated so the first tap can reveal the coral gate hint.
            Button(
                onClick = {
                    if (nextEnabled) actions.onGuidedNext() else onAttemptNext()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (nextEnabled) Forest else Forest.copy(alpha = 0.38f),
                    contentColor = Color.White,
                ),
            ) { Text("Next") }
        }
    }
}

@Composable
private fun FeetRuleSelector(state: BoardUiState, actions: BoardActions) {
    val draft = state.setter.draft
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(availableFeetRules(state)) { rule ->
                FilterChip(
                    selected = draft.feetRule == rule,
                    onClick = { actions.onSetFeetRule(rule) },
                    label = { Text(rule.label) },
                )
            }
        }
        Text(
            text = draft.feetRule.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeetRuleStep(state: BoardUiState, actions: BoardActions) {
    val setter = state.setter
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FeetRuleSelector(state = state, actions = actions)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onUndo, enabled = setter.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = actions.onRedo, enabled = setter.canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
            }
        }
    }
}

@Composable
private fun HoldStep(
    state: BoardUiState,
    actions: BoardActions,
    onClearRequested: () -> Unit,
) {
    val setter = state.setter
    val draft = setter.draft
    val paletteRoles = when (setter.guidedStep) {
        GuidedStep.START -> listOf(ProblemHoldRole.START)
        GuidedStep.FINISH -> listOf(ProblemHoldRole.FINISH)
        else -> if (draft.feetRule.usesFootMarks) {
            listOf(ProblemHoldRole.REGULAR, ProblemHoldRole.FOOT_ONLY)
        } else {
            listOf(ProblemHoldRole.REGULAR)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (paletteRoles.size > 1) {
            Text("Tap role, then tap holds", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(paletteRoles) { role ->
                FilterChip(
                    selected = setter.activeRole == role,
                    onClick = { actions.onSelectRole(role) },
                    label = { Text("${role.label} · ${draft.countFor(role)}") },
                    leadingIcon = { ProblemMarker(role = role, size = 18.dp) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onUndo, enabled = setter.canUndo) {
                Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = actions.onRedo, enabled = setter.canRedo) {
                Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
            }
            IconButton(
                onClick = onClearRequested,
                enabled = draft.assignments.isNotEmpty(),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear all holds")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = holdCountLabel(draft.assignments.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailsStep(state: BoardUiState, actions: BoardActions) {
    val draft = state.setter.draft

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StartFinishExplanation(startRule = draft.startRule, finishRule = draft.finishRule)

        // The board stays on screen here but takes no taps, which is worth saying
        // out loud — this is where a library edit lands.
        Text(
            text = "Holds are locked while you review. " +
                "Go back to a hold step to add or change them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        DraftDetailsForm(state = state, actions = actions)
    }
}

/** Name, grade, angle, accent, tags, notes, validation and save/publish — shared by wizard review and quick set. */
@Composable
private fun DraftDetailsForm(state: BoardUiState, actions: BoardActions) {
    val draft = state.setter.draft
    val issues = state.draftIssues
    val errors = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings = issues.filter { it.severity == IssueSeverity.WARNING }
    var confirmForerun by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = actions.onDraftNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Problem name") },
            singleLine = true,
        )
        Text("Grade · setter estimate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(BoulderGrade.options(state.gradeSystem)) { grade ->
                FilterChip(
                    selected = draft.grade.label(state.gradeSystem) == grade.label(state.gradeSystem),
                    onClick = { actions.onDraftGradeChange(grade) },
                    label = { Text(grade.label(state.gradeSystem)) },
                )
            }
        }
        AngleField(
            angleDegrees = draft.angleDegrees,
            onAngleChange = actions.onDraftAngleChange,
        )
        Text("Accent color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(Accent.entries) { accent ->
                FilterChip(
                    selected = draft.accent == accent,
                    onClick = { actions.onDraftAccentChange(accent) },
                    label = { Text(accent.label) },
                    leadingIcon = {
                        Box(Modifier.size(8.dp).background(accent.color(), CircleShape))
                    },
                )
            }
        }
        Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(ProblemTags.suggestions) { tag ->
                FilterChip(
                    selected = tag in draft.tags,
                    onClick = { actions.onToggleDraftTag(tag) },
                    label = { Text(tag) },
                )
            }
        }
        OutlinedTextField(
            value = draft.note,
            onValueChange = actions.onDraftNoteChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Setter notes") },
            minLines = 2,
            maxLines = 4,
        )

        if (errors.isEmpty()) {
            Surface(color = Sage.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                Text(
                    text = "Everything checks out. Publishing still needs a successful forerun.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        } else {
            IssueGroup(title = "Fix before publishing", issues = errors, tint = Coral)
        }
        if (warnings.isNotEmpty()) {
            IssueGroup(title = "Worth a look", issues = warnings, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = actions.onSaveDraftAndClose,
                enabled = draft.hasContent,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (draft.baseState == PublicationState.PUBLISHED || draft.baseState == PublicationState.BENCHMARK) "Save" else "Save draft")
            }
            Button(
                onClick = { confirmForerun = true },
                enabled = !ProblemValidator.hasErrors(issues),
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text("Publish…", maxLines = 1)
            }
        }
    }

    if (confirmForerun) {
        AlertDialog(
            onDismissRequest = { confirmForerun = false },
            title = { Text("Forerun confirmation") },
            text = {
                Text(
                    "Publishing requires a successful forerun. Have you climbed " +
                        (draft.name.ifBlank { "this problem" }) +
                        " from start to finish exactly as set, using the rules above?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForerun = false
                        actions.onConfirmForerunAndPublish()
                    },
                ) { Text("Yes — publish") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForerun = false }) { Text("Not yet") }
            },
        )
    }
}

/**
 * The wall adjusts 0–90°; the angle belongs to the problem, not the board.
 * Commits on release like the kickboard slider so autosave isn't spammed per frame.
 */
@Composable
private fun AngleField(angleDegrees: Int, onAngleChange: (Int) -> Unit) {
    var angleDraft by remember(angleDegrees) { mutableFloatStateOf(angleDegrees.toFloat()) }
    Column {
        Text(
            text = "Wall angle · ${angleDraft.roundToInt()}°",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = angleDraft,
            onValueChange = {
                angleDraft = (it / ProblemAngle.STEP_DEGREES).roundToInt() *
                    ProblemAngle.STEP_DEGREES.toFloat()
            },
            onValueChangeFinished = { onAngleChange(angleDraft.roundToInt()) },
            valueRange = ProblemAngle.MIN_DEGREES.toFloat()..ProblemAngle.MAX_DEGREES.toFloat(),
        )
        Text(
            text = "The incline this problem is set and forerun at. Grades only mean something at this angle.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IssueGroup(title: String, issues: List<ProblemIssue>, tint: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = "$title · ${issues.size}",
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
        issues.groupBy { it.code }.forEach { (_, group) ->
            group.forEach { issue ->
                Text(
                    text = "• ${issue.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun availableFeetRules(state: BoardUiState): List<FeetRule> =
    FeetRule.entries.filter { it != FeetRule.OPEN_KICKBOARD || state.board.hasKickboard }
