package com.example.android_go_librespot.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.android_go_librespot.service.model.LogEntry
import com.example.android_go_librespot.service.model.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun LogLevel.color(): Color = when (this) {
    LogLevel.DEBUG -> Color(0xFF8D8D8D)
    LogLevel.INFO -> Color(0xFF4CAF50)
    LogLevel.WARN -> Color(0xFFFFA000)
    LogLevel.ERROR -> Color(0xFFE53935)
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
fun LogConsole(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var activeLevels by remember { mutableStateOf(setOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)) }
    val clipboard = LocalClipboardManager.current
    val visible = logs.filter { it.level in activeLevels }
    val listState = rememberLazyListState()
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.size - 1)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Log console", style = MaterialTheme.typography.titleMedium)
            Row {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(visible.joinToString("\n") { "[${it.level}] ${it.message}" }))
                }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy logs")
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear logs")
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogLevel.entries.forEach { level ->
                FilterChip(
                    selected = level in activeLevels,
                    onClick = {
                        activeLevels = if (level in activeLevels) activeLevels - level else activeLevels + level
                    },
                    label = { Text(level.name) },
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(visible) { entry ->
                Row {
                    Text(
                        text = timeFormat.format(Date(entry.timestampMillis)) + " ",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "${entry.level.name.take(4)} ",
                        color = entry.level.color(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = entry.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
