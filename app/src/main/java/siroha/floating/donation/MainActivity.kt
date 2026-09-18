package siroha.floating.donation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import siroha.floating.donation.model.AppLayout
import siroha.floating.donation.model.AppSettings
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.model.OverlayType
import siroha.floating.donation.service.OverlayService
import siroha.floating.donation.storage.OverlayStorage
import siroha.floating.donation.ui.screens.AddEditOverlayDialog
import siroha.floating.donation.ui.screens.AddImageOverlayDialog
import siroha.floating.donation.ui.screens.AppLayoutDialog
import siroha.floating.donation.ui.screens.AppLayoutsScreen
import siroha.floating.donation.ui.screens.DashboardScreen
import siroha.floating.donation.ui.screens.LogViewerScreen
import siroha.floating.donation.ui.screens.OnboardingScreen
import siroha.floating.donation.ui.screens.OverlayCustomizationDialog
import siroha.floating.donation.ui.screens.SettingsScreen
import siroha.floating.donation.ui.theme.SirohaTheme
import siroha.floating.donation.util.Logger
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var overlayService: OverlayService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? OverlayService.LocalBinder
            overlayService = localBinder?.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            overlayService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val storage = OverlayStorage.getInstance(this)

        setContent {
            SirohaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SirohaApp(storage = storage, activity = this)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to service if running
        val intent = Intent(this, OverlayService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
            overlayService = null
        }
    }

    fun getOverlayService(): OverlayService? = overlayService

    fun startAndBindService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
}

enum class Screen {
    ONBOARDING, DASHBOARD, SETTINGS, APP_LAYOUTS, LOGS
}

