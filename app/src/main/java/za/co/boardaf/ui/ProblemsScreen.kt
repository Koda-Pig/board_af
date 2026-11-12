package za.co.boardaf.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
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

private val ACTIVE = ProblemLibraryQuery.STATUS_ACTIVE
private val ALL = ProblemLibraryQuery.ANY

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(
    state: BoardUiState,
    actions: BoardActions,
    contentPadding: PaddingValues,
    onOpenProblem: (String) -> Unit,
    onEditProblem: (String) -> Unit,
) {
    // The listing is shared state, not screen state: the board swipes through the
    // very same filtered, sorted list, so both read it from BoardUiState.
    val library = state.library
    val query = library.text
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var sortOpen by rememberSaveable { mutableStateOf(false) }
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

    val visibleProblems = state.visibleProblems
    val filtering = library.isFiltering

    val activeFilterChips = buildList<Pair<String, () -> Unit>> {
        fun chip(label: String, cleared: ProblemLibraryQuery) {
            add(label to { actions.onLibraryQueryChange(cleared) })
        }
        if (library.status != ACTIVE) chip("Status: ${library.status}", library.copy(status = ACTIVE))
        if (library.grade != ALL) chip("Grade: ${library.grade}", library.copy(grade = ALL))
        if (library.angle != ALL) chip("Angle: ${library.angle}", library.copy(angle = ALL))
        if (library.feetRule != ALL) chip("Feet: ${library.feetRule}", library.copy(feetRule = ALL))
        if (library.setter != ALL) chip("Setter: ${library.setter}", library.copy(setter = ALL))
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
                onValueChange = { actions.onLibraryQueryChange(library.copy(text = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find a problem") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { actions.onLibraryQueryChange(library.copy(text = "")) }) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { filtersOpen = true }) {
                    Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Filters")
                }
                Box {
                    // The button doubles as the readout, so the current ordering is
                    // visible without opening anything.
                    TextButton(onClick = { sortOpen = true }) {
                        Icon(Icons.Rounded.SwapVert, contentDescription = "Sort", modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(library.sort.label)
                    }
                    DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                        ProblemSort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    actions.onLibraryQueryChange(library.copy(sort = option))
                                    sortOpen = false
                                },
                                trailingIcon = {
                                    if (option == library.sort) {
                                        Icon(Icons.Rounded.Check, contentDescription = "Selected")
                                    }
                                },
                            )
                        }
                    }
                }
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
                            // Says what a tap does: these chips remove their filter.
                            trailingIcon = {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove filter",
                                    modifier = Modifier.size(16.dp),
                                )
                            },
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
                val searching = query.isNotBlank()
                val filtered = activeFilterChips.isNotEmpty()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = when {
                            searching && !filtered -> "No problems match “$query”."
                            searching -> "No problems match “$query” with these filters."
                            filtered -> "No problems match these filters."
                            state.problems.isEmpty() -> "No problems yet. Tap New to set one."
                            else -> "Nothing active. Archived problems are under Filters → Status."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (searching || filtered) {
                        TextButton(
                            onClick = { actions.onLibraryQueryChange(library.cleared().copy(text = "")) },
                        ) { Text(if (searching && !filtered) "Clear search" else "Clear search and filters") }
                    }
                }
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
                    selected = library.status,
                    onSelect = { actions.onLibraryQueryChange(library.copy(status = it)) },
                )
                FilterSection(
                    title = "Grade",
                    options = listOf(ALL) + BoulderGrade.options(state.gradeSystem).map { it.label(state.gradeSystem) },
                    selected = library.grade,
                    onSelect = { actions.onLibraryQueryChange(library.copy(grade = it)) },
                )
                if (angles.size > 1) {
                    FilterSection(
                        title = "Angle",
                        options = listOf(ALL) + angles.map { ProblemLibraryQuery.angleLabel(it) },
                        selected = library.angle,
                        onSelect = { actions.onLibraryQueryChange(library.copy(angle = it)) },
                    )
                }
                FilterSection(
                    title = "Feet rule",
                    options = listOf(ALL) + FeetRule.entries.map { it.label },
                    selected = library.feetRule,
                    onSelect = { actions.onLibraryQueryChange(library.copy(feetRule = it)) },
                )
                if (setters.size > 1) {
                    FilterSection(
                        title = "Setter",
                        options = listOf(ALL) + setters,
                        selected = library.setter,
                        onSelect = { actions.onLibraryQueryChange(library.copy(setter = it)) },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { actions.onLibraryQueryChange(library.cleared()) },
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
                    "${record.problem.grade.label(state.gradeSystem)} · " +
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
    val displayName = problemDisplayName(problem.name)
    val onBoard = problem.id == state.selectedProblemId

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = if (onBoard) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = if (onBoard) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, top = 13.dp, bottom = 13.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(11.dp).background(problem.accent.color(), CircleShape))
            Spacer(Modifier.width(11.dp))
            // The name gets its own line and may wrap: with the status chip beside
            // it, large font sizes squeezed it down to its first letters.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${problem.setter} · ${holdCountLabel(problem.assignments.size)} · ${problem.feetRule.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Published is the normal case; only the exceptions get a chip.
                if (problem.publicationState != PublicationState.PUBLISHED || onBoard) {
                    Row(
                        modifier = Modifier.padding(top = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (problem.publicationState != PublicationState.PUBLISHED) {
                            StatusChip(state = problem.publicationState)
                        }
                        if (onBoard) {
                            Text(
                                "On the board",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // No "est." here: the header already says grades are setter estimates.
            Surface(color = MaterialTheme.colorScheme.inverseSurface, shape = RoundedCornerShape(7.dp)) {
                Text(
                    "${problem.grade.label(state.gradeSystem)} · ${problem.angleDegrees}°",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
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
                    // No confirm dialog, the same as Archive: the delete is
                    // recoverable, and the snackbar that follows offers Undo.
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            actions.onDeleteProblem(problem.id)
                        },
                    )
                }
            }
        }
    }
}
