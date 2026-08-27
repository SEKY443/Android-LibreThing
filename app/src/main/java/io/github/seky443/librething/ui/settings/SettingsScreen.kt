package io.github.seky443.librething.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.seky443.librething.R
import io.github.seky443.librething.data.CredentialsType
import io.github.seky443.librething.data.DashboardBackgroundStyle
import io.github.seky443.librething.data.DeviceType
import io.github.seky443.librething.data.GoLibrespotConfig
import io.github.seky443.librething.data.ThemeMode
import io.github.seky443.librething.ui.dashboard.BlackScreenOverlayController
import io.github.seky443.librething.util.BatteryOptimizationHelper
import kotlin.math.roundToInt

/** Every direct item inside a [SettingsSection] card -- switch, radio row, text field, the
 * battery-status row, a slider -- uses this same vertical padding, and the card itself adds
 * none of its own. That's what keeps every section's top/bottom margin identical regardless of
 * what its first/last item happens to be, instead of it varying by item type the way scattered
 * one-off `Spacer`s did. */
private val SettingsRowPadding = 12.dp

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val config by viewModel.goLibrespotConfig.collectAsState()
    val appPrefs by viewModel.appPreferences.collectAsState()
    val isBatteryExempt by viewModel.isBatteryOptimizationExempt.collectAsState()
    val isOverlayPermissionGranted by viewModel.isOverlayPermissionGranted.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshBatteryOptimizationStatus()
        viewModel.refreshOverlayPermissionStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsSection(title = stringResource(R.string.settings_section_dashboard)) {
            SwitchRow(
                title = stringResource(R.string.settings_nerd_mode_title),
                subtitle = stringResource(R.string.settings_nerd_mode_subtitle),
                checked = appPrefs.nerdModeEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(nerdModeEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_fullscreen_mode_title),
                subtitle = stringResource(R.string.settings_fullscreen_mode_subtitle),
                checked = appPrefs.fullscreenModeEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(fullscreenModeEnabled = it) } },
            )
            SectionLabel(stringResource(R.string.settings_icon_row_label))
            SwitchRow(
                title = stringResource(R.string.settings_hide_console_button_title),
                checked = appPrefs.hideConsoleButtonEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(hideConsoleButtonEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_hide_fake_sleep_button_title),
                checked = appPrefs.hideFakeSleepButtonEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(hideFakeSleepButtonEnabled = it) } },
            )
            SectionLabel(stringResource(R.string.settings_volume_slider_label))
            SwitchRow(
                title = stringResource(R.string.settings_show_volume_slider_title),
                checked = appPrefs.volumeSliderEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(volumeSliderEnabled = it) } },
            )
        }

        // Its own section, not folded into Dashboard above -- the driving-safety warning below
        // needs to read as a standalone notice about this one feature, not a footnote buried
        // partway through an unrelated card.
        SettingsSection(title = stringResource(R.string.settings_section_gestures)) {
            SwitchRow(
                title = stringResource(R.string.settings_gesture_controls_title),
                subtitle = stringResource(R.string.settings_gesture_controls_subtitle),
                checked = appPrefs.gestureControlsEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(gestureControlsEnabled = it) } },
            )
            AnimatedVisibility(visible = appPrefs.gestureControlsEnabled) {
                Column(modifier = Modifier.padding(bottom = SettingsRowPadding)) {
                    GestureDrivingWarning()
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        LabeledSlider(
                            label = stringResource(R.string.settings_gesture_haptic_intensity_label),
                            valueText = stringResource(R.string.settings_gesture_haptic_intensity_value_format, appPrefs.gestureHapticIntensity * 100f),
                            value = appPrefs.gestureHapticIntensity,
                            onValueChange = { viewModel.updateAppPreferences { p -> p.copy(gestureHapticIntensity = it) } },
                            valueRange = 0f..4f,
                            rangeStartText = stringResource(R.string.settings_gesture_haptic_intensity_range_min),
                            rangeEndText = stringResource(R.string.settings_gesture_haptic_intensity_range_max),
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_gesture_transition_show_console_title),
                            subtitle = stringResource(R.string.settings_gesture_transition_show_console_subtitle),
                            checked = appPrefs.gestureTransitionShowConsoleEnabled,
                            onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(gestureTransitionShowConsoleEnabled = it) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_gesture_transition_rounded_cover_title),
                            subtitle = stringResource(R.string.settings_gesture_transition_rounded_cover_subtitle),
                            checked = appPrefs.gestureTransitionRoundedCoverEnabled,
                            onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(gestureTransitionRoundedCoverEnabled = it) } },
                        )
                        SwitchRow(
                            title = stringResource(R.string.settings_gesture_controls_full_screen_title),
                            subtitle = stringResource(R.string.settings_gesture_controls_full_screen_subtitle),
                            checked = appPrefs.gestureControlsFullScreenEnabled,
                            onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(gestureControlsFullScreenEnabled = it) } },
                        )
                    }
                }
            }
        }

        // Everything that only changes anything while the simplified dashboard is actually in
        // landscape orientation, all in one place instead of mixed into Dashboard above.
        SettingsSection(title = stringResource(R.string.settings_section_landscape)) {
            SwitchRow(
                title = stringResource(R.string.settings_landscape_hide_icon_row_title),
                subtitle = stringResource(R.string.settings_landscape_hide_icon_row_subtitle),
                checked = appPrefs.hideIconRowInLandscapeEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(hideIconRowInLandscapeEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_landscape_hide_last_session_title),
                checked = appPrefs.hideLastSessionLabelInLandscapeEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(hideLastSessionLabelInLandscapeEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_landscape_stretch_transport_title),
                subtitle = stringResource(R.string.settings_landscape_stretch_transport_subtitle),
                checked = appPrefs.landscapeStretchTransportRowEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(landscapeStretchTransportRowEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_landscape_left_align_title),
                subtitle = stringResource(R.string.settings_landscape_left_align_subtitle),
                checked = appPrefs.landscapeLeftAlignTrackInfoEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(landscapeLeftAlignTrackInfoEnabled = it) } },
            )
            AnimatedVisibility(visible = appPrefs.volumeSliderEnabled) {
                SwitchRow(
                    title = stringResource(R.string.settings_landscape_volume_slider_only_title),
                    checked = appPrefs.volumeSliderLandscapeOnly,
                    onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(volumeSliderLandscapeOnly = it) } },
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_appearance)) {
            SectionLabel(stringResource(R.string.settings_theme_label))
            RadioGroup(
                options = ThemeMode.entries,
                selected = appPrefs.themeMode,
                label = { stringResource(it.labelRes) },
                onSelected = { viewModel.updateAppPreferences { p -> p.copy(themeMode = it) } },
            )
            SectionLabel(stringResource(R.string.settings_dashboard_background_label))
            RadioGroup(
                options = DashboardBackgroundStyle.entries,
                selected = appPrefs.dashboardBackgroundStyle,
                label = { stringResource(it.labelRes) },
                vertical = true,
                onSelected = { viewModel.updateAppPreferences { p -> p.copy(dashboardBackgroundStyle = it) } },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_device_configuration)) {
            var deviceName by remember(config.deviceName) { mutableStateOf(config.deviceName) }
            OutlinedTextField(
                value = deviceName,
                onValueChange = {
                    deviceName = it
                    viewModel.updateConfig { c -> c.copy(deviceName = it) }
                },
                label = { Text(stringResource(R.string.settings_device_name_label)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = SettingsRowPadding),
                singleLine = true,
            )
            DeviceTypeDropdown(
                selected = config.deviceType,
                onSelected = { viewModel.updateConfig { c -> c.copy(deviceType = it) } },
                modifier = Modifier.padding(bottom = SettingsRowPadding),
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_playback)) {
            SectionLabel(stringResource(R.string.settings_bitrate_label))
            RadioGroup(
                options = GoLibrespotConfig.BITRATE_OPTIONS,
                selected = config.bitrate,
                label = { stringResource(R.string.settings_bitrate_option_format, it) },
                onSelected = { viewModel.updateConfig { c -> c.copy(bitrate = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_volume_normalization_title),
                subtitle = stringResource(R.string.settings_volume_normalization_subtitle),
                checked = !config.normalisationDisabled,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(normalisationDisabled = !it) } },
            )
            AnimatedVisibility(visible = !config.normalisationDisabled) {
                // Indented so it visually reads as "these two are options of the switch above",
                // not a separate, unrelated group of controls.
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_use_album_gain_title),
                        subtitle = stringResource(R.string.settings_use_album_gain_subtitle),
                        checked = config.normalisationUseAlbumGain,
                        onCheckedChange = { viewModel.updateConfig { c -> c.copy(normalisationUseAlbumGain = it) } },
                    )
                    LabeledSlider(
                        label = stringResource(R.string.settings_pregain_label),
                        valueText = stringResource(R.string.settings_pregain_value_format, "%+.1f".format(config.normalisationPregain)),
                        value = config.normalisationPregain,
                        onValueChange = { viewModel.updateConfig { c -> c.copy(normalisationPregain = it) } },
                        valueRange = -10f..10f,
                        rangeStartText = stringResource(R.string.settings_pregain_range_min),
                        rangeEndText = stringResource(R.string.settings_pregain_range_max),
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.settings_autoplay_title),
                subtitle = stringResource(R.string.settings_autoplay_subtitle),
                checked = !config.disableAutoplay,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(disableAutoplay = !it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_mask_track_transition_flash_title),
                subtitle = stringResource(R.string.settings_mask_track_transition_flash_subtitle),
                checked = appPrefs.maskTrackTransitionFlashEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(maskTrackTransitionFlashEnabled = it) } },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_oled_protection)) {
            SwitchRow(
                title = stringResource(R.string.settings_oled_pixel_shift_title),
                subtitle = stringResource(R.string.settings_oled_pixel_shift_subtitle),
                checked = appPrefs.oledPixelShiftEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(oledPixelShiftEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_oled_checkerboard_dim_title),
                subtitle = stringResource(R.string.settings_oled_checkerboard_dim_subtitle),
                checked = appPrefs.oledCheckerboardDimEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(oledCheckerboardDimEnabled = it) } },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_display_filters)) {
            SwitchRow(
                title = stringResource(R.string.settings_grayscale_filter_title),
                subtitle = stringResource(R.string.settings_grayscale_filter_subtitle),
                checked = appPrefs.grayscaleFilterEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(grayscaleFilterEnabled = it) } },
            )
            AnimatedVisibility(visible = appPrefs.grayscaleFilterEnabled) {
                FilterScheduleRow(
                    startMinutes = appPrefs.grayscaleFilterStartMinutes,
                    endMinutes = appPrefs.grayscaleFilterEndMinutes,
                    onStartChange = { viewModel.updateAppPreferences { p -> p.copy(grayscaleFilterStartMinutes = it) } },
                    onEndChange = { viewModel.updateAppPreferences { p -> p.copy(grayscaleFilterEndMinutes = it) } },
                    modifier = Modifier.padding(bottom = SettingsRowPadding),
                )
            }
            SwitchRow(
                title = stringResource(R.string.settings_red_light_filter_title),
                subtitle = stringResource(R.string.settings_red_light_filter_subtitle),
                checked = appPrefs.redLightFilterEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(redLightFilterEnabled = it) } },
            )
            AnimatedVisibility(visible = appPrefs.redLightFilterEnabled) {
                FilterScheduleRow(
                    startMinutes = appPrefs.redLightFilterStartMinutes,
                    endMinutes = appPrefs.redLightFilterEndMinutes,
                    onStartChange = { viewModel.updateAppPreferences { p -> p.copy(redLightFilterStartMinutes = it) } },
                    onEndChange = { viewModel.updateAppPreferences { p -> p.copy(redLightFilterEndMinutes = it) } },
                    modifier = Modifier.padding(bottom = SettingsRowPadding),
                )
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_discovery_auth)) {
            SwitchRow(
                title = stringResource(R.string.settings_zeroconf_title),
                subtitle = stringResource(R.string.settings_zeroconf_subtitle),
                checked = config.zeroconfEnabled,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(zeroconfEnabled = it) } },
            )
            CredentialsTypeDropdown(
                selected = config.credentialsType,
                onSelected = { viewModel.updateConfig { c -> c.copy(credentialsType = it) } },
                modifier = Modifier.padding(bottom = SettingsRowPadding),
            )
            AnimatedVisibility(visible = config.credentialsType == CredentialsType.SPOTIFY_TOKEN) {
                Column {
                    var username by remember(config.spotifyTokenUsername) { mutableStateOf(config.spotifyTokenUsername) }
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            viewModel.updateConfig { c -> c.copy(spotifyTokenUsername = it) }
                        },
                        label = { Text(stringResource(R.string.settings_spotify_username_label)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = SettingsRowPadding),
                        singleLine = true,
                    )
                    var token by remember(config.spotifyTokenAccessToken) { mutableStateOf(config.spotifyTokenAccessToken) }
                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            viewModel.updateConfig { c -> c.copy(spotifyTokenAccessToken = it) }
                        },
                        label = { Text(stringResource(R.string.settings_cached_token_label)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = SettingsRowPadding),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                }
            }
            AnimatedVisibility(visible = config.credentialsType == CredentialsType.DEVICE_AUTH) {
                Text(
                    stringResource(R.string.settings_device_auth_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = SettingsRowPadding),
                )
            }
            SwitchRow(
                title = stringResource(R.string.settings_optimistic_replies_title),
                subtitle = stringResource(R.string.settings_optimistic_replies_subtitle),
                checked = config.optimisticPlaybackReplies,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(optimisticPlaybackReplies = it) } },
            )
        }

        SettingsSection(title = stringResource(R.string.settings_section_app_behavior)) {
            SwitchRow(
                title = stringResource(R.string.settings_keep_screen_on_title),
                checked = appPrefs.keepScreenOn,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(keepScreenOn = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_hold_wake_lock_title),
                subtitle = stringResource(R.string.settings_hold_wake_lock_subtitle),
                checked = appPrefs.holdWakeLock,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(holdWakeLock = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_autostart_boot_title),
                checked = appPrefs.autostartOnBoot,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(autostartOnBoot = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_auto_restart_on_crash_title),
                subtitle = stringResource(R.string.settings_auto_restart_on_crash_subtitle),
                checked = appPrefs.autoRestartOnCrashEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(autoRestartOnCrashEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_auto_check_for_updates_title),
                subtitle = stringResource(R.string.settings_auto_check_for_updates_subtitle),
                checked = appPrefs.autoCheckForUpdatesEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(autoCheckForUpdatesEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_auto_clear_cache_title),
                subtitle = stringResource(R.string.settings_auto_clear_cache_subtitle),
                checked = appPrefs.autoClearCacheEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(autoClearCacheEnabled = it) } },
            )
            AnimatedVisibility(visible = appPrefs.autoClearCacheEnabled) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    LabeledSlider(
                        label = stringResource(R.string.settings_auto_clear_cache_max_size_label),
                        valueText = stringResource(R.string.settings_auto_clear_cache_max_size_value_format, appPrefs.autoClearCacheMaxSizeMb),
                        value = appPrefs.autoClearCacheMaxSizeMb.toFloat(),
                        onValueChange = { viewModel.updateAppPreferences { p -> p.copy(autoClearCacheMaxSizeMb = it.roundToInt()) } },
                        valueRange = 50f..2000f,
                        steps = 38,
                        rangeStartText = stringResource(R.string.settings_auto_clear_cache_range_min),
                        rangeEndText = stringResource(R.string.settings_auto_clear_cache_range_max),
                    )
                }
            }
            Column(modifier = Modifier.padding(vertical = SettingsRowPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (isBatteryExempt) Icons.Filled.CheckCircle else Icons.Filled.BatteryChargingFull,
                        contentDescription = null,
                    )
                    Column {
                        Text(stringResource(R.string.settings_battery_optimization_label), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (isBatteryExempt) R.string.settings_battery_exempt_subtitle else R.string.settings_battery_not_exempt_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = !isBatteryExempt) {
                    OutlinedButton(
                        onClick = { context.startActivity(BatteryOptimizationHelper.requestIgnoreBatteryOptimizationsIntent(context)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.settings_disable_battery_optimization_button))
                    }
                }
            }
        }

        SettingsSection(title = stringResource(R.string.settings_section_fake_sleep)) {
            SwitchRow(
                title = stringResource(R.string.settings_auto_sleep_idle_title),
                subtitle = stringResource(R.string.settings_auto_sleep_idle_subtitle),
                checked = appPrefs.autoSleepOnIdleEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(autoSleepOnIdleEnabled = it) } },
            )
            SwitchRow(
                title = stringResource(R.string.settings_fake_sleep_single_tap_wake_title),
                subtitle = stringResource(R.string.settings_fake_sleep_single_tap_wake_subtitle),
                checked = appPrefs.fakeSleepSingleTapWakeEnabled,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(fakeSleepSingleTapWakeEnabled = it) } },
            )
            AnimatedVisibility(visible = appPrefs.autoSleepOnIdleEnabled) {
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    LabeledSlider(
                        label = stringResource(R.string.settings_idle_delay_label),
                        valueText = stringResource(R.string.settings_idle_delay_value_format, appPrefs.autoSleepIdleDelaySeconds),
                        value = appPrefs.autoSleepIdleDelaySeconds.toFloat(),
                        onValueChange = { viewModel.updateAppPreferences { p -> p.copy(autoSleepIdleDelaySeconds = it.roundToInt()) } },
                        valueRange = 10f..300f,
                        steps = 57,
                        rangeStartText = stringResource(R.string.settings_idle_delay_range_min),
                        rangeEndText = stringResource(R.string.settings_idle_delay_range_max),
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_nap_mode_title),
                        subtitle = stringResource(R.string.settings_nap_mode_subtitle),
                        checked = appPrefs.autoSleepNapModeEnabled,
                        onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(autoSleepNapModeEnabled = it) } },
                    )
                }
            }
            Column(modifier = Modifier.padding(vertical = SettingsRowPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (isOverlayPermissionGranted) Icons.Filled.CheckCircle else Icons.Filled.Layers,
                        contentDescription = null,
                    )
                    Column {
                        Text(stringResource(R.string.settings_overlay_permission_label), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(
                                if (isOverlayPermissionGranted) R.string.settings_overlay_permission_granted_subtitle
                                else R.string.settings_overlay_permission_not_granted_subtitle,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AnimatedVisibility(visible = !isOverlayPermissionGranted) {
                    OutlinedButton(
                        onClick = { BlackScreenOverlayController.requestPermission(context) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(stringResource(R.string.settings_grant_overlay_permission_button))
                    }
                }
            }
        }
    }
}

/** A grouped block of related settings -- title above, contents in a rounded, tonally
 * distinct card below. Styled to match the Android Settings app's own grouped sections: a
 * small colored label, a [MaterialTheme.colorScheme.surfaceContainer] card with no elevation
 * shadow (the contrast against the page background does the separating), and generous corner
 * rounding. No vertical padding of its own -- every item inside supplies its own
 * [SettingsRowPadding], so the card's top/bottom margin is identical everywhere regardless of
 * what its first/last item is. */
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), content = content)
        }
    }
}

