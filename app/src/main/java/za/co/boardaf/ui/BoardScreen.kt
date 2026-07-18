package za.co.boardaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.IssueSeverity
import za.co.boardaf.model.Problem
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.ui.theme.BoardDark
import za.co.boardaf.ui.theme.BoardLine
import za.co.boardaf.ui.theme.BoardMuted
import za.co.boardaf.ui.theme.BoardPaper
import za.co.boardaf.ui.theme.Coral
import za.co.boardaf.ui.theme.Gold
import za.co.boardaf.ui.theme.Moss

@Composable
fun BoardScreen(
    state: BoardUiState,
    actions: BoardActions,
    contentPadding: PaddingValues,
) {
    val problem = state.selectedProblem
    val activeAssignments = if (state.isSetting) state.setter.draft.assignments else problem?.assignments.orEmpty()
    val surfaceMode = if (state.isSetting) BoardDisplayMode.SET else BoardDisplayMode.VIEW

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
                        BoardSurface(
                            board = state.board,
                            assignments = activeAssignments,
                            mode = surfaceMode,
                            onHoldClick = actions.onTapHold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .aspectRatio(BoardGeometry.IMAGE_ASPECT_RATIO),
                        )
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
            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { BoardHeader(state = state, problem = problem) }
                if (!state.isSetting && problem != null) {
                    item {
                        FeetRuleBanner(
                            feetRule = problem.feetRule,
                            footMarkCount = problem.assignments.count { it.role == ProblemHoldRole.FOOT_ONLY },
                        )
                    }
                    item {
                        StartFinishExplanation(
                            startRule = problem.startRule,
                            finishRule = problem.finishRule,
                        )
                    }
                }
                item {
                    BoardSurface(
                        board = state.board,
                        assignments = activeAssignments,
                        mode = surfaceMode,
                        onHoldClick = actions.onTapHold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(BoardGeometry.IMAGE_ASPECT_RATIO),
                    )
                }
                item {
                    if (state.isSetting) {
                        SetterPanel(state = state, actions = actions)
                    } else if (problem != null) {
                        ProblemDetails(
                            problem = problem,
                            state = state,
                            actions = actions,
                        )
                    }
                }
            }
        }
    }
}

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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.isSetting) "ROUTE SETTER" else "NOW VIEWING",
                color = BoardMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (state.isSetting) {
                    state.setter.draft.name.ifBlank { "Choose your holds" }
                } else {
                    problem?.name.orEmpty()
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!state.isSetting && problem != null) {
            StatusChip(state = problem.publicationState)
            Spacer(Modifier.width(8.dp))
            Surface(color = BoardDark, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = problem.grade.label(state.gradeSystem),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = BoardPaper,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
fun StatusChip(state: PublicationState, modifier: Modifier = Modifier) {
    val (container, content) = when (state) {
        PublicationState.DRAFT -> BoardLine to BoardDark
        PublicationState.NEEDS_REVIEW -> Coral.copy(alpha = 0.22f) to BoardDark
        PublicationState.PUBLISHED -> Moss.copy(alpha = 0.30f) to BoardDark
        PublicationState.BENCHMARK -> Gold.copy(alpha = 0.35f) to BoardDark
        PublicationState.ARCHIVED -> Color(0xFFE4E4DC) to BoardMuted
    }
    Surface(color = container, shape = RoundedCornerShape(7.dp), modifier = modifier) {
        Text(
            text = state.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = content,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
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
                    Text(problem.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${problem.grade.label(state.gradeSystem)} setter estimate · " +
                            "${problem.assignments.size} holds · ${problem.setter}",
                        color = BoardMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                StatusChip(state = problem.publicationState)
            }

            if (problem.publicationState == PublicationState.NEEDS_REVIEW) {
                Surface(color = Coral.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "This problem needs repair before it can be published again:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        issues.filter { it.severity == IssueSeverity.ERROR }.forEach {
                            Text("• ${it.message}", style = MaterialTheme.typography.bodySmall, color = BoardMuted)
                        }
                    }
                }
            }

            if (problem.note.isNotBlank()) {
                Text(problem.note, color = BoardMuted, style = MaterialTheme.typography.bodyMedium)
            }
            if (problem.tags.isNotEmpty()) {
                Text(
                    text = problem.tags.joinToString("  ·  "),
                    color = BoardMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }

            HorizontalDivider(color = BoardLine)
            MarkerLegend()
            HorizontalDivider(color = BoardLine)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { actions.onStartEditing(problem.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (problem.publicationState == PublicationState.NEEDS_REVIEW) "Repair" else "Edit")
                }
                OutlinedButton(
                    onClick = { actions.onDuplicateProblem(problem.id) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Duplicate")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (problem.publicationState) {
                    PublicationState.DRAFT, PublicationState.NEEDS_REVIEW -> {
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
                }
                if (problem.publicationState != PublicationState.ARCHIVED) {
                    OutlinedButton(
                        onClick = { actions.onArchiveProblem(problem.id) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Archive") }
                }
            }
        }
    }

    if (confirmForerun) {
        AlertDialog(
            onDismissRequest = { confirmForerun = false },
            title = { Text("Forerun confirmation") },
            text = {
                Text(
                    "Publishing requires a successful forerun. Have you climbed ${problem.name} " +
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
