package io.github.seky443.librething.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.seky443.librething.R

enum class AppDestination(
    @param:StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    DASHBOARD(R.string.nav_destination_dashboard, Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
    SETTINGS(R.string.nav_destination_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
}
