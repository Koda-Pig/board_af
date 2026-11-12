package za.co.boardaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import za.co.boardaf.BoardUiState
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.IssueCode
import za.co.boardaf.model.IssueSeverity
import za.co.boardaf.model.ProblemAngle
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemIssue
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.setter.GuidedStep
import za.co.boardaf.setter.SetterMode
import za.co.boardaf.setter.SetterReducer
import za.co.boardaf.ui.theme.Forest
import za.co.boardaf.ui.theme.Sage

/**
 * UI-only state the setter's strip and body share: which steps have had a gated
 * Next tapped (so the gate hint shows), and the clear-all confirmation.
 * Remember it inside the setting branch so it resets with each session.
 */
@Stable
class SetterChromeState(attemptedNextMask: Int = 0) {
    /** Bitmask of GuidedStep ordinals where Next was tapped while gated. */
    var attemptedNextMask by mutableIntStateOf(attemptedNextMask)
    var confirmClear by mutableStateOf(false)

    fun attemptedNext(step: GuidedStep): Boolean = attemptedNextMask and (1 shl step.ordinal) != 0

    fun markAttemptedNext(step: GuidedStep) {
        attemptedNextMask = attemptedNextMask or (1 shl step.ordinal)
    }

    companion object {
        val Saver: Saver<SetterChromeState, Int> = Saver(
            save = { it.attemptedNextMask },
            restore = { SetterChromeState(it) },
        )
    }
}

@Composable
fun rememberSetterChromeState(): SetterChromeState =
    rememberSaveable(saver = SetterChromeState.Saver) { SetterChromeState() }

/**
 * The setter for the wide layout, where there is room for everything at once:
 * the working strip and the body in one card beside the board.
 */
@Composable
fun SetterPanel(
    state: BoardUiState,
    actions: BoardActions,
    modifier: Modifier = Modifier,
) {
    val chrome = rememberSetterChromeState()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SetterStrip(state = state, actions = actions, chrome = chrome, onShowDetails = {})
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SetterBody(state = state, actions = actions, chrome = chrome)
        }
    }
    SetterDialogs(state = state, actions = actions, chrome = chrome)
}

/**
 * Everything needed while tapping holds, sized to fit the phone sheet's collapsed
 * peek: which step you're on, what to do, the active role(s) with counts, undo,
 * and the way forward. The setter can work a whole problem from here without
 * expanding the sheet — only the feet rule and details steps need the body, and
 * the board screen expands the sheet for those on its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetterStrip(
    state: BoardUiState,
    actions: BoardActions,
    chrome: SetterChromeState,
    /** Bring the body into view; quick set's form lives there. */
    onShowDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setter = state.setter
    val draft = setter.draft
    val quick = setter.mode == SetterMode.QUICK
    val step = setter.guidedStep
    // isNewProblem, not editingProblemId: autosave gives a new draft an id on the
    // first tap, which used to flip this label to "Editing" mid-session.
    val session = if (setter.isNewProblem) "NEW PROBLEM" else "EDITING"
    val blocking = SetterReducer.firstUnsatisfiedStep(draft)
    val nextEnabled = blocking == null || blocking.ordinal > step.ordinal

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!quick && step.ordinal > 0) {
                IconButton(onClick = actions.onGuidedBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous step")
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (quick) {
                        "$session · QUICK SET"
                    } else {
                        "$session · STEP ${step.ordinal + 1} OF ${GuidedStep.entries.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (quick) "Tap your holds" else step.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = actions.onCancelSetting) {
                Icon(Icons.Rounded.Close, contentDescription = "Close setter")
            }
        }

        if (quick) {
            Text(
                text = "Lowest row starts, top row finishes, kickboard holds are feet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StepHint(
                state = state,
                actions = actions,
                hasAttemptedNext = chrome.attemptedNext(step),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                quick -> {
                    Text(
                        text = holdCountLabel(draft.assignments.size),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f),
                    )
                    UndoButton(state, actions)
                    Button(
                        onClick = onShowDetails,
                        colors = ButtonDefaults.buttonColors(containerColor = Forest, contentColor = Color.White),
                    ) { Text("Details") }
                }

                step.roleForStep != null -> {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        paletteRoles(state).forEach { role ->
                            FilterChip(
                                selected = setter.activeRole == role,
                                onClick = { actions.onSelectRole(role) },
                                label = { Text("${role.label} · ${draft.countFor(role)}") },
                                leadingIcon = { ProblemMarker(role = role, size = 18.dp, onPhoto = false) },
                            )
                        }
                    }
                    UndoButton(state, actions)
                }

                step == GuidedStep.FEET_RULE -> Text(
                    text = draft.feetRule.label,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )

                else -> Spacer(Modifier.weight(1f))
            }

            if (!quick && step != GuidedStep.DETAILS) {
                // Stays clickable when gated so the first tap can reveal the gate hint.
                Button(
                    onClick = {
                        if (nextEnabled) actions.onGuidedNext() else chrome.markAttemptedNext(step)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (nextEnabled) Forest else Forest.copy(alpha = 0.38f),
                        contentColor = Color.White,
                    ),
                ) { Text("Next") }
            }
        }
    }
}

