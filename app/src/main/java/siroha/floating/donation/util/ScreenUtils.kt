package siroha.floating.donation.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

object ScreenUtils {

    fun getScreenSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val display = wm.defaultDisplay
            val point = Point()
            @Suppress("DEPRECATION")
            display.getRealSize(point)
            point
        }
    }

    fun clampPosition(
        x: Int, y: Int, width: Int, height: Int,
        screenWidth: Int, screenHeight: Int
    ): Pair<Int, Int> {
        val clampedX = x.coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val clampedY = y.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        return Pair(clampedX, clampedY)
    }

    fun clampSize(
        width: Int, height: Int,
        screenWidth: Int, screenHeight: Int,
        minWidth: Int = 100, minHeight: Int = 100
    ): Pair<Int, Int> {
        val clampedWidth = width.coerceIn(minWidth, screenWidth)
        val clampedHeight = height.coerceIn(minHeight, screenHeight)
        return Pair(clampedWidth, clampedHeight)
    }
}
