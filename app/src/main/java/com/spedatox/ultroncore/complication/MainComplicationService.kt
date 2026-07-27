package com.spedatox.ultroncore.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.spedatox.ultroncore.UltronWear
import com.spedatox.ultroncore.presentation.MainActivity
import java.time.LocalTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainComplicationService : SuspendingComplicationDataSourceService() {

    /** Shared, not per-request — see the note in MainTileService. */
    private val schedule get() = UltronWear.from(this).schedule

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder("14:30").build(),
                    contentDescription = PlainComplicationText.Builder("Next course time").build()
                ).build()
            }
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> {
                val currentTime = LocalTime.now()
                val today = LocalDate.now().dayOfWeek

                // First try to get current ongoing course
                val currentCourse = schedule.current(today, currentTime)

                // If no current course, get next upcoming course
                val nextCourse = currentCourse ?: schedule.upcoming(today, currentTime)

                val displayText = if (nextCourse != null) {
                    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                    nextCourse.startTime.format(timeFormatter)
                } else {
                    // No more courses today, check if we have any courses at all today
                    val todayCourses = schedule.byDay(today)
                    if (todayCourses.isNotEmpty()) {
                        "✓" // All courses done for today
                    } else {
                        "--:--" // No courses today
                    }
                }

                val contentDescription = when {
                    currentCourse != null -> "Current course: ${currentCourse.name} started at ${displayText}"
                    nextCourse != null -> "Next course: ${nextCourse.name} at ${displayText}"
                    else -> "No more courses today"
                }

                // Create intent to open the main app when tapped
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(displayText).build(),
                    contentDescription = PlainComplicationText.Builder(contentDescription).build()
                )
                    .setTapAction(pendingIntent)
                    .build()
            }
            else -> null
        }
    }
}
