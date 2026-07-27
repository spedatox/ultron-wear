package com.spedatox.ultroncore.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.degrees
import androidx.wear.protolayout.DimensionBuilders.SpProp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.spedatox.ultroncore.R
import com.spedatox.ultroncore.UltronWear
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private const val RESOURCES_VERSION = "3"

/**
 * Ultron palette, flattened to opaque ARGB.
 *
 * Protolayout has no alpha compositing worth relying on and no access to the
 * Compose palette, so these are the design tokens pre-resolved against black:
 * the accent family from Brands.kt (#8a93a6 and its bright/dim derivations) plus
 * the never-re-hued semantic green/amber/red.
 */
private object TileColors {
    val accent = argb(0xFF8A93A6.toInt())
    val accentBright = argb(0xFFABB1BF.toInt())
    val activeGreen = argb(0xFF4FA377.toInt())
    val amber = argb(0xFFD99C44.toInt())
    val red = argb(0xFFC84A3A.toInt())
    val background = argb(0xFF000000.toInt())
    val textPrimary = argb(0xFFD3D8E2.toInt())
    val textSecondary = argb(0xFF8A94A4.toInt())
    val textTertiary = argb(0xFF55606E.toInt())
    val outline = argb(0xFF373D45.toInt())
}

class MainTileService : TileService() {

