package com.spedatox.ultroncore.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spedatox.ultroncore.UltronWear

/**
 * Hands Igor this watch's Firebase Installation ID.
 *
 * Separate from [SyncWorker] and retried independently because it is the one
 * piece of state that, if it never lands, silently disables the entire feature:
 * Igor cannot ask a device it cannot address, and nothing else about the app
 * would look broken.
 */
class RegisterDeviceWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val fid = inputData.getString(KEY_FID)
        if (fid.isNullOrBlank()) return Result.failure()

        val app = UltronWear.from(applicationContext)
        if (!app.igor.isConfigured) return Result.success()

        return app.igor.registerDevice(SyncScheduler.deviceId(applicationContext), fid)
            .fold(
                onSuccess = {
                    Log.i(TAG, "Device registered with Igor")
                    Result.success()
                },
                onFailure = {
                    Log.i(TAG, "Device registration deferred: ${it.message}")
                    Result.retry()
                },
            )
    }

    companion object {
        const val KEY_FID = "fid"
        private const val TAG = "RegisterDevice"
    }
}
