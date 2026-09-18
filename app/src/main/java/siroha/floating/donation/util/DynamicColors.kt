package siroha.floating.donation.util

import android.content.Context
import android.os.Build
import androidx.annotation.ColorInt

/**
 * Reads the device's Material You dynamic accent colors (Android 12+/API 31,
 * `android.R.color.system_accent1_*`) for use in the native-View overlay
 * panels (`BubbleMenu`, `OverlayPanels`) — these aren't Compose, so they
 * can't use `MaterialTheme.colorScheme`/`dynamicDarkColorScheme()` like the
 * rest of the app (see `ui/theme/Theme.kt`).
 *
 * Our overlay panels are always dark-card styled regardless of the device's
 * light/dark system setting, so this deliberately always uses the tonal
 * indices Compose's `dynamicDarkColorScheme()` maps `primary`/`onPrimary` to
 * (system_accent1_200 / system_accent1_800) — not the light-theme indices —
 * so a checked switch/slider here matches the same hue as the in-app M3
 * switches, whatever the wallpaper-derived accent happens to be.
 *
 * Mapping source: Android's Material You dynamic color docs and the
 * material-components-android Color.md token tables (dark dynamic API
 * 31-33): primary -> system_accent1_200, onPrimary -> system_accent1_800.
 * `compileSdk` here is 35, so the `android.R.color.system_accent1_*`
 * constants resolve at compile time; the SDK_INT guard just prevents
 * calling `getColor()` on them on pre-31 devices where the resource
 * wouldn't exist at runtime.
 */
object DynamicColors {

    // M3 baseline (non-dynamic) dark-theme primary/onPrimary — used pre-API 31.
    private const val FALLBACK_ACCENT = 0xFFD0BCFF.toInt()
    private const val FALLBACK_ON_ACCENT = 0xFF381E72.toInt()
    private const val FALLBACK_OUTLINE = 0xFF938F99.toInt()

    @ColorInt
    fun accent(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return FALLBACK_ACCENT
        return try {
            context.getColor(android.R.color.system_accent1_200)
        } catch (e: Exception) {
            FALLBACK_ACCENT
        }
    }

    @ColorInt
    fun onAccent(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return FALLBACK_ON_ACCENT
        return try {
            context.getColor(android.R.color.system_accent1_800)
        } catch (e: Exception) {
            FALLBACK_ON_ACCENT
        }
    }

    /** Muted tone for unchecked switch tracks / inactive outlines. */
    @ColorInt
    fun neutralOutline(context: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return FALLBACK_OUTLINE
        return try {
            context.getColor(android.R.color.system_neutral2_500)
        } catch (e: Exception) {
            FALLBACK_OUTLINE
        }
    }
}