    /** The shared repository, not a fresh one. The original build constructed a
     *  `CourseRepository` here, which re-parsed the schedule JSON from assets on
     *  every single tile refresh — once a minute, forever. */
    private val schedule get() = UltronWear.from(this).schedule

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest) =
        com.google.common.util.concurrent.Futures.immediateFuture(buildTile())

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest) =
        com.google.common.util.concurrent.Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(
                    "academic_cap",
                    ResourceBuilders.ImageResource.Builder()
                        .setAndroidResourceByResId(
                            ResourceBuilders.AndroidImageResourceByResId.Builder()
                                .setResourceId(R.drawable.ic_academic_cap)
                                .build()
                        )
                        .build()
                )
                .build()
        )

    private fun buildTile(): TileBuilders.Tile {
        val currentTimeMillis = System.currentTimeMillis()
        val nextUpdateTimeMillis = currentTimeMillis + 60_000

        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setValidity(
                        TimelineBuilders.TimeInterval.Builder()
                            .setEndMillis(nextUpdateTimeMillis)
                            .build()
                    )
                    .setLayout(
                        LayoutElementBuilders.Layout.Builder()
                            .setRoot(createFullScreenTile())
                            .build()
                    )
                    .build()
            )
            .build()

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(timeline)
            .setFreshnessIntervalMillis(60_000)
            .build()
    }

    private fun createFullScreenTile(): LayoutElementBuilders.LayoutElement {
        val today = LocalDate.now().dayOfWeek
        val now = LocalTime.now()
        val nextCourse = schedule.upcoming(today, now)
        val currentCourse = schedule.current(today, now)

        val clickable = ModifiersBuilders.Clickable.Builder()
            .setOnClick(ActionBuilders.LaunchAction.Builder().build())
            .setId("open_app")
            .build()

        fun fmtTime(totalMin: Long): String {
            return if (totalMin >= 60) {
                val h = totalMin / 60
                val m = totalMin % 60
                if (m > 0) "${h}s ${m}dk" else "${h}s"
            } else "${totalMin}dk"
        }

        fun getCourseTitleSize(text: String): SpProp {
            return when {
                text.length > 40 -> sp(16f)
                text.length > 30 -> sp(18f)
                text.length > 20 -> sp(20f)
                text.length > 14 -> sp(22f)
                else -> sp(24f)
            }
        }

        var progressFraction: Float? = null
        var progressPercent = 0
        var timeRemaining = ""

        if (currentCourse != null) {
            val total = Duration.between(currentCourse.startTime, currentCourse.endTime).toMinutes().coerceAtLeast(1)
            val elapsed = Duration.between(currentCourse.startTime, now).toMinutes().coerceAtLeast(0)
            val remaining = (total - elapsed).coerceAtLeast(0)
            progressPercent = (elapsed * 100 / total).toInt().coerceIn(0, 100)
            progressFraction = (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            timeRemaining = fmtTime(remaining)
        }

        val (statusText, courseName, courseInfo, timeInfo, statusColor) = when {
            currentCourse != null -> {
                val status = "AKTİF"
                val name = currentCourse.name
                val info = currentCourse.roomNumber
                val time = "%${progressPercent} • ${timeRemaining} kaldı"
                Quintuple(status, name, info, time, TileColors.activeGreen)
            }
            nextCourse != null -> {
                val until = Duration.between(now, nextCourse.startTime).toMinutes().coerceAtLeast(0)
                val status = "SONRAKİ"
                val name = nextCourse.name
                val info = nextCourse.roomNumber
                val time = "${fmtTime(until)} sonra • ${nextCourse.startTime.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                Quintuple(status, name, info, time, TileColors.accentBright)
            }
            else -> {
                Quintuple("BOŞ", "Ders Yok", "Bugün", "İyi dinlenmeler", TileColors.accent)
            }
        }

        val refreshTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        // Background arc
        val backgroundCircle = LayoutElementBuilders.Arc.Builder()
            .addContent(
                LayoutElementBuilders.ArcLine.Builder()
                    .setColor(TileColors.outline)
                    .setThickness(dp(5f))
                    .setLength(degrees(360f))
                    .build()
            )
            .build()

        // Progress arc
        val progressArc = if (progressFraction != null && progressFraction > 0) {
            LayoutElementBuilders.Arc.Builder()
                .addContent(
                    LayoutElementBuilders.ArcLine.Builder()
                        .setColor(statusColor)
                        .setThickness(dp(5f))
                        .setLength(degrees((progressFraction * 360f).coerceIn(0f, 360f)))
                        .build()
                )
                .build()
        } else null

        // Center content
        val centerContent = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            // Status badge
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(statusColor)
                                    .setCorner(
                                        ModifiersBuilders.Corner.Builder()
                                            .setRadius(dp(14f))
                                            .build()
                                    )
                                    .build()
                            )
                            .setPadding(
                                ModifiersBuilders.Padding.Builder()
                                    .setStart(dp(14f))
                                    .setEnd(dp(14f))
                                    .setTop(dp(5f))
                                    .setBottom(dp(5f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(
                        LayoutElementBuilders.Text.Builder()
                            .setText(statusText)
                            .setFontStyle(
                                LayoutElementBuilders.FontStyle.Builder()
                                    .setSize(sp(11f))
                                    .setColor(TileColors.textPrimary)
                                    .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(12f)).build())
            // Course name
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(courseName)
                    .setMaxLines(2)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(getCourseTitleSize(courseName))
                            .setColor(TileColors.textPrimary)
                            .setWeight(LayoutElementBuilders.FONT_WEIGHT_BOLD)
                            .build()
                    )
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(8f)).build())
            // Course info (room)
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(courseInfo)
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(13f))
                            .setColor(TileColors.textSecondary)
                            .build()
                    )
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(6f)).build())
            // Time info
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(timeInfo)
                    .setMaxLines(1)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(11f))
                            .setColor(TileColors.textTertiary)
                            .build()
                    )
                    .build()
            )
            .addContent(LayoutElementBuilders.Spacer.Builder().setHeight(dp(16f)).build())
            // Refresh timestamp
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(refreshTime)
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(sp(9f))
                            .setColor(TileColors.textTertiary)
                            .build()
                    )
                    .build()
            )
            .build()

        // Full screen container
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(TileColors.background)
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                ModifiersBuilders.Padding.Builder()
                                    .setAll(dp(3f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(backgroundCircle)
                    .build()
            )
            .apply {
                if (progressArc != null) {
                    addContent(
                        LayoutElementBuilders.Box.Builder()
                            .setWidth(expand())
                            .setHeight(expand())
                            .setModifiers(
                                ModifiersBuilders.Modifiers.Builder()
                                    .setPadding(
                                        ModifiersBuilders.Padding.Builder()
                                            .setAll(dp(3f))
                                            .build()
                                    )
                                    .build()
                            )
                            .addContent(progressArc)
                            .build()
                    )
                }
            }
            .addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(expand())
                    .setHeight(expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setPadding(
                                ModifiersBuilders.Padding.Builder()
                                    .setAll(dp(18f))
                                    .build()
                            )
                            .build()
                    )
                    .addContent(centerContent)
                    .build()
            )
            .build()
    }

    private data class Quintuple<T, U, V, W, X>(val first: T, val second: U, val third: V, val fourth: W, val fifth: X)
}