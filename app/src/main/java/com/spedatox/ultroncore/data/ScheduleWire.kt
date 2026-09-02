package com.spedatox.ultroncore.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * The schedule wire format — one shape shared by the bundled asset, the local
 * cache and Igor's `GET /academic/schedule` response. Keeping all three
 * identical means the cache is a byte copy of the response and the offline
 * fallback needs no separate parser.
 */
@Serializable
data class ScheduleDto(
    val term: TermDto? = null,
    val courses: List<CourseDto> = emptyList(),
    /** Server-set. Lets the watch skip a redundant re-parse when nothing moved. */
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class TermDto(
    @SerialName("start_date") val startDate: String,
    @SerialName("total_weeks") val totalWeeks: Int = 14,
    @SerialName("required_rate") val requiredRate: Double = 0.70,
    val holidays: List<String> = emptyList(),
) {
    fun toDomain() = Term(
        startDate = startDate,
        totalWeeks = totalWeeks,
        requiredRate = requiredRate,
        holidays = holidays,
    )
}

@Serializable
data class CourseDto(
    val id: String,
    /** Optional on the wire for backward compatibility with the original
     *  `courses.json`, which predates subject grouping. See [resolvedCode]. */
    val code: String? = null,
    val name: String,
    val instructor: String = "",
    @SerialName("roomNumber") val roomNumber: String = "",
    val dayOfWeek: String,
    val startTime: String,
    val endTime: String,
) {
    /**
     * Subject code, derived from the id prefix when absent.
     *
     * The legacy ids are `phys101_tuesday_0900`, so the segment before the first
     * underscore is the subject. This fallback exists only so an old cache file
     * still loads; new schedules from Igor always carry an explicit `code`,
     * because deriving identity from a string prefix is exactly the kind of
     * thing that breaks the day a course id contains an underscore.
     */
    val resolvedCode: String
        get() = code?.takeIf { it.isNotBlank() }
            ?: id.substringBefore('_').uppercase().ifBlank { id }

    fun toDomain() = Course(
        id = id,
        code = resolvedCode,
        name = name,
        instructor = instructor,
        roomNumber = roomNumber,
        dayOfWeek = DayOfWeek.valueOf(dayOfWeek.uppercase()),
        startTime = LocalTime.parse(startTime),
        endTime = LocalTime.parse(endTime),
    )
}

/** Outbound attendance record for `POST /academic/attendance`. */
@Serializable
data class AttendanceSyncDto(
    @SerialName("slot_id") val slotId: String,
    @SerialName("course_code") val courseCode: String,
    val date: String,
    val status: String,
    @SerialName("recorded_at") val recordedAt: Long,
) {
    companion object {
        fun from(r: AttendanceRecord) = AttendanceSyncDto(
            slotId = r.slotId,
            courseCode = r.courseCode,
            date = r.date,
            status = when (r.status) {
                AttendanceStatus.ATTENDED -> "attended"
                AttendanceStatus.ABSENT -> "absent"
                AttendanceStatus.CANCELLED -> "cancelled"
            },
            recordedAt = r.recordedAt,
        )
    }
}

@Serializable
data class AttendanceSyncRequest(
    val device: String,
    val records: List<AttendanceSyncDto>,
)

@Serializable
data class AttendanceSyncResponse(
    val accepted: List<String> = emptyList(),
    /** Records Igor holds that the watch does not, or holds a newer answer for. */
    val records: List<AttendanceSyncDto> = emptyList(),
)

/**
 * Both identifiers go up. `fid` is a genuine FCM v1 target field — probing the
 * live endpoint confirms it, since an unknown field is rejected with 400 and
 * `fid` is not — but in practice a correctly registered watch still had every
 * fid-addressed send answered with 404 UNREGISTERED. `token` is the decade-old
 * path and does not depend on fid-addressing being healthy, so Igor prefers it
 * and keeps `fid` as the fallback.
 *
 * `token` is nullable because getToken() can fail (no Play services, no
 * network). A registration arriving without one is itself the diagnosis: this
 * watch cannot reach FCM at all.
 */
@Serializable
data class DeviceRegisterRequest(
    val device: String,
    val platform: String = "wear",
    val fid: String,
    val token: String? = null,
)