/**
 * The step's instruction, or — once Next was tapped while gated — why it's
 * blocked. On details & review the blocking step is an earlier one, so the hint
 * also offers the way back there.
 */
@Composable
private fun StepHint(state: BoardUiState, actions: BoardActions, hasAttemptedNext: Boolean) {
    val current = state.setter.guidedStep
    val blocking = SetterReducer.firstUnsatisfiedStep(state.setter.draft)
    val gated = blocking != null && blocking.ordinal <= current.ordinal
    // DETAILS renders no Next button, so hasAttemptedNext can never be set there —
    // yet that is exactly where a user lands on an incomplete problem and needs a
    // route back to the blocking step.
    val showGate = gated && (hasAttemptedNext || current == GuidedStep.DETAILS)

    if (showGate && blocking != null) {
        Column {
            Text(
                text = blocking.gateHint.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            // Only offer to go somewhere when it's somewhere else.
            if (blocking != current) {
                TextButton(
                    onClick = { actions.onGoToGuidedStep(blocking) },
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) { Text("Go to ${blocking.title}") }
            }
        }
    } else {
        Text(
            text = current.hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UndoButton(state: BoardUiState, actions: BoardActions) {
    IconButton(onClick = actions.onUndo, enabled = state.setter.canUndo) {
        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
    }
}

private fun paletteRoles(state: BoardUiState): List<ProblemHoldRole> {
    val draft = state.setter.draft
    return when (state.setter.guidedStep) {
        GuidedStep.START -> listOf(ProblemHoldRole.START)
        GuidedStep.FINISH -> listOf(ProblemHoldRole.FINISH)
        else -> if (draft.feetRule.usesFootMarks) {
            listOf(ProblemHoldRole.REGULAR, ProblemHoldRole.FOOT_ONLY)
        } else {
            listOf(ProblemHoldRole.REGULAR)
        }
    }
}

/**
 * What the expanded sheet adds below the strip: mode switch, step overview, the
 * feet rule picker, the less frequent hold tools, and the details form.
 */
@Composable
fun SetterBody(
    state: BoardUiState,
    actions: BoardActions,
    chrome: SetterChromeState,
    modifier: Modifier = Modifier,
) {
    val setter = state.setter
    val draft = setter.draft
    val step = setter.guidedStep

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SetterModeToggle(mode = setter.mode, onSelect = actions.onSetSetterMode)

        if (setter.mode == SetterMode.QUICK) {
            QuickSetContent(
                state = state,
                actions = actions,
                onClearRequested = { chrome.confirmClear = true },
            )
        } else {
            StepOverview(state = state, actions = actions)

            if (step != GuidedStep.FEET_RULE) {
                FeetRuleBanner(
                    feetRule = draft.feetRule,
                    footMarkCount = draft.countFor(ProblemHoldRole.FOOT_ONLY),
                )
            }

            when (step) {
                GuidedStep.FEET_RULE -> FeetRuleSelector(state = state, actions = actions)
                GuidedStep.START, GuidedStep.OTHER, GuidedStep.FINISH -> {
                    HoldTools(state = state, actions = actions, onClearRequested = { chrome.confirmClear = true })
                    MarkerLegend()
                }
                GuidedStep.DETAILS -> DetailsStep(state = state, actions = actions)
            }
        }
    }
}

/** Dialogs shared by strip and body; render once, alongside them. */
@Composable
fun SetterDialogs(state: BoardUiState, actions: BoardActions, chrome: SetterChromeState) {
    if (chrome.confirmClear) {
        AlertDialog(
            onDismissRequest = { chrome.confirmClear = false },
            title = { Text("Clear all holds?") },
            text = {
                Text(
                    "This removes all ${state.setter.draft.assignments.size} hold assignments " +
                        "from the draft. You can undo afterwards.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        chrome.confirmClear = false
                        actions.onClearDraftHolds()
                    },
                ) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { chrome.confirmClear = false }) { Text("Keep") }
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
    val draft = state.setter.draft

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Switch to Guided steps to fine-tune individual roles.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("Feet rule", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FeetRuleSelector(state = state, actions = actions)

        HoldTools(state = state, actions = actions, onClearRequested = onClearRequested)
        MarkerLegend()
        StartFinishExplanation(startRule = draft.startRule, finishRule = draft.finishRule)

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        DraftDetailsForm(state = state, actions = actions)
    }
}

/** All five steps at a glance, wrapped rather than scrolled so none is clipped. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepOverview(state: BoardUiState, actions: BoardActions) {
    val draft = state.setter.draft
    val current = state.setter.guidedStep
    val blocking = SetterReducer.firstUnsatisfiedStep(draft)
    val reachableOrdinal = blocking?.ordinal ?: GuidedStep.entries.lastIndex

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        GuidedStep.entries.forEach { step ->
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeetRuleSelector(state: BoardUiState, actions: BoardActions) {
    val draft = state.setter.draft
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            availableFeetRules(state).forEach { rule ->
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

/** The less frequent hold tools; undo itself lives in the strip. */
@Composable
private fun HoldTools(
    state: BoardUiState,
    actions: BoardActions,
    onClearRequested: () -> Unit,
) {
    val setter = state.setter
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onRedo, enabled = setter.canRedo) {
            Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
        }
        IconButton(
            onClick = onClearRequested,
            enabled = setter.draft.assignments.isNotEmpty(),
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Clear all holds")
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = holdCountLabel(setter.draft.assignments.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailsStep(state: BoardUiState, actions: BoardActions) {
    val draft = state.setter.draft

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StartFinishExplanation(startRule = draft.startRule, finishRule = draft.finishRule)

        // The board takes no taps on this step; say so, since a library edit
        // lands here and the obvious first move is to tap a hold.
        Text(
            text = "Holds are locked on this step. Go back a step to change them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        DraftDetailsForm(state = state, actions = actions)
    }
}

/** Name, grade, angle, accent, validation and save/publish — shared by wizard review and quick set. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftDetailsForm(state: BoardUiState, actions: BoardActions) {
    val draft = state.setter.draft
    val issues = state.draftIssues
    val errors = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings = issues.filter { it.severity == IssueSeverity.WARNING }
    // A missing name is said under the name field, in neutral ink: shown as a
    // coral error on arrival, it read as a mistake before anything was typed.
    val nameMissing = errors.any { it.code == IssueCode.NAME_MISSING }
    val otherErrors = errors.filterNot { it.code == IssueCode.NAME_MISSING }
    var confirmForerun by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft.name,
            onValueChange = actions.onDraftNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Problem name") },
            supportingText = if (nameMissing) {
                { Text("Needed before you can publish.") }
            } else {
                null
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
        )
        Text("Grade · setter estimate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BoulderGrade.options(state.gradeSystem).forEach { grade ->
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
        Column {
            Text("Accent color", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "The dot that tells this problem apart in the library.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Accent.entries.forEach { accent ->
                FilterChip(
                    selected = draft.accent == accent,
                    onClick = { actions.onDraftAccentChange(accent) },
                    label = { Text(accent.label) },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).background(accent.color(), CircleShape))
                    },
                )
            }
        }
        when {
            otherErrors.isNotEmpty() -> IssueGroup(
                title = "Fix before publishing",
                issues = otherErrors,
                tint = MaterialTheme.colorScheme.error,
            )
            !nameMissing -> Surface(color = Sage.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                Text(
                    text = "Everything checks out. Publishing still needs a successful forerun.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
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
                Text(if (draft.baseState == PublicationState.PUBLISHED) "Save" else "Save draft")
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
private fun IssueGroup(title: String, issues: List<ProblemIssue>, tint: Color) {
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
