package siroha.floating.donation.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.marginEnd
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.load
import siroha.floating.donation.R
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.model.OverlayStatus
import siroha.floating.donation.model.OverlayType
import siroha.floating.donation.util.Logger
import siroha.floating.donation.util.ScreenUtils

class OverlayWindow(
    private val context: Context,
    private var config: OverlayConfig,
    private val onConfigChanged: (OverlayConfig) -> Unit,
    private val onStatusChanged: (String, OverlayStatus) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var containerView: FrameLayout? = null
    private var overlayWebView: OverlayWebView? = null
    private var imageView: ImageView? = null
    private var isAdded = false
    private var layoutParams: WindowManager.LayoutParams? = null

    // Indikator visual (ikon M3) yang muncul saat overlay unlocked,
    // supaya user tahu bagian mana untuk geser dan bagian mana untuk resize
    private var moveHandle: View? = null
    private var resizeHandle: View? = null

    // Touch handling
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Resize
    private var isResizing = false
    private var initialWidth = 0
    private var initialHeight = 0

    /**
     * Container yang membungkus WebView/ImageView overlay.
     *
     * Root cause bug drag & resize:
     * WebView (dan beberapa View lain) selalu meng-konsumsi touch event untuk
     * dirinya sendiri (scrolling, zoom, dsb), sehingga event ACTION_DOWN/MOVE/UP
     * tidak pernah sampai ke OnTouchListener yang dipasang di containerView.
     * Akibatnya drag & resize cuma bisa lewat dialog Customize karena satu-satunya
     * jalur yang benar-benar memanggil updatePositionAndSize()/onConfigChanged().
     *
     * Fix: override onInterceptTouchEvent supaya saat overlay dalam kondisi
     * unlocked (mode edit posisi/ukuran), semua touch event di-intercept oleh
     * container itu sendiri sebelum sempat "dimakan" oleh child view-nya.
     * Saat locked, intercept dimatikan (return false) — meski sebenarnya window
     * juga sudah FLAG_NOT_TOUCHABLE saat locked jadi baris ini tidak akan pernah
     * dipanggil dalam kondisi itu.
     */
    private inner class DraggableContainer(context: Context) : FrameLayout(context) {
        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            return !config.isLocked
        }
    }

    private val imageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    fun create() {
        if (isAdded) return
        Logger.overlayCreate(config.name)

        val screenSize = ScreenUtils.getScreenSize(context)
        val (clampedW, clampedH) = ScreenUtils.clampSize(
            config.width, config.height,
            screenSize.x, screenSize.y
        )
        val (clampedX, clampedY) = ScreenUtils.clampPosition(
            config.positionX, config.positionY, clampedW, clampedH,
            screenSize.x, screenSize.y
        )

        layoutParams = createLayoutParams(clampedX, clampedY, clampedW, clampedH)

        containerView = DraggableContainer(context).apply {
            setBackgroundColor(0x00000000)
        }

        when (config.type) {
            OverlayType.WEB -> setupWebOverlay()
            OverlayType.IMAGE -> setupImageOverlay()
        }

        setupHandleIndicators()
        setupTouchHandling()
        applyLockState()
        applyOpacity()

        try {
            windowManager.addView(containerView, layoutParams)
            isAdded = true

            if (!config.isVisible) {
                containerView?.visibility = View.GONE
            }
        } catch (e: Exception) {
            Logger.e("Failed to add overlay window", e)
            onStatusChanged(config.id, OverlayStatus.ERROR)
        }
    }

    private fun createLayoutParams(x: Int, y: Int, width: Int, height: Int): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            width,
            height,
            type,
            getWindowFlags(config.isLocked),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
    }

    private fun getWindowFlags(locked: Boolean): Int {
        return if (locked) {
            // LOCKED: touch passes through to apps below
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            // UNLOCKED: overlay captures touch, user can drag/resize
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
    }

    private fun setupWebOverlay() {
        overlayWebView = OverlayWebView(context).apply {
            statusCallback = { status ->
                onStatusChanged(config.id, status)
            }
        }

        containerView?.addView(
            overlayWebView!!.webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        if (config.url.isNotEmpty()) {
            overlayWebView?.loadUrl(config.url)
        }
    }

    private fun setupImageOverlay() {
        imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0x00000000)
        }

        containerView?.addView(
            imageView!!,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        if (config.imagePath.isNotEmpty()) {
            loadImage(config.imagePath)
        }

        onStatusChanged(config.id, OverlayStatus.CONNECTED)
    }

    private fun loadImage(path: String) {
        imageView?.load(android.net.Uri.parse(path), imageLoader) {
            crossfade(true)
            listener(
                onError = { _, result ->
                    Logger.e("Failed to load image: ${result.throwable.message}")
                    onStatusChanged(config.id, OverlayStatus.ERROR)
                },
                onSuccess = { _, _ ->
                    onStatusChanged(config.id, OverlayStatus.CONNECTED)
                }
            )
        }
    }

    /**
     * Ikon indikator M3 di atas overlay: badge bulat di tengah (move) dan
     * di pojok kanan-bawah (resize), hanya tampil saat overlay unlocked.
     * Ikon ini tidak mengganggu touch handling karena DraggableContainer
     * sudah meng-intercept semua touch di level container saat unlocked.
     */
    private fun setupHandleIndicators() {
        val density = context.resources.displayMetrics.density
        val badgeSize = (32 * density).toInt()
        val iconSize = (18 * density).toInt()
        val edgeMargin = (6 * density).toInt()

        fun createBadge(iconRes: Int): FrameLayout {
            return FrameLayout(context).apply {
                background = androidx.core.content.ContextCompat.getDrawable(
                    context, R.drawable.overlay_handle_background
                )
                addView(
                    ImageView(context).apply {
                        setImageResource(iconRes)
                    },
                    FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
                )
            }
        }

        moveHandle = createBadge(R.drawable.ic_move_handle).also {
            containerView?.addView(
                it,
                FrameLayout.LayoutParams(badgeSize, badgeSize, Gravity.CENTER)
            )
        }

        resizeHandle = createBadge(R.drawable.ic_resize_handle).also {
            containerView?.addView(
                it,
                FrameLayout.LayoutParams(
                    badgeSize, badgeSize, Gravity.BOTTOM or Gravity.END
                ).apply {
                    bottomMargin = edgeMargin
                    marginEnd = edgeMargin
                }
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchHandling() {
        containerView?.setOnTouchListener { _, event ->
            if (config.isLocked) {
                return@setOnTouchListener false
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    // Check if in resize zone (bottom-right corner, 48dp area)
                    val resizeZone = (48 * context.resources.displayMetrics.density).toInt()
                    val viewWidth = containerView?.width ?: 0
                    val viewHeight = containerView?.height ?: 0
                    isResizing = event.x > viewWidth - resizeZone && event.y > viewHeight - resizeZone

                    if (isResizing) {
                        initialWidth = layoutParams?.width ?: config.width
                        initialHeight = layoutParams?.height ?: config.height
                    }

                    // Show semi-transparent background when unlocked and touched
                    containerView?.setBackgroundColor(0x22888888)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val screenSize = ScreenUtils.getScreenSize(context)
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (isResizing) {
                        val newWidth = (initialWidth + dx).coerceIn(100, screenSize.x)
                        val newHeight = (initialHeight + dy).coerceIn(100, screenSize.y)

                        layoutParams?.width = newWidth
                        layoutParams?.height = newHeight

                        // Clamp position after resize
                        val (cx, cy) = ScreenUtils.clampPosition(
                            layoutParams?.x ?: 0, layoutParams?.y ?: 0,
                            newWidth, newHeight, screenSize.x, screenSize.y
                        )
                        layoutParams?.x = cx
                        layoutParams?.y = cy
                    } else {
                        val newX = initialX + dx
                        val newY = initialY + dy
                        val w = layoutParams?.width ?: config.width
                        val h = layoutParams?.height ?: config.height

                        val (cx, cy) = ScreenUtils.clampPosition(
                            newX, newY, w, h, screenSize.x, screenSize.y
                        )
                        layoutParams?.x = cx
                        layoutParams?.y = cy
                    }

                    try {
                        windowManager.updateViewLayout(containerView, layoutParams)
                    } catch (e: Exception) {
                        Logger.e("Failed to update layout", e)
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    containerView?.setBackgroundColor(0x00000000)
                    val wasResizing = isResizing
                    isResizing = false

                    // Save position
                    val newConfig = config.copy(
                        positionX = layoutParams?.x ?: config.positionX,
                        positionY = layoutParams?.y ?: config.positionY,
                        width = layoutParams?.width ?: config.width,
                        height = layoutParams?.height ?: config.height
                    )
                    config = newConfig
                    onConfigChanged(newConfig)
                    Logger.positionChanged(config.name, newConfig.positionX, newConfig.positionY)

                    // Lebar berubah karena resize -> render ulang konten webview
                    // supaya proporsional, bukan ke-crop (lihat updateScaleToFit)
                    if (wasResizing && newConfig.type == OverlayType.WEB) {
                        overlayWebView?.updateScaleToFit(newConfig.width)
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun applyLockState() {
        layoutParams?.let { params ->
            params.flags = if (config.isLocked) {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            if (isAdded) {
                try {
                    windowManager.updateViewLayout(containerView, params)
                } catch (e: Exception) {
                    Logger.e("Failed to update lock state", e)
                }
            }
        }

        // Visual indicator for unlock state
        if (!config.isLocked) {
            containerView?.setBackgroundColor(0x11888888)
        } else {
            containerView?.setBackgroundColor(0x00000000)
        }

        // Ikon move/resize cuma relevan saat overlay bisa digeser/di-resize
        val handleVisibility = if (!config.isLocked) View.VISIBLE else View.GONE
        moveHandle?.visibility = handleVisibility
        resizeHandle?.visibility = handleVisibility
    }

    private fun applyOpacity() {
        containerView?.alpha = config.opacity
    }

    fun updateConfig(newConfig: OverlayConfig) {
        val oldConfig = config
        config = newConfig

        if (oldConfig.isLocked != newConfig.isLocked) {
            applyLockState()
            if (newConfig.isLocked) {
                Logger.overlayLock(newConfig.name)
            } else {
                Logger.overlayUnlock(newConfig.name)
            }
        }

        if (oldConfig.opacity != newConfig.opacity) {
            applyOpacity()
        }

        if (oldConfig.isVisible != newConfig.isVisible) {
            containerView?.visibility = if (newConfig.isVisible) View.VISIBLE else View.GONE
        }

        if (oldConfig.positionX != newConfig.positionX || oldConfig.positionY != newConfig.positionY ||
            oldConfig.width != newConfig.width || oldConfig.height != newConfig.height
        ) {
            updatePositionAndSize(newConfig.positionX, newConfig.positionY, newConfig.width, newConfig.height)
        }

        if (oldConfig.url != newConfig.url && newConfig.type == OverlayType.WEB) {
            overlayWebView?.loadUrl(newConfig.url)
        }

        if (oldConfig.imagePath != newConfig.imagePath && newConfig.type == OverlayType.IMAGE) {
            loadImage(newConfig.imagePath)
        }
    }

    fun updatePositionAndSize(x: Int, y: Int, width: Int, height: Int) {
        val screenSize = ScreenUtils.getScreenSize(context)
        val (cw, ch) = ScreenUtils.clampSize(width, height, screenSize.x, screenSize.y)
        val (cx, cy) = ScreenUtils.clampPosition(x, y, cw, ch, screenSize.x, screenSize.y)

        layoutParams?.apply {
            this.x = cx
            this.y = cy
            this.width = cw
            this.height = ch
        }

        if (isAdded) {
            try {
                windowManager.updateViewLayout(containerView, layoutParams)
                if (config.type == OverlayType.WEB) {
                    overlayWebView?.updateScaleToFit(cw)
                }
            } catch (e: Exception) {
                Logger.e("Failed to update position/size", e)
            }
        }
    }

    fun show() {
        containerView?.visibility = View.VISIBLE
        config = config.copy(isVisible = true)
        onConfigChanged(config)
    }

    fun hide() {
        containerView?.visibility = View.GONE
        config = config.copy(isVisible = false)
        onConfigChanged(config)
    }

    fun lock() {
        config = config.copy(isLocked = true)
        applyLockState()
        onConfigChanged(config)
        Logger.overlayLock(config.name)
    }

    fun unlock() {
        config = config.copy(isLocked = false)
        applyLockState()
        onConfigChanged(config)
        Logger.overlayUnlock(config.name)
    }

    fun reload() {
        overlayWebView?.reload()
    }

    fun resetPosition() {
        val screenSize = ScreenUtils.getScreenSize(context)
        val defaultX = 100
        val defaultY = 100
        val defaultW = 400
        val defaultH = 300
        updatePositionAndSize(defaultX, defaultY, defaultW, defaultH)
        config = config.copy(
            positionX = defaultX, positionY = defaultY,
            width = defaultW, height = defaultH,
            opacity = 1.0f, scale = 1.0f
        )
        applyOpacity()
        onConfigChanged(config)
    }

    fun getConfig(): OverlayConfig = config

    /**
     * Snapshot of what's ACTUALLY on screen right now — reads straight from the
     * live `layoutParams`, not [config]. Needed because [updatePositionAndSize]
     * (used when auto-switching to a per-app "App Layout") deliberately updates
     * only the window's live geometry and never touches [config]/persists
     * anything, so that switching apps doesn't silently overwrite the overlay's
     * Default position in storage. [getConfig] therefore keeps returning the
     * Default even while a per-app layout is being displayed — this is the
     * accessor "Simpan Posisi Sekarang" needs instead, so it saves whatever is
     * actually visible right now (Default or per-app) rather than stale Default
     * values.
     */
    data class LiveGeometry(val x: Int, val y: Int, val width: Int, val height: Int)

    fun getLiveGeometry(): LiveGeometry = LiveGeometry(
        x = layoutParams?.x ?: config.positionX,
        y = layoutParams?.y ?: config.positionY,
        width = layoutParams?.width ?: config.width,
        height = layoutParams?.height ?: config.height
    )

    /** Live on-screen opacity — see [getLiveGeometry] for why this can differ from `config.opacity`. */
    fun getLiveOpacity(): Float = containerView?.alpha ?: config.opacity

    /**
     * Sets the window's visible opacity directly, WITHOUT touching [config] or
     * persisting anything — the opacity counterpart to [updatePositionAndSize].
     * Used when auto-applying a per-app "App Layout"'s saved opacity as the
     * foreground app changes.
     */
    fun updateLiveOpacity(opacity: Float) {
        containerView?.alpha = opacity.coerceIn(0.10f, 1f)
    }

    fun destroy() {
        Logger.overlayRemove(config.name)
        try {
            overlayWebView?.destroy()
            overlayWebView = null
            imageView = null
            moveHandle = null
            resizeHandle = null
            if (isAdded) {
                windowManager.removeView(containerView)
                isAdded = false
            }
            containerView?.removeAllViews()
            containerView = null
        } catch (e: Exception) {
            Logger.e("Error destroying overlay window", e)
        }
    }

    fun clearCache() {
        overlayWebView?.clearCache()
    }

    fun onScreenSizeChanged() {
        if (!isAdded) return
        val screenSize = ScreenUtils.getScreenSize(context)
        val w = layoutParams?.width ?: config.width
        val h = layoutParams?.height ?: config.height
        val x = layoutParams?.x ?: config.positionX
        val y = layoutParams?.y ?: config.positionY

        val (cw, ch) = ScreenUtils.clampSize(w, h, screenSize.x, screenSize.y)
        val (cx, cy) = ScreenUtils.clampPosition(x, y, cw, ch, screenSize.x, screenSize.y)

        updatePositionAndSize(cx, cy, cw, ch)
    }
}
