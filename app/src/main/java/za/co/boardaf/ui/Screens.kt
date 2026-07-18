package za.co.boardaf.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import za.co.boardaf.BoardUiState
import za.co.boardaf.R
import za.co.boardaf.model.Accent
import za.co.boardaf.model.Attempt
import za.co.boardaf.model.BoardDefaults
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.DraftProblem
import za.co.boardaf.model.HoldRole
import za.co.boardaf.model.Problem
import za.co.boardaf.ui.theme.BoardDark
import za.co.boardaf.ui.theme.BoardInk
import za.co.boardaf.ui.theme.BoardLine
import za.co.boardaf.ui.theme.BoardMuted
import za.co.boardaf.ui.theme.BoardPaper
import za.co.boardaf.ui.theme.Coral
import za.co.boardaf.ui.theme.Gold
import za.co.boardaf.ui.theme.Moss
import za.co.boardaf.ui.theme.Sky
import za.co.boardaf.ui.theme.BoardAfTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun BoardScreen(
    state: BoardUiState,
    contentPadding: PaddingValues,
    onSelectRole: (HoldRole) -> Unit,
    onHoldClick: (String) -> Unit,
    onDraftNameChange: (String) -> Unit,
    onDraftGradeChange: (String) -> Unit,
    onDraftAccentChange: (Accent) -> Unit,
    onDraftNoteChange: (String) -> Unit,
    onClearDraft: () -> Unit,
    onSaveDraft: () -> Unit,
    onCancelDraft: () -> Unit,
    onLogAttempt: (String, Boolean, Int) -> Unit,
) {
    val problem = state.selectedProblem
    var climbing by rememberSaveable { mutableStateOf(false) }
    val activeHolds = if (state.isSetting) state.draft.holds else problem?.holds.orEmpty()

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
                        BoardHeader(problem = problem, isSetting = state.isSetting)
                        BoardSurface(
                            activeHolds = activeHolds,
                            isSetting = state.isSetting,
                            onHoldClick = onHoldClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 620.dp)
                                .aspectRatio(BoardGeometry.IMAGE_ASPECT_RATIO),
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .width(380.dp)
                        .fillMaxHeight(),
                ) {
                    item {
                        if (state.isSetting) {
                            SetterControls(
                                draft = state.draft,
                                selectedRole = state.selectedRole,
                                onSelectRole = onSelectRole,
                                onNameChange = onDraftNameChange,
                                onGradeChange = onDraftGradeChange,
                                onAccentChange = onDraftAccentChange,
                                onNoteChange = onDraftNoteChange,
                                onClear = onClearDraft,
                                onSave = onSaveDraft,
                                onCancel = onCancelDraft,
                            )
                        } else if (problem != null) {
                            ProblemDetails(problem = problem, onStart = { climbing = true })
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { BoardHeader(problem = problem, isSetting = state.isSetting) }
                item {
                    BoardSurface(
                        activeHolds = activeHolds,
                        isSetting = state.isSetting,
                        onHoldClick = onHoldClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(BoardGeometry.IMAGE_ASPECT_RATIO),
                    )
                }
                item {
                    if (state.isSetting) {
                        SetterControls(
                            draft = state.draft,
                            selectedRole = state.selectedRole,
                            onSelectRole = onSelectRole,
                            onNameChange = onDraftNameChange,
                            onGradeChange = onDraftGradeChange,
                            onAccentChange = onDraftAccentChange,
                            onNoteChange = onDraftNoteChange,
                            onClear = onClearDraft,
                            onSave = onSaveDraft,
                            onCancel = onCancelDraft,
                        )
                    } else if (problem != null) {
                        ProblemDetails(problem = problem, onStart = { climbing = true })
                    }
                }
            }
        }
    }

    if (climbing && problem != null) {
        ClimbSheet(
            problem = problem,
            onDismiss = { climbing = false },
            onComplete = { sent, seconds ->
                onLogAttempt(problem.id, sent, seconds)
                climbing = false
            },
        )
    }
}

