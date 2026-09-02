package com.spedatox.ultroncore.sync

import android.content.Context
import android.provider.Settings
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.security.MessageDigest
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
     * A stable identifier for *this watch*, surviving uninstall/reinstall.
     *
     * ── Why this changed ────────────────────────────────────────────────────
     * This used to be a random UUID in SharedPreferences. SharedPreferences is
     * wiped on uninstall, so every reinstall minted a fresh id and Igor — which
     * upserts on `device_id` — had no way to know it was the same watch. Five
     * reinstalls produced five device rows, every push fanned out to all of
     * them, and the four dead ones failed forever.
     *
     * The old comment argued a random UUID avoided "a persistent cross-app
     * identifier". That concern does not apply to ANDROID_ID on API 26+: it is
     * scoped per (app signing key, user, device), so it is not shared with any
     * other app and cannot be used to correlate the owner across installs by
     * anyone but us. It resets on factory reset, and it changes if the app's
     * signing key changes — which is exactly the semantics wanted here.
     *
     * Still hashed rather than sent raw: Igor needs to tell one device from
     * another, not to learn a platform identifier, and a truncated SHA-256 is
     * as good a key as the value itself.
     *
     * The SharedPreferences entry is kept as a cache and as the fallback path
     * for the (documented, rare) case of ANDROID_ID coming back null or as the
     * known-buggy all-zero value on some builds.
     */
    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }

        @Suppress("HardwareIds") // Scoped per signing key on API 26+; see above.
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()

        val id = if (androidId.isNullOrBlank() || androidId == "9774d56d682e549c") {
            // Null, or the notorious duplicated-across-devices constant from the
            // 2.2 era. Fall back to the old random id: a duplicate row is a far
            // better failure than every watch sharing one.
            "wear-" + UUID.randomUUID().toString().take(12)
        } else {
            "wear-" + sha256Hex(androidId).take(16)
        }
        prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        return id
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

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