@Composable
fun SirohaApp(
    storage: OverlayStorage,
    activity: MainActivity
) {
    val scope = rememberCoroutineScope()
    val overlaysFlow = remember(storage) { storage.getOverlays() }
    val appLayoutsFlow = remember(storage) { storage.getAppLayouts() }
    val settingsFlow = remember(storage) { storage.getSettings() }
    val overlays by overlaysFlow.collectAsState(initial = emptyList())
    val appLayouts by appLayoutsFlow.collectAsState(initial = emptyList())
    val settings by settingsFlow.collectAsState(initial = AppSettings())

    var currentScreen by remember { mutableStateOf(
        if (settings.onboardingCompleted) Screen.DASHBOARD else Screen.ONBOARDING
    ) }
    // Hoisted here (instead of inside SettingsScreen) so the scroll position survives
    // navigating away to the Logs screen and back — SettingsScreen itself is removed
    // from composition while Screen.LOGS is showing, which would otherwise reset it.
    val settingsScrollState = rememberScrollState()
    var overlayService by remember { mutableStateOf<OverlayService?>(null) }

    // Dialog states
    var showAddOverlayDialog by remember { mutableStateOf(false) }
    var showAddImageDialog by remember { mutableStateOf(false) }
    var overlayToEdit by remember { mutableStateOf<OverlayConfig?>(null) }
    var overlayToCustomize by remember { mutableStateOf<OverlayConfig?>(null) }
    var overlayForLayouts by remember { mutableStateOf<OverlayConfig?>(null) }
    var showAddLayoutDialog by remember { mutableStateOf(false) }
    var layoutToEdit by remember { mutableStateOf<AppLayout?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    // Update service reference
    DisposableEffect(Unit) {
        val checkService = {
            overlayService = activity.getOverlayService()
        }
        checkService()
        onDispose { }
    }

    // Update logger debug setting
    Logger.debugEnabled = settings.debugLogging

    // Update screen if onboarding status changes
    if (settings.onboardingCompleted && currentScreen == Screen.ONBOARDING) {
        currentScreen = Screen.DASHBOARD
    }

    // Back press: navigate to the previous screen instead of exiting the app outright.
    // Only the root (Dashboard) screen should ever trigger an exit, and even then
    // only after the user confirms via the dialog below.
    BackHandler(enabled = currentScreen == Screen.SETTINGS) {
        currentScreen = Screen.DASHBOARD
    }
    BackHandler(enabled = currentScreen == Screen.APP_LAYOUTS) {
        overlayForLayouts = null
        currentScreen = Screen.DASHBOARD
    }
    BackHandler(enabled = currentScreen == Screen.LOGS) {
        currentScreen = Screen.SETTINGS
    }
    BackHandler(enabled = currentScreen == Screen.DASHBOARD) {
        showExitDialog = true
    }

    when (currentScreen) {
        Screen.ONBOARDING -> {
            OnboardingScreen(
                onComplete = {
                    scope.launch {
                        storage.saveSettings(settings.copy(onboardingCompleted = true))
                    }
                    currentScreen = Screen.DASHBOARD
                }
            )
        }

        Screen.DASHBOARD -> {
            DashboardScreen(
                overlays = overlays,
                appLayouts = appLayouts,
                settings = settings,
                overlayService = activity.getOverlayService(),
                onAddOverlay = { showAddOverlayDialog = true },
                onAddImageOverlay = { showAddImageDialog = true },
                onEditOverlay = { overlay ->
                    overlayToEdit = overlay
                },
                onDeleteOverlay = { overlay ->
                    Logger.d("DELETE_OVERLAY: start id=${overlay.id}, name=${overlay.name}")
                    scope.launch {
                        // persistInactive = false: this overlay is being deleted right
                        // after, so don't let stopOverlay's own async storage write
                        // race with storage.removeOverlay below and resurrect it
                        // (see the doc comment on OverlayService.stopOverlay).
                        activity.getOverlayService()?.stopOverlay(overlay.id, persistInactive = false)
                        storage.removeOverlay(overlay.id)
                        // Also remove related app layouts
                        val relatedLayouts = appLayouts.filter { it.overlayId == overlay.id }
                        Logger.d("DELETE_OVERLAY: removing ${relatedLayouts.size} related layout(s) for id=${overlay.id}")
                        relatedLayouts.forEach { storage.removeAppLayout(it.id) }
                        Logger.d("DELETE_OVERLAY: done id=${overlay.id}")
                    }
                },
                onDuplicateOverlay = { overlay ->
                    scope.launch {
                        val duplicate = overlay.copy(
                            id = UUID.randomUUID().toString(),
                            name = "${overlay.name} (Copy)",
                            isActive = false,
                            createdAt = System.currentTimeMillis()
                        )
                        storage.addOverlay(duplicate)
                    }
                },
                onToggleOverlay = { overlay ->
                    scope.launch {
                        val service = activity.getOverlayService()
                        if (service != null && service.isOverlayActive(overlay.id)) {
                            service.stopOverlay(overlay.id)
                            storage.updateOverlay(overlay.copy(isActive = false))
                        } else {
                            if (!Settings.canDrawOverlays(activity)) {
                                return@launch
                            }
                            activity.startAndBindService()
                            // Small delay to let service bind
                            kotlinx.coroutines.delay(300)
                            val svc = activity.getOverlayService()
                            if (svc != null) {
                                val activeOverlay = overlay.copy(isActive = true)
                                svc.startOverlay(activeOverlay)
                                storage.updateOverlay(activeOverlay)
                            }
                        }
                    }
                },
                onCustomizeOverlay = { overlay ->
                    overlayToCustomize = overlay
                },
                onToggleAllOverlays = { turnOn ->
                    Logger.d("TOGGLE_ALL_OVERLAYS: turnOn=$turnOn")
                    scope.launch {
                        if (turnOn) {
                            if (!Settings.canDrawOverlays(activity)) {
                                return@launch
                            }
                            activity.startAndBindService()
                            kotlinx.coroutines.delay(300)
                            val svc = activity.getOverlayService()
                            if (svc != null) {
                                overlays.forEach { overlay ->
                                    val active = overlay.copy(isActive = true)
                                    svc.startOverlay(active)
                                    storage.updateOverlay(active)
                                }
                            }
                        } else {
                            val svc = activity.getOverlayService()
                            overlays.filter { it.isActive }.forEach { overlay ->
                                svc?.stopOverlay(overlay.id)
                                storage.updateOverlay(overlay.copy(isActive = false))
                            }
                        }
                    }
                },
                onToggleBubble = { turnOn ->
                    Logger.d("TOGGLE_BUBBLE: turnOn=$turnOn")
                    scope.launch {
                        if (turnOn) {
                            if (!Settings.canDrawOverlays(activity)) {
                                return@launch
                            }
                            activity.startAndBindService()
                            kotlinx.coroutines.delay(300)
                            activity.getOverlayService()?.setBubbleEnabled(true)
                        } else {
                            val svc = activity.getOverlayService()
                            if (svc != null) {
                                svc.setBubbleEnabled(false)
                            } else {
                                // Service tidak jalan sama sekali (mis. baru dibuka
                                // lagi habis force-close) -> tetap persist pilihan
                                // user supaya konsisten kalau service dinyalakan lagi
                                storage.saveSettings(settings.copy(bubbleEnabled = false))
                            }
                        }
                    }
                },
                onToggleEverything = { turnOn ->
                    Logger.d("TOGGLE_EVERYTHING: turnOn=$turnOn")
                    scope.launch {
                        if (turnOn) {
                            if (!Settings.canDrawOverlays(activity)) {
                                return@launch
                            }
                            activity.startAndBindService()
                            kotlinx.coroutines.delay(300)
                            val svc = activity.getOverlayService()
                            if (svc != null) {
                                svc.setBubbleEnabled(true)
                                overlays.forEach { overlay ->
                                    val active = overlay.copy(isActive = true)
                                    svc.startOverlay(active)
                                    storage.updateOverlay(active)
                                }
                            }
                        } else {
                            val svc = activity.getOverlayService()
                            if (svc != null) {
                                svc.setBubbleEnabled(false)
                            } else {
                                storage.saveSettings(settings.copy(bubbleEnabled = false))
                            }
                            overlays.filter { it.isActive }.forEach { overlay ->
                                svc?.stopOverlay(overlay.id)
                                storage.updateOverlay(overlay.copy(isActive = false))
                            }
                        }
                    }
                },
                onManageLayouts = { overlay ->
                    overlayForLayouts = overlay
                    currentScreen = Screen.APP_LAYOUTS
                },
                onOpenSettings = {
                    currentScreen = Screen.SETTINGS
                }
            )
        }

        Screen.SETTINGS -> {
            SettingsScreen(
                settings = settings,
                onSettingsChanged = { newSettings ->
                    scope.launch {
                        storage.saveSettings(newSettings)
                    }
                },
                onBack = { currentScreen = Screen.DASHBOARD },
                onExitApp = { showExitDialog = true },
                onViewLogs = { currentScreen = Screen.LOGS },
                scrollState = settingsScrollState
            )
        }

        Screen.LOGS -> {
            LogViewerScreen(
                debugLoggingEnabled = settings.debugLogging,
                onBack = { currentScreen = Screen.SETTINGS }
            )
        }

        Screen.APP_LAYOUTS -> {
            val overlay = overlayForLayouts
            if (overlay != null) {
                val filteredLayouts = appLayouts.filter { it.overlayId == overlay.id }
                AppLayoutsScreen(
                    overlay = overlay,
                    layouts = filteredLayouts,
                    onAddLayout = { showAddLayoutDialog = true },
                    onEditLayout = { layout -> layoutToEdit = layout },
                    onDeleteLayout = { layout ->
                        scope.launch { storage.removeAppLayout(layout.id) }
                    },
                    onDuplicateLayout = { layout ->
                        scope.launch {
                            val dup = layout.copy(
                                id = UUID.randomUUID().toString(),
                                appName = "${layout.appName} (Copy)"
                            )
                            storage.addAppLayout(dup)
                        }
                    },
                    onToggleLayout = { layout, enabled ->
                        scope.launch {
                            storage.updateAppLayout(layout.copy(isEnabled = enabled))
                        }
                    },
                    onBack = {
                        overlayForLayouts = null
                        currentScreen = Screen.DASHBOARD
                    }
                )
            }
        }
    }

    // ---- Dialogs ----

    if (showAddOverlayDialog) {
        AddEditOverlayDialog(
            onSave = { config ->
                scope.launch {
                    val withDefaults = config.copy(
                        width = settings.defaultWidth,
                        height = settings.defaultHeight,
                        opacity = settings.defaultOpacity,
                        positionX = settings.defaultPositionX,
                        positionY = settings.defaultPositionY
                    )
                    storage.addOverlay(withDefaults)
                }
                showAddOverlayDialog = false
            },
            onDismiss = { showAddOverlayDialog = false }
        )
    }

    if (showAddImageDialog) {
        AddImageOverlayDialog(
            onSave = { config ->
                scope.launch {
                    val withDefaults = config.copy(
                        width = settings.defaultWidth,
                        height = settings.defaultHeight,
                        opacity = settings.defaultOpacity,
                        positionX = settings.defaultPositionX,
                        positionY = settings.defaultPositionY
                    )
                    storage.addOverlay(withDefaults)
                }
                showAddImageDialog = false
            },
            onDismiss = { showAddImageDialog = false }
        )
    }

    overlayToEdit?.let { overlay ->
        if (overlay.type == OverlayType.IMAGE) {
            AddImageOverlayDialog(
                overlay = overlay,
                onSave = { updated ->
                    scope.launch {
                        storage.updateOverlay(updated)
                        activity.getOverlayService()?.updateOverlay(updated)
                    }
                    overlayToEdit = null
                },
                onDismiss = { overlayToEdit = null }
            )
        } else {
            AddEditOverlayDialog(
                overlay = overlay,
                onSave = { updated ->
                    scope.launch {
                        storage.updateOverlay(updated)
                        activity.getOverlayService()?.updateOverlay(updated)
                    }
                    overlayToEdit = null
                },
                onDismiss = { overlayToEdit = null }
            )
        }
    }

    overlayToCustomize?.let { overlay ->
        OverlayCustomizationDialog(
            config = overlay,
            onSave = { updated ->
                scope.launch {
                    storage.updateOverlay(updated)
                    activity.getOverlayService()?.updateOverlay(updated)
                }
                overlayToCustomize = null
            },
            onDismiss = { overlayToCustomize = null }
        )
    }

    if (showAddLayoutDialog && overlayForLayouts != null) {
        AppLayoutDialog(
            overlayId = overlayForLayouts!!.id,
            onSave = { layout ->
                scope.launch { storage.addAppLayout(layout) }
                showAddLayoutDialog = false
            },
            onDismiss = { showAddLayoutDialog = false }
        )
    }

    layoutToEdit?.let { layout ->
        AppLayoutDialog(
            layout = layout,
            overlayId = layout.overlayId,
            onSave = { updated ->
                scope.launch { storage.updateAppLayout(updated) }
                layoutToEdit = null
            },
            onDelete = {
                scope.launch { storage.removeAppLayout(layout.id) }
                layoutToEdit = null
            },
            onDismiss = { layoutToEdit = null }
        )
    }

    // Exit confirmation dialog. Confirming sends the app to the background
    // (moveTaskToBack) rather than finish()-ing the Activity, so any active
    // floating overlays / the foreground service keep running normally.
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Keluar Aplikasi") },
            text = {
                Text(
                    if (overlays.any { it.isActive })
                        "Overlay yang aktif akan tetap berjalan di background. Keluar dari aplikasi?"
                    else
                        "Keluar dari aplikasi?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        activity.moveTaskToBack(true)
                    }
                ) {
                    Text("Keluar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
