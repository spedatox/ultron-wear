package com.spedatox.ultroncore.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
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
 *
 * ── Why LazyColumn and not ScalingLazyColumn ────────────────────────────────
 * SLC's fisheye applies a per-item `graphicsLayer` with both a scale and an
 * alpha, recomputed for every visible item on every frame of a scroll. An alpha
 * below 1 makes that layer composite offscreen, so each of the ~5 cards on
 * screen during a fling was being rendered into its own buffer and blitted back
 * — on a Mali-G68 MP2 that is the frame budget, spent on an effect this design
 * never asked for. Google's own guidance says to use LazyColumn "if scaling /
 * fisheye functionality is not required ... to avoid any overhead of measuring
 * and calculating scaling and transparency effects for the content items".
 *
 * Ultron's design language is a flat HUD of glass plates with hard rims; the
 * fisheye actively fought it. Rotary/bezel scrolling and the scroll indicator
 * are unaffected — `ScreenScaffold` has a `LazyListState` overload and drives
 * both from it exactly as it did from the SLC state.
 */
@Composable
fun ScheduleScreen(
    vm: UltronViewModel,
    palette: UltronPalette,
    listState: LazyListState,
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

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // Rotary is NOT free here. ScreenScaffold wires the bezel
            // automatically for ScalingLazyColumn, TransformingLazyColumn and
            // Picker — and for nothing else. Dropping SLC for the fisheye's
            // frame cost therefore also dropped bezel scrolling, which is the
            // primary way this watch is driven. Wiring it back explicitly is
            // the price of the flat list, and it is worth paying.
            .rotaryScrollable(
                RotaryScrollableDefaults.behavior(listState),
                focusRequester = rememberActiveFocusRequester(),
            ),
        // A flat list does not self-centre the way SLC did, so the vertical
        // padding has to keep the first and last card clear of the round bezel
        // itself. Tune here if a card corner ever kisses the edge.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
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
            // Indexed rather than the list overload: a stable `key` per row is
            // what lets the list keep a card's state and slot across
            // recompositions instead of rebuilding it when the schedule flow
            // re-emits.
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
