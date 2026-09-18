package siroha.floating.donation.ui.screens

import android.content.Intent
import android.graphics.drawable.Drawable
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import siroha.floating.donation.model.AppLayout
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.ui.components.AppSwitch
import siroha.floating.donation.ui.components.GroupedListItem
import siroha.floating.donation.ui.components.GroupedListSection
import siroha.floating.donation.ui.components.getItemPosition
import siroha.floating.donation.util.ForegroundAppWatcher
import siroha.floating.donation.util.InstalledApps

private fun drawableToImageBitmapOrNull(icon: Drawable?): ImageBitmap? {
    if (icon == null) return null
    return try {
        icon.toBitmap().asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

/**
 * Ikon aplikasi untuk satu baris "Configured Apps". Sebelumnya tidak ada ikon
 * sama sekali di sini (beda dengan "Choose App" picker di AddEditOverlay/
 * AppLayoutDialog yang sudah menampilkan ikon) — jadi tiap layout diambil
 * ikonnya sendiri lewat PackageManager, di-cache dengan `remember` per
 * packageName supaya tidak query ulang tiap recomposition/scroll.
 */
@Composable
private fun AppLayoutIcon(packageName: String) {
    val context = LocalContext.current
    var icon by remember(packageName) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(packageName) {
        val drawable = withContext(Dispatchers.IO) {
            InstalledApps.loadIcon(context, packageName)
        }
        icon = drawableToImageBitmapOrNull(drawable)
    }

    val loadedIcon = icon
    if (loadedIcon != null) {
        Image(
            bitmap = loadedIcon,
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
    } else {
        Icon(
            Icons.Default.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp)
        )
    }
}

/**
 * Peringatan + tombol pintas ke Settings kalau izin "Usage Access" belum
 * aktif — tanpa izin ini, [ForegroundAppWatcher] tidak pernah tahu aplikasi
 * apa yang sedang di depan, jadi App Layout yang dikonfigurasi di sini TIDAK
 * AKAN PERNAH otomatis diterapkan (overlay tetap diam di layout Default).
 * Ditaruh di atas daftar supaya kelihatan baik saat daftar masih kosong
 * maupun sudah terisi.
 */
@Composable
private fun UsageAccessBanner() {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Izin \"Usage Access\" belum aktif",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Tanpa izin ini, overlay tidak bisa mendeteksi aplikasi apa yang " +
                    "sedang dibuka, jadi App Layout di bawah tidak akan otomatis " +
                    "diterapkan saat kamu pindah aplikasi.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }) {
                    Text("Aktifkan Izin")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLayoutsScreen(
    overlay: OverlayConfig,
    layouts: List<AppLayout>,
    onAddLayout: () -> Unit,
    onEditLayout: (AppLayout) -> Unit,
    onDeleteLayout: (AppLayout) -> Unit,
    onDuplicateLayout: (AppLayout) -> Unit,
    onToggleLayout: (AppLayout, Boolean) -> Unit,
    onBack: () -> Unit
) {
    var layoutToDelete by remember { mutableStateOf<AppLayout?>(null) }
    val context = LocalContext.current
    // Layar ini dibongkar-pasang penuh tiap kali dinavigasi ke/dari (bukan
    // dipertahankan di back-stack), jadi `remember` di sini otomatis
    // mengecek ulang tiap kali user membuka layar ini lagi — termasuk sehabis
    // balik dari Settings setelah mengaktifkan izinnya.
    val hasUsageAccess = remember { ForegroundAppWatcher.hasUsageAccess(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Layouts: ${overlay.name}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddLayout,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Layout") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!hasUsageAccess) {
                UsageAccessBanner()
            }
            if (layouts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No app layouts",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add layouts for specific apps\nto customize overlay position per app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    GroupedListSection(title = "Configured Apps") {
                        layouts.forEachIndexed { index, layout ->
                            val position = getItemPosition(index, layouts.size)

                            GroupedListItem(
                                position = position,
                                onClick = { onEditLayout(layout) }
                            ) {
                                AppLayoutIcon(packageName = layout.packageName)

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = layout.appName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${layout.packageName} • ${layout.width}x${layout.height} • (${layout.positionX}, ${layout.positionY})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                AppSwitch(
                                    checked = layout.isEnabled,
                                    onCheckedChange = { onToggleLayout(layout, it) }
                                )

                                var menuExpanded by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { menuExpanded = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.MoreVert,
                                            contentDescription = "More options",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Duplicate") },
                                            leadingIcon = {
                                                Icon(Icons.Default.ContentCopy, contentDescription = null)
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                onDuplicateLayout(layout)
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Delete") },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            onClick = {
                                                menuExpanded = false
                                                layoutToDelete = layout
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(88.dp))
                }
            }
        }
    }

    layoutToDelete?.let { layout ->
        AlertDialog(
            onDismissRequest = { layoutToDelete = null },
            title = { Text("Delete Layout") },
            text = { Text("Delete layout for \"${layout.appName}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteLayout(layout)
                        layoutToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { layoutToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
