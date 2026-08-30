package com.spedatox.ultroncore

import android.app.Application
import android.content.Context
import com.spedatox.ultroncore.data.AttendanceStore
import com.spedatox.ultroncore.data.IgorClient
import com.spedatox.ultroncore.data.ScheduleRepository
import com.spedatox.ultroncore.design.UltronFonts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The process-wide object graph.
 *
 * Deliberately hand-rolled rather than Hilt/Koin. There are four singletons and
 * no scoping requirements; a DI framework here would add an annotation
 * processor and reflection to the cold start of an app whose entire performance
 * budget is the cold start.
 *
 * The reason this exists at all is that [MainActivity], the tile service and the
 * complication service are three separate entry points into one process. The
 * original build constructed a fresh `CourseRepository` in each of them, which
 * meant the schedule JSON was parsed from assets three times — once per surface,
 * every time the watch face refreshed a complication.
 */
class UltronWear : Application() {

    /** Application-lifetime scope for warm-up and sync. `SupervisorJob` so one
     *  failed sync never cancels the others. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val schedule: ScheduleRepository by lazy { ScheduleRepository(this) }
    val attendance: AttendanceStore by lazy { AttendanceStore(this) }
    val igor: IgorClient by lazy { IgorClient() }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Warm both stores off the main thread before any surface asks for them.
        // Both are IO-bound and independent, so they overlap.
        scope.launch { schedule.load() }
        scope.launch { attendance.load() }
        // Parse both TTFs now, off the main thread, so the schedule list's first
        // use of each weight never blocks a scroll frame on disk I/O.
        scope.launch(Dispatchers.IO) { UltronFonts.preload(this@UltronWear) }
    }

    companion object {
        @Volatile
        private var instance: UltronWear? = null

        fun from(context: Context): UltronWear =
            instance ?: (context.applicationContext as UltronWear).also { instance = it }
    }
}