/** Safety notice shown right under the gesture-controls toggle whenever it's on -- deliberately
 * styled in the theme's error colors instead of matching the rest of the section, so it reads as
 * a warning at a glance rather than blending in with an ordinary setting's subtitle. */
@Composable
private fun GestureDrivingWarning() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = SettingsRowPadding)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            stringResource(R.string.settings_gesture_driving_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

/** A small colored sub-heading within a [SettingsSection], for grouping a handful of related
 * rows (e.g. the icon-row toggles) without needing a whole separate section/card for them. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

/**
 * A full-width, whole-row-tappable switch, matching the Android Settings app's own behavior
 * (tapping anywhere on the row toggles it, not just the switch itself) via [toggleable] with
 * the switch's own `onCheckedChange` left null so it's purely driven by the row. Uses the same
 * [SettingsRowPadding] every other settings row does.
 */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = {
                    haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                    onCheckedChange(it)
                },
            )
            .padding(vertical = SettingsRowPadding),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A row of mutually-exclusive options, each individually tappable (not just its
 * [RadioButton]) with a matching haptic tick -- again mirroring how Android Settings' own
 * radio-style rows behave. Horizontal for short label sets (theme, bitrate); [vertical] for
 * longer ones that wrap poorly side by side (background style). [label] is `@Composable` so
 * call sites can resolve it via [stringResource] directly. */
