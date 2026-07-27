package com.spedatox.ultroncore.notification

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.sync.FallbackAskScheduler
import com.spedatox.ultroncore.sync.SyncScheduler
import kotlinx.coroutines.launch

/**
 * Igor's channel to the wrist.
 *
 * ── Why data-only messages ──────────────────────────────────────────────────
 * Igor must send these as **data messages** (no `notification` block in the FCM
 * v1 payload). A message carrying a `notification` block is rendered by the
 * system tray when the app is backgrounded and [onMessageReceived] is never
 * called — which would mean no ledger entry, no action buttons, and no way to
 * answer. Data-only with `android.priority = "high"` guarantees this callback
 * runs even in Doze. The server side of this contract is documented in
 * `docs/ULTRON_WEAR_BACKEND.md` and enforced by the payload builder in
 * `app/services/fcm.py`.
 */
class AttendanceMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        when (data["type"]) {
            TYPE_ATTENDANCE_ASK -> handleAsk(data)
            TYPE_SYNC_REQUEST -> {
                // Igor wants the ledger now (e.g. it is answering a question in
                // chat and needs current numbers).
                SyncScheduler.syncNow(applicationContext)
            }
            else -> Log.i(TAG, "Ignoring FCM message of type=${data["type"]}")
        }
    }

    private fun handleAsk(data: Map<String, String>) {
        val ask = AttendanceAsk.from(data)
        if (ask == null) {
            Log.w(TAG, "Malformed attendance_ask payload: $data")
            return
        }
        val app = UltronWear.from(applicationContext)
        app.scope.launch {
            // Do not re-ask something already answered — a duplicate push, or a
            // push that races the local fallback, must be idempotent.
            app.attendance.load()
            val alreadyAnswered = app.attendance.records.value["${ask.slotId}@${ask.date}"] != null
            if (alreadyAnswered) {
                Log.i(TAG, "Ask for ${ask.slotId}@${ask.date} already answered; dropping")
                return@launch
            }
            AttendanceNotifier(applicationContext).ask(ask)
            // The push arrived, so the local safety net for this occurrence is
            // no longer needed.
            FallbackAskScheduler.cancel(applicationContext, ask)
        }
    }

    /**
     * A new registration means Igor's stored address for this watch is stale.
     * Register immediately; if the watch is offline the work is retried by
     * [SyncScheduler].
     *
     * NOTE this is `onRegistered`, not `onNewToken`. firebase-messaging 25.1.0
     * deprecated the whole registration-token API (`getToken`, `deleteToken`,
     * `onNewToken`) in favour of the Firebase Installation ID, and the SDK
     * source marks `onNewToken` with "@deprecated Use onRegistered(String)
     * instead". Tokens still work today, but they are on the way out, so this
     * app addresses itself by FID from the start rather than shipping on an API
     * with an announced end.
     */
    override fun onRegistered(installationId: String) {
        Log.i(TAG, "FCM registration refreshed")
        SyncScheduler.registerDevice(applicationContext, installationId)
    }

    private companion object {
        const val TAG = "UltronFcm"
        const val TYPE_ATTENDANCE_ASK = "attendance_ask"
        const val TYPE_SYNC_REQUEST = "sync_request"
    }
}
