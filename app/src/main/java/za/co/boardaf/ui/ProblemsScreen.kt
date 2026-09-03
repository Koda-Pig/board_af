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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import za.co.boardaf.BoardUiState
import za.co.boardaf.data.DeletedProblem
import za.co.boardaf.data.DeletedProblems
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.Problem
import za.co.boardaf.model.PublicationState

private const val ACTIVE = "Active"
private const val ALL = "All"

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(
    state: BoardUiState,
    actions: BoardActions,
    contentPadding: PaddingValues,
    onOpenProblem: (String) -> Unit,
    onEditProblem: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var statusFilter by rememberSaveable { mutableStateOf(ACTIVE) }
    var gradeFilter by rememberSaveable(state.gradeSystem) { mutableStateOf(ALL) }
    var angleFilter by rememberSaveable { mutableStateOf(ALL) }
    var feetFilter by rememberSaveable { mutableStateOf(ALL) }
    var setterFilter by rememberSaveable { mutableStateOf(ALL) }
    var deletedOpen by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Deleted problems are a separate list, so only the text query applies —
    // status, grade and the rest describe the live library.
    val deleted = state.deletedProblems
        .filter { problemDisplayName(it.problem.name).contains(query, ignoreCase = true) }
        .sortedByDescending { it.deletedAt }

    val setters = state.problems.map { it.setter }.filter { it.isNotBlank() }.distinct().sorted()
    // The angle filter only earns its row once the library actually spans inclines.
    val angles = state.problems.map { it.angleDegrees }.distinct().sorted()

    val statusOptions = listOf(ACTIVE, ALL) + PublicationState.entries.map { it.label }

    val visibleProblems = state.problems.filter { problem ->
        val statusOk = when (statusFilter) {
            ACTIVE -> problem.publicationState != PublicationState.ARCHIVED
            ALL -> true
            else -> problem.publicationState.label == statusFilter
        }
        statusOk &&
            (gradeFilter == ALL || problem.grade.label(state.gradeSystem) == gradeFilter) &&
            (angleFilter == ALL || "${problem.angleDegrees}°" == angleFilter) &&
            (feetFilter == ALL || problem.feetRule.label == feetFilter) &&
            (setterFilter == ALL || problem.setter == setterFilter) &&
            problemDisplayName(problem.name).contains(query, ignoreCase = true)
    }

    val filtering = statusFilter != ACTIVE ||
        gradeFilter != ALL ||
        angleFilter != ALL ||
        feetFilter != ALL ||
        setterFilter != ALL ||
        query.isNotBlank()

    val activeFilterChips = buildList {
        if (statusFilter != ACTIVE) add("Status: $statusFilter" to { statusFilter = ACTIVE })
        if (gradeFilter != ALL) add("Grade: $gradeFilter" to { gradeFilter = ALL })
        if (angleFilter != ALL) add("Angle: $angleFilter" to { angleFilter = ALL })
        if (feetFilter != ALL) add("Feet: $feetFilter" to { feetFilter = ALL })
        if (setterFilter != ALL) add("Setter: $setterFilter" to { setterFilter = ALL })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("PROBLEM LIBRARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(
                text = if (filtering) {
                    "${visibleProblems.size} of ${state.problems.size} problems"
                } else {
                    "${visibleProblems.size} problems"
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Grades are setter estimates.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find a problem") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
        item {
            TextButton(onClick = { filtersOpen = true }) {
                Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Filters")
            }
        }
        if (activeFilterChips.isNotEmpty()) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    activeFilterChips.forEach { (label, clear) ->
                        FilterChip(
                            selected = true,
                            onClick = clear,
                            label = { Text(label) },
                        )
                    }
                }
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (deleted.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "RECENTLY DELETED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Recoverable on this device for ${DeletedProblems.RETENTION_MS.inDays()} days.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { deletedOpen = !deletedOpen }) {
                        Text(if (deletedOpen) "Hide" else "Show ${deleted.size}")
                    }
                }
            }
            if (deletedOpen) {
                items(deleted, key = { "deleted-${it.problem.id}" }) { record ->
                    DeletedProblemRow(record = record, state = state, actions = actions)
                }
            }
        }
    }

    // Filters live on a bottom sheet: chips wrap instead of clipping in nested
    // LazyRows, and dismissing lands straight back on the filtered results.
    if (filtersOpen) {
        ModalBottomSheet(
            onDismissRequest = { filtersOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    "Filter problems",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FilterSection(
                    title = "Status",
                    options = statusOptions,
                    selected = statusFilter,
                    onSelect = { statusFilter = it },
                )
                FilterSection(
                    title = "Grade",
                    options = listOf(ALL) + BoulderGrade.options(state.gradeSystem).map { it.label(state.gradeSystem) },
                    selected = gradeFilter,
                    onSelect = { gradeFilter = it },
                )
                if (angles.size > 1) {
                    FilterSection(
                        title = "Angle",
                        options = listOf(ALL) + angles.map { "$it°" },
                        selected = angleFilter,
                        onSelect = { angleFilter = it },
                    )
                }
                FilterSection(
                    title = "Feet rule",
                    options = listOf(ALL) + FeetRule.entries.map { it.label },
                    selected = feetFilter,
                    onSelect = { feetFilter = it },
                )
                if (setters.size > 1) {
                    FilterSection(
                        title = "Setter",
                        options = listOf(ALL) + setters,
                        selected = setterFilter,
                        onSelect = { setterFilter = it },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            statusFilter = ACTIVE
                            gradeFilter = ALL
                            angleFilter = ALL
                            feetFilter = ALL
                            setterFilter = ALL
                        },
                        enabled = activeFilterChips.isNotEmpty(),
                    ) { Text("Clear all") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { filtersOpen = false }) {
                        Text(
                            if (visibleProblems.size == 1) "Show 1 problem" else "Show ${visibleProblems.size} problems",
                        )
                    }
                }
            }
        }
    }
}

