package com.spedatox.ultroncore.notification

import java.time.LocalDate

/**
 * One "derse girdin mi?" question, however it arrived.
 *
 * Igor pushes this as an FCM data message; the local fallback worker builds the
 * identical object from the cached schedule. Both paths converge here so the
 * notification, the prompt screen and the ledger write never need to know which
 * trigger fired.
 */
data class AttendanceAsk(
    val slotId: String,
    val courseCode: String,
    val courseName: String,
    val date: LocalDate,
    val timeLabel: String,
    val room: String,
) {
    /** Stable per occurrence — used as the notification id so a re-delivered
     *  push replaces the existing notification instead of stacking a second
     *  copy of the same question. */
    val notificationId: Int get() = "$slotId@$date".hashCode()

    fun toExtras(): Map<String, String> = mapOf(
        KEY_SLOT to slotId,
        KEY_CODE to courseCode,
        KEY_NAME to courseName,
        KEY_DATE to date.toString(),
        KEY_TIME to timeLabel,
        KEY_ROOM to room,
    )

    companion object {
        const val KEY_SLOT = "slot_id"
        const val KEY_CODE = "course_code"
        const val KEY_NAME = "course_name"
        const val KEY_DATE = "date"
        const val KEY_TIME = "time"
        const val KEY_ROOM = "room"

        /** Parse from an FCM data payload or an Intent's extras. Returns null on
         *  anything malformed — a bad push must not crash the receiver. */
        fun from(data: Map<String, String?>): AttendanceAsk? {
            val slot = data[KEY_SLOT] ?: return null
            val date = runCatching { LocalDate.parse(data[KEY_DATE]) }.getOrNull() ?: return null
            return AttendanceAsk(
                slotId = slot,
                courseCode = data[KEY_CODE].orEmpty(),
                courseName = data[KEY_NAME].orEmpty().ifBlank { slot },
                date = date,
                timeLabel = data[KEY_TIME].orEmpty(),
                room = data[KEY_ROOM].orEmpty(),
            )
        }
    }
}
