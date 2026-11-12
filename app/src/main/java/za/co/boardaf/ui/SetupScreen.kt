package za.co.boardaf.ui

import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import za.co.boardaf.BoardUiState
import za.co.boardaf.model.BoardPhoto
import za.co.boardaf.model.BoardSetup
import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.ConfiguredBoard
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.ProblemValidator
import za.co.boardaf.model.PublicationState
import za.co.boardaf.ui.theme.Coral
import za.co.boardaf.ui.theme.Gold
import za.co.boardaf.ui.theme.Sky

@Composable
fun SetupScreen(
    state: BoardUiState,
    actions: BoardActions,
    contentPadding: PaddingValues,
) {
    val board = state.board
    val mainCount = board.holds.count { it.zone == BoardZoneType.MAIN }
    val kickerCount = board.holds.count { it.zone == BoardZoneType.KICKBOARD }
    var setupHintDismissed by rememberSaveable { mutableStateOf(false) }
    // Re-mapping is a distinct mode on the same board preview: in it a tap selects
    // a hold instead of correcting its capability, so one gesture never means two
    // things while markers are being lined up.
    var remapping by rememberSaveable { mutableStateOf(false) }
    var selectedHoldId by rememberSaveable { mutableStateOf<String?>(null) }
    var boundaryDraft by remember(board.kickboardTopY) {
        mutableFloatStateOf(board.kickboardTopY)
    }
    // A boundary or kickboard change re-validates every problem and can silently
    // demote published ones to Needs review — that deserves a warning first.
    var pendingChange by remember { mutableStateOf<PendingSetupChange?>(null) }

    fun demotedBy(proposed: BoardSetup): List<String> {
        val proposedBoard = ConfiguredBoard.from(proposed)
        return state.problems
            .filter { it.publicationState == PublicationState.PUBLISHED }
            .filter { ProblemValidator.hasErrors(ProblemValidator.validate(it, proposedBoard)) }
            .map { problemDisplayName(it.name) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("BOARD SETUP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Text(board.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // The one everyday preference, so it comes before the calibration tools.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Grade system", fontWeight = FontWeight.Bold)
                Text(
                    "How difficulty is shown across the app.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradeSystem.entries.forEach { system ->
                        FilterChip(
                            selected = state.gradeSystem == system,
                            onClick = { actions.onSetGradeSystem(system) },
                            label = { Text(system.label) },
                        )
                    }
                }
            }
        }


        if (state.storageIssues.isNotEmpty()) {
            item {
                Surface(color = Coral.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Storage notices", fontWeight = FontWeight.Bold)
                        state.storageIssues.forEach { issue ->
                            Text("• ${issue.message}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.unreadableRecords.isNotEmpty()) {
                            Text(
                                "${state.unreadableRecords.size} unreadable record(s) are retained inside the app's local store.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (board.setupConfirmedAt == null && !setupHintDismissed) {
            item {
                Surface(color = Gold.copy(alpha = 0.20f), shape = RoundedCornerShape(14.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Confirm your board zones", fontWeight = FontWeight.Bold)
                        Text(
                            "Holds below the boundary line are classified as kickboard, foot-only holds " +
                                "(currently $kickerCount of them). Adjust the boundary or tap holds below to " +
                                "correct exceptions, then confirm.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = actions.onConfirmBoardSetup,
                            ) {
                                Text("Looks right — confirm")
                            }
                            // Deliberately NOT onConfirmBoardSetup: confirming is a
                            // claim about the board that the user hasn't made yet.
                            // This hides the card for now and it returns next launch.
                            TextButton(onClick = { setupHintDismissed = true }) {
                                Text("Not now")
                            }
                        }
                    }
                }
            }
        }

        item {
            BoardSurface(
                board = board,
                assignments = emptyList(),
                mode = if (remapping) BoardDisplayMode.REMAP else BoardDisplayMode.CONFIGURE,
                onHoldClick = { holdId ->
                    if (remapping) {
                        selectedHoldId = holdId.takeIf { it != selectedHoldId }
                    } else {
                        // One tap reclassifies a hold, which can break published
                        // problems — the same check every other setup edit gets.
                        val demoted = demotedBy(state.setup.withCapabilityToggled(holdId))
                        if (demoted.isEmpty()) {
                            actions.onToggleHoldCapability(holdId)
                        } else {
                            pendingChange = PendingSetupChange.CapabilityToggle(holdId, demoted)
                        }
                    }
                },
                photoPath = state.boardPhotoPath,
                selectedHoldId = selectedHoldId.takeIf { remapping },
                onHoldDragged = actions.onMoveHold,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(board.aspectRatio),
            )
        }

        item {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapabilityLegendDot(color = Sky, filled = true)
                Spacer(Modifier.width(6.dp))
                Text("Hands and feet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                CapabilityLegendDot(color = Gold, filled = false)
                Spacer(Modifier.width(6.dp))
                Text("Foot only", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(16.dp))
                Text("White ring = corrected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // Re-map controls sit right under the board, not down in the Hold map card:
        // they're used while dragging markers, so they have to be in view.
        if (remapping) {
            item {
                RemapControls(
                    selectedHoldId = selectedHoldId,
                    suggesting = state.isSuggestingHolds,
                    onSuggestHolds = actions.onSuggestHolds,
                    onAddHold = { selectedHoldId = actions.onAddHold(0.5f, 0.5f) },
                    onRemoveSelected = { holdId ->
                        val demoted = demotedBy(state.setup.withHoldRemoved(holdId))
                        if (demoted.isEmpty()) {
                            actions.onRemoveHold(holdId)
                            selectedHoldId = null
                        } else {
                            pendingChange = PendingSetupChange.HoldRemoval(holdId, demoted)
                        }
                    },
                    onDone = {
                        remapping = false
                        selectedHoldId = null
                    },
                )
            }
        }

        // Directly below the board too, so the boundary line it moves stays in view.
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kickboard", fontWeight = FontWeight.Bold)
                        Text(
                            if (board.hasKickboard) {
                                "Main board: $mainCount holds · Kickboard: $kickerCount holds"
                            } else {
                                "This board has no kickboard."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = board.hasKickboard,
                        onCheckedChange = { enabled ->
                            val demoted = demotedBy(state.setup.withKickboardEnabled(enabled))
                            if (demoted.isEmpty()) {
                                actions.onSetKickboardEnabled(enabled)
                            } else {
                                pendingChange = PendingSetupChange.Kickboard(enabled, demoted)
                            }
                        },
                    )
                }
                if (board.hasKickboard) {
                    Text(
                        "Slide to move the boundary line on the board. Holds below it " +
                            "default to foot-only; tap a hold to correct an exception.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = boundaryDraft,
                        onValueChange = { boundaryDraft = it },
                        onValueChangeFinished = {
                            val demoted = demotedBy(state.setup.withBoundary(boundaryDraft))
                            if (demoted.isEmpty()) {
                                actions.onSetKickboardBoundary(boundaryDraft)
                            } else {
                                pendingChange = PendingSetupChange.Boundary(boundaryDraft, demoted)
                            }
                        },
                        valueRange = 0.4f..0.98f,
                    )
                }
            }
        }


        item {
            BoardPhotoCard(photo = board.photo, actions = actions)
        }

        item {
            HoldMapCard(
                board = board,
                setup = state.setup,
                remapping = remapping,
                onToggleRemapping = {
                    remapping = !remapping
                    selectedHoldId = null
                },
                onUseBundled = {
                    val demoted = demotedBy(state.setup.withBundledHolds())
                    if (demoted.isEmpty()) {
                        actions.onUseBundledHoldLayout()
                        selectedHoldId = null
                    } else {
                        pendingChange = PendingSetupChange.BundledLayout(demoted)
                    }
                },
            )
        }

        item {
            // Facts, not settings: plain text, so nothing here looks tappable.
            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("About this board", fontWeight = FontWeight.Bold)
                SetupFact("Wall angle is set per problem (0–90°).")
                SetupFact("Climbing height ${board.heightMeters} m · ${board.holds.size} mapped holds.")
                if (board.setupConfirmedAt != null) SetupFact("Zones confirmed.")
            }
        }

        item {
            CloudSyncCard(cloud = state.cloud, actions = actions)
        }

    }

    state.holdSuggestion?.let { suggestion ->
        AlertDialog(
            onDismissRequest = actions.onDismissHoldSuggestion,
            title = { Text("Use these suggestions?") },
            text = {
                Text(
                    buildString {
                        append(
                            if (suggestion.snapped == 1) {
                                "1 marker would move onto a hold found in the photo"
                            } else {
                                "${suggestion.snapped} markers would move onto holds found in the photo"
                            },
                        )
                        if (suggestion.added > 0) {
                            append(
                                if (suggestion.added == 1) {
                                    ", and 1 hold the map doesn't have yet would be added"
                                } else {
                                    ", and ${suggestion.added} holds the map doesn't have yet would be added"
                                },
                            )
                        }
                        append(". ")
                        if (suggestion.unmatched > 0) {
                            append(
                                if (suggestion.unmatched == 1) {
                                    "1 marker had nothing to snap to and stays where it is. "
                                } else {
                                    "${suggestion.unmatched} markers had nothing to snap to and stay where they are. "
                                },
                            )
                        }
                        append("Check the board above afterwards and drag anything that landed wrong.")
                        if (suggestion.demoted.isNotEmpty()) {
                            val listed = suggestion.demoted.take(3).joinToString(", ") { "“$it”" }
                            val extra = (suggestion.demoted.size - 3).takeIf { it > 0 }
                                ?.let { " and $it more" }.orEmpty()
                            append("\n\n$listed$extra will move to Needs review because this breaks their holds.")
                        }
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = actions.onAcceptHoldSuggestion) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissHoldSuggestion) { Text("Cancel") }
            },
        )
    }

    pendingChange?.let { change ->
        val count = change.demoted.size
        val listed = change.demoted.take(3).joinToString(", ") { "“$it”" }
        val extra = (count - 3).takeIf { it > 0 }?.let { " and $it more" }.orEmpty()
        AlertDialog(
            onDismissRequest = {
                boundaryDraft = board.kickboardTopY
                pendingChange = null
            },
            title = { Text(if (count == 1) "Unpublish this problem?" else "Unpublish $count problems?") },
            text = {
                Text(
                    if (count == 1) {
                        "$listed will move to Needs review because this change breaks its " +
                            "holds. It needs repair and a fresh forerun to publish again."
                    } else {
                        "$listed$extra will move to Needs review because this change breaks " +
                            "their holds. Each needs repair and a fresh forerun to publish again."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (change) {
                            is PendingSetupChange.Boundary -> actions.onSetKickboardBoundary(change.y)
                            is PendingSetupChange.Kickboard -> actions.onSetKickboardEnabled(change.enabled)
                            is PendingSetupChange.CapabilityToggle -> actions.onToggleHoldCapability(change.holdId)
                            is PendingSetupChange.HoldRemoval -> {
                                actions.onRemoveHold(change.holdId)
                                selectedHoldId = null
                            }
                            is PendingSetupChange.BundledLayout -> {
                                actions.onUseBundledHoldLayout()
                                selectedHoldId = null
                            }
                        }
                        pendingChange = null
                    },
                ) { Text("Apply anyway") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        boundaryDraft = board.kickboardTopY
                        pendingChange = null
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

/**
 * Re-shoot the wall from inside the app. The capture is written by the user's own
 * camera app into a FileProvider URI, so no CAMERA permission is involved.
 *
 * Hold positions are normalized to the frame and are *not* re-detected, so a photo
 * taken from a different spot leaves every hold marker off its hold. The board
 * preview directly above this card is the check for that, and the warning says so.
 */
@Composable
private fun BoardPhotoCard(photo: BoardPhoto?, actions: BoardActions) {
    var error by remember { mutableStateOf<String?>(null) }
    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved -> actions.onBoardPhotoCaptured(saved) }
    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        // A null result is a cancelled picker, not a broken import.
        if (uri != null) actions.onBoardPhotoImported(uri.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Board photo", fontWeight = FontWeight.Bold)
        Text(
            text = if (photo == null) {
                "Showing the photo bundled with the app. Take or upload your own " +
                    "when the wall or the lighting has changed."
            } else {
                "Your photo · ${photo.widthPx} × ${photo.heightPx} · taken " +
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(photo.capturedAt))
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Surface(color = Gold.copy(alpha = 0.20f), shape = RoundedCornerShape(10.dp)) {
            Text(
                "Hold positions are not re-detected. Shoot the whole wall square-on, " +
                    "then check the markers above still sit on their holds — Hold map " +
                    "below is where you drag back any that have drifted.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    error = null
                    val target = actions.onBeginBoardPhotoCapture()
                    if (target == null) {
                        error = "Couldn't prepare a place to save the photo."
                        return@Button
                    }
                    runCatching { captureLauncher.launch(target.toUri()) }.onFailure {
                        // No camera app: release the destination we just claimed.
                        actions.onBoardPhotoCaptured(false)
                        error = "No camera app is available on this device."
                    }
                },
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (photo == null) "Take a board photo" else "Retake photo")
            }
            OutlinedButton(
                onClick = {
                    error = null
                    runCatching {
                        pickLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    }.onFailure {
                        error = "No photo picker is available on this device."
                    }
                },
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (photo == null) "Upload photo" else "Replace from gallery")
            }
            if (photo != null) {
                TextButton(onClick = actions.onClearBoardPhoto) { Text("Use bundled") }
            }
        }
    }
}

/**
 * Re-map holds against the photo on screen. Hold centers are stored as positions
 * in the frame and nothing re-detects them, so this is how markers get back onto
 * their holds after the wall is re-shot or the holds themselves are moved.
 *
 * A drag commits as soon as the finger lifts, with no confirmation: dragging is
 * continuous and a dialog per hold would make the job unusable, and a demotion to
 * Needs review is reversible anyway. Removing a hold does ask first, because the
 * problems that used it lose a hold outright.
 */
@Composable
private fun HoldMapCard(
    board: ConfiguredBoard,
    setup: BoardSetup,
    remapping: Boolean,
    onToggleRemapping: () -> Unit,
    onUseBundled: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Hold map", fontWeight = FontWeight.Bold)
        Text(
            text = "${board.holds.size} holds · " + if (setup.hasCustomLayout) {
                "your own map"
            } else {
                "the map bundled with the app"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (remapping) {
            Text(
                "Re-mapping now — the controls are just under the board.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                "Markers are stored as positions in the photo, not found in it. Re-map " +
                    "after re-shooting the wall, or whenever you move holds around.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleRemapping) { Text("Re-map holds") }
                if (setup.hasCustomLayout) {
                    TextButton(onClick = onUseBundled) { Text("Use bundled map") }
                }
            }
        }
    }
}

/**
 * Re-map mode's working controls. A drag commits as soon as the finger lifts,
 * with no confirmation: dragging is continuous and a dialog per hold would make
 * the job unusable, and a demotion to Needs review is reversible anyway. Removing
 * a hold does ask first, because the problems that used it lose a hold outright.
 */
@Composable
private fun RemapControls(
    selectedHoldId: String?,
    suggesting: Boolean,
    onSuggestHolds: () -> Unit,
    onAddHold: () -> Unit,
    onRemoveSelected: (String) -> Unit,
    onDone: () -> Unit,
) {
    Surface(color = Gold.copy(alpha = 0.20f), shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Re-mapping holds", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Button(onClick = onDone) { Text("Done") }
            }
            Text(
                "Drag each marker onto the middle of its hold. Pinch to zoom in, " +
                    "double-tap to zoom back out. Tap a marker to select it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onSuggestHolds, enabled = !suggesting) {
                    if (suggesting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Searching…")
                    } else {
                        Text("Suggest from photo")
                    }
                }
                OutlinedButton(onClick = onAddHold) { Text("Add hold") }
            }
            selectedHoldId?.let { holdId ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Selected $holdId",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(
                        onClick = { onRemoveSelected(holdId) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Remove hold") }
                }
            }
            Text(
                "Suggest from photo snaps markers onto likely holds; you confirm before " +
                    "anything moves. An added hold lands mid-board, already selected.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A Setup edit awaiting confirmation because it would demote published problems. */
private sealed interface PendingSetupChange {
    val demoted: List<String>

    data class Boundary(val y: Float, override val demoted: List<String>) : PendingSetupChange
    data class Kickboard(val enabled: Boolean, override val demoted: List<String>) : PendingSetupChange
    data class HoldRemoval(val holdId: String, override val demoted: List<String>) : PendingSetupChange
    data class CapabilityToggle(val holdId: String, override val demoted: List<String>) : PendingSetupChange
    data class BundledLayout(override val demoted: List<String>) : PendingSetupChange
}

@Composable
private fun CapabilityLegendDot(color: Color, filled: Boolean) {
    Canvas(modifier = Modifier.size(14.dp)) {
        // Match board overlays: outline rings, not opaque fills.
        drawCircle(
            color = color,
            style = if (filled) {
                Stroke(width = 2.dp.toPx())
            } else {
                Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)))
            },
        )
    }
}

@Composable
private fun SetupFact(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
