package com.spedatox.ultroncore.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.data.AttendanceCalculator
import com.spedatox.ultroncore.notification.AttendanceAsk
import com.spedatox.ultroncore.notification.AttendanceNotifier
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * ════════════════════════════════════════════════════════════════════════════
 *  The safety net under FCM.
 *
 *  Igor pushing the question is the primary path and stays that way. But the
 *  question fires the moment a lecture ends — which is exactly when the watch is
 *  most likely to be on a campus wifi it cannot reach the internet through, deep
 *  in a concrete building, or in Doze with a stale connection. FCM's guarantee
 *  is "eventually, if reachable", and an attendance question that arrives four
 *  hours late is a question you answer from memory, badly.
 *
 *  So: every upcoming occurrence gets a local worker armed for [GRACE_MINUTES]
 *  after the bell. If the push landed first, the FCM handler cancels this worker
 *  and nothing happens. If it did not, the watch asks on its own — offline,
 *  on time, from the cached schedule.
 *
 *  Set [ENABLED] to false to run FCM-only.
 * ════════════════════════════════════════════════════════════════════════════
 */
object FallbackAskScheduler {

    /** Kill switch for the local safety net. */
    const val ENABLED = true

    /** How long after the bell to wait for Igor's push before asking locally. */
    const val GRACE_MINUTES = 15L

    /**
     * How far ahead to arm. One day, re-armed on every sync and every app open.
     * Arming the whole 14-week term would mean ~180 pending WorkManager entries,
     * which is both wasteful and stale the first time a room changes.
     */
    private const val HORIZON_HOURS = 26L

    private const val TAG_FALLBACK = "attendance_fallback"

    fun uniqueName(slotId: String, date: LocalDate) = "$TAG_FALLBACK:$slotId@$date"

    /** Cancel the net for one occurrence — called when its push arrives. */
    fun cancel(context: Context, ask: AttendanceAsk) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(ask.slotId, ask.date))
    }

    /**
     * Re-arm against the current schedule. Idempotent: uses unique work per
     * occurrence with KEEP, so calling it on every app open and every sync does
     * not reset timers that are already ticking.
     */
    suspend fun rearm(context: Context) {
        if (!ENABLED) return
        val app = UltronWear.from(context)
        app.schedule.load()
        app.attendance.load()

        val courses = app.schedule.courses.value
        if (courses.isEmpty()) return

        val term = app.schedule.term.value
        val records = app.attendance.records.value
        val now = LocalDateTime.now()
        val horizon = now.plusHours(HORIZON_HOURS)
        val wm = WorkManager.getInstance(context)

        AttendanceCalculator.occurrences(courses, term)
            .asSequence()
            .filter { it.endsAt.isAfter(now) && it.endsAt.isBefore(horizon) }
            .filter { records["${it.course.id}@${it.date}"] == null }
            .forEach { occ ->
                val fireAt = occ.endsAt.plusMinutes(GRACE_MINUTES)
                val delay = Duration.between(now, fireAt).toMillis().coerceAtLeast(0)
                val request = OneTimeWorkRequestBuilder<FallbackAskWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(
                        workDataOf(
                            AttendanceAsk.KEY_SLOT to occ.course.id,
                            AttendanceAsk.KEY_CODE to occ.course.code,
                            AttendanceAsk.KEY_NAME to occ.course.name,
                            AttendanceAsk.KEY_DATE to occ.date.toString(),
                            AttendanceAsk.KEY_TIME to occ.course.timeString,
                            AttendanceAsk.KEY_ROOM to occ.course.roomNumber,
                        )
                    )
                    .addTag(TAG_FALLBACK)
                    .build()
                wm.enqueueUniqueWork(
                    uniqueName(occ.course.id, occ.date),
                    ExistingWorkPolicy.KEEP,
                    request,
                )
            }
    }
}

/**
 * Fires [FallbackAskScheduler.GRACE_MINUTES] after a lecture ends. Re-checks the
 * ledger first: between arming and firing, the push may have arrived and been
 * answered, in which case this does nothing.
 */
class FallbackAskWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val data = listOf(
            AttendanceAsk.KEY_SLOT,
            AttendanceAsk.KEY_CODE,
            AttendanceAsk.KEY_NAME,
            AttendanceAsk.KEY_DATE,
            AttendanceAsk.KEY_TIME,
            AttendanceAsk.KEY_ROOM,
        ).associateWith { inputData.getString(it) }

        val ask = AttendanceAsk.from(data) ?: return Result.failure()

        val app = UltronWear.from(applicationContext)
        app.attendance.load()
        if (app.attendance.records.value["${ask.slotId}@${ask.date}"] != null) {
            // Already answered — the push won the race. Nothing to do.
            return Result.success()
        }

        AttendanceNotifier(applicationContext).ask(ask)
        return Result.success()
    }
}
