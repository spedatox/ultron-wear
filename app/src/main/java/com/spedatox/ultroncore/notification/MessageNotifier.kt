package com.spedatox.ultroncore.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.spedatox.ultroncore.R
import com.spedatox.ultroncore.presentation.MainActivity

/**
 * A plain message from Igor on the wrist.
 *
 * ── Why this exists ─────────────────────────────────────────────────────────
 * Igor's `notifications` skill has always sent `type=notification` with a title
 * and body, and [AttendanceMessagingService] had no branch for it — the message
 * fell through to the `else` and was logged as ignored. FCM reported "delivered"
 * the whole time, because delivery only means FCM handed the payload to the app;
 * what the app does with it is not FCM's business. The result was a push that
 * looked healthy end to end and produced nothing on the watch.
 *
 * ── Why a second channel ────────────────────────────────────────────────────
 * Separate from the attendance channel on purpose. Attendance fires after every
 * teaching hour and earns its high importance; a general message should not be
 * able to make that channel noisier, and silencing chatter should not silence
 * the question that decides whether you pass a course.
 */
class MessageNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        ensureChannel()
    }

    fun show(title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGE)
            .setSmallIcon(R.drawable.ic_ultron_mark)
            .setContentTitle(title.ifBlank { context.getString(R.string.app_name) })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .setVibrate(VIBRATION)
            .build()

        try {
            // A distinct id per message so a second one does not silently
            // replace an unread first.
            manager.notify(NOTIFICATION_ID_BASE + (System.currentTimeMillis() % 1000).toInt(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted. Nothing to do — the app asks for
            // it at launch, and a dropped informational message is survivable.
        }
    }

    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_MESSAGE,
            context.getString(R.string.channel_message),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_message_desc)
            enableVibration(true)
            vibrationPattern = VIBRATION
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_MESSAGE = "ultron_message"
        const val NOTIFICATION_ID_BASE = 20_000

        /** Single short buzz. These are informational, not a question. */
        val VIBRATION = longArrayOf(0, 140)
    }
}
