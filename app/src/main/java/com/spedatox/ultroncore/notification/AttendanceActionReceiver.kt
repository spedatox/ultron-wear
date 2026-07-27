package com.spedatox.ultroncore.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.data.AttendanceStatus
import com.spedatox.ultroncore.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Records the answer tapped straight from the notification.
 *
 * Uses `goAsync()` because writing the ledger is disk IO and a BroadcastReceiver
 * that returns from `onReceive` is eligible to have its process killed. Without
 * the pending result the watch could — and on a memory-pressured Wear device
 * routinely would — drop the answer between the tap and the fsync.
 */
class AttendanceActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ANSWER) return

        val extras = intent.extras ?: return
        val payload = listOf(
            AttendanceAsk.KEY_SLOT,
            AttendanceAsk.KEY_CODE,
            AttendanceAsk.KEY_NAME,
            AttendanceAsk.KEY_DATE,
            AttendanceAsk.KEY_TIME,
            AttendanceAsk.KEY_ROOM,
        ).associateWith { extras.getString(it) }

        val ask = AttendanceAsk.from(payload) ?: run {
            Log.w(TAG, "Answer intent missing occurrence data")
            return
        }

        val status = runCatching {
            AttendanceStatus.valueOf(extras.getString(EXTRA_STATUS).orEmpty())
        }.getOrElse {
            Log.w(TAG, "Answer intent has no valid status")
            return
        }

        // Confirm the tap landed before doing anything slow — on a watch the
        // haptic *is* the acknowledgement.
        context.getSystemService(Vibrator::class.java)?.takeIf { it.hasVibrator() }?.vibrate(
            VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        )

        val pending = goAsync()
        val app = UltronWear.from(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.attendance.load()
                app.attendance.record(
                    slotId = ask.slotId,
                    courseCode = ask.courseCode,
                    date = ask.date,
                    status = status,
                )
                AttendanceNotifier(context).dismiss(ask.notificationId)
                // Answer is durable locally; getting it to Igor is best-effort
                // and retried by WorkManager.
                SyncScheduler.syncNow(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record answer for ${ask.slotId}", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_ANSWER = "com.spedatox.ultroncore.ANSWER_ATTENDANCE"
        const val EXTRA_STATUS = "status"
        private const val TAG = "AttendanceAction"
    }
}
