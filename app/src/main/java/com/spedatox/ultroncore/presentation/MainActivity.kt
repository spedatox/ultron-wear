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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.design.LocalUltronPalette
import com.spedatox.ultroncore.design.ThemeEngine
import com.spedatox.ultroncore.design.UltronTheme
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

        setContent {
            UltronTheme {
                CompositionLocalProvider(LocalUltronPalette provides palette) {
                    // The root fill is drawn once here rather than by each
                    // screen, so switching destinations never repaints a
                    // full-screen background.
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        UltronWearApp()
                    }
                }
            }
        }

        // Everything below is off the critical path to first frame.
        lifecycleScope.launch(Dispatchers.Default) {
            SyncScheduler.ensurePeriodicSync(this@MainActivity)
            SyncScheduler.syncNow(this@MainActivity)
            FallbackAskScheduler.rearm(this@MainActivity)
            FirebaseRegistration.refresh(this@MainActivity)
        }
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
private fun UltronWearApp() {
    val app = UltronWear.from(androidx.compose.ui.platform.LocalContext.current)
    val vm: UltronViewModel = viewModel(factory = UltronViewModel.factory(app))
    val palette = LocalUltronPalette.current
    val navController = rememberSwipeDismissableNavController()

    // Hoisted so returning to the schedule restores the scroll position instead
    // of snapping to the top.
    val scheduleListState = rememberScalingLazyListState()

    AppScaffold {
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = ROUTE_SCHEDULE,
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
                ScreenScaffold {
                    AttendanceScreen(vm = vm, palette = palette)
                }
            }
        }
    }
}

private const val ROUTE_SCHEDULE = "schedule"
private const val ROUTE_ATTENDANCE = "attendance"
