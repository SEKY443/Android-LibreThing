package com.example.android_go_librespot.ui.navigation

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
