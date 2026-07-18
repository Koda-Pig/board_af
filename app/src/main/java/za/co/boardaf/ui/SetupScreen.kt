package za.co.boardaf.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import za.co.boardaf.BoardUiState
import za.co.boardaf.model.BoardGeometry
import za.co.boardaf.model.BoardZoneType
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.ui.theme.BoardDark
import za.co.boardaf.ui.theme.BoardLine
import za.co.boardaf.ui.theme.BoardMuted
import za.co.boardaf.ui.theme.Coral
import za.co.boardaf.ui.theme.Gold
import za.co.boardaf.ui.theme.Moss
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

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("BOARD SETUP", style = MaterialTheme.typography.labelSmall, color = BoardMuted, fontWeight = FontWeight.Bold)
            Text(board.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
                            Text("• ${issue.message}", style = MaterialTheme.typography.bodySmall, color = BoardMuted)
                        }
                        if (state.unreadableRecords.isNotEmpty()) {
                            Text(
                                "${state.unreadableRecords.size} unreadable record(s) are retained inside the app's local store.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BoardMuted,
                            )
                        }
                    }
                }
            }
        }

        if (board.setupConfirmedAt == null) {
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
                            color = BoardMuted,
                        )
                        Button(
                            onClick = actions.onConfirmBoardSetup,
                            colors = ButtonDefaults.buttonColors(containerColor = BoardDark, contentColor = Color.White),
                        ) {
                            Text("Looks right — confirm")
                        }
                    }
                }
            }
        }

        item {
            BoardSurface(
                board = board,
                assignments = emptyList(),
                mode = BoardDisplayMode.CONFIGURE,
                onHoldClick = actions.onToggleHoldCapability,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(BoardGeometry.IMAGE_ASPECT_RATIO),
            )
        }

        item {
            Row(
                modifier = Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CapabilityLegendDot(color = Sky, filled = true)
                Spacer(Modifier.width(6.dp))
                Text("Hands and feet", style = MaterialTheme.typography.labelSmall, color = BoardMuted)
                Spacer(Modifier.width(16.dp))
                CapabilityLegendDot(color = Gold, filled = false)
                Spacer(Modifier.width(6.dp))
                Text("Foot only", style = MaterialTheme.typography.labelSmall, color = BoardMuted)
                Spacer(Modifier.width(16.dp))
                Text("White ring = corrected", style = MaterialTheme.typography.labelSmall, color = BoardMuted)
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BoardLine, RoundedCornerShape(14.dp))
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
                            color = BoardMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = board.hasKickboard,
                        onCheckedChange = actions.onSetKickboardEnabled,
                    )
                }
                if (board.hasKickboard) {
                    Text(
                        "Boundary · holds below default to foot-only. Tap a hold above to correct exceptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BoardMuted,
                    )
                    Slider(
                        value = board.kickboardTopY,
                        onValueChange = actions.onSetKickboardBoundary,
                        valueRange = 0.4f..0.98f,
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BoardLine, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                SetupValue("Wall angle", "${board.angleDegrees}°")
                HorizontalDivider(color = BoardLine)
                SetupValue("Climbing height", "${board.heightMeters} m")
                HorizontalDivider(color = BoardLine)
                SetupValue("Mapped holds", "${board.holds.size}")
                if (board.setupConfirmedAt != null) {
                    HorizontalDivider(color = BoardLine)
                    SetupValue("Zones", "Confirmed ✓")
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BoardLine, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Boulder grade system", fontWeight = FontWeight.Bold)
                Text(
                    "Choose how problem difficulties are shown across the app. Grades are setter estimates.",
                    color = BoardMuted,
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

        item {
            CloudSyncCard(cloud = state.cloud, actions = actions)
        }

        item {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                Row(modifier = Modifier.padding(15.dp), verticalAlignment = Alignment.Top) {
                    Column {
                        Text("Image-space mapping", fontWeight = FontWeight.Bold)
                        Text(
                            "Hold centers are stored as normalized photo coordinates, and each hold stores " +
                                "its zone and capability. Validation reads the stored classification, not the line.",
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
private fun CapabilityLegendDot(color: Color, filled: Boolean) {
    Canvas(modifier = Modifier.size(14.dp)) {
        if (filled) {
            drawCircle(color = color)
        } else {
            drawCircle(
                color = color,
                style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f))),
            )
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
