package io.github.seky443.librething.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.seky443.librething.R
import io.github.seky443.librething.service.model.LogEntry
import io.github.seky443.librething.service.model.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// internal: the simplified dashboard's console long-press options sheet reuses the same
// level->color mapping for its filter chips.
internal fun LogLevel.color(): Color = when (this) {
    LogLevel.DEBUG -> Color(0xFF9E9E9E)
    LogLevel.INFO -> Color(0xFF66BB6A)
    LogLevel.WARN -> Color(0xFFFFB300)
    LogLevel.ERROR -> Color(0xFFEF5350)
}

/** A small filled dot in [LogLevel.color], used as a level filter [FilterChip]'s leadingIcon.
 * These colors are tuned for legibility on the console's own pure-black background -- reusing
 * one of them as the chip *text* color instead would fight FilterChip's own selected/unselected
 * content color and can read poorly against the app's actual (light or dark) theme surface, so
 * the color-coding here stays confined to this dot and the label text keeps FilterChip's normal,
 * theme-correct color. */
@Composable
internal fun LogLevelDot(level: LogLevel, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).background(level.color(), CircleShape))
}

// The console is a fixed black "terminal", not a theme surface: readable in both light and
// dark app themes without switching, and it's the look a log console is expected to have.
// ConsoleBackground is internal: the simplified dashboard's card reuses it so switching to its
// console view looks like the same terminal, not a differently-tinted lookalike.
internal val ConsoleBackground = Color.Black
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

    Column(modifier = modifier) {
        Row(
            // Fixed height regardless of expanded state: the trailing copy/clear IconButtons
            // (48dp minimum touch target) only render while expanded, and without a floor here
            // the row would shrink to the plain Icon+Text height while collapsed, making the
            // header visibly resize when toggled.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(if (expanded) R.string.content_desc_collapse_log_console else R.string.content_desc_expand_log_console),
                )
                Text(stringResource(R.string.log_console_title), style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                Row {
                    IconButton(onClick = {
                        clipboard.setText(AnnotatedString(visible.joinToString("\n") { "[${it.level}] ${it.message}" }))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.content_desc_copy_logs))
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = stringResource(R.string.content_desc_clear_logs))
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
                            leadingIcon = { LogLevelDot(level) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                ConsoleLogList(
                    logs = visible,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .background(ConsoleBackground, RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/** The raw scrolling log list, factored out of [LogConsole] so the simplified dashboard's
 * card (see [SimpleDashboardScreen]) can embed the exact same rendering when toggled to its
 * console view, without also pulling in the header/filter-chip chrome around it. */
@Composable
internal fun ConsoleLogList(
    logs: List<LogEntry>,
    modifier: Modifier = Modifier,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
) {
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(logs) { entry -> LogEntryRow(entry) }
    }
}

/** One log entry, tag+time on their own line and message on the next -- shared by
 * [ConsoleLogList]'s full scrolling view and [io.github.seky443.librething.ui.dashboard.TransitionConsolePeek]'s
 * lightweight static peek behind the cover-skip transition, so the two render identically
 * instead of drifting apart. A long message no longer competes with the tag/timestamp prefix
 * for line width, and every tag lines up at a fixed 4 characters (DEBUG/ERROR truncate,
 * INFO/WARN already fit). */
@Composable
internal fun LogEntryRow(entry: LogEntry, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row {
            Text(
                text = "[${entry.level.name.take(4)}] ",
                color = entry.level.color(),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = "[${timeFormat.format(Date(entry.timestampMillis))}]",
                color = ConsoleTimestampColor,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            text = entry.message,
            color = ConsoleMessageColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
