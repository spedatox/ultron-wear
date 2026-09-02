package com.spedatox.ultroncore.presentation

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.spedatox.ultroncore.BuildConfig
import com.spedatox.ultroncore.sync.SyncScheduler
import kotlinx.coroutines.tasks.await

/**
 * Gets this watch's FCM address to Igor.
 *
 * ── Why the Installation ID and not a registration token ────────────────────
 * firebase-messaging 25.1.0 deprecated `getToken()`, `deleteToken()` and
 * `onNewToken()` in favour of registering by Firebase Installation ID, and the
 * Admin SDKs followed: `Message(token=…)` now raises a DeprecationWarning and
 * `Message(fid=…)` is the supported target. Registration tokens still work, but
 * building a new integration on an API with an announced end date is how you
 * schedule your own migration for a semester you would rather spend studying.
 *
 * Every call is guarded, because a build with no `google-services.json` has no
 * `FirebaseApp` and these calls throw. That is a supported configuration — an
 * offline-only Ultron Wear still shows the schedule and still asks locally via
 * [com.spedatox.ultroncore.sync.FallbackAskScheduler] — so a missing Firebase
 * must degrade quietly rather than crash on launch.
 */
object FirebaseRegistration {

    suspend fun refresh(context: Context) {
        if (!BuildConfig.HAS_FIREBASE) {
            Log.i(TAG, "No Firebase config in this build; push disabled")
            return
        }
        if (FirebaseApp.getApps(context).isEmpty()) {
            Log.w(TAG, "Firebase config present but no app initialised")
            return
        }
        try {
            // ── Why the token is fetched AND kept ────────────────────────────
            // Calling getToken() is what actually registers this installation
            // with FCM; the FID alone only identifies it to Firebase
            // Installations. But registering is not enough on its own: a watch
            // that registered correctly still had every fid-addressed send come
            // back 404 UNREGISTERED, which Igor cannot distinguish from "app
            // uninstalled" — so it deactivated a brand-new device seconds after
            // it appeared.
            //
            // So the token is now sent to Igor as well, and Igor targets by
            // token when it has one. That is the decade-old path and does not
            // depend on fid-addressing being healthy. The fid still goes up and
            // is still the fallback, so nothing regresses if the token is
            // unavailable — and a device row arriving with an empty token is
            // itself the signal that this watch cannot reach FCM at all.
            val token = runCatching { FirebaseMessaging.getInstance().token.await() }
                .onFailure { Log.w(TAG, "FCM token unavailable: ${it.message}") }
                .getOrNull()

            val fid = FirebaseInstallations.getInstance().id.await()
            if (fid.isNullOrBlank()) {
                Log.w(TAG, "Firebase returned an empty installation id")
                return
            }
            Log.i(TAG, "Registering: fid=${fid.take(8)}… token=${if (token.isNullOrBlank()) "NONE" else "present"}")
            SyncScheduler.registerDevice(context, fid, token)
        } catch (e: Exception) {
            // No Play services, no network, or a throttled request. All
            // recoverable — the periodic sync will try again.
            Log.i(TAG, "Registration deferred: ${e.message}")
        }
    }

    private const val TAG = "FirebaseRegistration"
}
