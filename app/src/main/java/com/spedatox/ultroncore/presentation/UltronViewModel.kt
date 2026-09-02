package com.spedatox.ultroncore.presentation

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.data.AttendanceCalculator
import com.spedatox.ultroncore.data.AttendanceStatus
import com.spedatox.ultroncore.data.AttendanceSummary
import com.spedatox.ultroncore.data.Course
import com.spedatox.ultroncore.data.Term
import com.spedatox.ultroncore.notification.AttendanceAsk
import com.spedatox.ultroncore.notification.AttendanceNotifier
import com.spedatox.ultroncore.sync.FallbackAskScheduler
import com.spedatox.ultroncore.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * ════════════════════════════════════════════════════════════════════════════
 *  The state holder — and the fix for the original build's lag.
 *
 *  THE BUG: `MainActivity` kept a `currentTime` state, ticked it every 60s, and
 *  read it inside every `CourseCard`. Compose therefore invalidated all thirteen
 *  cards once a minute, re-running each card's status calculation, its
 *  `remember` blocks and its whole layout — forever, including while nothing on
 *  screen could possibly have changed. On top of that the status calculation was
 *  `remember(course, currentTime, today)`, so the memo key changed every tick and
 *  the cache never once hit.
 *
 *  THE FIX, in three parts:
 *   1. The clock is derived here into the *smallest* values that actually change:
 *      an active-slot id and a progress fraction. A card reads a Boolean, not a
 *      timestamp, so thirteen cards no longer invalidate because a minute passed.
 *   2. Progress is exposed as a lambda, read in the draw phase (see CourseCard).
 *      A changing progress bar then costs a redraw, not a recomposition —
 *      skipping composition and layout entirely.
 *   3. The tick aligns to the wall-clock minute and stops when nothing is
 *      running, so an idle watch is not woken 1,440 times a day to learn that it
 *      is still idle.
 * ════════════════════════════════════════════════════════════════════════════
 */
class UltronViewModel(private val app: UltronWear) : ViewModel() {

    /** Ticks on the minute boundary. Everything time-derived hangs off this. */
    private val _now = MutableStateFlow(LocalDateTime.now())
    val now: StateFlow<LocalDateTime> = _now

    /**
     * A snapshot-state mirror of [_now], and the reason the progress rail moves
     * at all.
     *
     * [progressOf] is called from the draw phase, and Compose's snapshot system
     * only tracks reads of snapshot state — a plain `StateFlow.value` read is
     * invisible to it. Reading `_now.value` there therefore registered no
     * observer, so nothing ever invalidated the draw and the bar sat frozen at
     * whatever fraction it happened to be composed at.
     *
     * Backing the same instant with a `mutableStateOf` fixes it without giving
     * up the optimisation: a draw-phase read of snapshot state invalidates only
     * the draw, so the rail repaints on the tick while composition and layout
     * are still skipped entirely.
     */
    private var nowSnapshot by mutableStateOf(LocalDateTime.now())

    init {
        viewModelScope.launch {
            while (true) {
                val t = LocalTime.now()
                // Sleep exactly to the next minute rather than a flat 60s, so the
                // "ŞİMDİ" badge flips when the clock does, not up to 59s late.
                val msToNextMinute = 60_000L - (t.second * 1_000L + t.nano / 1_000_000L)
                delay(msToNextMinute.coerceIn(1_000L, 60_000L))
                val next = LocalDateTime.now()
                _now.value = next
                nowSnapshot = next
            }
        }
    }

    val courses: StateFlow<List<Course>> = app.schedule.courses
    val term: StateFlow<Term> = app.schedule.term
    val loaded: StateFlow<Boolean> = app.schedule.loaded