@Composable
private fun BoardHeader(problem: Problem?, isSetting: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSetting) "ROUTE SETTER" else "NOW VIEWING",
                color = BoardMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (isSetting) "Choose your holds" else problem?.name.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (!isSetting && problem != null) {
            Surface(color = BoardDark, shape = RoundedCornerShape(8.dp)) {
                Text(
                    text = problem.grade,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = BoardPaper,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun ProblemDetails(problem: Problem, onStart: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                        "${problem.grade} · ${problem.holds.size} holds · ${problem.sends} sends",
                        color = BoardMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (problem.note.isNotBlank()) {
                Text(problem.note, color = BoardMuted, style = MaterialTheme.typography.bodyMedium)
            }
            HorizontalDivider(color = BoardLine)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                HoldRole.entries.forEach { role ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(role.markerColor(), CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(role.label, style = MaterialTheme.typography.labelSmall, color = BoardMuted)
                    }
                }
            }
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = BoardDark),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Start climb", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SetterControls(
    draft: DraftProblem,
    selectedRole: HoldRole,
    onSelectRole: (HoldRole) -> Unit,
    onNameChange: (String) -> Unit,
    onGradeChange: (String) -> Unit,
    onAccentChange: (Accent) -> Unit,
    onNoteChange: (String) -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NEW PROBLEM", style = MaterialTheme.typography.labelSmall, color = BoardMuted, fontWeight = FontWeight.Bold)
                    Text("Set the line", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel")
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Problem name") },
                singleLine = true,
            )
            Text("Grade", style = MaterialTheme.typography.labelMedium, color = BoardMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(BoardDefaults.grades) { grade ->
                    FilterChip(
                        selected = draft.grade == grade,
                        onClick = { onGradeChange(grade) },
                        label = { Text(grade) },
                    )
                }
            }
            Text("Marker", style = MaterialTheme.typography.labelMedium, color = BoardMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(Accent.entries) { accent ->
                    FilterChip(
                        selected = draft.accent == accent,
                        onClick = { onAccentChange(accent) },
                        label = { Text(accent.label) },
                        leadingIcon = {
                            Box(Modifier.size(8.dp).background(accent.color(), CircleShape))
                        },
                    )
                }
            }
            Text("Hold type · ${draft.holds.size} selected", style = MaterialTheme.typography.labelMedium, color = BoardMuted)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(HoldRole.entries) { role ->
                    FilterChip(
                        selected = selectedRole == role,
                        onClick = { onSelectRole(role) },
                        label = { Text(role.label) },
                        leadingIcon = {
                            Box(Modifier.size(8.dp).background(role.markerColor(), CircleShape))
                        },
                    )
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Sky, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Choose a hold type, then tap holds on the photo. Tap an assigned hold again to remove it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BoardMuted,
                    )
                }
            }
            OutlinedTextField(
                value = draft.note,
                onValueChange = onNoteChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Setter notes") },
                minLines = 2,
                maxLines = 4,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Clear")
                }
                Button(
                    onClick = onSave,
                    enabled = draft.canSave,
                    modifier = Modifier.weight(1.6f),
                    colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = BoardDark),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Save problem", maxLines = 1)
                }
            }
            if (!draft.canSave) {
                Text(
                    "Add a name, at least one start, and a finish.",
                    style = MaterialTheme.typography.labelSmall,
                    color = BoardMuted,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClimbSheet(
    problem: Problem,
    onDismiss: () -> Unit,
    onComplete: (Boolean, Int) -> Unit,
) {
    var elapsed by remember { mutableIntStateOf(0) }
    LaunchedEffect(problem.id) {
        while (true) {
            delay(1_000)
            elapsed += 1
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = BoardDark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("CLIMBING NOW", style = MaterialTheme.typography.labelMedium, color = Coral, fontWeight = FontWeight.Bold)
            Text(problem.name, style = MaterialTheme.typography.headlineSmall, color = BoardPaper, fontWeight = FontWeight.Bold)
            Text(
                text = "%02d:%02d".format(elapsed / 60, elapsed % 60),
                style = MaterialTheme.typography.displayMedium,
                color = BoardPaper,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { onComplete(false, elapsed) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Attempt")
                }
                Button(
                    onClick = { onComplete(true, elapsed) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Moss, contentColor = BoardDark),
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null)
                    Spacer(Modifier.width(5.dp))
                    Text("Log send")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun ProblemsScreen(
    state: BoardUiState,
    contentPadding: PaddingValues,
    onSelectProblem: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var grade by rememberSaveable { mutableStateOf("All") }
    val visibleProblems = state.problems.filter { problem ->
        (grade == "All" || problem.grade == grade) &&
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
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Find a problem") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
            )
            LazyRow(
                modifier = Modifier.padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(listOf("All") + BoardDefaults.grades) { item ->
                    FilterChip(
                        selected = grade == item,
                        onClick = { grade = item },
                        label = { Text(item) },
                    )
                }
            }
        }
        items(visibleProblems, key = { it.id }) { problem ->
            Card(
                onClick = { onSelectProblem(problem.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = if (problem.id == state.selectedProblemId) CardDefaults.outlinedCardBorder() else null,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(11.dp).background(problem.accent.color(), CircleShape))
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(problem.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${problem.setter} · ${problem.holds.size} holds · ${problem.sends} sends",
                            style = MaterialTheme.typography.bodySmall,
                            color = BoardMuted,
                        )
                    }
                    Surface(color = BoardDark, shape = RoundedCornerShape(7.dp)) {
                        Text(
                            problem.grade,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            color = BoardPaper,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
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
fun SessionsScreen(state: BoardUiState, contentPadding: PaddingValues) {
    val sentCount = state.attempts.count { it.sent }
    val sendRate = if (state.attempts.isEmpty()) 0 else sentCount * 100 / state.attempts.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("TRAINING LOG", style = MaterialTheme.typography.labelSmall, color = BoardMuted, fontWeight = FontWeight.Bold)
            Text("Your sessions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Sends", sentCount.toString(), Icons.Rounded.EmojiEvents, Modifier.weight(1f))
                StatCard("Attempts", state.attempts.size.toString(), Icons.Rounded.Timer, Modifier.weight(1f))
                StatCard("Send rate", "$sendRate%", Icons.Rounded.Check, Modifier.weight(1f))
            }
            Text(
                "RECENT ATTEMPTS",
                modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = BoardMuted,
                fontWeight = FontWeight.Bold,
            )
        }
        items(state.attempts, key = { it.id }) { attempt ->
            AttemptRow(attempt = attempt, problem = state.problems.firstOrNull { it.id == attempt.problemId })
        }
        if (state.attempts.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = Coral)
                        Spacer(Modifier.height(8.dp))
                        Text("No attempts yet", fontWeight = FontWeight.Bold)
                        Text("Start a climb from the board.", color = BoardMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(icon, contentDescription = null, tint = Coral, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = BoardMuted, maxLines = 1)
        }
    }
}

@Composable
private fun AttemptRow(attempt: Attempt, problem: Problem?) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(if (attempt.sent) Moss.copy(alpha = 0.28f) else Coral.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (attempt.sent) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = if (attempt.sent) "Sent" else "Attempted",
                    tint = if (attempt.sent) Color(0xFF4F8143) else Coral,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(problem?.name ?: "Unknown problem", fontWeight = FontWeight.Bold)
                Text(
                    "${attempt.durationSeconds / 60}m ${attempt.durationSeconds % 60}s · ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(attempt.timestamp))}",
                    color = BoardMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(problem?.grade.orEmpty(), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SetupScreen(contentPadding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("BOARD SETUP", style = MaterialTheme.typography.labelSmall, color = BoardMuted, fontWeight = FontWeight.Bold)
            Text("Home board", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Image(
                painter = painterResource(R.drawable.home_board),
                contentDescription = "Home board",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BoardLine, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                SetupValue("Wall angle", "20°")
                HorizontalDivider(color = BoardLine)
                SetupValue("Climbing height", "4.8 m")
                HorizontalDivider(color = BoardLine)
                SetupValue("Mapped holds", "43")
            }
        }
        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Sky)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Image-space mapping", fontWeight = FontWeight.Bold)
                        Text(
                            "All hold centers are stored as normalized photo coordinates, so they stay aligned on every Android screen size.",
                            color = BoardMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = BoardMuted, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}

fun Accent.color(): Color = when (this) {
    Accent.SKY -> Sky
    Accent.CORAL -> Coral
    Accent.OCHRE -> Gold
    Accent.MOSS -> Moss
}

@Preview(name = "Phone", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
private fun BoardScreenPhonePreview() {
    BoardAfTheme {
        BoardScreen(
            state = BoardUiState(
                problems = BoardDefaults.problems,
                selectedProblemId = BoardDefaults.problems.first().id,
            ),
            contentPadding = PaddingValues(),
            onSelectRole = {},
            onHoldClick = {},
            onDraftNameChange = {},
            onDraftGradeChange = {},
            onDraftAccentChange = {},
            onDraftNoteChange = {},
            onClearDraft = {},
            onSaveDraft = {},
            onCancelDraft = {},
            onLogAttempt = { _, _, _ -> },
        )
    }
}

@Preview(name = "Tablet", widthDp = 1000, heightDp = 800, showBackground = true)
@Composable
private fun BoardScreenTabletPreview() {
    BoardScreenPhonePreview()
}
