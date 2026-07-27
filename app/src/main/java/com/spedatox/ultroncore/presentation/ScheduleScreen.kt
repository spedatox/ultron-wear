package com.spedatox.ultroncore.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Text
import com.spedatox.ultroncore.R
import com.spedatox.ultroncore.design.GlassRadiusSmall
import com.spedatox.ultroncore.design.UltronPalette
import com.spedatox.ultroncore.design.UltronType
import com.spedatox.ultroncore.design.ultronGlass
import com.spedatox.ultroncore.presentation.components.CourseCard
import com.spedatox.ultroncore.presentation.components.DayHeader
import com.spedatox.ultroncore.presentation.components.SlotState
import com.spedatox.ultroncore.presentation.components.UltronHeader

/**
 * The schedule — the app's home screen.
 *
 * Note what is NOT here: no `currentTime` state, no per-card status computation,
 * no `remember` keyed on the clock. The view model hands down an active id and a
 * next id; each card compares its own id and gets a Boolean. That is what keeps
 * a minute tick from invalidating thirteen cards. See [UltronViewModel].
 */
@Composable
fun ScheduleScreen(
    vm: UltronViewModel,
    palette: UltronPalette,
    listState: ScalingLazyListState,
    onCourseClick: (courseId: String) -> Unit,
    onAttendanceClick: () -> Unit,
) {
    val week by vm.week.collectAsStateWithLifecycle()
    val timeline by vm.timeline.collectAsStateWithLifecycle()
    val currentWeek by vm.currentWeek.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val unanswered by vm.unansweredCount.collectAsStateWithLifecycle()

    if (week.isEmpty()) {
        EmptySchedule(loaded = loaded, palette = palette)
        return
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            UltronHeader(week = currentWeek, palette = palette)
        }

        if (unanswered > 0) {
            item(key = "catchup") {
                CatchUpBanner(
                    count = unanswered,
                    palette = palette,
                    onClick = onAttendanceClick,
                )
            }
        }

        week.forEach { section ->
            item(key = "day:${section.day}") {
                DayHeader(
                    day = section.day,
                    isToday = section.day == timeline.today,
                    palette = palette,
                )
            }
            // Indexed rather than the list overload: ScalingLazyColumn's `items`
            // takes a count, and a stable `key` per row is what lets the list
            // keep a card's state and slot across recompositions instead of
            // rebuilding it when the schedule flow re-emits.
            items(
                count = section.courses.size,
                key = { i -> section.courses[i].id },
            ) { index ->
                val course = section.courses[index]
                CourseCard(
                    course = course,
                    state = when (course.id) {
                        timeline.activeId -> SlotState.ACTIVE
                        timeline.nextId -> SlotState.NEXT
                        else -> SlotState.IDLE
                    },
                    palette = palette,
                    progress = { vm.progressOf(course) },
                    onClick = { onCourseClick(course.id) },
                )
            }
        }

        item(key = "attendance_entry") {
            AttendanceEntryButton(palette = palette, onClick = onAttendanceClick)
        }
    }
}

@Composable
private fun EmptySchedule(loaded: Boolean, palette: UltronPalette) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(if (loaded) R.string.no_courses else R.string.loading),
            style = UltronType.head,
            color = palette.textDim,
            textAlign = TextAlign.Center,
        )
        if (loaded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.no_courses_hint),
                style = UltronType.body,
                color = palette.textFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Amber, because this is the design language's "needs your attention, nothing is
 * broken" state. Red is reserved for a course you have already failed.
 */
@Composable
private fun CatchUpBanner(
    count: Int,
    palette: UltronPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .ultronGlass(palette, radius = GlassRadiusSmall, accentRim = palette.amber.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.pending_answers, count),
            style = UltronType.readout,
            color = palette.amberBright,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AttendanceEntryButton(palette: UltronPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .ultronGlass(palette, radius = GlassRadiusSmall)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.attendance_title).uppercase(TURKISH),
            style = UltronType.label,
            color = palette.accentBright,
        )
    }
}

private val TURKISH: java.util.Locale = java.util.Locale.forLanguageTag("tr")
