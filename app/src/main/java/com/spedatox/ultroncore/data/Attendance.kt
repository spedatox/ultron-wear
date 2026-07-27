package com.spedatox.ultroncore.data

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * What happened at one occurrence of one teaching hour.
 *
 * [CANCELLED] is not a third flavour of absence — it is the instructor not
 * holding the class, which removes that hour from the denominator entirely.
 * Conflating it with [ABSENT] is the single easiest way to make this whole
 * feature lie to you, so it is a distinct state everywhere: storage, math, UI
 * and the Igor payload.
 */
@Serializable
enum class AttendanceStatus {
    @SerialName("attended") ATTENDED,
    @SerialName("absent") ABSENT,
    @SerialName("cancelled") CANCELLED,
}

/**
 * One answer to one "derse girdin mi?" question.
 *
 * Identity is `(slotId, date)`. Re-answering the same occurrence overwrites
 * rather than appends — you are allowed to correct yourself, and a duplicate
 * would silently double-count against your attendance budget.
 */
@Serializable
@Immutable
data class AttendanceRecord(
    /** [Course.id] of the teaching hour this answers for. */
    val slotId: String,
    /** Denormalised [Course.code]. Kept on the record so the ledger can still
     *  compute a verdict for a course that has been dropped from the schedule
     *  mid-semester — the past absences remain real. */
    val courseCode: String,
    /** ISO-8601. The specific calendar occurrence, not the weekday. */
    val date: String,
    val status: AttendanceStatus,
    /** Epoch millis. Used to resolve conflicts when the watch and Igor both
     *  hold a record for the same occurrence — newest answer wins. */
    val recordedAt: Long,
    /** False until Igor has acknowledged this record. Drives the sync queue. */
    val synced: Boolean = false,
) {
    val localDate: LocalDate get() = LocalDate.parse(date)

    /** The composite key. */
    val key: String get() = "$slotId@$date"
}

/**
 * Semester parameters. Defaults encode the standard Turkish undergraduate rule
 * the user is actually governed by — 14 teaching weeks, 70% attendance
 * mandatory — but both are configurable because summer school, 12-week terms
 * and 80%-attendance lab courses all exist.
 */
@Serializable
@Immutable
data class Term(
    /** ISO-8601 date of the **Monday of week 1**. Everything that maps a date
     *  to a week number counts from here. */
    val startDate: String,
    val totalWeeks: Int = 14,
    /** Fraction of hours that must be attended. 0.70 = 70%. */
    val requiredRate: Double = 0.70,
    /** Dates with no teaching (resmî tatil, ara tatil, bayram). Occurrences on
     *  these dates are never scheduled, never asked about, and never counted in
     *  the denominator. */
    val holidays: List<String> = emptyList(),
) {
    val start: LocalDate get() = LocalDate.parse(startDate)

    val holidayDates: Set<LocalDate> by lazy { holidays.mapTo(HashSet()) { LocalDate.parse(it) } }

    /** 1-based teaching week for [date], or null if outside the term. */
    fun weekOf(date: LocalDate): Int? {
        val days = java.time.temporal.ChronoUnit.DAYS.between(start, date)
        if (days < 0) return null
        val week = (days / 7).toInt() + 1
        return if (week in 1..totalWeeks) week else null
    }

    fun isTeachingDay(date: LocalDate): Boolean =
        weekOf(date) != null && date !in holidayDates
}

/** How much trouble a course is in. */
enum class AttendanceRisk {
    /** Comfortable margin. */
    SAFE,
    /** Two or fewer absences left — every further miss matters. */
    WARNING,
    /** Zero left. One more absence fails the course. */
    CRITICAL,
    /** Already over the limit. Devamsızlıktan kaldı. */
    FAILED,
}

/**
 * The computed verdict for one subject. Everything the UI and Igor need to say
 * "you have N left" without re-deriving the math.
 */
@Immutable
data class AttendanceSummary(
    val courseCode: String,
    val courseName: String,
    /** Teaching hours per week for this subject (its slot count). */
    val weeklyHours: Int,
    /** Hours the term would hold if nothing were cancelled. */
    val scheduledHours: Int,
    /** [scheduledHours] minus cancelled hours — the real denominator. */
    val effectiveHours: Int,
    /** Absence budget: floor(effectiveHours × (1 − requiredRate)). */
    val allowedAbsences: Int,
    val attendedHours: Int,
    val absentHours: Int,
    val cancelledHours: Int,
    /** Hours already elapsed that have no answer yet. */
    val unansweredHours: Int,
) {
    /** Absences still available. Negative means the limit is already blown. */
    val remainingAbsences: Int get() = allowedAbsences - absentHours

    val risk: AttendanceRisk
        get() = when {
            remainingAbsences < 0 -> AttendanceRisk.FAILED
            remainingAbsences == 0 -> AttendanceRisk.CRITICAL
            remainingAbsences <= 2 -> AttendanceRisk.WARNING
            else -> AttendanceRisk.SAFE
        }

    /**
     * Attendance rate over hours that have actually happened and been answered.
     * Returns null before the first answer — a rate of "0%" on day one is
     * technically true and completely useless.
     */
    val currentRate: Double?
        get() {
            val answered = attendedHours + absentHours
            return if (answered == 0) null else attendedHours.toDouble() / answered
        }

    /** Fraction of the absence budget burned, clamped for use as a meter. */
    val budgetUsed: Float
        get() = if (allowedAbsences <= 0) 1f
        else (absentHours.toFloat() / allowedAbsences).coerceIn(0f, 1f)
}