@Composable
private fun <T> RadioGroup(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    vertical: Boolean = false,
) {
    val haptics = LocalHapticFeedback.current
    val optionRow = @Composable { option: T, fillWidth: Boolean ->
        Row(
            modifier = Modifier
                .let { if (fillWidth) it.fillMaxWidth() else it }
                .selectable(
                    selected = option == selected,
                    role = Role.RadioButton,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onSelected(option)
                    },
                )
                .padding(vertical = SettingsRowPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = option == selected, onClick = null)
            Text(label(option), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (vertical) {
        Column { options.forEach { optionRow(it, true) } }
    } else {
        // Not fillMaxWidth here: these sit side by side sharing the row's width, so each one
        // must size to its own content instead of every option greedily claiming the whole
        // width and squeezing the rest down to a sliver. spacedBy is still needed even so --
        // without it, adjacent options (each already flush against its own radio button/text)
        // sit directly against each other with no visual separation at all.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { optionRow(it, false) }
        }
    }
}

/** A labeled [Slider] with its current value shown opposite the label and the range's endpoints
 * captioned underneath -- the shape shared by the pregain and idle-delay sliders. Nudges a
 * haptic tick each time the rounded value changes, not on every raw drag delta. */
@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    rangeStartText: String,
    rangeEndText: String,
    steps: Int = 0,
) {
    val haptics = LocalHapticFeedback.current
    Column(modifier = Modifier.padding(vertical = SettingsRowPadding)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(valueText, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        var lastHapticStep by remember(value) { mutableStateOf(value.roundToInt()) }
        Slider(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)
                val step = newValue.roundToInt()
                if (step != lastHapticStep) {
                    lastHapticStep = step
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            },
            valueRange = valueRange,
            steps = steps,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(rangeStartText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(rangeEndText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeviceTypeDropdown(selected: DeviceType, onSelected: (DeviceType) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_device_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeviceType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(stringResource(type.labelRes)) }, onClick = { onSelected(type); expanded = false })
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CredentialsTypeDropdown(selected: CredentialsType, onSelected: (CredentialsType) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = stringResource(selected.labelRes),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.settings_authentication_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CredentialsType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(stringResource(type.labelRes)) }, onClick = { onSelected(type); expanded = false })
            }
        }
    }
}

