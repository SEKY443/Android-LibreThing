package com.example.android_go_librespot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.android_go_librespot.data.CredentialsType
import com.example.android_go_librespot.data.DeviceType
import com.example.android_go_librespot.data.GoLibrespotConfig
import com.example.android_go_librespot.util.BatteryOptimizationHelper

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val config by viewModel.goLibrespotConfig.collectAsState()
    val appPrefs by viewModel.appPreferences.collectAsState()
    val isBatteryExempt by viewModel.isBatteryOptimizationExempt.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.refreshBatteryOptimizationStatus() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsSection(title = "Device configuration") {
            var deviceName by remember(config.deviceName) { mutableStateOf(config.deviceName) }
            OutlinedTextField(
                value = deviceName,
                onValueChange = {
                    deviceName = it
                    viewModel.updateConfig { c -> c.copy(deviceName = it) }
                },
                label = { Text("Device name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer()
            DeviceTypeDropdown(
                selected = config.deviceType,
                onSelected = { viewModel.updateConfig { c -> c.copy(deviceType = it) } },
            )
        }

        SettingsSection(title = "Playback") {
            Text("Bitrate", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                GoLibrespotConfig.BITRATE_OPTIONS.forEach { option ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = config.bitrate == option,
                            onClick = { viewModel.updateConfig { c -> c.copy(bitrate = option) } },
                        )
                        Text("${option}kbps")
                    }
                }
            }
            Spacer()
            SwitchRow(
                title = "Volume normalization",
                subtitle = "Target -14 LUFS (ITU-R BS.1770)",
                checked = !config.normalisationDisabled,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(normalisationDisabled = !it) } },
            )
            if (!config.normalisationDisabled) {
                SwitchRow(
                    title = "Use album gain",
                    subtitle = "Off applies per-track gain instead",
                    checked = config.normalisationUseAlbumGain,
                    onCheckedChange = { viewModel.updateConfig { c -> c.copy(normalisationUseAlbumGain = it) } },
                )
                Text("Pregain: ${"%.1f".format(config.normalisationPregain)} dB", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = config.normalisationPregain,
                    onValueChange = { viewModel.updateConfig { c -> c.copy(normalisationPregain = it) } },
                    valueRange = -10f..10f,
                )
            }
            Spacer()
            SwitchRow(
                title = "Autoplay",
                subtitle = "Keep playing similar tracks when the queue ends",
                checked = !config.disableAutoplay,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(disableAutoplay = !it) } },
            )
        }

        SettingsSection(title = "Discovery & authentication") {
            SwitchRow(
                title = "Zeroconf / mDNS discovery",
                subtitle = "Show up as a speaker to Spotify apps on this network",
                checked = config.zeroconfEnabled,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(zeroconfEnabled = it) } },
            )
            Spacer()
            CredentialsTypeDropdown(
                selected = config.credentialsType,
                onSelected = { viewModel.updateConfig { c -> c.copy(credentialsType = it) } },
            )
            if (config.credentialsType == CredentialsType.SPOTIFY_TOKEN) {
                Spacer()
                var username by remember(config.spotifyTokenUsername) { mutableStateOf(config.spotifyTokenUsername) }
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        viewModel.updateConfig { c -> c.copy(spotifyTokenUsername = it) }
                    },
                    label = { Text("Spotify username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer()
                var token by remember(config.spotifyTokenAccessToken) { mutableStateOf(config.spotifyTokenAccessToken) }
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it
                        viewModel.updateConfig { c -> c.copy(spotifyTokenAccessToken = it) }
                    },
                    label = { Text("Cached access token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            }
            Spacer()
            SwitchRow(
                title = "Optimistic playback replies",
                subtitle = "Advanced: acknowledge play/pause/seek immediately instead of waiting on the audio backend",
                checked = config.optimisticPlaybackReplies,
                onCheckedChange = { viewModel.updateConfig { c -> c.copy(optimisticPlaybackReplies = it) } },
            )
        }

        SettingsSection(title = "App behavior") {
            SwitchRow(
                title = "Keep screen always on",
                checked = appPrefs.keepScreenOn,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(keepScreenOn = it) } },
            )
            SwitchRow(
                title = "Hold wake lock while running",
                subtitle = "Prevents the CPU from sleeping so playback doesn't stutter in the background",
                checked = appPrefs.holdWakeLock,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(holdWakeLock = it) } },
            )
            SwitchRow(
                title = "Autostart on boot",
                checked = appPrefs.autostartOnBoot,
                onCheckedChange = { viewModel.updateAppPreferences { p -> p.copy(autostartOnBoot = it) } },
            )
            Spacer()
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(
                    if (isBatteryExempt) Icons.Filled.CheckCircle else Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                )
                Spacer()
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("Battery optimization", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (isBatteryExempt) "Exempt — background playback won't be throttled" else "Not exempt — Android may kill the service in the background",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (!isBatteryExempt) {
                OutlinedButton(
                    onClick = { context.startActivity(BatteryOptimizationHelper.requestIgnoreBatteryOptimizationsIntent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Disable battery optimization for this app")
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer()
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Spacer() = androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DeviceTypeDropdown(selected: DeviceType, onSelected: (DeviceType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Device type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeviceType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.label) }, onClick = { onSelected(type); expanded = false })
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CredentialsTypeDropdown(selected: CredentialsType, onSelected: (CredentialsType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Authentication") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            CredentialsType.entries.forEach { type ->
                DropdownMenuItem(text = { Text(type.label) }, onClick = { onSelected(type); expanded = false })
            }
        }
    }
}
