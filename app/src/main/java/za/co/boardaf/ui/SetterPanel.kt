package za.co.boardaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.boardaf.BoardUiState
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.IssueSeverity
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemIssue
import za.co.boardaf.model.ProblemTags
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.setter.GuidedStep
import za.co.boardaf.setter.SetterReducer
import za.co.boardaf.ui.theme.BoardLine
import za.co.boardaf.ui.theme.BoardMuted
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
                        color = BoardMuted,
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

            WizardStepper(state = state, actions = actions)

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
private fun WizardStepper(state: BoardUiState, actions: BoardActions) {
    val setter = state.setter
    val draft = setter.draft
    val current = setter.guidedStep
    val blocking = SetterReducer.firstUnsatisfiedStep(draft)
    val reachableOrdinal = blocking?.ordinal ?: GuidedStep.entries.lastIndex
    val nextEnabled = blocking == null || blocking.ordinal > current.ordinal

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (nextEnabled) current.hint else blocking.gateHint.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (nextEnabled) BoardMuted else Coral,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = actions.onGuidedBack,
                enabled = current.ordinal > 0,
            ) { Text("Back") }
            if (current != GuidedStep.DETAILS) {
                TextButton(
                    onClick = actions.onGuidedNext,
                    enabled = nextEnabled,
                ) { Text("Next") }
            }
        }
    }
}

@Composable
private fun FeetRuleStep(state: BoardUiState, actions: BoardActions) {
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
            color = BoardMuted,
        )
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
            Text("Tap role, then tap holds", style = MaterialTheme.typography.labelMedium, color = BoardMuted)
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
                text = "${draft.assignments.size} holds",
                style = MaterialTheme.typography.labelMedium,
                color = BoardMuted,
            )
        }
    }
}

@Composable
private fun DetailsStep(state: BoardUiState, actions: BoardActions) {
    val draft = state.setter.draft
    val issues = state.draftIssues
    val errors = issues.filter { it.severity == IssueSeverity.ERROR }
    val warnings = issues.filter { it.severity == IssueSeverity.WARNING }
    var confirmForerun by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StartFinishExplanation(startRule = draft.startRule, finishRule = draft.finishRule)

        HorizontalDivider(color = BoardLine)

        OutlinedTextField(
            value = draft.name,
            onValueChange = actions.onDraftNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Problem name") },
            singleLine = true,
        )
        Text("Grade · setter estimate", style = MaterialTheme.typography.labelMedium, color = BoardMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(BoulderGrade.options(state.gradeSystem)) { grade ->
                FilterChip(
                    selected = draft.grade.label(state.gradeSystem) == grade.label(state.gradeSystem),
                    onClick = { actions.onDraftGradeChange(grade) },
                    label = { Text(grade.label(state.gradeSystem)) },
                )
            }
        }
        Text("Accent color", style = MaterialTheme.typography.labelMedium, color = BoardMuted)
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
        Text("Tags", style = MaterialTheme.typography.labelMedium, color = BoardMuted)
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
            IssueGroup(title = "Worth a look", issues = warnings, tint = BoardMuted)
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
                    color = BoardMuted,
                )
            }
        }
    }
}

private fun availableFeetRules(state: BoardUiState): List<FeetRule> =
    FeetRule.entries.filter { it != FeetRule.OPEN_KICKBOARD || state.board.hasKickboard }
