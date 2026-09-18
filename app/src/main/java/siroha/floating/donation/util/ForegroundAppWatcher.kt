package siroha.floating.donation.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process

/**
 * Mendeteksi package aplikasi yang sedang tampil di depan (foreground) memakai
 * UsageStatsManager. Dipakai supaya overlay bisa otomatis pindah ke App Layout
 * (posisi + ukuran + opacity) yang sudah diatur khusus untuk aplikasi itu --
 * mis. Mobile Legends pakai posisi/ukuran sendiri, aplikasi lain pakai
 * posisi/ukuran sendiri (lihat `OverlayService.onForegroundAppChanged`).
 *
 * Butuh izin "Usage Access" (PACKAGE_USAGE_STATS) yang wajib diaktifkan manual
 * oleh user lewat halaman Settings sistem -- tidak bisa lewat dialog izin biasa.
 * Tanpa izin ini, [getCurrentForegroundPackage] selalu null dan overlay tetap
 * memakai layout Default seperti biasa (fitur App Layout per aplikasi cuma
 * tidak akan pernah otomatis aktif, tidak ada yang crash/rusak).
 */
object ForegroundAppWatcher {

    /** Cek apakah izin Usage Access sudah aktif untuk app ini. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Ambil satu kali package yang sedang di foreground dengan menelusuri event
     * penggunaan aplikasi beberapa detik terakhir. Null kalau izin belum aktif
     * atau belum ada event yang tertangkap.
     */
    fun getCurrentForegroundPackage(context: Context): String? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val end = System.currentTimeMillis()
        val start = end - 8_000 // 8 detik terakhir cukup untuk menangkap event terbaru
        val events = usm.queryEvents(start, end)
        var lastPackage: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = event.eventType
            if (type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                type == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    /**
     * Poller ringan berbasis Handler yang mengecek foreground app secara berkala
     * dan hanya memanggil [onChanged] saat package benar-benar berubah (bukan
     * setiap tick), supaya hemat baterai/CPU.
     */
    class Watcher(
        private val context: Context,
        private val intervalMs: Long = 1200L,
        private val onChanged: (String) -> Unit
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var lastPackage: String? = null
        private var running = false

        private val tick = object : Runnable {
            override fun run() {
                if (!running) return
                val current = getCurrentForegroundPackage(context)
                if (current != null && current != lastPackage) {
                    lastPackage = current
                    onChanged(current)
                }
                handler.postDelayed(this, intervalMs)
            }
        }

        fun start() {
            if (running) return
            running = true
            handler.post(tick)
        }

        fun stop() {
            running = false
            handler.removeCallbacks(tick)
        }
    }
}
