package com.example.android_go_librespot.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.android_go_librespot.service.model.LogEntry
import com.example.android_go_librespot.service.model.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun LogLevel.color(): Color = when (this) {
    LogLevel.DEBUG -> Color(0xFF9E9E9E)
    LogLevel.INFO -> Color(0xFF66BB6A)
    LogLevel.WARN -> Color(0xFFFFB300)
    LogLevel.ERROR -> Color(0xFFEF5350)
}

// The console is a fixed black "terminal", not a theme surface: readable in both light and
// dark app themes without switching, and it's the look a log console is expected to have.
private val ConsoleBackground = Color.Black
private val ConsoleTimestampColor = Color(0xFF8A8A8A)
private val ConsoleMessageColor = Color(0xFFE0E0E0)

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
fun LogConsole(
    logs: List<LogEntry>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Collapsed by default: the log console is a diagnostic tool, not something that needs to
    // occupy Dashboard space on every visit -- only expand it when actually chasing something.
    var expanded by remember { mutableStateOf(false) }
    var activeLevels by remember { mutableStateOf(setOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)) }
    val clipboard = LocalClipboardManager.current
    val visible = logs.filter { it.level in activeLevels }
    val listState = rememberLazyListState()
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty()) listState.animateScrollToItem(visible.size - 1)
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse log console" else "Expand log console",
                )
                Text("Log console", style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
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
        }

        AnimatedVisibility(visible = expanded) {
            Column {
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
                            label = {
                                Text(
                                    level.name,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(ConsoleBackground, RoundedCornerShape(12.dp)),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(visible) { entry ->
                        Row {
                            Text(
                                text = timeFormat.format(Date(entry.timestampMillis)) + " ",
                                color = ConsoleTimestampColor,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = "${entry.level.name.take(4)} ",
                                color = entry.level.color(),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Text(
                                text = entry.message,
                                color = ConsoleMessageColor,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
