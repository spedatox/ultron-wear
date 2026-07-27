package com.spedatox.ultroncore.data

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

/**
 * One scheduled **ders saati** — a single teaching hour in a single weekly slot.
 *
 * This is deliberately the finest grain in the model. A three-hour course on
 * Tuesday morning is three [Course] rows, not one row with a duration, because
 * attendance in the Turkish system is counted per hour: you can walk into the
 * 09:00 and skip the 11:00, and the yoklama records that as one absence, not
 * zero and not three. Grouping back up to the subject happens via [code].
 */
@Immutable
data class Course(
    /** Unique per slot. Stable across schedule refreshes — it is the key the
     *  attendance ledger and the Igor sync both join on. */
    val id: String,
    /** Subject code (`PHYS101`). Groups the weekly slots that share an
     *  attendance budget. Two different courses must never share a code. */
    val code: String,
    val name: String,
    val instructor: String,
    val roomNumber: String,
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val endTime: LocalTime,
) {
    val timeString: String
        get() = "${fmt(startTime)} - ${fmt(endTime)}"

    val startString: String get() = fmt(startTime)

    val durationMinutes: Long
        get() = Duration.between(startTime, endTime).toMinutes()

    private fun fmt(t: LocalTime) =
        "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
}
