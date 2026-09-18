package siroha.floating.donation.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Binder
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import siroha.floating.donation.R
import siroha.floating.donation.model.AppLayout
import siroha.floating.donation.model.BubbleConfig
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.model.OverlayStatus
import siroha.floating.donation.overlay.AddOverlayPanel
import siroha.floating.donation.overlay.BubbleMenu
import siroha.floating.donation.overlay.BubbleMenuAction
import siroha.floating.donation.overlay.BubbleSettingsPanel
import siroha.floating.donation.overlay.FloatingBubble
import siroha.floating.donation.overlay.OverlayCustomizationPanel
import siroha.floating.donation.overlay.OverlayWindow
import siroha.floating.donation.storage.OverlayStorage
import siroha.floating.donation.util.ForegroundAppWatcher
import siroha.floating.donation.util.Logger

class OverlayService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var storage: OverlayStorage
    private lateinit var notificationController: NotificationController

    private val overlayWindows = mutableMapOf<String, OverlayWindow>()
    private var floatingBubble: FloatingBubble? = null
    private var bubbleConfig = BubbleConfig()
    // Independen dari jumlah overlay aktif — mencerminkan AppSettings.bubbleEnabled,
    // di-load saat service start dan diperbarui lewat setBubbleEnabled()
    private var bubbleEnabled = true
    private var bubbleMenu: BubbleMenu? = null
    private var bubbleSettingsPanel: BubbleSettingsPanel? = null
    private var addOverlayPanel: AddOverlayPanel? = null
    private var overlayCustomizationPanel: OverlayCustomizationPanel? = null

    // Status tracking
    private val overlayStatuses = mutableMapOf<String, OverlayStatus>()

    /**
     * Package aplikasi yang terdeteksi di depan layar SEKARANG (buat pilih
     * App Layout yang tepat). Null kalau belum pernah terdeteksi sama sekali
     * atau izin Usage Access belum aktif -- overlay tetap pakai layout
     * Default seperti biasa dalam kondisi ini, tidak ada yang rusak.
     */
    private var currentForegroundPackage: String? = null
    private var appWatcher: ForegroundAppWatcher.Watcher? = null

    /**
     * Cache in-memory dari seluruh App Layout tersimpan, disinkronkan terus-
     * menerus lewat `collect` (bukan cuma sekali baca di [onCreate]) supaya
     * perubahan dari MainActivity (tambah/edit/hapus App Layout lewat layar
     * "App Layouts") atau dari [saveCurrentPositions] di sini sendiri langsung
     * kepakai tanpa perlu restart Service. Dipakai supaya resolusi layout
     * (mis. di [startOverlay] atau [onForegroundAppChanged]) bisa langsung
     * synchronous, tidak perlu nunggu `Flow.first()` tiap kali app berpindah.
     */
    private var cachedAppLayouts: List<AppLayout> = emptyList()

    // Callbacks for UI
    var onOverlayStatusChanged: ((String, OverlayStatus) -> Unit)? = null
    var onBubbleClicked: (() -> Unit)? = null

    inner class LocalBinder : Binder() {
        fun getService(): OverlayService = this@OverlayService
    }

    private val actionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                NotificationController.ACTION_SHOW -> showAllOverlays()
                NotificationController.ACTION_HIDE -> hideAllOverlays()
                NotificationController.ACTION_LOCK -> lockAllOverlays()
                NotificationController.ACTION_UNLOCK -> unlockAllOverlays()
                NotificationController.ACTION_EXIT -> exitService()
                NotificationController.ACTION_CLEAR_EXIT -> clearAndExit()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Logger.serviceStart()
        storage = OverlayStorage.getInstance(this)
        notificationController = NotificationController(this)

        // Register broadcast receiver
        val filter = IntentFilter().apply {
            addAction(NotificationController.ACTION_SHOW)
            addAction(NotificationController.ACTION_HIDE)
            addAction(NotificationController.ACTION_LOCK)
            addAction(NotificationController.ACTION_UNLOCK)
            addAction(NotificationController.ACTION_EXIT)
            addAction(NotificationController.ACTION_CLEAR_EXIT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(actionReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(actionReceiver, filter)
        }

        // Load settings
        serviceScope.launch {
            val settings = storage.getSettings().first()
            bubbleConfig = settings.bubbleConfig
            bubbleEnabled = settings.bubbleEnabled
            Logger.debugEnabled = settings.debugLogging
            // Bubble sekarang independen dari overlay: kalau memang diaktifkan
            // user, langsung tampilkan begitu service hidup, tidak perlu nunggu
            // ada overlay yang start dulu.
            if (bubbleEnabled) {
                createBubble()
            }
        }

        // Sinkronkan cache App Layout terus-menerus (lihat dokumentasi
        // cachedAppLayouts) -- pakai collect, bukan first(), supaya perubahan
        // yang dibuat lewat MainActivity atau saveCurrentPositions() di sini
        // sendiri langsung tercermin tanpa perlu restart Service.
        serviceScope.launch {
            storage.getAppLayouts().collect { layouts -> cachedAppLayouts = layouts }
        }

        // Pantau perpindahan aplikasi supaya overlay yang aktif otomatis ikut
        // pindah ke App Layout (posisi + ukuran + opacity) yang sudah diatur
        // khusus untuk aplikasi itu, kalau ada. Kalau izin Usage Access belum
        // aktif, watcher tetap jalan tapi tidak akan pernah mendapat
        // perubahan (semua overlay tetap pakai layout Default seperti biasa).
        appWatcher = ForegroundAppWatcher.Watcher(this) { pkg -> onForegroundAppChanged(pkg) }
        appWatcher?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val overlayName = getActiveOverlayName()
        val isVisible = overlayWindows.values.any { it.getConfig().isVisible }
        val isLocked = overlayWindows.values.all { it.getConfig().isLocked }

        val notification = notificationController.buildNotification(
            overlayName, isVisible, isLocked
        )
        startForeground(NotificationController.NOTIFICATION_ID, notification)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Clamp all overlays and bubble to new screen bounds
        overlayWindows.values.forEach { it.onScreenSizeChanged() }
        floatingBubble?.onScreenSizeChanged()
    }

    fun startOverlay(config: OverlayConfig) {
        Logger.d("START_OVERLAY: id=${config.id}, name=${config.name}, alreadyActive=${overlayWindows.containsKey(config.id)}")
        if (overlayWindows.containsKey(config.id)) {
            // Already active, update instead
            updateOverlay(config)
            return
        }

        val window = OverlayWindow(
            context = this,
            config = config,
            onConfigChanged = { updatedConfig ->
                serviceScope.launch {
                    storage.updateOverlay(updatedConfig)
                }
                updateNotificationState()
            },
            onStatusChanged = { id, status ->
                overlayStatuses[id] = status
                onOverlayStatusChanged?.invoke(id, status)
            }
        )
        window.create()
        overlayWindows[config.id] = window
        Logger.overlayCreate(config.name)

        // Kalau overlay ini dinyalakan saat aplikasi lain yang sudah punya App
        // Layout aktif untuk overlay ini kebetulan sedang di depan, langsung
        // terapkan posisi/ukuran/opacity App Layout itu -- bukan nampilin
        // Default dulu baru pindah begitu poller berikutnya jalan. Ini murni
        // menggeser window yang BARU SAJA dibuat (updatePositionAndSize/
        // updateLiveOpacity tidak menyentuh config/storage sama sekali), jadi
        // layout Default overlay ini tetap aman tidak ketimpa.
        ForegroundAppWatcher.getCurrentForegroundPackage(this)?.let { currentForegroundPackage = it }
        applyLayoutForForegroundApp(config.id, window)

        // Menyalakan overlay APAPUN otomatis menyalakan floating bubble juga
        // (bukan cuma dibuat kalau kebetulan sudah enabled) — supaya user
        // selalu punya kontrol show/hide/lock begitu ada overlay yang aktif.
        // Pakai setBubbleEnabled(true), bukan createBubble() langsung, supaya
        // AppSettings.bubbleEnabled ikut ke-persist true dan switch "Floating
        // Bubble" di UI otomatis sinkron ke ON (reaktif lewat Flow yang sama).
        // Ini cuma satu arah: mematikan overlay TIDAK otomatis mematikan
        // bubble (bubble tetap independen untuk arah "off").
        setBubbleEnabled(true)

        // Update notification
        updateNotificationState()

        // Mark active in storage
        serviceScope.launch {
            storage.updateOverlay(config.copy(isActive = true))
        }
    }

    /**
     * Stops an active overlay window and, by default, persists `isActive = false`
     * back to storage so re-opening the app reflects it as stopped.
     *
     * `persistInactive = false` is used when the overlay is being deleted right
     * after this call: the storage write below reads the current saved list and
     * writes it back with just `isActive` flipped, based on a snapshot taken
     * *outside* of DataStore's atomic transaction. If the delete's own
     * read-modify-write (`OverlayStorage.removeOverlay`) races with this one and
     * loses, this update's write — still containing the "deleted" overlay —
     * would overwrite the deletion and make it reappear. Skipping this write
     * entirely when the overlay is about to be deleted removes that race.
     */
    fun stopOverlay(id: String, persistInactive: Boolean = true) {
        Logger.d("STOP_OVERLAY: id=$id, persistInactive=$persistInactive, wasActive=${overlayWindows.containsKey(id)}")
        overlayWindows[id]?.let { window ->
            window.destroy()
            overlayWindows.remove(id)
            overlayStatuses.remove(id)
        }

        updateNotificationState()

        if (persistInactive) {
            serviceScope.launch {
                val overlays = storage.getOverlays().first()
                val overlay = overlays.find { it.id == id }
                if (overlay != null) {
                    Logger.d("STOP_OVERLAY: persisting isActive=false for id=$id")
                    storage.updateOverlay(overlay.copy(isActive = false))
                } else {
                    Logger.d("STOP_OVERLAY: id=$id not found in storage, nothing to persist")
                }
            }
        } else {
            Logger.d("STOP_OVERLAY: skipped persisting isActive (persistInactive=false)")
        }

        if (overlayWindows.isEmpty() && !bubbleEnabled) {
            Logger.d("STOP_OVERLAY: no windows left and bubble disabled, calling exitService(persistInactive=$persistInactive)")
            exitService(persistInactive = persistInactive)
        }
    }

    fun updateOverlay(config: OverlayConfig) {
        overlayWindows[config.id]?.updateConfig(config)
        updateNotificationState()
    }

    fun showOverlay(id: String) {
        overlayWindows[id]?.show()
        updateNotificationState()
    }

    fun hideOverlay(id: String) {
        overlayWindows[id]?.hide()
        updateNotificationState()
    }

    fun lockOverlay(id: String) {
        overlayWindows[id]?.lock()
        updateNotificationState()
    }

    fun unlockOverlay(id: String) {
        overlayWindows[id]?.unlock()
        updateNotificationState()
    }

    fun resetOverlayPosition(id: String) {
        overlayWindows[id]?.resetPosition()
    }

    fun reloadOverlay(id: String) {
        overlayWindows[id]?.reload()
    }

    fun getOverlayStatus(id: String): OverlayStatus {
        return overlayStatuses[id] ?: OverlayStatus.IDLE
    }

    fun getActiveOverlayIds(): Set<String> = overlayWindows.keys.toSet()

    fun isOverlayActive(id: String): Boolean = overlayWindows.containsKey(id)

    // ---- Bulk operations ----

    fun showAllOverlays() {
        overlayWindows.values.forEach { it.show() }
        updateNotificationState()
    }

    fun hideAllOverlays() {
        overlayWindows.values.forEach { it.hide() }
        updateNotificationState()
    }

    fun lockAllOverlays() {
        overlayWindows.values.forEach { it.lock() }
        updateNotificationState()
    }

    fun unlockAllOverlays() {
        overlayWindows.values.forEach { it.unlock() }
        updateNotificationState()
    }

    // ---- Bubble ----

    /**
     * Nyala/matiin floating bubble secara independen dari overlay — ini yang
     * dipakai fitur toggle master "Floating Bubble" di Dashboard. Beda dari
     * dulu (bubble otomatis ikut lifecycle overlay pertama/terakhir), sekarang
     * bubble punya switch sendiri yang dipersist ke AppSettings.bubbleEnabled.
     */
    fun setBubbleEnabled(enabled: Boolean) {
        Logger.d("SET_BUBBLE_ENABLED: enabled=$enabled")
        bubbleEnabled = enabled
        serviceScope.launch {
            val settings = storage.getSettings().first()
            storage.saveSettings(settings.copy(bubbleEnabled = enabled))
        }

        if (enabled) {
            createBubble()
            updateNotificationState()
        } else {
            destroyBubble()
            if (overlayWindows.isEmpty()) {
                // Tidak ada overlay maupun bubble lagi yang perlu dipertahankan
                // -> matikan service foreground-nya sepenuhnya. persistInactive
                // = false karena tidak ada overlay yang statusnya berubah di sini.
                Logger.d("SET_BUBBLE_ENABLED(false): no overlays left either, exiting service")
                exitService(persistInactive = false)
            } else {
                updateNotificationState()
            }
        }
    }

    fun isBubbleEnabled(): Boolean = bubbleEnabled

    private fun createBubble() {
        if (floatingBubble != null) return
        floatingBubble = FloatingBubble(
            context = this,
            config = bubbleConfig,
            onBubbleClicked = {
                showBubbleMenu()
            },
            onConfigChanged = { newConfig ->
                bubbleConfig = newConfig
                serviceScope.launch {
                    val settings = storage.getSettings().first()
                    storage.saveSettings(settings.copy(bubbleConfig = newConfig))
                }
            }
        )
        floatingBubble?.create()
    }

    private fun destroyBubble() {
        bubbleMenu?.dismiss()
        bubbleMenu = null
        bubbleSettingsPanel?.dismiss()
        bubbleSettingsPanel = null
        addOverlayPanel?.dismiss()
        addOverlayPanel = null
        overlayCustomizationPanel?.dismiss()
        overlayCustomizationPanel = null
        floatingBubble?.destroy()
        floatingBubble = null
    }

    /**
     * Tap on the floating bubble: hide the bubble and show a draggable floating
     * menu in its place (same position): Simpan Posisi Sekarang, Pengaturan
     * Bubble, Tambah Overlay Baru, Kustomisasi Overlay, Sembunyikan Bubble,
     * dan Stop Semua. The handle strip at the top ("∷ Geser") brings the
     * bubble back wherever the menu ends up being dragged to.
     */
    private fun showBubbleMenu() {
        val bubble = floatingBubble ?: return
        if (bubbleMenu?.isShowing() == true) return

        val (x, y) = bubble.getPosition()
        bubble.hide()

        val actions = listOf(
            BubbleMenuAction(label = "Simpan Posisi Sekarang", iconRes = R.drawable.ic_save) {
                saveCurrentPositions()
                collapseBubbleMenu()
            },
            BubbleMenuAction(label = "Pengaturan Bubble", iconRes = R.drawable.ic_bubble) {
                showBubbleSettingsPanel(x, y)
            },
            BubbleMenuAction(label = "Tambah Overlay Baru", iconRes = R.drawable.ic_add_circle) {
                showAddOverlayPanel(x, y)
            },
            BubbleMenuAction(label = "Kustomisasi Overlay", iconRes = R.drawable.ic_tune) {
                showOverlayCustomizationPanel(x, y)
            },
            BubbleMenuAction(label = "Sembunyikan Bubble", iconRes = R.drawable.ic_hide) {
                bubbleMenu?.dismiss()
                // BUKAN setBubbleEnabled(false) (itu benar2 mematikan bubble +
                // persist bubbleEnabled=false ke storage). Ini cuma nyembunyiin
                // window-nya di posisi terakhir + kunci geser — bubble masih
                // "on" secara status, tap di titik itu langsung munculin lagi.
                floatingBubble?.hideAsGhost()
            },
            BubbleMenuAction(label = "Stop Semua", iconRes = R.drawable.ic_stop_circle) {
                exitService()
            }
        )

        if (bubbleMenu == null) bubbleMenu = BubbleMenu(this)
        bubbleMenu?.show(
            anchorX = x,
            anchorY = y,
            onCollapse = { collapseBubbleMenu() },
            actions = actions
        )
    }

    /** Dismisses the floating menu and brings the bubble back at wherever the menu was left. */
    private fun collapseBubbleMenu() {
        val menu = bubbleMenu ?: return
        val (x, y) = menu.getPosition()
        menu.dismiss()
        floatingBubble?.moveTo(x, y)
        floatingBubble?.show()
    }

    /** Brings the bubble back at an explicit position — used when collapsing out of a sub-panel. */
    private fun collapseToBubble(x: Int, y: Int) {
        floatingBubble?.moveTo(x, y)
        floatingBubble?.show()
    }

    // ---- Sub-panels (Pengaturan Bubble / Tambah Overlay Baru / Kustomisasi Overlay) ----

    private fun showBubbleSettingsPanel(x: Int, y: Int) {
        bubbleMenu?.dismiss()
        if (bubbleSettingsPanel == null) bubbleSettingsPanel = BubbleSettingsPanel(this)
        bubbleSettingsPanel?.show(
            anchorX = x,
            anchorY = y,
            initialSize = bubbleConfig.size,
            initialOpacity = bubbleConfig.opacity,
            onSave = { size, opacity ->
                val pos = bubbleSettingsPanel?.getPosition() ?: Pair(x, y)
                bubbleConfig = bubbleConfig.copy(size = size, opacity = opacity)
                floatingBubble?.updateConfig(bubbleConfig)
                serviceScope.launch {
                    val settings = storage.getSettings().first()
                    storage.saveSettings(settings.copy(bubbleConfig = bubbleConfig))
                }
                bubbleSettingsPanel?.dismiss()
                collapseToBubble(pos.first, pos.second)
            },
            onClose = {
                val pos = bubbleSettingsPanel?.getPosition() ?: Pair(x, y)
                bubbleSettingsPanel?.dismiss()
                collapseToBubble(pos.first, pos.second)
            }
        )
    }

    private fun showAddOverlayPanel(x: Int, y: Int) {
        bubbleMenu?.dismiss()
        if (addOverlayPanel == null) addOverlayPanel = AddOverlayPanel(this)
        addOverlayPanel?.show(
            anchorX = x,
            anchorY = y,
            onAdd = { name, url, opacity ->
                val pos = addOverlayPanel?.getPosition() ?: Pair(x, y)
                val config = OverlayConfig(name = name, url = url, opacity = opacity, isActive = true)
                serviceScope.launch { storage.addOverlay(config) }
                startOverlay(config)
                addOverlayPanel?.dismiss()
                collapseToBubble(pos.first, pos.second)
            },
            onCancel = {
                val pos = addOverlayPanel?.getPosition() ?: Pair(x, y)
                addOverlayPanel?.dismiss()
                collapseToBubble(pos.first, pos.second)
            }
        )
    }

    private fun showOverlayCustomizationPanel(x: Int, y: Int) {
        bubbleMenu?.dismiss()
        serviceScope.launch {
            val overlays = storage.getOverlays().first()
            if (overlayCustomizationPanel == null) overlayCustomizationPanel = OverlayCustomizationPanel(this@OverlayService)
            overlayCustomizationPanel?.show(
                anchorX = x,
                anchorY = y,
                overlays = overlays,
                activeIds = getActiveOverlayIds(),
                onToggleActive = { config, active ->
                    if (active) startOverlay(config) else stopOverlay(config.id)
                },
                onToggleLock = { config, locked ->
                    if (isOverlayActive(config.id)) {
                        if (locked) lockOverlay(config.id) else unlockOverlay(config.id)
                    } else {
                        serviceScope.launch { storage.updateOverlay(config.copy(isLocked = locked)) }
                    }
                },
                onOpacityChange = { config, opacity ->
                    if (isOverlayActive(config.id)) {
                        updateOverlay(config.copy(opacity = opacity))
                    } else {
                        serviceScope.launch { storage.updateOverlay(config.copy(opacity = opacity)) }
                    }
                },
                onDelete = { config ->
                    if (isOverlayActive(config.id)) stopOverlay(config.id, persistInactive = false)
                    serviceScope.launch { storage.removeOverlay(config.id) }
                },
                onUnlockAll = { unlockAllOverlays() },
                onDeactivateAll = { stopAllOverlays() },
                onClose = {
                    val pos = overlayCustomizationPanel?.getPosition() ?: Pair(x, y)
                    overlayCustomizationPanel?.dismiss()
                    collapseToBubble(pos.first, pos.second)
                }
            )
        }
    }

    /** "Simpan Posisi Sekarang" — force-flush current bubble + active overlay geometry to storage. */
    private fun saveCurrentPositions() {
        val pkg = currentForegroundPackage
        val geometrySnapshot = overlayWindows.mapValues { (_, window) ->
            window.getLiveGeometry() to window.getLiveOpacity()
        }
        serviceScope.launch {
            val settings = storage.getSettings().first()
            storage.saveSettings(settings.copy(bubbleConfig = bubbleConfig))

            if (pkg != null && pkg != packageName) {
                // Aplikasi lain (bukan aplikasi ini sendiri) terdeteksi di depan
                // -> simpan geometri yang lagi ditampilkan SEKARANG (bisa jadi
                // hasil App Layout yang sudah otomatis diterapkan, atau masih
                // Default kalau app ini belum punya App Layout) sebagai App
                // Layout untuk aplikasi tsb. Kalau overlay ini BELUM punya App
                // Layout untuk aplikasi ini, bikin baru otomatis (bukan cuma
                // menimpa Default) supaya aplikasi ini langsung muncul di layar
                // "App Layouts" tanpa user perlu tambah manual dulu.
                val label = appLabelFor(pkg)
                overlayWindows.keys.forEach { id ->
                    val (geometry, opacity) = geometrySnapshot.getValue(id)
                    val existing = cachedAppLayouts.firstOrNull { it.overlayId == id && it.packageName == pkg }
                    val layout = (existing ?: AppLayout(overlayId = id, packageName = pkg)).copy(
                        appName = label,
                        positionX = geometry.x,
                        positionY = geometry.y,
                        width = geometry.width,
                        height = geometry.height,
                        opacity = opacity,
                        // "Simpan" berarti "pakai layout ini" -- kalau kebetulan
                        // profil ini sebelumnya dinonaktifkan lewat "App Layouts",
                        // simpan manual di sini menyalakannya lagi, supaya user
                        // tidak bingung kenapa posisi yang baru saja disimpan
                        // tidak pernah kepakai.
                        isEnabled = true
                    )
                    if (existing != null) storage.updateAppLayout(layout) else storage.addAppLayout(layout)
                }
            } else {
                // Tidak ada aplikasi lain yang terdeteksi (izin Usage Access
                // belum aktif) atau yang di depan adalah aplikasi ini sendiri
                // -> simpan ke layout Default overlay seperti biasa.
                overlayWindows.forEach { (id, window) ->
                    val (geometry, opacity) = geometrySnapshot.getValue(id)
                    val config = window.getConfig()
                    storage.updateOverlay(
                        config.copy(
                            positionX = geometry.x,
                            positionY = geometry.y,
                            width = geometry.width,
                            height = geometry.height,
                            opacity = opacity
                        )
                    )
                }
            }
        }
    }

    /**
     * Terapkan App Layout (kalau ada & aktif untuk [pkg] yang sedang di depan)
     * ke satu [window] yang baru dibuat. Dipanggil dari [startOverlay] supaya
     * overlay yang baru dinyalakan langsung muncul di posisi App Layout-nya
     * kalau kebetulan aplikasi itu sudah di depan, bukan nampilin Default dulu.
     */
    private fun applyLayoutForForegroundApp(overlayId: String, window: OverlayWindow) {
        val layout = resolveAppLayout(overlayId, currentForegroundPackage) ?: return
        window.updatePositionAndSize(layout.positionX, layout.positionY, layout.width, layout.height)
        window.updateLiveOpacity(layout.opacity)
    }

    /**
     * Cari App Layout yang cocok untuk (overlayId, pkg) dari [cachedAppLayouts],
     * null kalau tidak ada / dinonaktifkan / [pkg] adalah aplikasi ini sendiri
     * (App Layout tidak pernah berlaku untuk aplikasi ini sendiri).
     */
    private fun resolveAppLayout(overlayId: String, pkg: String?): AppLayout? {
        if (pkg == null || pkg == packageName) return null
        return cachedAppLayouts.firstOrNull {
            it.overlayId == overlayId && it.packageName == pkg && it.isEnabled
        }
    }

    /**
     * Dipanggil tiap kali aplikasi yang di depan layar berganti (lewat
     * [appWatcher]). Untuk semua overlay yang sedang aktif: kalau App Layout
     * untuk package baru ini ada & aktif, pindahkan window ke posisi/ukuran/
     * opacity itu; kalau tidak ada, pastikan window kembali ke layout Default
     * overlay. Baik kasus App Layout maupun Default di sini SAMA-SAMA cuma
     * memindahkan window yang sudah ada (`updatePositionAndSize`/
     * `updateLiveOpacity`) — tidak pernah menyentuh `config`/storage, jadi
     * layout Default aslinya tidak pernah ketimpa hanya karena user
     * pindah-pindah aplikasi.
     */
    private fun onForegroundAppChanged(pkg: String) {
        currentForegroundPackage = pkg
        if (overlayWindows.isEmpty()) return

        overlayWindows.forEach { (id, window) ->
            val layout = resolveAppLayout(id, pkg)
            if (layout != null) {
                window.updatePositionAndSize(layout.positionX, layout.positionY, layout.width, layout.height)
                window.updateLiveOpacity(layout.opacity)
            } else {
                val config = window.getConfig()
                window.updatePositionAndSize(config.positionX, config.positionY, config.width, config.height)
                window.updateLiveOpacity(config.opacity)
            }
        }
    }

    /** Ambil nama tampilan (label) sebuah aplikasi dari package-nya, fallback ke nama package kalau gagal. */
    private fun appLabelFor(pkg: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    /** "Matikan Semua Overlay" in Kustomisasi Overlay — stops every currently-active overlay window. */
    private fun stopAllOverlays() {
        overlayWindows.keys.toList().forEach { id -> stopOverlay(id) }
    }

    // ---- Notification ----

    private fun updateNotificationState() {
        if (overlayWindows.isEmpty() && floatingBubble == null) return
        val name = getActiveOverlayName()
        val isVisible = overlayWindows.values.any { it.getConfig().isVisible }
        val isLocked = overlayWindows.values.all { it.getConfig().isLocked }
        notificationController.updateNotification(name, isVisible, isLocked)
    }

    private fun getActiveOverlayName(): String {
        return when (overlayWindows.size) {
            0 -> "No overlay"
            1 -> overlayWindows.values.first().getConfig().name
            else -> "${overlayWindows.size} overlays"
        }
    }

    // ---- Exit ----

    /**
     * Fully tears down the service (called when the last overlay window closes)
     * and, by default, persists `isActive = false` for every overlay.
     *
     * `persistInactive = false` is used when we got here via a delete
     * (`stopOverlay(id, persistInactive = false)` -> this), for the same
     * reason documented on `stopOverlay`: `storage.saveOverlays()` here
     * writes back a list read *before* `OverlayStorage.removeOverlay()` runs,
     * so if this write lands after the delete's write it would resurrect the
     * just-deleted overlay. Skipping it when a delete triggered this call
     * removes that race.
     */
    fun exitService(persistInactive: Boolean = true) {
        Logger.d("EXIT_SERVICE: persistInactive=$persistInactive, overlayWindows=${overlayWindows.size}")
        overlayWindows.values.toList().forEach { it.destroy() }
        overlayWindows.clear()
        overlayStatuses.clear()
        destroyBubble()
        bubbleEnabled = false

        // Mark all overlays as inactive
        if (persistInactive) {
            serviceScope.launch {
                val overlays = storage.getOverlays().first()
                val updated = overlays.map { it.copy(isActive = false) }
                Logger.d("EXIT_SERVICE: marking ${updated.size} overlay(s) inactive")
                storage.saveOverlays(updated)
            }
        } else {
            Logger.d("EXIT_SERVICE: skipped marking overlays inactive (persistInactive=false)")
        }

        // BUG FIX: destroyBubble() di atas cuma matiin window bubble-nya doang,
        // tapi AppSettings.bubbleEnabled tidak pernah ditulis ulang jadi false
        // di sini sebelumnya -> switch "Floating Bubble" di Dashboard kebaca
        // stuck ON selamanya (bukan cuma sekali render basi, tapi memang datanya
        // yang salah di storage), meski bubble aslinya sudah mati total. Ini
        // yang bikin "Stop Semua" dari floating menu kelihatan cuma matiin
        // bubble-nya doang tapi switch-nya tidak ikut sinkron walau app-nya
        // sudah dipindah-pindah menu.
        serviceScope.launch {
            val settings = storage.getSettings().first()
            storage.saveSettings(settings.copy(bubbleEnabled = false))
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationController.cancel()
        Logger.serviceStop()
        stopSelf()
    }

    fun clearAndExit() {
        Logger.d("CLEAR_AND_EXIT: overlayWindows=${overlayWindows.size}")
        overlayWindows.values.toList().forEach { window ->
            window.clearCache()
            window.destroy()
        }
        overlayWindows.clear()
        overlayStatuses.clear()
        destroyBubble()
        bubbleEnabled = false

        // Mark all overlays as inactive
        serviceScope.launch {
            val overlays = storage.getOverlays().first()
            val updated = overlays.map { it.copy(isActive = false) }
            Logger.d("CLEAR_AND_EXIT: marking ${updated.size} overlay(s) inactive")
            storage.saveOverlays(updated)
        }

        // Sama seperti fix di exitService(): bubble ikut dimatikan total di
        // sini juga, jadi bubbleEnabled wajib ikut di-persist false.
        serviceScope.launch {
            val settings = storage.getSettings().first()
            storage.saveSettings(settings.copy(bubbleEnabled = false))
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationController.cancel()
        Logger.serviceStop()
        stopSelf()
    }

    override fun onDestroy() {
        Logger.d("SERVICE_ON_DESTROY: overlayWindows=${overlayWindows.size}")
        try {
            unregisterReceiver(actionReceiver)
        } catch (e: Exception) {
            Logger.e("Error unregistering receiver", e)
        }
        appWatcher?.stop()
        appWatcher = null
        overlayWindows.values.toList().forEach { it.destroy() }
        overlayWindows.clear()
        destroyBubble()
        serviceScope.cancel()
        notificationController.cancel()
        Logger.serviceStop()
        super.onDestroy()
    }
}
