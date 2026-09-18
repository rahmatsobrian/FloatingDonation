package siroha.floating.donation.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import siroha.floating.donation.util.LogEntry
import siroha.floating.donation.util.LogLevel
import siroha.floating.donation.util.Logger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    debugLoggingEnabled: Boolean,
    onBack: () -> Unit
) {
    val entries = Logger.entries
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Logs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val text = entries.joinToString("\n") { entry ->
                                "${Logger.formatTime(entry.timestamp)} [${entry.level}] ${entry.message}"
                            }
                            clipboard.setText(AnnotatedString(text))
                        },
                        enabled = entries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy logs")
                    }
                    IconButton(
                        onClick = { Logger.clear() },
                        enabled = entries.isNotEmpty()
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear logs")
                    }
                }
            )
        }
    ) { padding ->
        when {
            !debugLoggingEnabled -> EmptyState(
                padding = padding,
                title = "Debug Logging is off",
                subtitle = "Turn it on in Settings > Debug to start capturing logs here."
            )

            entries.isEmpty() -> EmptyState(
                padding = padding,
                title = "No logs yet",
                subtitle = "Logs will show up here as the app and overlays run."
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(entries.asReversed()) { entry ->
                    LogRow(entry)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(padding: androidx.compose.foundation.layout.PaddingValues, title: String, subtitle: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> Color(0xFFFFA726)
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
        LogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "${Logger.formatTime(entry.timestamp)}  ${entry.level.name}",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.7f)
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}
