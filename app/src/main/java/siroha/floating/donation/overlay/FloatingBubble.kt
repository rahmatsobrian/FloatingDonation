package siroha.floating.donation.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import siroha.floating.donation.R
import siroha.floating.donation.model.BubbleConfig
import siroha.floating.donation.util.Logger
import siroha.floating.donation.util.ScreenUtils

class FloatingBubble(
    private val context: Context,
    private var config: BubbleConfig,
    private val onBubbleClicked: () -> Unit,
    private val onConfigChanged: (BubbleConfig) -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleView: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAdded = false

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val clickThreshold = 10

    // "Sembunyikan Bubble" dari menu bubble mengaktifkan mode ini: window
    // TETAP ada di posisi yang sama (bukan destroy, bukan matiin bubbleEnabled
    // di storage), cuma dibikin INVISIBLE (bukan GONE, supaya tetap bisa
    // menerima sentuhan di posisi itu) + geser dikunci total. Tap di posisi
    // terakhir itu langsung memunculkannya lagi lewat unghost().
    private var isGhosted = false

    fun create() {
        if (isAdded) return

        val sizePx = (config.size * context.resources.displayMetrics.density).toInt()
        val screenSize = ScreenUtils.getScreenSize(context)
        val (cx, cy) = ScreenUtils.clampPosition(
            config.positionX, config.positionY, sizePx, sizePx,
            screenSize.x, screenSize.y
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            sizePx, sizePx, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cx
            y = cy
        }

        bubbleView = FrameLayout(context).apply {
            val imageView = ImageView(context).apply {
                setImageResource(R.drawable.ic_bubble)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val padding = (8 * context.resources.displayMetrics.density).toInt()
                setPadding(padding, padding, padding, padding)
            }
            addView(
                imageView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            alpha = config.opacity
            // Was a static XML drawable (bubble_background.xml, fixed #6200EE
            // purple) — now built programmatically so the bubble itself also
            // follows the user's Material You wallpaper color like the rest
            // of the floating UI (menu, panels). See DynamicColors.kt.
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(resolveAccentColor(context))
            }
            elevation = 8f * context.resources.displayMetrics.density
        }

        setupTouchHandling()

        try {
            windowManager.addView(bubbleView, layoutParams)
            isAdded = true
        } catch (e: Exception) {
            Logger.e("Failed to add floating bubble", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchHandling() {
        bubbleView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = layoutParams?.x ?: 0
                    initialY = layoutParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Geser dikunci total selama ghosted — tap-nya cuma boleh
                    // ngembaliin bubble, tidak boleh mindahin posisinya dulu.
                    if (!isGhosted) {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()

                        if (kotlin.math.abs(dx) > clickThreshold || kotlin.math.abs(dy) > clickThreshold) {
                            isDragging = true
                        }

                        if (isDragging) {
                            val screenSize = ScreenUtils.getScreenSize(context)
                            val sizePx = layoutParams?.width ?: (config.size * context.resources.displayMetrics.density).toInt()
                            val newX = initialX + dx
                            val newY = initialY + dy

                            val (cx, cy) = ScreenUtils.clampPosition(
                                newX, newY, sizePx, sizePx,
                                screenSize.x, screenSize.y
                            )
                            layoutParams?.x = cx
                            layoutParams?.y = cy

                            try {
                                windowManager.updateViewLayout(bubbleView, layoutParams)
                            } catch (e: Exception) {
                                Logger.e("Failed to update bubble layout", e)
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (isGhosted) {
                        // Tap di posisi terakhir -> langsung muncul lagi, tidak
                        // buka menu (beda dari tap normal saat bubble terlihat).
                        unghost()
                    } else if (!isDragging) {
                        onBubbleClicked()
                    } else {
                        // Snap to edge if enabled
                        if (config.snapToEdge) {
                            snapToEdge()
                        }
                        // Save position
                        config = config.copy(
                            positionX = layoutParams?.x ?: config.positionX,
                            positionY = layoutParams?.y ?: config.positionY
                        )
                        onConfigChanged(config)
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val screenSize = ScreenUtils.getScreenSize(context)
        val currentX = layoutParams?.x ?: 0
        val sizePx = layoutParams?.width ?: (config.size * context.resources.displayMetrics.density).toInt()

        val targetX = if (currentX + sizePx / 2 < screenSize.x / 2) {
            0 // Snap to left
        } else {
            screenSize.x - sizePx // Snap to right
        }

        layoutParams?.x = targetX
        try {
            windowManager.updateViewLayout(bubbleView, layoutParams)
        } catch (e: Exception) {
            Logger.e("Failed to snap bubble", e)
        }
    }

    fun updateConfig(newConfig: BubbleConfig) {
        config = newConfig
        bubbleView?.alpha = config.opacity

        val sizePx = (config.size * context.resources.displayMetrics.density).toInt()
        layoutParams?.width = sizePx
        layoutParams?.height = sizePx

        if (isAdded) {
            try {
                windowManager.updateViewLayout(bubbleView, layoutParams)
            } catch (e: Exception) {
                Logger.e("Failed to update bubble config", e)
            }
        }
    }

    fun show() {
        isGhosted = false
        bubbleView?.visibility = android.view.View.VISIBLE
        bubbleView?.alpha = config.opacity
    }

    fun hide() {
        bubbleView?.visibility = android.view.View.GONE
    }

    /**
     * "Sembunyikan Bubble" di menu bubble — BEDA dari [hide]: ini TIDAK
     * menyentuh `bubbleEnabled` (bubble tetap dianggap "on" secara status),
     * dan window-nya tidak di-destroy, cuma dibikin invisible di posisi
     * terakhirnya + geser dikunci (lihat `ACTION_MOVE` di [setupTouchHandling]).
     *
     * PAKAI `alpha = 0f`, BUKAN `visibility = INVISIBLE` (percobaan sebelumnya)
     * — ternyata `INVISIBLE` bikin window ini berhenti nerima sentuhan sama
     * sekali (tap di posisi terakhir tidak memunculkan apa-apa), kemungkinan
     * karena `bubbleView` adalah ROOT content dari window-nya sendiri (bukan
     * child View di dalam ViewGroup lain), dan behavior touch-dispatch untuk
     * root view yang non-VISIBLE ternyata tidak konsisten/tidak bisa
     * diandalkan.
     *
     * PENTING (akar masalah kenapa fix alpha-only di atas TETAP tidak cukup):
     * fungsi ini SELALU dipanggil dari dalam bubble menu (lihat
     * `OverlayService.showBubbleMenu()`), dan menu itu sendiri dibuka dengan
     * cara manggil [hide] duluan (`visibility = GONE`) buat nyembunyiin bubble
     * selagi menunya tampil. Jalur "Sembunyikan Bubble" cuma nutup menu-nya
     * (`bubbleMenu?.dismiss()`) lalu manggil fungsi ini — TIDAK PERNAH lewat
     * [show]/`collapseBubbleMenu()` yang biasanya mengembalikan
     * `visibility = VISIBLE`. Jadi `bubbleView` masuk ke fungsi ini dalam
     * keadaan MASIH `GONE` sisa dari `hide()` di awal, dan set `alpha = 0f`
     * doang tidak mengubah itu — window tetap tidak menerima sentuhan sama
     * sekali, persis gejala yang dilaporkan lagi. Makanya di sini WAJIB juga
     * eksplisit set `visibility = VISIBLE` sebelum menerapkan `alpha = 0f`,
     * supaya urutannya jadi: pulihkan dulu ke VISIBLE (baru bisa menerima
     * sentuhan), baru dibikin transparan total secara visual.
     */
    fun hideAsGhost() {
        isGhosted = true
        bubbleView?.visibility = android.view.View.VISIBLE
        bubbleView?.alpha = 0f
    }

    private fun unghost() {
        isGhosted = false
        bubbleView?.visibility = android.view.View.VISIBLE
        bubbleView?.alpha = config.opacity
    }

    fun isGhosted(): Boolean = isGhosted

    fun destroy() {
        try {
            if (isAdded) {
                windowManager.removeView(bubbleView)
                isAdded = false
            }
            bubbleView = null
        } catch (e: Exception) {
            Logger.e("Error destroying floating bubble", e)
        }
    }

    fun getPosition(): Pair<Int, Int> = Pair(layoutParams?.x ?: 0, layoutParams?.y ?: 0)

    fun getSizePx(): Int = layoutParams?.width
        ?: (config.size * context.resources.displayMetrics.density).toInt()

    /** Moves the bubble window to an absolute screen position and persists it. */
    fun moveTo(x: Int, y: Int) {
        layoutParams?.x = x
        layoutParams?.y = y
        if (isAdded) {
            try {
                windowManager.updateViewLayout(bubbleView, layoutParams)
            } catch (e: Exception) {
                Logger.e("Failed to move floating bubble", e)
            }
        }
        config = config.copy(positionX = x, positionY = y)
        onConfigChanged(config)
    }

    fun onScreenSizeChanged() {
        if (!isAdded) return
        val screenSize = ScreenUtils.getScreenSize(context)
        val sizePx = layoutParams?.width ?: (config.size * context.resources.displayMetrics.density).toInt()
        val (cx, cy) = ScreenUtils.clampPosition(
            layoutParams?.x ?: 0, layoutParams?.y ?: 0,
            sizePx, sizePx, screenSize.x, screenSize.y
        )
        layoutParams?.x = cx
        layoutParams?.y = cy
        try {
            windowManager.updateViewLayout(bubbleView, layoutParams)
        } catch (e: Exception) {
            Logger.e("Failed to clamp bubble on screen change", e)
        }
    }
}
