package com.spedatox.ultroncore.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Every WorkManager entry point in the app, in one place. */
object SyncScheduler {

    private const val PREFS = "ultron_wear"
    private const val KEY_DEVICE_ID = "device_id"

    private const val WORK_SYNC_NOW = "sync_now"
    private const val WORK_SYNC_PERIODIC = "sync_periodic"
    private const val WORK_REGISTER = "register_device"

    private val networked = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * A stable per-install identifier.
     *
     * Deliberately a random UUID rather than ANDROID_ID or any hardware
     * identifier: Igor only needs to tell one device from another, and a
     * hardware id would be a persistent cross-app identifier for the owner with
     * no upside here. It resets on reinstall, which is correct — a reinstall is
     * a new FCM token anyway.
     */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        val id = "wear-" + UUID.randomUUID().toString().take(12)
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    /** Push/pull as soon as there is a network. Coalesces — answering three
     *  questions quickly enqueues one sync, not three. */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networked)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_SYNC_NOW, ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * The background heartbeat. Six hours, not fifteen minutes: the schedule
     * changes a few times a semester and the ledger is pushed eagerly on every
     * answer, so a tighter interval would spend battery confirming that nothing
     * happened.
     */
    fun ensurePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(networked)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_SYNC_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** @param fid the Firebase Installation ID this watch is addressed by. */
    fun registerDevice(context: Context, fid: String) {
        val request = OneTimeWorkRequestBuilder<RegisterDeviceWorker>()
            .setConstraints(networked)
            .setInputData(workDataOf(RegisterDeviceWorker.KEY_FID to fid))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_REGISTER, ExistingWorkPolicy.REPLACE, request)
    }
}
