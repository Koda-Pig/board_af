package za.co.boardaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.boardaf.BoardUiState
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.Problem
import za.co.boardaf.model.PublicationState
import za.co.boardaf.ui.theme.BoardDark
import za.co.boardaf.ui.theme.BoardMuted
import za.co.boardaf.ui.theme.BoardPaper

private const val ALL = "All"

@Composable
fun ProblemsScreen(
    state: BoardUiState,
    actions: BoardActions,
    contentPadding: PaddingValues,
    onOpenProblem: (String) -> Unit,
    onEditProblem: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf(ALL) }
    var gradeFilter by rememberSaveable(state.gradeSystem) { mutableStateOf(ALL) }
    var feetFilter by rememberSaveable { mutableStateOf(ALL) }
    var setterFilter by rememberSaveable { mutableStateOf(ALL) }
    var tagFilter by rememberSaveable { mutableStateOf(ALL) }

    val setters = state.problems.map { it.setter }.filter { it.isNotBlank() }.distinct().sorted()
    val tags = state.problems.flatMap { it.tags }.distinct().sorted()

    val visibleProblems = state.problems.filter { problem ->
        val statusOk = when (statusFilter) {
            ALL -> problem.publicationState != PublicationState.ARCHIVED
            else -> problem.publicationState.label == statusFilter
        }
        statusOk &&
            (gradeFilter == ALL || problem.grade.label(state.gradeSystem) == gradeFilter) &&
            (feetFilter == ALL || problem.feetRule.label == feetFilter) &&
            (setterFilter == ALL || problem.setter == setterFilter) &&
            (tagFilter == ALL || tagFilter in problem.tags) &&
            problem.name.contains(query, ignoreCase = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("PROBLEM LIBRARY", style = MaterialTheme.typography.labelSmall, color = BoardMuted, fontWeight = FontWeight.Bold)
            Text("${state.problems.size} problems", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "Grades are setter estimates.",
                style = MaterialTheme.typography.bodySmall,
                color = BoardMuted,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find a problem") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
            )
        }
        item {
            FilterRow(
                title = "Status",
                options = listOf(ALL) + PublicationState.entries.map { it.label },
                selected = statusFilter,
                onSelect = { statusFilter = it },
            )
        }
        item {
            FilterRow(
                title = "Grade",
                options = listOf(ALL) + BoulderGrade.options(state.gradeSystem).map { it.label(state.gradeSystem) },
                selected = gradeFilter,
                onSelect = { gradeFilter = it },
            )
        }
        item {
            FilterRow(
                title = "Feet rule",
                options = listOf(ALL) + FeetRule.entries.map { it.label },
                selected = feetFilter,
                onSelect = { feetFilter = it },
            )
        }
        if (setters.size > 1) {
            item {
                FilterRow(
                    title = "Setter",
                    options = listOf(ALL) + setters,
                    selected = setterFilter,
                    onSelect = { setterFilter = it },
                )
            }
        }
        if (tags.isNotEmpty()) {
            item {
                FilterRow(
                    title = "Tag",
                    options = listOf(ALL) + tags,
                    selected = tagFilter,
                    onSelect = { tagFilter = it },
                )
            }
        }
        items(visibleProblems, key = { it.id }) { problem ->
            ProblemCard(
                problem = problem,
                state = state,
                actions = actions,
                onOpen = { onOpenProblem(problem.id) },
                onEdit = { onEditProblem(problem.id) },
            )
        }
        if (visibleProblems.isEmpty()) {
            item {
                Text(
                    "No problems match that filter.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(30.dp),
                    color = BoardMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun FilterRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = BoardMuted)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(options) { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(option) },
                )
            }
        }
    }
}

@Composable
private fun ProblemCard(
    problem: Problem,
    state: BoardUiState,
    actions: BoardActions,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (problem.id == state.selectedProblemId) CardDefaults.outlinedCardBorder() else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, top = 13.dp, bottom = 13.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(11.dp).background(problem.accent.color(), CircleShape))
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        problem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusChip(state = problem.publicationState)
                }
                Text(
                    "${problem.setter} · ${problem.assignments.size} holds · ${problem.feetRule.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoardMuted,
                )
            }
            Surface(color = BoardDark, shape = RoundedCornerShape(7.dp)) {
                Text(
                    "est. ${problem.grade.label(state.gradeSystem)}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = BoardPaper,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Actions for ${problem.name}")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(if (problem.publicationState == PublicationState.NEEDS_REVIEW) "Repair" else "Edit")
                        },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = {
                            menuOpen = false
                            actions.onDuplicateProblem(problem.id)
                        },
                    )
                    if (problem.publicationState == PublicationState.ARCHIVED) {
                        DropdownMenuItem(
                            text = { Text("Restore") },
                            onClick = {
                                menuOpen = false
                                actions.onUnarchiveProblem(problem.id)
                            },
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                menuOpen = false
                                actions.onArchiveProblem(problem.id)
                            },
                        )
                    }
                }
            }
        }
    }
}
