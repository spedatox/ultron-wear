package com.spedatox.ultroncore.data

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.floor

/**
 * The attendance arithmetic. Pure functions over (schedule, term, ledger) — no
 * Android, no IO, no state. That is what makes the rule below testable, and the
 * rule is the entire reason this app exists.
 *
 * ── The rule ────────────────────────────────────────────────────────────────
 *   scheduled  = every teaching hour the term actually holds (holidays removed)
 *   effective  = scheduled − hours the instructor cancelled
 *   allowed    = floor(effective × (1 − requiredRate))
 *   remaining  = allowed − hours you were absent
 *
 * The two subtractions are the part people get wrong. A cancelled class is not
 * an absence *and* is not a class — it leaves the denominator, which means every
 * cancellation slightly *shrinks* your absence budget. That is counter-intuitive
 * and it is correct: 70% of a smaller number is a smaller number.
 *
 * `floor` is deliberate and conservative. With 42 hours the budget is
 * floor(12.6) = 12, not 13. Rounding up here would tell you that you have a
 * spare absence you do not have, on the one question where being wrong costs a
 * course.
 */
object AttendanceCalculator {

    /** One concrete calendar occurrence of one teaching hour. */
    data class Occurrence(
        val course: Course,
        val date: LocalDate,
        val week: Int,
    ) {
        val key: String get() = "${course.id}@$date"
        val endsAt: LocalDateTime get() = date.atTime(course.endTime)
        val startsAt: LocalDateTime get() = date.atTime(course.startTime)
    }

    /**
     * Expand the weekly schedule into every dated occurrence in the term,
     * skipping holidays. This is the backbone: the denominator, the "which class
     * just ended" lookup and the fallback scheduler all read from it.
     */
    fun occurrences(courses: List<Course>, term: Term): List<Occurrence> {
        if (courses.isEmpty()) return emptyList()
        val out = ArrayList<Occurrence>(courses.size * term.totalWeeks)
        val monday = term.start.with(java.time.DayOfWeek.MONDAY)
        for (week in 1..term.totalWeeks) {
            val weekStart = monday.plusWeeks((week - 1).toLong())
            for (course in courses) {
                val date = weekStart.plusDays((course.dayOfWeek.value - 1).toLong())
                if (date in term.holidayDates) continue
                out.add(Occurrence(course, date, week))
            }
        }
        return out
    }

    /** Occurrences of one subject only. */
    fun occurrencesOf(code: String, courses: List<Course>, term: Term): List<Occurrence> =
        occurrences(courses.filter { it.code == code }, term)

    /**
     * The verdict for every subject in the schedule.
     *
     * @param now used to decide which past hours are still unanswered. Passed in
     *   rather than read from the clock so this stays pure and testable.
     */
    fun summarise(
        courses: List<Course>,
        term: Term,
        records: Map<String, AttendanceRecord>,
        now: LocalDateTime,
    ): List<AttendanceSummary> {
        if (courses.isEmpty()) return emptyList()

        val all = occurrences(courses, term)
        val byCode = all.groupBy { it.course.code }

        return byCode.map { (code, occ) ->
            val name = occ.first().course.name
            val weekly = courses.count { it.code == code }

            var attended = 0
            var absent = 0
            var cancelled = 0
            var unanswered = 0

            for (o in occ) {
                when (records[o.key]?.status) {
                    AttendanceStatus.ATTENDED -> attended++
                    AttendanceStatus.ABSENT -> absent++
                    AttendanceStatus.CANCELLED -> cancelled++
                    // No answer yet. Only counts as a gap once the hour is over;
                    // a class that has not happened is not a missing answer.
                    null -> if (o.endsAt.isBefore(now)) unanswered++
                }
            }

            val scheduled = occ.size
            val effective = scheduled - cancelled
            val allowed = floor(effective * (1.0 - term.requiredRate)).toInt()

            AttendanceSummary(
                courseCode = code,
                courseName = name,
                weeklyHours = weekly,
                scheduledHours = scheduled,
                effectiveHours = effective,
                allowedAbsences = allowed,
                attendedHours = attended,
                absentHours = absent,
                cancelledHours = cancelled,
                unansweredHours = unanswered,
            )
        }.sortedBy { it.courseName }
    }

    /**
     * The occurrence that just ended and still needs an answer — what the
     * post-class prompt asks about.
     *
     * @param graceMinutes how long after the bell an occurrence stays askable.
     *   Beyond this window the question is stale; it belongs in the "eksik
     *   cevaplar" catch-up list instead of interrupting you hours later.
     */
    fun pendingAnswer(
        courses: List<Course>,
        term: Term,
        records: Map<String, AttendanceRecord>,
        now: LocalDateTime,
        graceMinutes: Long = 180,
    ): Occurrence? = occurrences(courses, term)
        .asSequence()
        .filter { it.endsAt.isBefore(now) }
        .filter { it.endsAt.isAfter(now.minusMinutes(graceMinutes)) }
        .filter { records[it.key] == null }
        .minByOrNull { it.endsAt }

    /** Every past occurrence with no answer, oldest first — the catch-up list. */
    fun unanswered(
        courses: List<Course>,
        term: Term,
        records: Map<String, AttendanceRecord>,
        now: LocalDateTime,
    ): List<Occurrence> = occurrences(courses, term)
        .filter { it.endsAt.isBefore(now) && records[it.key] == null }
        .sortedBy { it.endsAt }
}
