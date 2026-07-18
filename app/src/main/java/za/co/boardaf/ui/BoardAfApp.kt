package za.co.boardaf.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import za.co.boardaf.BoardViewModel
import za.co.boardaf.ui.theme.BoardDark
import za.co.boardaf.ui.theme.BoardPaper
import za.co.boardaf.ui.theme.Coral

private enum class Destination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    BOARD("board", "Board", Icons.Rounded.Home),
    PROBLEMS("problems", "Problems", Icons.AutoMirrored.Rounded.ViewList),
    SESSIONS("sessions", "Sessions", Icons.Rounded.BarChart),
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

    BackHandler(enabled = state.isSetting) {
        viewModel.cancelSetting()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BoardPaper) {
        Scaffold(
            containerColor = BoardPaper,
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
                NavHost(
                    navController = navController,
                    startDestination = Destination.BOARD.route,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(Destination.BOARD.route) {
                        BoardScreen(
                            state = state,
                            contentPadding = padding,
                            onSelectRole = viewModel::selectRole,
                            onHoldClick = viewModel::toggleDraftHold,
                            onDraftNameChange = viewModel::setDraftName,
                            onDraftGradeChange = viewModel::setDraftGrade,
                            onDraftAccentChange = viewModel::setDraftAccent,
                            onDraftNoteChange = viewModel::setDraftNote,
                            onClearDraft = viewModel::clearDraftHolds,
                            onSaveDraft = viewModel::saveDraft,
                            onCancelDraft = viewModel::cancelSetting,
                            onLogAttempt = viewModel::logAttempt,
                        )
                    }
                    composable(Destination.PROBLEMS.route) {
                        ProblemsScreen(
                            state = state,
                            contentPadding = padding,
                            onSelectProblem = { problemId ->
                                viewModel.selectProblem(problemId)
                                navController.navigate(Destination.BOARD.route) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    composable(Destination.SESSIONS.route) {
                        SessionsScreen(state = state, contentPadding = padding)
                    }
                    composable(Destination.SETUP.route) {
                        SetupScreen(contentPadding = padding)
                    }
                }
            }
        }
    }
}
