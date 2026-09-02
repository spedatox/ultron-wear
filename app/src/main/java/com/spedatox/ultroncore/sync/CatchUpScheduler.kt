package com.spedatox.ultroncore.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.data.AttendanceCalculator
import com.spedatox.ultroncore.data.AttendanceRisk
import com.spedatox.ultroncore.notification.AttendanceNotifier
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * ════════════════════════════════════════════════════════════════════════════
 *  The standing reminder that the ledger has holes.
 *
 *  ── Why this is separate from FallbackAskScheduler ─────────────────────────
 *  That one asks about ONE occurrence, ONCE, fifteen minutes after the bell. It
 *  is the right shape for "you just left class" and the wrong shape for
 *  everything after: miss that single notification — phone face down, watch off
 *  the wrist, notification swiped while walking — and nothing ever asks again.
 *  The hour then sits unanswered forever.
 *
 *  An unanswered hour is not neutral. It is an unknown in the arithmetic that
 *  resolves, at the end of term, into an absence you cannot appeal because you
 *  no longer remember whether you were there. This worker exists so that never
 *  happens silently.
 *
 *  ── Why evening, and only once a day ───────────────────────────────────────
 *  Late enough that the day's classes are over and the list is complete, and
 *  once a day because a reminder that arrives more often than you can act on it
 *  is one you learn to dismiss without reading. The moment the backlog is empty
 *  it goes quiet entirely and cancels any reminder still showing.
 * ════════════════════════════════════════════════════════════════════════════
 */
object CatchUpScheduler {

    /** Local hour at which the daily check runs. */
    private const val HOUR_OF_DAY = 20

    private const val WORK_CATCHUP = "attendance_catchup"

    /**
     * Arm the daily check. Idempotent — KEEP, so calling this on every app open
     * does not reset the timer and push the reminder further away each time the
     * app is used.
     */
    fun ensureDaily(context: Context) {
        val now = LocalDateTime.now()
        var fireAt = now.toLocalDate().atTime(LocalTime.of(HOUR_OF_DAY, 0))
        if (!fireAt.isAfter(now)) fireAt = fireAt.plusDays(1)

        val request = PeriodicWorkRequestBuilder<CatchUpWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(Duration.between(now, fireAt).toMinutes(), TimeUnit.MINUTES)
            // No network requirement: the whole computation is local, over the
            // cached schedule and the on-disk ledger. Gating this on
            // connectivity would silence the reminder exactly when the watch is
            // offline — which is also when Igor's push cannot reach it, so it is
            // the moment the local net matters most.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_CATCHUP,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

/**
 * Counts the holes and, if there are any, says what they are going to cost.
 */
class CatchUpWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = UltronWear.from(applicationContext)
        app.schedule.load()
        app.attendance.load()

        val courses = app.schedule.courses.value
        if (courses.isEmpty()) return Result.success()

        val term = app.schedule.term.value
        val records = app.attendance.records.value
        val now = LocalDateTime.now()

        val pending = AttendanceCalculator.unanswered(courses, term, records, now)
        val notifier = AttendanceNotifier(applicationContext)

        if (pending.isEmpty()) {
            // Nothing outstanding. Clear any reminder still on the watch rather
            // than leaving a stale one to be tapped into an empty list.
            notifier.dismissCatchUp()
            return Result.success()
        }

        notifier.catchUp(count = pending.size, headline = headline(courses, term, records, now, pending))
        return Result.success()
    }

    /**
     * The consequence, not the count.
     *
     * Picks the subject that is closest to failing among those with holes, and
     * states its remaining budget. "3 ders kaydedilmedi" is a chore; "MAT101: 2
     * hak kaldı" is a reason to open the app. Falls back to null — and so to the
     * generic string — when nothing is close enough to be worth alarming about.
     */
    private fun headline(
        courses: List<com.spedatox.ultroncore.data.Course>,
        term: com.spedatox.ultroncore.data.Term,
        records: Map<String, com.spedatox.ultroncore.data.AttendanceRecord>,
        now: LocalDateTime,
        pending: List<AttendanceCalculator.Occurrence>,
    ): String? {
        val codesWithHoles = pending.map { it.course.code }.toSet()
        val worst = AttendanceCalculator
            .summarise(courses, term, records, now)
            .filter { it.courseCode in codesWithHoles }
            .minByOrNull { it.remainingAbsences }
            ?: return null

        return when {
            worst.risk == AttendanceRisk.FAILED ->
                "${worst.courseName}: devamsızlık sınırı aşıldı."
            worst.remainingAbsences <= 2 ->
                "${worst.courseName}: ${worst.remainingAbsences} hak kaldı. Kaydetmezsen bilmiyorsun."
            else -> null
        }
    }
}
