package com.example.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.pages.HomeScreen
import com.example.ui.pages.LessonsScreen
import com.example.ui.pages.PdfViewerScreen
import com.example.ui.pages.QuestionScreen
import com.example.ui.pages.SettingsScreen
import com.example.ui.pages.StatisticsScreen
import com.example.ui.viewmodel.StudyViewModel

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Lessons : Screen("lessons", "Lessons", Icons.Filled.MenuBook, Icons.Outlined.MenuBook)
    data object Statistics : Screen("statistics", "Stats", Icons.Filled.BarChart, Icons.Outlined.BarChart)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Lessons,
    Screen.Statistics,
    Screen.Settings
)

@Composable
fun StudyApp(
    viewModel: StudyViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()

    LaunchedEffect(errorMessage) {
        errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Long
            )
            viewModel.clearError()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let { msg ->
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSuccess()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination?.route

    val isTopLevelDestination = bottomNavItems.any { it.route == currentDestination }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(modifier = Modifier.testTag("bottom_navigation_bar")) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            modifier = Modifier.testTag("nav_item_${screen.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateToLessons = { navController.navigate(Screen.Lessons.route) },
                    onNavigateToQuiz = { lessonId ->
                        navController.navigate("quiz/$lessonId")
                    },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.Lessons.route) {
                LessonsScreen(
                    viewModel = viewModel,
                    onNavigateToQuiz = { lessonId ->
                        navController.navigate("quiz/$lessonId")
                    },
                    onNavigateToPdf = { lessonId ->
                        navController.navigate("pdf/$lessonId")
                    }
                )
            }

            composable(Screen.Statistics.route) {
                StatisticsScreen(
                    viewModel = viewModel,
                    onNavigateToQuiz = { lessonId ->
                        navController.navigate("quiz/$lessonId")
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }

            composable(
                route = "quiz/{lessonId}",
                arguments = listOf(navArgument("lessonId") { type = NavType.LongType })
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getLong("lessonId") ?: 0L
                QuestionScreen(
                    lessonId = lessonId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToStats = {
                        navController.navigate(Screen.Statistics.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(
                route = "pdf/{lessonId}",
                arguments = listOf(navArgument("lessonId") { type = NavType.LongType })
            ) { backStackEntry ->
                val lessonId = backStackEntry.arguments?.getLong("lessonId") ?: 0L
                PdfViewerScreen(
                    lessonId = lessonId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToQuiz = { lId ->
                        navController.navigate("quiz/$lId")
                    }
                )
            }
        }
    }
}
