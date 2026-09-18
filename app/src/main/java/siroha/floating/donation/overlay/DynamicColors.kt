package siroha.floating.donation.overlay

import android.content.Context
import android.graphics.Color
import android.os.Build

/**
 * Central Material You (Android 12+ / API 31+) dynamic color helpers, shared
 * by EVERY piece of the floating-bubble UI — the bubble icon itself, the
 * bubble menu (6-item list), and the sub-panels (Pengaturan Bubble, Tambah
 * Overlay Baru, Kustomisasi Overlay) — so the whole floating UI follows the
 * user's wallpaper-derived system theme instead of a fixed hardcoded purple.
 *
 * On API < 31 (the OS itself has no dynamic color to read), everything here
 * falls back to the original fixed brand hex values so older devices still
 * get a sensible, consistent look.
 */

private const val ACCENT_FALLBACK = "#9C7BFF"
private const val SECONDARY_FALLBACK = "#FFC107"
private const val DANGER_FALLBACK = "#FF8A80"

private fun dynamicOrFallback(context: Context, dynamicColorRes: Int, fallbackHex: String): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            context.getColor(dynamicColorRes)
        } catch (e: Exception) {
            Color.parseColor(fallbackHex)
        }
    } else {
        Color.parseColor(fallbackHex)
    }

/** Primary dynamic tone — bubble icon background, primary CTAs, active switches, sliders, menu/section icons. */
fun resolveAccentColor(context: Context): Int =
    dynamicOrFallback(context, android.R.color.system_accent1_200, ACCENT_FALLBACK)

/** Secondary dynamic tone (muted variant of the same hue) — lock chip + neutral/secondary actions. */
fun resolveSecondaryColor(context: Context): Int =
    dynamicOrFallback(context, android.R.color.system_accent2_200, SECONDARY_FALLBACK)

/** Tertiary dynamic tone (distinct hue) — destructive actions: delete chip, "Matikan Semua". */
fun resolveDangerColor(context: Context): Int =
    dynamicOrFallback(context, android.R.color.system_accent3_200, DANGER_FALLBACK)

/** Tonal fill: [pct] (0-255) opacity of [color] — used for low-opacity backdrops/pills. */
fun resolveTonal(color: Int, pct: Int): Int =
    Color.argb(pct, Color.red(color), Color.green(color), Color.blue(color))
