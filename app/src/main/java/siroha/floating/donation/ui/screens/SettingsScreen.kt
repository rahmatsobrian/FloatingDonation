package siroha.floating.donation.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BorderOuter
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import siroha.floating.donation.model.AppSettings
import siroha.floating.donation.ui.components.AppSwitch
import siroha.floating.donation.ui.components.GroupedItemPosition
import siroha.floating.donation.ui.components.GroupedListItem
import siroha.floating.donation.ui.components.GroupedListSection

/** Ikon leading kecil yang seragam untuk tiap item Settings, mengikuti pola yang sudah dipakai di "View Logs"/"Exit App". */
@Composable
private fun SettingLeadingIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.width(16.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChanged: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onExitApp: () -> Unit,
    onViewLogs: () -> Unit,
    scrollState: ScrollState = rememberScrollState()
) {
    var currentSettings by remember { mutableStateOf(settings) }
    var widthText by remember { mutableStateOf(settings.defaultWidth.toString()) }
    var heightText by remember { mutableStateOf(settings.defaultHeight.toString()) }

    fun update(newSettings: AppSettings) {
        currentSettings = newSettings
        onSettingsChanged(newSettings)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // Overlay Defaults
            GroupedListSection(title = "Overlay Defaults") {
                GroupedListItem(position = GroupedItemPosition.FIRST) {
                    SettingLeadingIcon(Icons.Filled.AspectRatio)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Default Size",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Width and height a new overlay uses when it's first created",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = widthText,
                                onValueChange = { text ->
                                    if (text.length <= 5 && text.all { it.isDigit() }) {
                                        widthText = text
                                        val value = text.toIntOrNull()
                                        if (value != null && value in 50..5000) {
                                            update(currentSettings.copy(defaultWidth = value))
                                        }
                                    }
                                },
                                label = { Text("Width (px)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = heightText,
                                onValueChange = { text ->
                                    if (text.length <= 5 && text.all { it.isDigit() }) {
                                        heightText = text
                                        val value = text.toIntOrNull()
                                        if (value != null && value in 50..5000) {
                                            update(currentSettings.copy(defaultHeight = value))
                                        }
                                    }
                                },
                                label = { Text("Height (px)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                GroupedListItem(position = GroupedItemPosition.MIDDLE) {
                    SettingLeadingIcon(Icons.Filled.Opacity)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default Opacity: ${(currentSettings.defaultOpacity * 100).toInt()}%")
                        Slider(
                            value = currentSettings.defaultOpacity,
                            onValueChange = { update(currentSettings.copy(defaultOpacity = it)) },
                            valueRange = 0.1f..1f
                        )
                        Text(
                            "Transparency applied to a new overlay by default",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                GroupedListItem(position = GroupedItemPosition.MIDDLE) {
                    SettingLeadingIcon(Icons.Filled.PinDrop)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Remember Position")
                        Text(
                            "Reopen each overlay at the same spot on screen where it was last left",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.rememberPosition,
                        onCheckedChange = { update(currentSettings.copy(rememberPosition = it)) }
                    )
                }
                GroupedListItem(position = GroupedItemPosition.LAST) {
                    SettingLeadingIcon(Icons.Filled.Animation)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Overlay Animation")
                        Text(
                            "Animate overlays when they appear, move, or close",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.overlayAnimation,
                        onCheckedChange = { update(currentSettings.copy(overlayAnimation = it)) }
                    )
                }
            }

            // WebView
            GroupedListSection(title = "WebView") {
                GroupedListItem(position = GroupedItemPosition.FIRST) {
                    SettingLeadingIcon(Icons.Filled.Code)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("JavaScript")
                        Text(
                            "Let pages loaded inside the overlay run JavaScript",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.javaScriptEnabled,
                        onCheckedChange = { update(currentSettings.copy(javaScriptEnabled = it)) }
                    )
                }
                GroupedListItem(position = GroupedItemPosition.MIDDLE) {
                    SettingLeadingIcon(Icons.Filled.PlayCircleOutline)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Media Playback")
                        Text(
                            "Allow audio and video in the overlay to start without a tap first",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.mediaPlaybackEnabled,
                        onCheckedChange = { update(currentSettings.copy(mediaPlaybackEnabled = it)) }
                    )
                }
                GroupedListItem(position = GroupedItemPosition.MIDDLE) {
                    SettingLeadingIcon(Icons.Filled.Storage)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Cache")
                        Text(
                            "Store loaded web content locally so pages reload faster",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.cacheEnabled,
                        onCheckedChange = { update(currentSettings.copy(cacheEnabled = it)) }
                    )
                }
                GroupedListItem(position = GroupedItemPosition.LAST) {
                    SettingLeadingIcon(Icons.Filled.Cookie)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Cookies")
                        Text(
                            "Allow sites opened in the overlay to save and read cookies",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.cookiesEnabled,
                        onCheckedChange = { update(currentSettings.copy(cookiesEnabled = it)) }
                    )
                }
            }

            // Floating Bubble
            GroupedListSection(title = "Floating Bubble") {
                GroupedListItem(position = GroupedItemPosition.FIRST) {
                    SettingLeadingIcon(Icons.Filled.RadioButtonChecked)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Size: ${currentSettings.bubbleConfig.size}dp")
                        Slider(
                            value = currentSettings.bubbleConfig.size.toFloat(),
                            onValueChange = {
                                update(currentSettings.copy(
                                    bubbleConfig = currentSettings.bubbleConfig.copy(size = it.toInt())
                                ))
                            },
                            valueRange = 32f..80f
                        )
                        Text(
                            "Diameter of the floating bubble handle used to open and move overlays",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                GroupedListItem(position = GroupedItemPosition.MIDDLE) {
                    SettingLeadingIcon(Icons.Filled.Opacity)
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Opacity: ${(currentSettings.bubbleConfig.opacity * 100).toInt()}%")
                        Slider(
                            value = currentSettings.bubbleConfig.opacity,
                            onValueChange = {
                                update(currentSettings.copy(
                                    bubbleConfig = currentSettings.bubbleConfig.copy(opacity = it)
                                ))
                            },
                            valueRange = 0.2f..1f
                        )
                        Text(
                            "Transparency of the bubble handle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                GroupedListItem(position = GroupedItemPosition.MIDDLE) {
                    SettingLeadingIcon(Icons.Filled.VisibilityOff)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Auto Hide")
                        Text(
                            "Fade the bubble out when it's left idle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.bubbleConfig.autoHide,
                        onCheckedChange = {
                            update(currentSettings.copy(
                                bubbleConfig = currentSettings.bubbleConfig.copy(autoHide = it)
                            ))
                        }
                    )
                }
                GroupedListItem(position = GroupedItemPosition.LAST) {
                    SettingLeadingIcon(Icons.Filled.BorderOuter)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Snap to Edge")
                        Text(
                            "Bubble jumps to the nearest screen edge after you drag and release it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.bubbleConfig.snapToEdge,
                        onCheckedChange = {
                            update(currentSettings.copy(
                                bubbleConfig = currentSettings.bubbleConfig.copy(snapToEdge = it)
                            ))
                        }
                    )
                }
            }

            // Notification
            GroupedListSection(title = "Notification") {
                GroupedListItem(position = GroupedItemPosition.ONLY) {
                    SettingLeadingIcon(Icons.Filled.Notifications)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Notification Controls")
                        Text(
                            "Show Show/Hide and Lock/Unlock action buttons on the persistent overlay notification",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.notificationControlsEnabled,
                        onCheckedChange = { update(currentSettings.copy(notificationControlsEnabled = it)) }
                    )
                }
            }

            // Performance
            GroupedListSection(title = "Performance") {
                GroupedListItem(position = GroupedItemPosition.FIRST) {
                    SettingLeadingIcon(Icons.Filled.Speed)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Hardware Acceleration")
                        Text(
                            "Use the GPU to render overlay content, smoother but uses more battery",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.hardwareAcceleration,
                        onCheckedChange = { update(currentSettings.copy(hardwareAcceleration = it)) }
                    )
                }
                GroupedListItem(position = GroupedItemPosition.MIDDLE) {
                    SettingLeadingIcon(Icons.Filled.Storage)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("WebView Cache")
                        Text(
                            "Keep a separate cache for overlay WebViews to reduce reload times",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.webViewCache,
                        onCheckedChange = { update(currentSettings.copy(webViewCache = it)) }
                    )
                }
                GroupedListItem(position = GroupedItemPosition.LAST) {
                    SettingLeadingIcon(Icons.Filled.MotionPhotosOff)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Reduce Animation")
                        Text(
                            "Turn off non-essential motion effects to save resources on low-end devices",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.reduceAnimation,
                        onCheckedChange = { update(currentSettings.copy(reduceAnimation = it)) }
                    )
                }
            }

            // Debug
            GroupedListSection(title = "Debug") {
                GroupedListItem(position = GroupedItemPosition.FIRST) {
                    SettingLeadingIcon(Icons.Filled.BugReport)
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Debug Logging")
                        Text(
                            "Record app activity to an in-app log buffer (max 500 entries)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AppSwitch(
                        checked = currentSettings.debugLogging,
                        onCheckedChange = { update(currentSettings.copy(debugLogging = it)) }
                    )
                }
                GroupedListItem(position = GroupedItemPosition.LAST, onClick = onViewLogs) {
                    Icon(
                        Icons.AutoMirrored.Filled.ListAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("View Logs")
                        Text(
                            "Open the recorded debug logs, with options to copy or clear them",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // App
            GroupedListSection(title = "App") {
                GroupedListItem(position = GroupedItemPosition.ONLY, onClick = onExitApp) {
                    Icon(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            "Exit App",
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            "Stop the app and close any active floating overlays",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
