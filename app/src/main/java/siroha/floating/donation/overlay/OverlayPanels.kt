package siroha.floating.donation.overlay

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import siroha.floating.donation.R
import siroha.floating.donation.model.OverlayConfig
import siroha.floating.donation.util.Logger
import siroha.floating.donation.util.ScreenUtils
import siroha.floating.donation.util.UrlValidator

/**
 * Shared plumbing for the small floating panels opened from the bubble menu
 * (Pengaturan Bubble, Tambah Overlay Baru, Kustomisasi Overlay). Each panel is
 * its own draggable WindowManager window, same idea as [BubbleMenu]: a
 * "∷ Geser" handle strip at the top lets it be dragged around; everything
 * below it is normal, focusable content (so text fields/sliders work).
 *
 * Visual language follows Material 3 Expressive: large rounded corners,
 * tonal (low-opacity accent-on-surface) fills for chips/buttons instead of
 * flat text, a soft outline on the card, and a bold pill-shaped slider.
 */
internal abstract class BasePanel(protected val context: Context) {

    protected val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    protected var rootView: View? = null
    protected var layoutParams: WindowManager.LayoutParams? = null
    protected var isAdded = false

    protected val density = context.resources.displayMetrics.density
    protected fun dp(v: Int) = (v * density).toInt()

    /**
     * Resolves a Material You (Android 12+/API 31+) wallpaper-derived system
     * color, falling back to a fixed brand hex on older OS versions where
     * dynamic color doesn't exist. Every accent used in these panels goes
     * through this instead of a hardcoded hex so the UI follows the user's
     * system theme. Shared with [BubbleMenu] and [FloatingBubble] via
     * DynamicColors.kt so the whole floating UI stays in sync.
     */
    protected val accentColor: Int by lazy { resolveAccentColor(context) }
    protected val secondaryColor: Int by lazy { resolveSecondaryColor(context) }
    protected val dangerColor: Int by lazy { resolveDangerColor(context) }

    fun isShowing(): Boolean = isAdded

    fun getPosition(): Pair<Int, Int> = Pair(layoutParams?.x ?: 0, layoutParams?.y ?: 0)

    /** Re-measures + re-applies the window's layout params — used after content height changes. */
    protected fun requestResize() {
        val root = rootView ?: return
        val params = layoutParams ?: return
        try {
            windowManager.updateViewLayout(root, params)
        } catch (e: Exception) {
            Logger.e("Failed to resize floating panel", e)
        }
    }

    /** Tonal fill: [pct] percent opacity of [color] on the dark surface. */
    private fun tonal(color: Int, pct: Int): Int =
        Color.argb(pct, Color.red(color), Color.green(color), Color.blue(color))

