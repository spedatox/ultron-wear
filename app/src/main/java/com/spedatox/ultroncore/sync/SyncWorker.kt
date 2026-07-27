package com.spedatox.ultroncore.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.data.AttendanceRecord
import com.spedatox.ultroncore.data.AttendanceStatus
import com.spedatox.ultroncore.data.AttendanceSyncDto

/**
 * Settles the watch against Igor: pushes unsynced answers, pulls the schedule,
 * and merges anything Igor knows that the watch does not.
 *
 * Runs under a network constraint, so WorkManager holds it until there is a
 * connection instead of burning retries against a dead radio in a lecture hall.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = UltronWear.from(applicationContext)
        if (!app.igor.isConfigured) {
            // No endpoint compiled in. This is a valid offline-only build, not a
            // failure to retry forever.
            return Result.success()
        }

        app.attendance.load()
        app.schedule.load()

        val device = SyncScheduler.deviceId(applicationContext)
        var ok = true

        // 1. Attendance, both directions in one round trip.
        val pending = app.attendance.pendingSync()
        app.igor.syncAttendance(device, pending)
            .onSuccess { response ->
                app.attendance.markSynced(response.accepted)
                app.attendance.mergeFromServer(response.records.mapNotNull(::toRecord))
            }
            .onFailure {
                Log.i(TAG, "Attendance sync deferred: ${it.message}")
                ok = false
            }

        // 2. Schedule refresh. Independent of the above — a failed attendance
        //    push should not block picking up a room change.
        if (!app.schedule.refreshFromIgor(app.igor)) ok = false

        // 3. Re-arm the local safety net against whatever the schedule now says.
        FallbackAskScheduler.rearm(applicationContext)

        return if (ok) Result.success() else Result.retry()
    }

    private fun toRecord(dto: AttendanceSyncDto): AttendanceRecord? {
        val status = when (dto.status) {
            "attended" -> AttendanceStatus.ATTENDED
            "absent" -> AttendanceStatus.ABSENT
            "cancelled" -> AttendanceStatus.CANCELLED
            else -> return null
        }
        return AttendanceRecord(
            slotId = dto.slotId,
            courseCode = dto.courseCode,
            date = dto.date,
            status = status,
            recordedAt = dto.recordedAt,
            synced = true,
        )
    }

    private companion object { const val TAG = "SyncWorker" }
}