/** A start/end time pair for a scheduled display filter -- each button opens a [TimePickerDialog]
 * and shows the picked time as "HH:mm", 24-hour, matching [rememberTimePickerState]'s default
 * (device locale's is-24-hour setting isn't read here, since a fixed format keeps the two ends of
 * the range visually comparable regardless of what the system clock format happens to be). Minutes
 * are minutes-since-midnight (0..1439); an end before the start is a valid overnight range (e.g.
 * 22:00-06:00), left to whatever reads [AppPreferences.grayscaleFilterStartMinutes]/etc. to
 * interpret as wrapping past midnight rather than being special-cased here. */
@Composable
private fun FilterScheduleRow(
    startMinutes: Int,
    endMinutes: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editingStart by remember { mutableStateOf(false) }
    var editingEnd by remember { mutableStateOf(false) }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(SettingsRowPadding)) {
        OutlinedButton(onClick = { editingStart = true }, modifier = Modifier.weight(1f)) {
            Text("${stringResource(R.string.settings_filter_schedule_start)} ${formatMinutesOfDay(startMinutes)}")
        }
        OutlinedButton(onClick = { editingEnd = true }, modifier = Modifier.weight(1f)) {
            Text("${stringResource(R.string.settings_filter_schedule_end)} ${formatMinutesOfDay(endMinutes)}")
        }
    }

    if (editingStart) {
        TimePickerDialog(
            initialMinutes = startMinutes,
            onConfirm = { onStartChange(it); editingStart = false },
            onDismiss = { editingStart = false },
        )
    }
    if (editingEnd) {
        TimePickerDialog(
            initialMinutes = endMinutes,
            onConfirm = { onEndChange(it); editingEnd = false },
            onDismiss = { editingEnd = false },
        )
    }
}

private fun formatMinutesOfDay(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinutes: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = androidx.compose.material3.rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        text = { androidx.compose.material3.TimePicker(state = state) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.settings_filter_time_picker_confirm))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_filter_time_picker_cancel))
            }
        },
    )
}
