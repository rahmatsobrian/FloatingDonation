package siroha.floating.donation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import siroha.floating.donation.MainActivity
import siroha.floating.donation.R

class NotificationController(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "siroha_overlay_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_SHOW = "siroha.floating.donation.ACTION_SHOW"
        const val ACTION_HIDE = "siroha.floating.donation.ACTION_HIDE"
        const val ACTION_LOCK = "siroha.floating.donation.ACTION_LOCK"
        const val ACTION_UNLOCK = "siroha.floating.donation.ACTION_UNLOCK"
        const val ACTION_EXIT = "siroha.floating.donation.ACTION_EXIT"
        const val ACTION_CLEAR_EXIT = "siroha.floating.donation.ACTION_CLEAR_EXIT"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannel()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for floating overlay"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        overlayName: String,
        isVisible: Boolean,
        isLocked: Boolean
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Siroha Floating Donation")
            .setContentText("$overlayName is active")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        // Show/Hide action
        if (isVisible) {
            builder.addAction(
                R.drawable.ic_hide,
                "Hide",
                createActionPending(ACTION_HIDE, 1)
            )
        } else {
            builder.addAction(
                R.drawable.ic_show,
                "Show",
                createActionPending(ACTION_SHOW, 1)
            )
        }

        // Lock/Unlock action
        if (isLocked) {
            builder.addAction(
                R.drawable.ic_unlock,
                "Unlock",
                createActionPending(ACTION_UNLOCK, 2)
            )
        } else {
            builder.addAction(
                R.drawable.ic_lock,
                "Lock",
                createActionPending(ACTION_LOCK, 2)
            )
        }

        // Exit action
        builder.addAction(
            R.drawable.ic_exit,
            "Exit",
            createActionPending(ACTION_EXIT, 3)
        )

        return builder.build()
    }

    private fun createActionPending(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun updateNotification(overlayName: String, isVisible: Boolean, isLocked: Boolean) {
        val notification = buildNotification(overlayName, isVisible, isLocked)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
