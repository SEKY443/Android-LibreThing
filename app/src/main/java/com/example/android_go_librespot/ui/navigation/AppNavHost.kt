package com.example.android_go_librespot.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.android_go_librespot.ui.dashboard.DashboardScreen
import com.example.android_go_librespot.ui.dashboard.DashboardViewModel
import com.example.android_go_librespot.ui.settings.SettingsScreen
import com.example.android_go_librespot.ui.settings.SettingsViewModel

private const val SLIDE_DURATION_MS = 220

private fun routeIndex(route: String?): Int =
    AppDestination.entries.indexOfFirst { it.route == route }.coerceAtLeast(0)

/**
 * Slide direction is based on tab order (Dashboard is index 0, Settings is index 1): moving to
 * a higher-index tab slides content in from the right, matching the bottom nav item's position,
 * and moving to a lower-index tab slides in from the left, so switching back and forth doesn't
 * always slide the same way. Used for both forward (`enterTransition`/`exitTransition`) and pop
 * (`popEnterTransition`/`popExitTransition`) cases below: this is tab navigation via `navigate()`
 * with `popUpTo`/`restoreState`, not a push/pop stack, so only the forward pair actually fires
 * in practice, but all four read `initialState`/`targetState` the same way regardless.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabEnterTransition(): EnterTransition {
    val direction = if (routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)) 1 else -1
    return slideInHorizontally(animationSpec = tween(SLIDE_DURATION_MS)) { fullWidth -> direction * fullWidth }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabExitTransition(): ExitTransition {
    val direction = if (routeIndex(targetState.destination.route) >= routeIndex(initialState.destination.route)) 1 else -1
    return slideOutHorizontally(animationSpec = tween(SLIDE_DURATION_MS)) { fullWidth -> -direction * fullWidth }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination

            NavigationBar {
                AppDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.DASHBOARD.route,
            modifier = Modifier.padding(paddingValues),
            enterTransition = { tabEnterTransition() },
            exitTransition = { tabExitTransition() },
            popEnterTransition = { tabEnterTransition() },
            popExitTransition = { tabExitTransition() },
        ) {
            composable(AppDestination.DASHBOARD.route) {
                DashboardScreen(viewModel = viewModel<DashboardViewModel>())
            }
            composable(AppDestination.SETTINGS.route) {
                SettingsScreen(viewModel = viewModel<SettingsViewModel>())
            }
        }
    }
}