private fun Long.inDays(): Long = this / (24L * 60 * 60 * 1000)

/**
 * One recoverable delete. Restoring is the safe action and gets the button;
 * "Remove" is the only genuinely irreversible one here, so it confirms first.
 */
@Composable
private fun DeletedProblemRow(
    record: DeletedProblem,
    state: BoardUiState,
    actions: BoardActions,
) {
    var confirmForget by rememberSaveable { mutableStateOf(false) }
    val displayName = problemDisplayName(record.problem.name)

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "est. ${record.problem.grade.label(state.gradeSystem)} · " +
                        "${holdCountLabel(record.problem.assignments.size)} · deleted " +
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(record.deletedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { actions.onRestoreDeletedProblem(record.problem.id) }) {
                Text("Restore")
            }
            TextButton(onClick = { confirmForget = true }) { Text("Remove") }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Remove permanently?") },
            text = {
                Text(
                    "“$displayName” will stop being recoverable on this device. " +
                        "The deletion itself is unaffected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmForget = false
                        actions.onForgetDeletedProblem(record.problem.id)
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSection(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            options.forEach { option ->
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
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val displayName = problemDisplayName(problem.name)

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
                        displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusChip(state = problem.publicationState)
                }
                Text(
                    "${problem.setter} · ${holdCountLabel(problem.assignments.size)} · ${problem.feetRule.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(7.dp)) {
                Text(
                    "est. ${problem.grade.label(state.gradeSystem)} · ${problem.angleDegrees}°",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "Actions for $displayName")
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
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            menuOpen = false
                            confirmDelete = true
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete problem?") },
            text = {
                Text(
                    "“$displayName” leaves the library. You can put it back from " +
                        "Recently deleted for ${DeletedProblems.RETENTION_MS.inDays()} days.",
                )
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
