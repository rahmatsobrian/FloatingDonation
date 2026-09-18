package siroha.floating.donation.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * A single launchable app on the device, as shown in the app picker used by
 * "Add App Layout" (see AppLayoutDialog). Only apps that expose a launcher
 * activity are listed here — that's what queryIntentActivities(ACTION_MAIN /
 * CATEGORY_LAUNCHER) returns, and it only requires the <queries> declaration
 * in AndroidManifest.xml, not the QUERY_ALL_PACKAGES permission.
 */
data class InstalledAppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable?
)

object InstalledApps {

    /**
     * Queries PackageManager for every app with a launcher entry. This is a
     * blocking call (PackageManager IPC) — always run it off the main thread,
     * e.g. inside `withContext(Dispatchers.IO) { ... }`.
     *
     * Excludes this app's own package, since overlaying over itself isn't
     * a supported use case.
     */
    fun listLaunchable(context: Context): List<InstalledAppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        return resolveInfos
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = try {
                    resolveInfo.loadLabel(pm).toString()
                } catch (e: Exception) {
                    pkg
                }
                val icon = try {
                    resolveInfo.loadIcon(pm)
                } catch (e: Exception) {
                    null
                }
                InstalledAppInfo(appName = label, packageName = pkg, icon = icon)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
    }

    /**
     * Best-effort icon lookup for a package that might not have a launcher
     * activity (e.g. an existing layout entered manually before this picker
     * existed). Also a blocking call — run off the main thread.
     */
    fun loadIcon(context: Context, packageName: String): Drawable? {
        return try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }
}
