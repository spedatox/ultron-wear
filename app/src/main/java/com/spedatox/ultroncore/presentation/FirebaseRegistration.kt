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
            // ── Why getToken() is called and its result thrown away ──────────
            // The FID identifies this installation to Firebase Installations —
            // it does NOT, on its own, register the installation with FCM as a
            // messaging target. Without an FCM registration, every send to this
            // fid comes back 404 / UNREGISTERED, which is indistinguishable
            // from "the app was uninstalled" and caused Igor to deactivate a
            // brand-new device seconds after it registered.
            //
            // Calling getToken() is what performs the FCM registration. It is
            // deprecated in firebase-messaging 25.1.0 in favour of addressing
            // by fid, and we do address by fid — but deprecated means "do not
            // build on this identifier", not "this no longer registers you".
            // Until Google ships a dedicated register-only call, this is the
            // supported way to make the installation reachable. The token
            // itself is deliberately unused; the side effect is the point.
            runCatching { FirebaseMessaging.getInstance().token.await() }
                .onFailure { Log.i(TAG, "FCM registration deferred: ${it.message}") }

            val fid = FirebaseInstallations.getInstance().id.await()
            if (fid.isNullOrBlank()) {
                Log.w(TAG, "Firebase returned an empty installation id")
                return
            }
            SyncScheduler.registerDevice(context, fid)
        } catch (e: Exception) {
            // No Play services, no network, or a throttled request. All
            // recoverable — the periodic sync will try again.
            Log.i(TAG, "Registration deferred: ${e.message}")
        }
    }

    private const val TAG = "FirebaseRegistration"
}
