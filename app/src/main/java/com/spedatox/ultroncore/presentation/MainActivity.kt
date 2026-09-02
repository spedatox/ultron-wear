/* Ultron Wear — academic schedule + attendance, for the Speda Mark VI ecosystem.
 * Design language: Speda (Heartbreaker) · Agent: Ultron Mark III · Accent #8a93a6
 */

package com.spedatox.ultroncore.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.design.LocalUltronPalette
import com.spedatox.ultroncore.design.ThemeEngine
import com.spedatox.ultroncore.design.UltronTheme
import com.spedatox.ultroncore.sync.CatchUpScheduler
import com.spedatox.ultroncore.sync.FallbackAskScheduler
import com.spedatox.ultroncore.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Denied is survivable: answers can still be given inside the app. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        val app = UltronWear.from(this)
        val palette = ThemeEngine.palette

        requestNotificationPermissionIfNeeded()

        // The catch-up reminder taps straight through to the list of holes;
        // landing on the schedule would cost a second tap for the one action
        // that notification exists to prompt.
        val startOnAttendance = intent?.getBooleanExtra(EXTRA_OPEN_ATTENDANCE, false) == true

        setContent {
            UltronTheme {
                CompositionLocalProvider(LocalUltronPalette provides palette) {
                    // No background modifier here on purpose. The window theme
                    // already paints black (`android:windowBackground`), so a
                    // black Box on top of it was a second full-screen opaque
                    // blit on every single frame — pure overdraw for zero
                    // pixels of difference. See res/values/themes.xml.
                    Box(Modifier.fillMaxSize()) {
                        UltronWearApp(startOnAttendance = startOnAttendance)
                    }
                }
            }
        }

        // Everything below is off the critical path to first frame.
        lifecycleScope.launch(Dispatchers.Default) {
            SyncScheduler.ensurePeriodicSync(this@MainActivity)
            SyncScheduler.syncNow(this@MainActivity)
            FallbackAskScheduler.rearm(this@MainActivity)
            // The standing net under the per-occurrence ask: that one fires
            // once and is gone, this one keeps asking while holes remain.
            CatchUpScheduler.ensureDaily(this@MainActivity)
            FirebaseRegistration.refresh(this@MainActivity)
        }
    }

    companion object {
        /** Set by the catch-up reminder so tapping it lands on the holes. */
        const val EXTRA_OPEN_ATTENDANCE = "open_attendance"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
private fun UltronWearApp(startOnAttendance: Boolean) {
    val app = UltronWear.from(androidx.compose.ui.platform.LocalContext.current)
    val vm: UltronViewModel = viewModel(factory = UltronViewModel.factory(app))
    val palette = LocalUltronPalette.current
    val navController = rememberSwipeDismissableNavController()

    // Hoisted so returning to the schedule restores the scroll position instead
    // of snapping to the top.
    val scheduleListState = rememberLazyListState()
    // Hoisted for the same reason, and so ScreenScaffold below can wire rotary
    // input and the scroll indicator to it.
    val attendanceListState = rememberLazyListState()

    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = if (startOnAttendance) ROUTE_ATTENDANCE else ROUTE_SCHEDULE,
        ) {
            composable(ROUTE_SCHEDULE) {
                ScreenScaffold(scrollState = scheduleListState) {
                    ScheduleScreen(
                        vm = vm,
                        palette = palette,
                        listState = scheduleListState,
                        onCourseClick = { navController.navigate(ROUTE_ATTENDANCE) },
                        onAttendanceClick = { navController.navigate(ROUTE_ATTENDANCE) },
                    )
                }
            }
            composable(ROUTE_ATTENDANCE) {
                ScreenScaffold(scrollState = attendanceListState) {
                    AttendanceScreen(
                        vm = vm,
                        palette = palette,
                        listState = attendanceListState,
                    )
                }
            }
        }
    }
}

private const val ROUTE_SCHEDULE = "schedule"
private const val ROUTE_ATTENDANCE = "attendance"
