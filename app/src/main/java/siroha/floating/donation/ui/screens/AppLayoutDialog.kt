package siroha.floating.donation.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import siroha.floating.donation.model.AppLayout
import siroha.floating.donation.ui.components.AppSwitch
import siroha.floating.donation.util.InstalledAppInfo
import siroha.floating.donation.util.InstalledApps

private fun drawableToImageBitmapOrNull(icon: Drawable?): ImageBitmap? {
    if (icon == null) return null
    return try {
        icon.toBitmap().asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

@Composable
fun AppLayoutDialog(
    layout: AppLayout? = null,
    overlayId: String,
    onSave: (AppLayout) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isEdit = layout != null
    val context = LocalContext.current

    var packageName by remember { mutableStateOf(layout?.packageName ?: "") }
    var appName by remember { mutableStateOf(layout?.appName ?: "") }
    var positionX by remember { mutableIntStateOf(layout?.positionX ?: 100) }
    var positionY by remember { mutableIntStateOf(layout?.positionY ?: 100) }
    var width by remember { mutableIntStateOf(layout?.width ?: 400) }
    var height by remember { mutableIntStateOf(layout?.height ?: 300) }
    var opacity by remember { mutableFloatStateOf(layout?.opacity ?: 1.0f) }
    var isEnabled by remember { mutableStateOf(layout?.isEnabled ?: true) }
    var packageError by remember { mutableStateOf<String?>(null) }
    var appNameError by remember { mutableStateOf<String?>(null) }

    var selectedIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    var showManualEntry by remember { mutableStateOf(false) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoadingApps by remember { mutableStateOf(true) }

    // Load the launchable app list once, off the main thread — this is a
    // PackageManager IPC call and can take a noticeable moment on devices
    // with a lot of apps installed.
    LaunchedEffect(Unit) {
        installedApps = withContext(Dispatchers.IO) { InstalledApps.listLaunchable(context) }
        isLoadingApps = false
    }

    // Best-effort icon for a pre-filled package (edit mode, or a layout that
    // was entered manually before this picker existed) that isn't in the
    // freshly loaded list yet.
    LaunchedEffect(packageName, installedApps) {
        if (packageName.isNotBlank() && selectedIcon == null) {
            val fromList = installedApps.firstOrNull { it.packageName == packageName }?.icon
            val drawable = fromList ?: withContext(Dispatchers.IO) {
                InstalledApps.loadIcon(context, packageName)
            }
            selectedIcon = drawableToImageBitmapOrNull(drawable)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Edit App Layout" else "Add App Layout") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "App",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = if (appNameError != null || packageError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            appSearchQuery = ""
                            showAppPicker = true
                        }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = selectedIcon
                    if (icon != null) {
                        Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            Icons.Filled.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (appName.isBlank()) "Tap to choose an app" else appName,
                            color = if (appName.isBlank()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (packageName.isNotBlank()) {
                            Text(
                                packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                (appNameError ?: packageError)?.let { message ->
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                TextButton(onClick = { showManualEntry = !showManualEntry }) {
                    Text(if (showManualEntry) "Hide manual entry" else "App not in the list? Enter it manually")
                }

                if (showManualEntry) {
                    OutlinedTextField(
                        value = appName,
                        onValueChange = {
                            appName = it
                            appNameError = null
                        },
                        label = { Text("App Name") },
                        placeholder = { Text("e.g., Genshin Impact") },
                        isError = appNameError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = packageName,
                        onValueChange = {
                            packageName = it
                            packageError = null
                            selectedIcon = null
                        },
                        label = { Text("Package Name") },
                        placeholder = { Text("e.g., com.miHoYo.GenshinImpact") },
                        isError = packageError != null,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Size", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = width.toString(),
                        onValueChange = { width = it.toIntOrNull() ?: width },
                        label = { Text("Width") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = height.toString(),
                        onValueChange = { height = it.toIntOrNull() ?: height },
                        label = { Text("Height") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Position", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = positionX.toString(),
                        onValueChange = { positionX = it.toIntOrNull() ?: positionX },
                        label = { Text("X") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = positionY.toString(),
                        onValueChange = { positionY = it.toIntOrNull() ?: positionY },
                        label = { Text("Y") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Opacity: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enabled")
                    AppSwitch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                }

                if (isEdit && onDelete != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete Layout", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var hasError = false
                    if (packageName.isBlank()) {
                        packageError = "Package name cannot be empty"
                        hasError = true
                    }
                    if (appName.isBlank()) {
                        appNameError = "App name cannot be empty"
                        hasError = true
                    }
                    if (!hasError) {
                        val result = (layout ?: AppLayout()).copy(
                            overlayId = overlayId,
                            packageName = packageName.trim(),
                            appName = appName.trim(),
                            positionX = positionX.coerceAtLeast(0),
                            positionY = positionY.coerceAtLeast(0),
                            width = width.coerceAtLeast(100),
                            height = height.coerceAtLeast(100),
                            opacity = opacity,
                            isEnabled = isEnabled
                        )
                        onSave(result)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            isLoading = isLoadingApps,
            searchQuery = appSearchQuery,
            onSearchQueryChange = { appSearchQuery = it },
            onAppSelected = { app ->
                appName = app.appName
                packageName = app.packageName
                appNameError = null
                packageError = null
                selectedIcon = drawableToImageBitmapOrNull(app.icon)
                showAppPicker = false
                appSearchQuery = ""
            },
            onDismiss = {
                showAppPicker = false
                appSearchQuery = ""
            }
        )
    }
}

@Composable
private fun AppPickerDialog(
    apps: List<InstalledAppInfo>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onAppSelected: (InstalledAppInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = remember(apps, searchQuery) {
        if (searchQuery.isBlank()) {
            apps
        } else {
            apps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose App") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    filtered.isEmpty() -> {
                        Text(
                            "No apps found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(filtered, key = { it.packageName }) { app ->
                                val bitmap = remember(app.packageName) {
                                    drawableToImageBitmapOrNull(app.icon)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAppSelected(app) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else {
                                        Icon(
                                            Icons.Filled.Apps,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName)
                                        Text(
                                            app.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
