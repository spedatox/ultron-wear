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
import com.spedatox.ultroncore.data.AttendanceStatus
import com.spedatox.ultroncore.presentation.AttendanceActivity

/**
 * Puts the question on the wrist.
 *
 * The three answers are notification **actions**, not just a tap-through to the
 * app. On a watch that difference is the whole feature: answering from the
 * notification shade is one tap on a wrist you have already raised, while
 * launching an Activity costs a cold Compose start and several seconds. The
 * content intent still opens [AttendanceActivity] for anyone who wants the
 * fuller screen with the remaining-absence count.
 */
class AttendanceNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        ensureChannel()
    }

    fun ask(ask: AttendanceAsk) {
        val subtitle = buildString {
            append(ask.timeLabel)
            if (ask.room.isNotBlank()) append(" · ").append(ask.room)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ASK)
            .setSmallIcon(R.drawable.ic_ultron_mark)
            .setContentTitle(ask.courseName)
            .setContentText(context.getString(R.string.ask_did_you_attend))
            .setStyle(NotificationCompat.BigTextStyle().bigText("$subtitle\n${context.getString(R.string.ask_did_you_attend)}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // Do not auto-cancel on tap: the action receiver dismisses it once an
            // answer is actually recorded, so a stray tap cannot lose the question.
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(openIntent(ask))
            .addAction(action(ask, AttendanceStatus.ATTENDED, R.string.answer_yes))
            .addAction(action(ask, AttendanceStatus.ABSENT, R.string.answer_no))
            .addAction(action(ask, AttendanceStatus.CANCELLED, R.string.answer_cancelled))
            .setVibrate(VIBRATION)
            .build()

        try {
            manager.notify(ask.notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted. Nothing to do here; the app
            // requests it at launch and the question survives in the
            // "eksik cevaplar" list either way.
        }
    }

    fun dismiss(notificationId: Int) = manager.cancel(notificationId)

    private fun action(
        ask: AttendanceAsk,
        status: AttendanceStatus,
        labelRes: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, AttendanceActionReceiver::class.java).apply {
            action = AttendanceActionReceiver.ACTION_ANSWER
            putExtra(AttendanceActionReceiver.EXTRA_STATUS, status.name)
            ask.toExtras().forEach { (k, v) -> putExtra(k, v) }
        }
        val pending = PendingIntent.getBroadcast(
            context,
            // Unique per (occurrence, answer) or the three actions would collide
            // and all three buttons would record the same status.
            ask.notificationId * 31 + status.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(0, context.getString(labelRes), pending).build()
    }

    private fun openIntent(ask: AttendanceAsk): PendingIntent {
        val intent = Intent(context, AttendanceActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            ask.toExtras().forEach { (k, v) -> putExtra(k, v) }
        }
        return PendingIntent.getActivity(
            context,
            ask.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ASK,
            context.getString(R.string.channel_attendance),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_attendance_desc)
            enableVibration(true)
            vibrationPattern = VIBRATION
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ASK = "attendance_ask"

        /** Short double-tap. Long buzz patterns on a watch are punishment, and
         *  this fires after every single teaching hour. */
        val VIBRATION = longArrayOf(0, 120, 90, 120)
    }
}
