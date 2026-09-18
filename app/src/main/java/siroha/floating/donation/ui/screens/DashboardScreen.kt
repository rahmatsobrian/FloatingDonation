package siroha.floating.donation.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import siroha.floating.donation.model.AppLayout
import siroha.floating.donation.model.AppSettings
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.model.OverlayType
import siroha.floating.donation.service.OverlayService
import siroha.floating.donation.ui.components.AppSwitch
import siroha.floating.donation.ui.components.GroupedItemPosition
import siroha.floating.donation.ui.components.GroupedListItem
import siroha.floating.donation.ui.components.GroupedListSection
import siroha.floating.donation.ui.components.getItemPosition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    overlays: List<OverlayConfig>,
    appLayouts: List<AppLayout>,
    settings: AppSettings,
    overlayService: OverlayService?,
    onAddOverlay: () -> Unit,
    onAddImageOverlay: () -> Unit,
    onEditOverlay: (OverlayConfig) -> Unit,
    onDeleteOverlay: (OverlayConfig) -> Unit,
    onDuplicateOverlay: (OverlayConfig) -> Unit,
    onToggleOverlay: (OverlayConfig) -> Unit,
    onToggleAllOverlays: (Boolean) -> Unit,
    onToggleBubble: (Boolean) -> Unit,
    onToggleEverything: (Boolean) -> Unit,
    onCustomizeOverlay: (OverlayConfig) -> Unit,
    onManageLayouts: (OverlayConfig) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var showAddMenu by remember { mutableStateOf(false) }
    var overlayToDelete by remember { mutableStateOf<OverlayConfig?>(null) }
    var expandedMenuId by remember { mutableStateOf<String?>(null) }
    var showClearExitDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Siroha")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                AnimatedVisibility(visible = showAddMenu) {
                    Column(horizontalAlignment = Alignment.End) {
                        FloatingActionButton(
                            onClick = {
                                showAddMenu = false
                                onAddImageOverlay()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Image, contentDescription = "Add Image Overlay")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        FloatingActionButton(
                            onClick = {
                                showAddMenu = false
                                onAddOverlay()
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Add Web Overlay")
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        if (overlays.size >= settings.maxOverlays) {
                            Toast.makeText(
                                context,
                                "Maximum ${settings.maxOverlays} overlays reached",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showAddMenu = !showAddMenu
                        }
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Overlay") }
                )
            }
        }
    ) { padding ->
        if (overlays.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No overlays yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add your first overlay to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item {
                    val activeCount = overlays.count { it.isActive }
                    // Switch "All Overlays" nyala kalau ADA overlay yang aktif
                    // (minimal 1), bukan cuma kalau semuanya aktif — jadi kalau
                    // user nyalain 1 overlay lewat list di bawah, switch ini
                    // ikut kelihatan ON sebagai indikator status, tapi nge-tap
                    // switch ini tetap start/stop SEMUA overlay sekaligus,
                    // bukan cuma menyalakan yang sudah aktif.
                    val anyOverlayOn = overlays.any { it.isActive }
                    // "Overlays + Bubble" sekarang juga longgar kayak "All
                    // Overlays": nyala kalau ADA overlay yang aktif (bukan
                    // harus semua) DAN bubble aktif.
                    val bubbleOn = settings.bubbleEnabled
                    val everythingOn = anyOverlayOn && bubbleOn

                    GroupedListSection(title = "Master Controls") {
                        MasterControlItem(
                            position = GroupedItemPosition.FIRST,
                            icon = Icons.Default.Layers,
                            title = "All Overlays",
                            subtitle = if (overlays.isEmpty())
                                "No overlay configured"
                            else
                                "$activeCount of ${overlays.size} active",
                            checked = anyOverlayOn,
                            enabled = overlays.isNotEmpty(),
                            onCheckedChange = onToggleAllOverlays
                        )
                        MasterControlItem(
                            position = GroupedItemPosition.MIDDLE,
                            icon = Icons.Default.RadioButtonChecked,
                            title = "Floating Bubble",
                            subtitle = if (bubbleOn) "Active" else "Off",
                            checked = bubbleOn,
                            enabled = true,
                            onCheckedChange = onToggleBubble
                        )
                        MasterControlItem(
                            position = GroupedItemPosition.LAST,
                            icon = Icons.Default.PowerSettingsNew,
                            title = "Overlays + Bubble",
                            subtitle = "Start/stop semuanya sekaligus",
                            checked = everythingOn,
                            enabled = overlays.isNotEmpty(),
                            onCheckedChange = onToggleEverything
                        )
                    }
                }

                item {
                    GroupedListSection(title = "Overlays") {
                        overlays.forEachIndexed { index, overlay ->
                            val position = getItemPosition(index, overlays.size)
                            val isActive = overlayService?.isOverlayActive(overlay.id) == true

                            GroupedListItem(
                                position = position,
                                onClick = {
                                    // Menggunakan fully qualified name untuk menghindari bentrok dengan ikon
                                    if (!android.provider.Settings.canDrawOverlays(context)) {
                                        val intent = Intent(
                                            android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                        return@GroupedListItem
                                    }
                                    onToggleOverlay(overlay)
                                }
                            ) {
                                // Icon dengan lingkaran warna (mengikuti gaya referensi)
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isActive)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (overlay.type == OverlayType.IMAGE)
                                            Icons.Default.Image else Icons.Default.Language,
                                        contentDescription = null,
                                        tint = if (isActive)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                // Name and status
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = overlay.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isActive) {
                                            buildString {
                                                append(if (overlay.isVisible) "Visible" else "Hidden")
                                                append(" • ")
                                                append(if (overlay.isLocked) "Locked" else "Unlocked")
                                            }
                                        } else {
                                            if (overlay.type == OverlayType.WEB)
                                                overlay.url.take(40) else "Image overlay"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isActive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Action buttons
                                if (isActive) {
                                    // Show/Hide
                                    IconButton(
                                        onClick = {
                                            if (overlay.isVisible) {
                                                overlayService?.hideOverlay(overlay.id)
                                            } else {
                                                overlayService?.showOverlay(overlay.id)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (overlay.isVisible)
                                                Icons.Default.Visibility
                                            else Icons.Default.VisibilityOff,
                                            contentDescription = if (overlay.isVisible) "Hide" else "Show",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    // Lock/Unlock
                                    IconButton(
                                        onClick = {
                                            if (overlay.isLocked) {
                                                overlayService?.unlockOverlay(overlay.id)
                                            } else {
                                                overlayService?.lockOverlay(overlay.id)
                                            }
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (overlay.isLocked)
                                                Icons.Default.Lock
                                            else Icons.Default.LockOpen,
                                            contentDescription = if (overlay.isLocked) "Unlock" else "Lock",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // More menu
                                Box {
                                    IconButton(
                                        onClick = { expandedMenuId = overlay.id },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = expandedMenuId == overlay.id,
                                        onDismissRequest = { expandedMenuId = null }
                                    ) {
                                        if (isActive) {
                                            DropdownMenuItem(
                                                text = { Text("Stop") },
                                                onClick = {
                                                    expandedMenuId = null
                                                    onToggleOverlay(overlay)
                                                },
                                                leadingIcon = { Icon(Icons.Default.Stop, null) }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Reset Position") },
                                                onClick = {
                                                    expandedMenuId = null
                                                    overlayService?.resetOverlayPosition(overlay.id)
                                                },
                                                leadingIcon = { Icon(Icons.Default.RestartAlt, null) }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text("Edit") },
                                            onClick = {
                                                expandedMenuId = null
                                                onEditOverlay(overlay)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Edit, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Customize") },
                                            onClick = {
                                                expandedMenuId = null
                                                onCustomizeOverlay(overlay)
                                            },
                                            leadingIcon = { Icon(Icons.Default.Tune, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("App Layouts") },
                                            onClick = {
                                                expandedMenuId = null
                                                onManageLayouts(overlay)
                                            },
                                            leadingIcon = { Icon(Icons.Default.ViewInAr, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Duplicate") },
                                            onClick = {
                                                expandedMenuId = null
                                                onDuplicateOverlay(overlay)
                                            },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, null) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            onClick = {
                                                expandedMenuId = null
                                                overlayToDelete = overlay
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete, null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(88.dp)) // FAB clearance
                }
            }
        }
    }

    // Delete confirmation dialog
    overlayToDelete?.let { overlay ->
        AlertDialog(
            onDismissRequest = { overlayToDelete = null },
            title = { Text("Delete Overlay") },
            text = { Text("Are you sure you want to delete \"${overlay.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteOverlay(overlay)
                        overlayToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { overlayToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear & Exit dialog
    if (showClearExitDialog) {
        AlertDialog(
            onDismissRequest = { showClearExitDialog = false },
            title = { Text("Clear & Exit") },
            text = { Text("This will stop all overlays, clear WebView cache, and reset runtime state. Your saved overlay configurations will be kept. Continue?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearExitDialog = false
                        overlayService?.clearAndExit()
                    }
                ) {
                    Text("Clear & Exit", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Satu baris di section "Master Controls" — lingkaran ikon (gaya sama dengan
 * item overlay) di kiri, AppSwitch (indikator centang/silang) di kanan.
 * Ini beneran start/stop (lewat callback yang diteruskan sampai ke
 * OverlayService), bukan cuma toggle show/hide visibility.
 */
@Composable
private fun MasterControlItem(
    position: GroupedItemPosition,
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    GroupedListItem(
        position = position,
        onClick = if (enabled) {
            { onCheckedChange(!checked) }
        } else null
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (checked)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (checked)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (checked)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        AppSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
