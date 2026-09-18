package siroha.floating.donation.util

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
)

object Logger {
    private const val TAG = "SirohaDonation"
    private const val MAX_ENTRIES = 500

    var debugEnabled: Boolean = false

    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // In-memory buffer so logs are viewable from inside the app (Settings > Debug >
    // View Logs) instead of only via `adb logcat`. Backed by a SnapshotStateList so
    // any Composable reading it recomposes automatically as new entries arrive.
    val entries = mutableStateListOf<LogEntry>()

    fun clear() {
        entries.clear()
    }

    fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))

    private fun record(level: LogLevel, message: String) {
        if (!debugEnabled) return
        entries.add(LogEntry(System.currentTimeMillis(), level, message))
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }

    fun d(message: String) {
        if (debugEnabled) {
            Log.d(TAG, message)
            record(LogLevel.DEBUG, message)
        }
    }

    fun i(message: String) {
        Log.i(TAG, message)
        record(LogLevel.INFO, message)
    }

    fun w(message: String) {
        Log.w(TAG, message)
        record(LogLevel.WARN, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
        record(LogLevel.ERROR, message + (throwable?.message?.let { ": $it" } ?: ""))
    }

    // Structured events
    fun serviceStart() = i("SERVICE_START")
    fun serviceStop() = i("SERVICE_STOP")
    fun overlayCreate(name: String) = i("OVERLAY_CREATE: $name")
    fun overlayRemove(name: String) = i("OVERLAY_REMOVE: $name")
    fun overlayLock(name: String) = i("OVERLAY_LOCK: $name")
    fun overlayUnlock(name: String) = i("OVERLAY_UNLOCK: $name")
    fun webViewLoad(url: String) = i("WEBVIEW_LOAD: ${sanitizeUrl(url)}")
    fun webViewError(error: String) = e("WEBVIEW_ERROR: $error")
    fun positionChanged(name: String, x: Int, y: Int) = d("POSITION_CHANGED: $name -> ($x, $y)")
    fun layoutChanged(name: String) = d("LAYOUT_CHANGED: $name")
    fun permissionError(permission: String) = e("PERMISSION_ERROR: $permission")

    private fun sanitizeUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme}://${uri.host}${uri.path ?: ""}"
        } catch (e: Exception) {
            "[invalid url]"
        }
    }
}
