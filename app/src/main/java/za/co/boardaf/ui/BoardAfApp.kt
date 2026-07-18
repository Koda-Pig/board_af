package za.co.boardaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import za.co.boardaf.ui.theme.Coral

/** All screen callbacks in one place so screens and previews stay lightweight. */
data class BoardActions(
    val onSelectProblem: (String) -> Unit = {},
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
    val onDraftAccentChange: (Accent) -> Unit = {},
    val onDraftNoteChange: (String) -> Unit = {},
    val onToggleDraftTag: (String) -> Unit = {},
    val onSetSetterMode: (SetterMode) -> Unit = {},
    val onGuidedNext: () -> Unit = {},
    val onGuidedBack: () -> Unit = {},
    val onGoToGuidedStep: (GuidedStep) -> Unit = {},
    val onSetReviewing: (Boolean) -> Unit = {},
    val onArchiveProblem: (String) -> Unit = {},
    val onUnarchiveProblem: (String) -> Unit = {},
    val onToggleBenchmark: (String) -> Unit = {},
    val onPublishProblem: (String) -> Unit = {},
    val onSetKickboardEnabled: (Boolean) -> Unit = {},
    val onSetKickboardBoundary: (Float) -> Unit = {},
    val onToggleHoldCapability: (String) -> Unit = {},
    val onConfirmBoardSetup: () -> Unit = {},
    val onSetGradeSystem: (GradeSystem) -> Unit = {},
)

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
            onDraftAccentChange = viewModel::setDraftAccent,
            onDraftNoteChange = viewModel::setDraftNote,
            onToggleDraftTag = viewModel::toggleDraftTag,
            onSetSetterMode = viewModel::setSetterMode,
            onGuidedNext = viewModel::guidedNext,
            onGuidedBack = viewModel::guidedBack,
            onGoToGuidedStep = viewModel::goToGuidedStep,
            onSetReviewing = viewModel::setReviewing,
            onArchiveProblem = viewModel::archiveProblem,
            onUnarchiveProblem = viewModel::unarchiveProblem,
            onToggleBenchmark = viewModel::toggleBenchmark,
            onPublishProblem = viewModel::publishProblem,
            onSetKickboardEnabled = viewModel::setKickboardEnabled,
            onSetKickboardBoundary = viewModel::setKickboardBoundary,
            onToggleHoldCapability = viewModel::toggleHoldCapability,
            onConfirmBoardSetup = viewModel::confirmBoardSetup,
            onSetGradeSystem = viewModel::setGradeSystem,
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BoardEvent.TapRejected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    val result = snackbarHostState.showSnackbar(
                        message = event.rejection.message,
                        actionLabel = if (event.rejection.offerFootInstead) "Mark as foot instead" else null,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.markFootInstead(event.rejection.holdId)
                    }
                }

                is BoardEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    BackHandler(enabled = state.isSetting) {
        viewModel.cancelSetting()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BoardPaper) {
        Scaffold(
            containerColor = BoardPaper,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (currentDestination == Destination.BOARD && state.isSetting) {
                                "Set a problem"
                            } else {
                                "BOARD AF  ·  ${currentDestination.label}"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                    },
                    actions = {
                        if (currentDestination == Destination.BOARD || currentDestination == Destination.PROBLEMS) {
                            IconButton(
                                onClick = {
                                    viewModel.startSetting()
                                    if (currentDestination != Destination.BOARD) {
                                        navController.navigate(Destination.BOARD.route) {
                                            launchSingleTop = true
                                        }
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = "New problem")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = BoardDark,
                        titleContentColor = BoardPaper,
                        actionIconContentColor = Coral,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = BoardDark) {
                    Destination.entries.forEach { destination ->
                        val selected = currentDestination == destination
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (destination != Destination.BOARD) {
                                    viewModel.cancelSetting()
                                }
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
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = BoardDark,
                                selectedTextColor = Coral,
                                indicatorColor = Coral,
                                unselectedIconColor = BoardPaper.copy(alpha = 0.58f),
                                unselectedTextColor = BoardPaper.copy(alpha = 0.58f),
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Coral)
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
                                    navController.navigate(Destination.BOARD.route) {
                                        launchSingleTop = true
                                    }
                                },
                                onEditProblem = { problemId ->
                                    viewModel.startEditing(problemId)
                                    navController.navigate(Destination.BOARD.route) {
                                        launchSingleTop = true
                                    }
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
