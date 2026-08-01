package za.co.boardaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import za.co.boardaf.BoardEvent
import za.co.boardaf.BoardViewModel
import za.co.boardaf.model.Accent
import za.co.boardaf.model.BoulderGrade
import za.co.boardaf.model.FeetRule
import za.co.boardaf.model.GradeSystem
import za.co.boardaf.model.ProblemHoldRole
import za.co.boardaf.setter.GuidedStep
import za.co.boardaf.setter.SetterMode
import za.co.boardaf.ui.theme.BoardDark
import za.co.boardaf.ui.theme.BoardPaper
import za.co.boardaf.ui.theme.Forest
import za.co.boardaf.ui.theme.Sage

/** All screen callbacks in one place so screens and previews stay lightweight. */
data class BoardActions(
    val onSelectProblem: (String) -> Unit = {},
    val onSelectAdjacentProblem: (Int) -> Unit = {},
    val onStartSetting: () -> Unit = {},
    val onStartEditing: (String) -> Unit = {},
    val onDuplicateProblem: (String) -> Unit = {},
    val onCancelSetting: () -> Unit = {},
    val onSaveDraftAndClose: () -> Unit = {},
    val onConfirmForerunAndPublish: () -> Unit = {},
    val onTapHold: (String) -> Unit = {},
    val onMarkFootInstead: (String) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onRedo: () -> Unit = {},
    val onClearDraftHolds: () -> Unit = {},
    val onSelectRole: (ProblemHoldRole) -> Unit = {},
    val onSetFeetRule: (FeetRule) -> Unit = {},
    val onDraftNameChange: (String) -> Unit = {},
    val onDraftGradeChange: (BoulderGrade) -> Unit = {},
    val onDraftAngleChange: (Int) -> Unit = {},
    val onDraftAccentChange: (Accent) -> Unit = {},
    val onDraftNoteChange: (String) -> Unit = {},
    val onToggleDraftTag: (String) -> Unit = {},
    val onGuidedNext: () -> Unit = {},
    val onGuidedBack: () -> Unit = {},
    val onGoToGuidedStep: (GuidedStep) -> Unit = {},
    val onSetSetterMode: (SetterMode) -> Unit = {},
    val onArchiveProblem: (String) -> Unit = {},
    val onUnarchiveProblem: (String) -> Unit = {},
    val onDeleteProblem: (String) -> Unit = {},
    val onToggleBenchmark: (String) -> Unit = {},
    val onPublishProblem: (String) -> Unit = {},
    val onSetKickboardEnabled: (Boolean) -> Unit = {},
    val onSetKickboardBoundary: (Float) -> Unit = {},
    val onToggleHoldCapability: (String) -> Unit = {},
    val onConfirmBoardSetup: () -> Unit = {},
    val onSetGradeSystem: (GradeSystem) -> Unit = {},
    val onCloudSignIn: (String, String) -> Unit = { _, _ -> },
    val onCloudCreateAccount: (String, String) -> Unit = { _, _ -> },
    val onCloudSignOut: () -> Unit = {},
    val onCloudSyncNow: () -> Unit = {},
)

