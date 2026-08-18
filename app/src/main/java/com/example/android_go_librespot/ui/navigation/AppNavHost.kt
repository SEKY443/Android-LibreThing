package com.example.android_go_librespot.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.android_go_librespot.ui.dashboard.DashboardScreen
import com.example.android_go_librespot.ui.dashboard.DashboardViewModel
import com.example.android_go_librespot.ui.settings.SettingsScreen
import com.example.android_go_librespot.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

/**
 * A [HorizontalPager] rather than Navigation Compose's `NavHost`: with just these two flat
 * tabs (no deep links, no back-stack-worthy "drill into detail" screens), a pager gets a real
 * swipe-to-switch gesture -- the page follows your finger and springs to place -- essentially
 * for free, where `NavHost` only offers scripted enter/exit transitions driven by taps.
 */
@Composable
fun AppNavHost() {
    val pagerState = rememberPagerState(pageCount = { AppDestination.entries.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppDestination.entries.forEachIndexed { index, destination ->
                    val selected = pagerState.currentPage == index
                    NavigationBarItem(
                        selected = selected,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        icon = { Icon(if (selected) destination.selectedIcon else destination.unselectedIcon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
        ) { page ->
            when (AppDestination.entries[page]) {
                AppDestination.DASHBOARD -> DashboardScreen(viewModel = viewModel<DashboardViewModel>())
                AppDestination.SETTINGS -> SettingsScreen(viewModel = viewModel<SettingsViewModel>())
            }
        }
    }
}
