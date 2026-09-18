package siroha.floating.donation.util

import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Warna aksen "dynamic color" (Material You) yang dipakai floating menu &
 * panel-panelnya (BubbleMenu, OverlayPanels).
 *
 * Di Android 12+ (API 31+) diambil dari palet tonal sistem
 * (`android.R.color.system_accent1_*`) — hasil ekstraksi warna wallpaper user,
 * jadi beda perangkat/wallpaper, beda warna aksennya, sesuai wallpaper masing2
 * user (bukan ungu hardcode kayak sebelumnya). Di bawah API 31 dynamic color
 * belum ada di OS sama sekali, jadi dipakai [FALLBACK] ungu brand seperti versi
 * lama supaya tetap ada warna yang jelas.
 */
object DynamicColor {
    private const val FALLBACK = "#9C7BFF"

    /** Aksen terang untuk dipakai di atas background gelap (tone 200 = pop, tetap kebaca). */
    fun accent(context: Context): Int = tonalColor(context, android.R.color.system_accent1_200)

    /** Versi lebih pekat/gelap dari aksen yang sama, buat fill tonal yang lebih tebal (mis. track pill). */
    fun accentDeep(context: Context): Int = tonalColor(context, android.R.color.system_accent1_400)

    private fun tonalColor(context: Context, resId: Int): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Color.parseColor(FALLBACK)
        return try {
            ContextCompat.getColor(context, resId)
        } catch (e: Exception) {
            Color.parseColor(FALLBACK)
        }
    }
}