    /** Card container + header. */
    protected fun buildCard(): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bubble_menu_background)
            setPadding(dp(16), dp(6), dp(16), dp(16))
        }
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(26))
        }
        // Drag handle as a plain pill (no text/icon) — the classic bottom-sheet
        // grab-handle shape, familiar enough on its own without a "Geser" label.
        header.addView(View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(Color.argb(140, 255, 255, 255))
            }
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4))
        })
        card.addView(header)
        setupDrag(header)
        return card
    }

    protected fun sectionTitle(iconRes: Int, label: String): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(14))
        }
        row.addView(ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(accentColor)
            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) }
        })
        row.addView(TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        })
        return row
    }

    protected fun divider(): View = View(context).apply {
        setBackgroundColor(Color.argb(28, 255, 255, 255))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(12)
            bottomMargin = dp(12)
        }
    }

    /**
     * A tappable row. When [filled] is true it renders as a pill-shaped tonal
     * button (rounded, low-opacity fill of [color]) — used for primary/bulk
     * actions. When false it's a plain colored label+icon row (secondary
     * actions like "Tutup"/"Batal").
     */
    protected fun actionButton(
        label: String,
        color: Int,
        iconRes: Int? = null,
        filled: Boolean = false,
        textSizeSp: Float = 14f,
        iconSizeDp: Int = 18,
        onClick: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            if (filled) {
                background = GradientDrawable().apply {
                    cornerRadius = dp(28).toFloat()
                    setColor(tonal(color, 36))
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
            } else {
                setPadding(dp(10), dp(10), dp(10), dp(10))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        if (iconRes != null) {
            row.addView(ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(color)
                layoutParams = LinearLayout.LayoutParams(dp(iconSizeDp), dp(iconSizeDp)).apply { marginEnd = dp(8) }
            })
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(color)
            textSize = textSizeSp
            setTypeface(typeface, Typeface.BOLD)
            // "Matikan Semua" is longer than "Buka Semua" and was wrapping onto a
            // second line inside the same fixed-width pill, which made that
            // button taller than its neighbor so the pair looked misaligned
            // (visible in the user's screenshot). Force a single line — combined
            // with the smaller textSizeSp/iconSizeDp passed for the bulk-action
            // row below — so both pills end up the same height.
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        return row
    }

    /** Small circular tonal icon button (used for per-item lock/delete). */
    protected fun iconChip(iconRes: Int, tint: Int, sizeDp: Int = 34, onClick: () -> Unit): FrameLayout {
        val chip = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tonal(tint, 32))
            }
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginStart = dp(8) }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        chip.addView(ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(tint)
            layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
        })
        return chip
    }

    /** Styles a Switch as a filled tonal pill (checked = [accent], unchecked = translucent white). */
    protected fun styleSwitch(switch: Switch, accent: Int) {
        switch.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(accent, Color.argb(70, 255, 255, 255))
        )
        switch.thumbTintList = ColorStateList.valueOf(Color.WHITE)
    }

    /**
     * A labelled 0..100-style slider row. Returns the SeekBar for further wiring.
     *
     * The visible track is NOT the SeekBar's own `progressDrawable` — see the
     * long history above (changelog #32-#35) of that track staying invisible
     * no matter how thick/opaque/correctly-ordered the custom
     * LayerDrawable+ClipDrawable was, because the platform's default SeekBar
     * style keeps re-tinting/re-theming a `progressDrawable` in ways that
     * are inconsistent across OEM skins even after nulling the tint lists.
     * Instead the track is two plain `View`s (`trackBg` = full-width grey
     * pill, `trackFill` = accent pill resized by plain `layoutParams.width`
     * on every progress change) stacked in a `FrameLayout` UNDER the SeekBar.
     * The SeekBar itself is left with a fully transparent progressDrawable
     * and is only there for touch handling + the thumb (which was already
     * rendering correctly, since `seek.thumb` is a plain Drawable assignment
     * the framework doesn't re-tint the same way). A plain View's width is
     * never touched by any theme/tint machinery, so this is guaranteed to
     * actually show up regardless of device/theme.
     */
    protected fun sliderRow(
        labelPrefix: String,
        initialPercentOrValue: Int,
        rangeMin: Int,
        rangeMax: Int,
        unit: String,
        accent: Int = accentColor,
        onChange: (Int, TextView) -> Unit
    ): Pair<LinearLayout, SeekBar> {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            }
        }
        val label = TextView(context).apply {
            text = "$labelPrefix: $initialPercentOrValue$unit"
            setTextColor(Color.argb(220, 255, 255, 255))
            textSize = 13f
            setPadding(0, 0, 0, dp(4))
        }
        container.addView(label)

        val span = rangeMax - rangeMin
        val rowHeightDp = 32
        val trackThicknessDp = 8
        // Matches the SeekBar's own horizontal padding (10dp) + thumb radius
        // (10dp) below — i.e. exactly where the SeekBar's thumb center
        // actually travels — so the drawn track lines up under the thumb
        // instead of overshooting or falling short of it at either end.
        val edgeInsetDp = 20

        val sliderStack = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(rowHeightDp))
        }

        val trackBg = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(trackThicknessDp / 2).toFloat()
                setColor(tonal(Color.WHITE, 70))
            }
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(trackThicknessDp), Gravity.CENTER_VERTICAL).apply {
                marginStart = dp(edgeInsetDp)
                marginEnd = dp(edgeInsetDp)
            }
        }
        sliderStack.addView(trackBg)

        val trackFill = View(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(trackThicknessDp / 2).toFloat()
                setColor(accent)
            }
            layoutParams = FrameLayout.LayoutParams(dp(trackThicknessDp), dp(trackThicknessDp), Gravity.CENTER_VERTICAL or Gravity.START).apply {
                marginStart = dp(edgeInsetDp)
            }
        }
        sliderStack.addView(trackFill)

        val seek = SeekBar(context).apply {
            max = span
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, dp(rowHeightDp))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            // Fully transparent: this SeekBar only handles touch + draws the
            // thumb now. The actual visible track is trackBg/trackFill above.
            progressDrawable = GradientDrawable().apply { setColor(Color.TRANSPARENT) }
            thumb = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setStroke(dp(3), accent)
                setSize(dp(20), dp(20))
            }
            splitTrack = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                progressTintList = null
                progressBackgroundTintList = null
                thumbTintList = null
            }
        }
        sliderStack.addView(seek)

        fun updateFill(progress: Int) {
            val travel = trackBg.width // 0 until the first layout pass
            if (travel <= 0) return
            val fraction = if (span == 0) 0f else progress.toFloat() / span.toFloat()
            val fillWidth = (dp(trackThicknessDp) + ((travel - dp(trackThicknessDp)) * fraction)).toInt()
            val params = trackFill.layoutParams as FrameLayout.LayoutParams
            if (params.width != fillWidth) {
                params.width = fillWidth
                trackFill.layoutParams = params
            }
        }

        seek.progress = (initialPercentOrValue - rangeMin).coerceIn(0, span)
        // trackBg has no real width yet on this first pass (layout hasn't run),
        // so also sync once its width becomes known.
        trackBg.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateFill(seek.progress) }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + rangeMin
                label.text = "$labelPrefix: $value$unit"
                updateFill(progress)
                onChange(value, label)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        container.addView(sliderStack)
        return Pair(container, seek)
    }

    protected fun show(anchorX: Int, anchorY: Int, widthDp: Int, card: View) {
        dismiss()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        // No FLAG_NOT_FOCUSABLE (flags = 0): these panels contain EditText/SeekBar
        // which need to receive touch+keyboard focus (unlike the bubble/menu,
        // which are pure display+tap surfaces).
        val params = WindowManager.LayoutParams(
            dp(widthDp),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX
            y = anchorY
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
        }
        layoutParams = params
        rootView = card
        try {
            windowManager.addView(card, params)
            isAdded = true
        } catch (e: Exception) {
            Logger.e("Failed to show floating panel", e)
        }
    }

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val clickThreshold = 10

    private fun setupDrag(handle: View) {
        handle.setOnTouchListener { v, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > clickThreshold || kotlin.math.abs(dy) > clickThreshold) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val screenSize = ScreenUtils.getScreenSize(context)
                        val w = rootView?.width?.takeIf { it > 0 } ?: params.width
                        val h = rootView?.height?.takeIf { it > 0 } ?: dp(200)
                        val (cx, cy) = ScreenUtils.clampPosition(
                            initialX + dx, initialY + dy, w, h, screenSize.x, screenSize.y
                        )
                        params.x = cx
                        params.y = cy
                        try {
                            windowManager.updateViewLayout(rootView, params)
                        } catch (e: Exception) {
                            Logger.e("Failed to drag panel", e)
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun dismiss() {
        if (!isAdded) return
        try {
            windowManager.removeView(rootView)
        } catch (e: Exception) {
            Logger.e("Failed to dismiss floating panel", e)
        }
        rootView = null
        layoutParams = null
        isAdded = false
    }
}

/** "Pengaturan Bubble" — live preview + size/transparency sliders for the floating bubble. */
internal class BubbleSettingsPanel(context: Context) : BasePanel(context) {

    fun show(
        anchorX: Int,
        anchorY: Int,
        initialSize: Int,
        initialOpacity: Float,
        onSave: (size: Int, opacity: Float) -> Unit,
        onClose: () -> Unit
    ) {
        val card = buildCard()
        card.addView(sectionTitle(R.drawable.ic_bubble, "Pengaturan Bubble"))

        var size = initialSize
        var opacityPercent = (initialOpacity * 100).toInt()

        val previewFrame = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(255, 10, 10, 12))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(140))
        }
        val previewCircle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accentColor)
            }
        }
        fun layoutPreview() {
            val px = dp(size).coerceIn(dp(24), dp(120))
            previewCircle.layoutParams = FrameLayout.LayoutParams(px, px, Gravity.CENTER)
            previewCircle.alpha = opacityPercent / 100f
        }
        previewFrame.addView(previewCircle)
        card.addView(previewFrame)
        layoutPreview()

        val (sizeRow, _) = sliderRow("Ukuran", size, 30, 120, "dp") { value, _ ->
            size = value
            layoutPreview()
        }
        card.addView(sizeRow)

        val (opacityRow, _) = sliderRow("Transparansi", opacityPercent, 20, 100, "%") { value, _ ->
            opacityPercent = value
            layoutPreview()
        }
        card.addView(opacityRow)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
        }
        buttonsRow.addView(actionButton("Simpan", accentColor, R.drawable.ic_check, filled = true) {
            onSave(size, opacityPercent / 100f)
        }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        buttonsRow.addView(actionButton("Tutup", secondaryColor, R.drawable.ic_close) {
            onClose()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        card.addView(buttonsRow)

        show(anchorX, anchorY, 260, card)
    }
}

/** "Tambah Overlay Baru" — name/link/transparency form that creates + activates a new overlay. */
internal class AddOverlayPanel(context: Context) : BasePanel(context) {

    fun show(
        anchorX: Int,
        anchorY: Int,
        onAdd: (name: String, url: String, opacity: Float) -> Unit,
        onCancel: () -> Unit
    ) {
        val card = buildCard()
        card.addView(sectionTitle(R.drawable.ic_add_circle, "Tambah Overlay Baru"))

        fun underlinedInput(hintText: String, fieldInputType: Int): EditText {
            val edit = EditText(context).apply {
                this.hint = hintText
                setHintTextColor(Color.argb(120, 255, 255, 255))
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.TRANSPARENT)
                textSize = 14f
                this.inputType = fieldInputType
                setSingleLine(true)
            }
            val wrapper = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(6)
                }
            }
            wrapper.addView(edit)
            wrapper.addView(View(context).apply {
                setBackgroundColor(Color.argb(90, 255, 255, 255))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            })
            card.addView(wrapper)
            return edit
        }

        val nameInput = underlinedInput("Nama overlay", InputType.TYPE_CLASS_TEXT)
        val linkInput = underlinedInput("Link overlay (https://...)", InputType.TYPE_TEXT_VARIATION_URI or InputType.TYPE_CLASS_TEXT)

        var opacityPercent = 100
        val (opacityRow, _) = sliderRow("Transparansi", opacityPercent, 20, 100, "%") { value, _ ->
            opacityPercent = value
        }
        card.addView(opacityRow)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(14)
            }
        }
        buttonsRow.addView(actionButton("Tambah & Aktifkan", accentColor, R.drawable.ic_check, filled = true) {
            val name = nameInput.text.toString().trim()
            val url = linkInput.text.toString().trim()
            when {
                name.isEmpty() -> Toast.makeText(context, "Nama overlay tidak boleh kosong", Toast.LENGTH_SHORT).show()
                !UrlValidator.isValid(url) -> Toast.makeText(
                    context,
                    UrlValidator.getErrorMessage(url) ?: "Link overlay tidak valid",
                    Toast.LENGTH_SHORT
                ).show()
                else -> onAdd(name, UrlValidator.sanitize(url), opacityPercent / 100f)
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        buttonsRow.addView(actionButton("Batal", secondaryColor, R.drawable.ic_close) {
            onCancel()
        }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        card.addView(buttonsRow)

        show(anchorX, anchorY, 280, card)
    }
}

/** "Kustomisasi Overlay" — per-overlay active/lock/opacity controls + bulk actions. */
internal class OverlayCustomizationPanel(context: Context) : BasePanel(context) {

    fun show(
        anchorX: Int,
        anchorY: Int,
        overlays: List<OverlayConfig>,
        activeIds: Set<String>,
        onToggleActive: (OverlayConfig, Boolean) -> Unit,
        onToggleLock: (OverlayConfig, Boolean) -> Unit,
        onOpacityChange: (OverlayConfig, Float) -> Unit,
        onDelete: (OverlayConfig) -> Unit,
        onUnlockAll: () -> Unit,
        onDeactivateAll: () -> Unit,
        onClose: () -> Unit
    ) {
        val items = overlays.toMutableList()
        val card = buildCard()
        card.addView(sectionTitle(R.drawable.ic_tune, "Kustomisasi Overlay"))

        val bulkRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            // CENTER_VERTICAL so if either pill still ends up a hair taller than
            // the other (e.g. font metrics rounding), they stay center-aligned
            // instead of top-aligned, which is what made the mismatch obvious.
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        // Smaller text/icon (13sp/16dp instead of the default 14sp/18dp) so
        // "Matikan Semua" — longer than "Buka Semua" — reliably fits on one
        // line inside its half-width pill instead of wrapping to two lines and
        // becoming taller than "Buka Semua" next to it.
        bulkRow.addView(
            actionButton("Buka Semua", accentColor, R.drawable.ic_unlock, filled = true, textSizeSp = 13f, iconSizeDp = 16, onClick = { onUnlockAll() })
                .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) } }
        )
        bulkRow.addView(
            actionButton("Matikan Semua", dangerColor, R.drawable.ic_power_off, filled = true, textSizeSp = 13f, iconSizeDp = 16, onClick = { onDeactivateAll() })
                .apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) } }
        )
        card.addView(bulkRow)

        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val maxScrollHeight = (ScreenUtils.getScreenSize(context).y * 0.45f).toInt()
        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            addView(listContainer)
        }
        card.addView(scroll)

        // ScrollView has no built-in max-height: cap it AFTER the real content
        // height is known so short lists don't leave dead empty space below them.
        fun capScrollHeight() {
            listContainer.post {
                val contentH = listContainer.height
                val targetH = if (contentH > maxScrollHeight) maxScrollHeight else LinearLayout.LayoutParams.WRAP_CONTENT
                if (scroll.layoutParams.height != targetH) {
                    scroll.layoutParams = scroll.layoutParams.apply { height = targetH }
                    requestResize()
                }
            }
        }

        fun rebuildList() {
            listContainer.removeAllViews()
            if (items.isEmpty()) {
                listContainer.addView(TextView(context).apply {
                    text = "Belum ada overlay"
                    setTextColor(Color.argb(150, 255, 255, 255))
                    textSize = 13f
                    setPadding(0, dp(8), 0, dp(8))
                })
                capScrollHeight()
                return
            }
            items.forEachIndexed { index, overlay ->
                val isActive = activeIds.contains(overlay.id) || overlay.isActive
                var opacityPercent = (overlay.opacity * 100).toInt()

                val block = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        cornerRadius = dp(16).toFloat()
                        setColor(Color.argb(60, 255, 255, 255))
                    }
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = if (index == 0) 0 else dp(10)
                    }
                }

                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                topRow.addView(TextView(context).apply {
                    text = overlay.name.ifBlank { "(tanpa nama)" }
                    setTextColor(Color.WHITE)
                    textSize = 15f
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                val activeSwitch = Switch(context).apply {
                    isChecked = isActive
                    text = if (isActive) "Aktif" else "Nonaktif"
                    setTextColor(Color.argb(210, 255, 255, 255))
                    textSize = 11.5f
                }
                styleSwitch(activeSwitch, accentColor)
                activeSwitch.setOnCheckedChangeListener { _, checked ->
                    activeSwitch.text = if (checked) "Aktif" else "Nonaktif"
                    onToggleActive(overlay, checked)
                }
                topRow.addView(activeSwitch)
                block.addView(topRow)

                val actionsRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(6)
                    }
                }
                val (opacityRow, _) = sliderRow("Transparansi", opacityPercent, 10, 100, "%") { value, _ ->
                    opacityPercent = value
                    onOpacityChange(overlay, value / 100f)
                }
                opacityRow.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                actionsRow.addView(opacityRow)

                var locked = overlay.isLocked
                val lockChip = iconChip(
                    if (locked) R.drawable.ic_lock else R.drawable.ic_unlock,
                    secondaryColor
                ) {}
                lockChip.setOnClickListener {
                    locked = !locked
                    (lockChip.getChildAt(0) as ImageView).setImageResource(
                        if (locked) R.drawable.ic_lock else R.drawable.ic_unlock
                    )
                    onToggleLock(overlay, locked)
                }
                actionsRow.addView(lockChip)

                actionsRow.addView(iconChip(R.drawable.ic_delete, dangerColor) {
                    items.remove(overlay)
                    onDelete(overlay)
                    rebuildList()
                })

                block.addView(actionsRow)
                listContainer.addView(block)
            }
            capScrollHeight()
        }
        rebuildList()

        card.addView(actionButton("Selesai", accentColor, R.drawable.ic_check, filled = true) { onClose() }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                this.gravity = Gravity.CENTER_HORIZONTAL
            }
        })

        show(anchorX, anchorY, 300, card)
    }
}
