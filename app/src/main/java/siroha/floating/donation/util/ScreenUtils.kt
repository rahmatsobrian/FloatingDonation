package siroha.floating.donation.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.Log
import android.view.WindowManager

/**
 * Preset posisi untuk fitur "Quick Align" — cara cepat & andal buat
 * nempelin overlay ke sudut/tepi/tengah layar pakai satu tap, tanpa harus
 * drag manual (sangat berguna di landscape, di mana drag ke tepi kanan
 * dulunya bermasalah). Lihat [ScreenUtils.alignPosition].
 */
enum class AlignPosition {
    TOP_LEFT, TOP, TOP_RIGHT,
    LEFT, CENTER, RIGHT,
    BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
}

object ScreenUtils {

    private const val TAG = "ScreenUtils"

    /**
     * Ukuran layar SAAT INI dalam piksel, sudah disesuaikan dengan rotasi
     * (orientation) yang sedang aktif.
     *
     * INI AKAR MASALAH bug "taruh overlay di kanan layar horizontal lalu simpan
     * posisi, overlay tidak kembali ke posisi kanan":
     *
     * Sebelumnya ukuran dibaca lewat `WindowManager.currentWindowMetrics` dari
     * context OverlayService (context *non-Activity*). Untuk context Service,
     * `currentWindowMetrics` TIDAK selalu mengikuti rotasi layar yang
     * sebenarnya — di banyak device ia melaporkan bound orientasi "natural"
     * (portrait) walau layar lagi landscape. Akibatnya, posisi x yang besar
     * (overlay di kanan layar landscape) di-clamp memakai lebar portrait pas
     * di-apply ulang (di `updatePositionAndSize` / `create` / dialog),
     * sehingga overlay "lompat" balik ke kiri — bukan kembali ke kanan.
     *
     * FIX: baca langsung dari objek `Display` (`getRealSize`), yang
     * didokumentasikan "adjusted based on the current rotation of the display"
     * — persis ruang koordinat tempat overlay window benar-benar ditempatkan
     * (overlay SYSTEM_ALERT_WINDOW digambar di atas seluruh layar, jadi
     * memang TIDAK boleh dikurangi inset window). `currentWindowMetrics` cuma
     * dipakai jadi fallback terakhir kalau Display null/throw.
     */
    @Suppress("DEPRECATION")
    fun getScreenSize(context: Context): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // Sumber utama: Display secara langsung. getRealSize() menghormati
        // rotasi layar saat ini, dan untuk overlay ini memang yang kita mau
        // (full real size, tanpa inset — overlay mengisi seluruh layar).
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display ?: wm.defaultDisplay
            } else {
                wm.defaultDisplay
            }
            if (display != null) {
                val point = Point()
                display.getRealSize(point)
                if (point.x > 0 && point.y > 0) {
                    return point
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Display.getRealSize failed, falling back to window metrics", e)
        }

        // Fallback terakhir: window metrics. CATATAN: di context Service ini
        // bisa jadi tidak mengikuti rotasi (lihat keterangan di atas) — lebih
        // baik daripada nilai hardcoded, tapi tetap bukan sumber utama.
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val bounds = wm.currentWindowMetrics.bounds
                val w = bounds.width()
                val h = bounds.height()
                if (w > 0 && h > 0) Point(w, h) else DEFAULT_SIZE
            } catch (e: Exception) {
                DEFAULT_SIZE
            }
        } else {
            DEFAULT_SIZE
        }
    }

    private val DEFAULT_SIZE = Point(1080, 1920)

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

    /**
     * Menyusutkan (width, height) agar PAS di dalam (maxWidth, maxHeight)
     * sambil menjaga rasio aspek (tidak menyengkol/pecah). Tidak pernah
     * memperbesar — hanya memperkecil. Dipakai fitur "Fit to Screen" dan saat
     * overlay dipindah ke orientasi yang ukurannya lebih kecil dari saat
     * disimpan.
     */
    fun fitKeepAspect(
        width: Int, height: Int,
        maxWidth: Int, maxHeight: Int
    ): Pair<Int, Int> {
        if (width <= 0 || height <= 0 ||
            maxWidth <= 0 || maxHeight <= 0
        ) {
            return Pair(maxWidth.coerceAtLeast(1), maxHeight.coerceAtLeast(1))
        }
        val scale = minOf(
            maxWidth.toFloat() / width.toFloat(),
            maxHeight.toFloat() / height.toFloat(),
            1f // jangan pernah memperbesar
        )
        val scaledW = (width * scale).toInt().coerceAtLeast(1)
        val scaledH = (height * scale).toInt().coerceAtLeast(1)
        return Pair(scaledW, scaledH)
    }

    /**
     * Posisi (x, y) target untuk satu preset [AlignPosition], dihitung dari
     * ukuran layar SAAT INI (harus lewat [getScreenSize] supaya rotasi-aman).
     * Ukuran overlay di sini SEHARUSNYA ukuran live-nya (bisa saja sudah
     * di-resize user), jadi preset ini menempatkan overlay persis di tempat
     * yang dimaksud di orientasi apapun.
     */
    fun alignPosition(
        align: AlignPosition,
        width: Int, height: Int,
        screenWidth: Int, screenHeight: Int
    ): Pair<Int, Int> {
        val maxX = (screenWidth - width).coerceAtLeast(0)
        val maxY = (screenHeight - height).coerceAtLeast(0)
        val x = when (align) {
            AlignPosition.TOP_LEFT, AlignPosition.LEFT, AlignPosition.BOTTOM_LEFT -> 0
            AlignPosition.TOP, AlignPosition.CENTER, AlignPosition.BOTTOM -> maxX / 2
            AlignPosition.TOP_RIGHT, AlignPosition.RIGHT, AlignPosition.BOTTOM_RIGHT -> maxX
        }
        val y = when (align) {
            AlignPosition.TOP_LEFT, AlignPosition.TOP, AlignPosition.TOP_RIGHT -> 0
            AlignPosition.LEFT, AlignPosition.CENTER, AlignPosition.RIGHT -> maxY / 2
            AlignPosition.BOTTOM_LEFT, AlignPosition.BOTTOM, AlignPosition.BOTTOM_RIGHT -> maxY
        }
        return Pair(x, y)
    }
}