/** Material's default navigation bar is 80dp; trimming 8dp buys the board photo height. */
private val NAV_BAR_HEIGHT = 72.dp

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    BOARD("board", "Board", Icons.Rounded.Home),
    PROBLEMS("problems", "Problems", Icons.AutoMirrored.Rounded.ViewList),
    SETUP("setup", "Setup", Icons.Rounded.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardAfApp(viewModel: BoardViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = Destination.entries.firstOrNull {
        it.route == backStackEntry?.destination?.route
    } ?: Destination.BOARD
    val snackbarHostState = remember { SnackbarHostState() }
    val haptics = LocalHapticFeedback.current

    val actions = remember(viewModel) {
        BoardActions(
            onSelectProblem = viewModel::selectProblem,
            onSelectAdjacentProblem = viewModel::selectAdjacentProblem,
            onStartSetting = viewModel::startSetting,
            onStartEditing = viewModel::startEditing,
            onDuplicateProblem = viewModel::duplicateProblem,
            onCancelSetting = viewModel::cancelSetting,
            onSaveDraftAndClose = viewModel::saveDraftAndClose,
            onConfirmForerunAndPublish = viewModel::confirmForerunAndPublish,
            onTapHold = viewModel::tapHold,
            onMarkFootInstead = viewModel::markFootInstead,
            onUndo = viewModel::undo,
            onRedo = viewModel::redo,
            onClearDraftHolds = viewModel::clearDraftHolds,
            onSelectRole = viewModel::selectRole,
            onSetFeetRule = viewModel::setFeetRule,
            onDraftNameChange = viewModel::setDraftName,
            onDraftGradeChange = viewModel::setDraftGrade,
            onDraftAngleChange = viewModel::setDraftAngle,
            onDraftAccentChange = viewModel::setDraftAccent,
            onDraftNoteChange = viewModel::setDraftNote,
            onToggleDraftTag = viewModel::toggleDraftTag,
            onGuidedNext = viewModel::guidedNext,
            onGuidedBack = viewModel::guidedBack,
            onGoToGuidedStep = viewModel::goToGuidedStep,
            onSetSetterMode = viewModel::setSetterMode,
            onArchiveProblem = viewModel::archiveProblem,
            onUnarchiveProblem = viewModel::unarchiveProblem,
            onDeleteProblem = viewModel::deleteProblem,
            onToggleBenchmark = viewModel::toggleBenchmark,
            onPublishProblem = viewModel::publishProblem,
            onSetKickboardEnabled = viewModel::setKickboardEnabled,
            onSetKickboardBoundary = viewModel::setKickboardBoundary,
            onToggleHoldCapability = viewModel::toggleHoldCapability,
            onConfirmBoardSetup = viewModel::confirmBoardSetup,
            onSetGradeSystem = viewModel::setGradeSystem,
            onCloudSignIn = viewModel::cloudSignIn,
            onCloudCreateAccount = viewModel::cloudCreateAccount,
            onCloudSignOut = viewModel::cloudSignOut,
            onCloudSyncNow = viewModel::cloudSyncNow,
        )
    }

    var confirmNewSession by rememberSaveable { mutableStateOf(false) }

    fun navigateToBoard() {
        navController.navigate(Destination.BOARD.route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BoardEvent.TapRejected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    val result = snackbarHostState.showSnackbar(
                        message = event.rejection.message,
                        actionLabel = if (event.rejection.offerFootInstead) "Mark as foot instead" else null,
                        duration = if (event.rejection.offerFootInstead) {
                            SnackbarDuration.Long
                        } else {
                            SnackbarDuration.Short
                        },
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.markFootInstead(event.rejection.holdId)
                    }
                }

                is BoardEvent.Message -> {
                    val result = snackbarHostState.showSnackbar(
                        message = event.text,
                        actionLabel = event.actionLabel,
                        duration = if (event.actionLabel != null) {
                            SnackbarDuration.Long
                        } else {
                            SnackbarDuration.Short
                        },
                    )
                    if (result == SnackbarResult.ActionPerformed && event.undoArchive != null) {
                        viewModel.restoreArchived(event.undoArchive)
                    }
                }
            }
        }
    }

    // Back walks the wizard one step; from the first step it closes the session
    // (the draft is autosaved on every action, so nothing is lost).
    if (confirmNewSession) {
        AlertDialog(
            onDismissRequest = { confirmNewSession = false },
            title = { Text("Start a new problem?") },
            text = {
                Text(
                    "Your current draft is saved to the library, and the setter will " +
                        "start again from an empty board.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmNewSession = false
                        viewModel.startSetting()
                        if (currentDestination != Destination.BOARD) navigateToBoard()
                    },
                ) { Text("Start new") }
            },
            dismissButton = {
                TextButton(onClick = { confirmNewSession = false }) { Text("Keep editing") }
            },
        )
    }

    BackHandler(enabled = state.isSetting) {
        // Quick set has no steps to walk; back just closes the (autosaved) session.
        if (state.setter.mode == SetterMode.GUIDED && state.setter.guidedStep.ordinal > 0) {
            viewModel.guidedBack()
        } else {
            viewModel.cancelSetting()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            // No top bar: the board photo gets that vertical space instead.
            bottomBar = {
                val navBarColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BoardDark,
                    selectedTextColor = Sage,
                    indicatorColor = Sage,
                    unselectedIconColor = BoardPaper.copy(alpha = 0.58f),
                    unselectedTextColor = BoardPaper.copy(alpha = 0.58f),
                )
                val bottomInset = WindowInsets.navigationBars.asPaddingValues()
                    .calculateBottomPadding()
                NavigationBar(
                    containerColor = BoardDark,
                    // 8dp shorter than the Material default (80dp), on top of the
                    // system gesture inset the bar still has to clear.
                    modifier = Modifier.height(NAV_BAR_HEIGHT + bottomInset),
                ) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination == destination,
                            onClick = {
                                // Keep the setter session alive across tabs so returning
                                // to Board resumes where the user left off (draft is autosaved).
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            colors = navBarColors,
                        )
                        if (destination == Destination.PROBLEMS) {
                            NavigationBarItem(
                                selected = false,
                                // Restarting a live session silently was the original
                                // bug, so a live session asks first.
                                onClick = {
                                    if (state.isSetting) {
                                        confirmNewSession = true
                                    } else {
                                        viewModel.startSetting()
                                        if (currentDestination != Destination.BOARD) {
                                            navigateToBoard()
                                        }
                                    }
                                },
                                icon = { Icon(Icons.Rounded.Add, contentDescription = "New problem") },
                                label = { Text("New") },
                                colors = navBarColors,
                            )
                        }
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Forest)
                    }
                } else {
                    NavHost(
                        navController = navController,
                        startDestination = Destination.BOARD.route,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        composable(Destination.BOARD.route) {
                            BoardScreen(
                                state = state,
                                actions = actions,
                                contentPadding = padding,
                            )
                        }
                        composable(Destination.PROBLEMS.route) {
                            ProblemsScreen(
                                state = state,
                                actions = actions,
                                contentPadding = padding,
                                onOpenProblem = { problemId ->
                                    viewModel.selectProblem(problemId)
                                    navigateToBoard()
                                },
                                onEditProblem = { problemId ->
                                    viewModel.startEditing(problemId)
                                    navigateToBoard()
                                },
                            )
                        }
                        composable(Destination.SETUP.route) {
                            SetupScreen(
                                state = state,
                                actions = actions,
                                contentPadding = padding,
                            )
                        }
                    }
                }
            }
        }
    }
}