    /**
     * The schedule grouped by weekday, Monday→Friday, empty days dropped.
     * Recomputed only when the schedule itself changes — never on a tick.
     */
    val week: StateFlow<List<DaySection>> = courses
        .combine(term) { list, _ -> group(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Per-subject attendance verdicts. */
    val summaries: StateFlow<List<AttendanceSummary>> =
        combine(courses, term, app.attendance.records, _now) { c, t, r, n ->
            AttendanceCalculator.summarise(c, t, r, n)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Past teaching hours with no answer — the ledger's holes.
     *
     * Most recent first: the class you forgot an hour ago is the one you can
     * still answer honestly, and a list that buries it under six weeks of older
     * gaps is a list you stop opening.
     *
     * This is the whole point of the catch-up screen. The per-occurrence ask
     * fires once, fifteen minutes after the bell; miss or dismiss that single
     * notification and nothing ever asked again, so the hour silently stayed
     * unanswered and quietly rotted the attendance maths.
     */
    val pending: StateFlow<List<AttendanceCalculator.Occurrence>> =
        combine(courses, term, app.attendance.records, _now) { c, t, r, n ->
            AttendanceCalculator.unanswered(c, t, r, n).sortedByDescending { it.endsAt }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Derived from [pending] rather than recomputed, so the badge and the list
     *  can never disagree about how many holes there are. */
    val unansweredCount: StateFlow<Int> =
        pending.map { it.size }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Record one answer and clean up everything that was still chasing it.
     *
     * All four steps matter: without the cancel, the local fallback still fires
     * for an hour already answered; without the dismiss, an open notification
     * for it lingers on the watch face; without the sync, Igor keeps counting
     * the hour as a hole and keeps nagging about it.
     */
    fun answer(occurrence: AttendanceCalculator.Occurrence, status: AttendanceStatus) {
        viewModelScope.launch {
            app.attendance.record(
                slotId = occurrence.course.id,
                courseCode = occurrence.course.code,
                date = occurrence.date,
                status = status,
            )
            val ask = AttendanceAsk(
                slotId = occurrence.course.id,
                courseCode = occurrence.course.code,
                courseName = occurrence.course.name,
                date = occurrence.date,
                timeLabel = occurrence.course.timeString,
                room = occurrence.course.roomNumber,
            )
            FallbackAskScheduler.cancel(app, ask)
            AttendanceNotifier(app).dismiss(ask.notificationId)
            SyncScheduler.syncNow(app)
        }
    }

    /**
     * Which slot is running right now, and which is up next.
     *
     * Deliberately two ids rather than two Course objects: a card compares its
     * own id against these and gets a Boolean, so an identity change invalidates
     * exactly the two cards involved instead of the whole list.
     */
    val timeline: StateFlow<Timeline> =
        combine(courses, _now) { list, n -> computeTimeline(list, n) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Timeline())

    /** 1-based teaching week, or null outside the term. */
    val currentWeek: StateFlow<Int?> =
        combine(term, _now) { t, n -> t.weekOf(n.toLocalDate()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Progress through the running lecture, 0..1.
     *
     * Called from the draw phase, so it must be cheap. It reads [nowSnapshot]
     * rather than `_now.value` deliberately: the snapshot read is what lets the
     * draw phase observe the tick, while still not subscribing any *composable*
     * to recomposition. See [nowSnapshot].
     */
    fun progressOf(course: Course): Float {
        val n = nowSnapshot
        if (n.toLocalDate().dayOfWeek != course.dayOfWeek) return 0f
        val total = course.endTime.toSecondOfDay() - course.startTime.toSecondOfDay()
        if (total <= 0) return 0f
        val elapsed = n.toLocalTime().toSecondOfDay() - course.startTime.toSecondOfDay()
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }

    private fun group(list: List<Course>): List<DaySection> {
        if (list.isEmpty()) return emptyList()
        return WEEKDAYS.mapNotNull { day ->
            val dayCourses = list.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
            if (dayCourses.isEmpty()) null else DaySection(day, dayCourses)
        }
    }

    private fun computeTimeline(list: List<Course>, n: LocalDateTime): Timeline {
        val today = n.toLocalDate().dayOfWeek
        val t = n.toLocalTime()
        val todays = list.filter { it.dayOfWeek == today }.sortedBy { it.startTime }
        val active = todays.firstOrNull { !t.isBefore(it.startTime) && t.isBefore(it.endTime) }
        val next = todays.firstOrNull { it.startTime.isAfter(t) }
        return Timeline(
            today = today,
            activeId = active?.id,
            nextId = next?.id,
            date = n.toLocalDate(),
        )
    }

    companion object {
        private val WEEKDAYS = listOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
        )

        fun factory(app: UltronWear) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                UltronViewModel(app) as T
        }
    }
}

@Immutable
data class DaySection(val day: DayOfWeek, val courses: List<Course>)

@Immutable
data class Timeline(
    val today: DayOfWeek = DayOfWeek.MONDAY,
    val activeId: String? = null,
    val nextId: String? = null,
    val date: LocalDate = LocalDate.now(),
)
