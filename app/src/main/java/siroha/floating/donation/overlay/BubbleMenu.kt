package siroha.floating.donation.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import siroha.floating.donation.R
import siroha.floating.donation.util.Logger
import siroha.floating.donation.util.ScreenUtils

/**
 * A single row in the floating menu. [iconRes] is a Material-symbol-style
 * vector icon (see res/drawable/ic_*.xml) — the menu no longer uses emoji.
 */
data class BubbleMenuAction(
    val label: String,
    val iconRes: Int? = null,
    val onClick: () -> Unit
)

/**
 * Draggable floating menu that REPLACES the bubble when it's tapped (the bubble
 * is hidden while this is showing). It's its own small overlay window — not a
 * full-screen popup — so it can be dragged around exactly like the bubble.
 *
 * The top of the card is a thin drag-handle strip labelled "∷ Geser" (Indonesian
 * for "drag") — a small, unmistakable affordance instead of an icon that needs
 * interpreting. Tapping anywhere in that strip goes back to the bubble; tapping
 * a row below (separated by a thin divider) runs that action. Tapping a row is
 * a click; moving past the drag threshold is a drag, same click-vs-drag
 * distinction FloatingBubble uses.
 */
class BubbleMenu(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootView: LinearLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isAdded = false

    private var rowHeightPx = 0
    private var headerHeightPx = 0
    private var topPaddingPx = 0
    private var cardWidthPx = 0
    private var rowCount = 0
    private var dividerHeightPx = 0

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val clickThreshold = 10

    fun isShowing(): Boolean = isAdded

    fun getPosition(): Pair<Int, Int> = Pair(layoutParams?.x ?: 0, layoutParams?.y ?: 0)

    fun show(
        anchorX: Int,
        anchorY: Int,
        onCollapse: () -> Unit,
        actions: List<BubbleMenuAction>
    ) {
        dismiss()

        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        rowHeightPx = dp(46)
        headerHeightPx = dp(28)
        topPaddingPx = dp(6)
        cardWidthPx = dp(250)
        dividerHeightPx = dp(1)

        // "Collapse" is kept internally only as index 0's action (what happens
        // when the handle strip is tapped) — it's not rendered as a text row.
        val allRows = listOf(BubbleMenuAction("Collapse", onClick = onCollapse)) + actions
        rowCount = allRows.size

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            cardWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = anchorX
            y = anchorY
        }
        layoutParams = params

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bubble_menu_background)
            setPadding(dp(6), topPaddingPx, dp(6), dp(6))
        }

        // Drag-handle strip: a plain pill (same shape as the sub-panels in
        // OverlayPanels.kt) instead of a text label — familiar bottom-sheet
        // grab-handle affordance, no text needed to explain it.
        card.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                headerHeightPx
            )

            addView(View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(Color.argb(140, 255, 255, 255))
                }
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(4))
            })
        })

        // Thin divider between the handle strip and the action list below it.
        card.addView(View(context).apply {
            setBackgroundColor(Color.argb(50, 255, 255, 255))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dividerHeightPx
            )
        })

        val accent = resolveAccentColor(context)
        actions.forEach { action ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), 0, dp(14), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    rowHeightPx
                )
            }
            if (action.iconRes != null) {
                row.addView(FrameLayout(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(resolveTonal(accent, 30))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(10) }
                    addView(android.widget.ImageView(context).apply {
                        setImageResource(action.iconRes)
                        setColorFilter(accent)
                        layoutParams = FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
                    })
                })
            }
            row.addView(TextView(context).apply {
                text = action.label
                setTextColor(Color.WHITE)
                textSize = 13.5f
                maxLines = 1
            })
            card.addView(row)
        }

        rootView = card
        setupTouchHandling(card, allRows)

        try {
            windowManager.addView(card, params)
            isAdded = true
        } catch (e: Exception) {
            Logger.e("Failed to show bubble menu", e)
        }
    }

    private fun setupTouchHandling(view: LinearLayout, actions: List<BubbleMenuAction>) {
        view.setOnTouchListener { v, event ->
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
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (kotlin.math.abs(dx) > clickThreshold || kotlin.math.abs(dy) > clickThreshold) {
                        isDragging = true
                    }

                    if (isDragging) {
                        val screenSize = ScreenUtils.getScreenSize(context)
                        val w = v.width.takeIf { it > 0 } ?: cardWidthPx
                        val h = v.height.takeIf { it > 0 }
                            ?: (headerHeightPx + dividerHeightPx + rowHeightPx * (rowCount - 1))
                        val newX = initialX + dx
                        val newY = initialY + dy

                        val (cx, cy) = ScreenUtils.clampPosition(
                            newX, newY, w, h, screenSize.x, screenSize.y
                        )
                        layoutParams?.x = cx
                        layoutParams?.y = cy

                        try {
                            windowManager.updateViewLayout(view, layoutParams)
                        } catch (e: Exception) {
                            Logger.e("Failed to drag bubble menu", e)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val relativeY = event.y - topPaddingPx
                        val index = if (relativeY < headerHeightPx) {
                            0
                        } else {
                            val afterDivider = relativeY - headerHeightPx - dividerHeightPx
                            (1 + (afterDivider / rowHeightPx).toInt())
                        }.coerceIn(0, actions.size - 1)
                        actions[index].onClick()
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
            Logger.e("Failed to dismiss bubble menu", e)
        }
        rootView = null
        layoutParams = null
        isAdded = false
    }
}
