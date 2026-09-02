package com.spedatox.ultroncore.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Text
import com.spedatox.ultroncore.R
import com.spedatox.ultroncore.data.AttendanceCalculator
import com.spedatox.ultroncore.data.AttendanceRisk
import com.spedatox.ultroncore.data.AttendanceStatus
import com.spedatox.ultroncore.data.AttendanceSummary
import com.spedatox.ultroncore.design.GlassRadius
import com.spedatox.ultroncore.design.GlassRadiusSmall
import com.spedatox.ultroncore.design.UltronPalette
import com.spedatox.ultroncore.design.UltronType
import com.spedatox.ultroncore.design.ultronGlass
import com.spedatox.ultroncore.presentation.components.SectionLabel

/**
 * The answer to the only question that matters: how many more classes can I miss
 * before I fail?
 *
 * Sorted by risk, not alphabetically. The course about to fail you belongs at
 * the top of a screen you glance at, and a screen you have to scroll to find bad
 * news on is a screen that delivers bad news late.
 *
 * Flat [LazyColumn] rather than a ScalingLazyColumn, for the reasons spelled out
 * on [ScheduleScreen]. The list state is hoisted so `ScreenScaffold` can drive
 * rotary input and the scroll indicator from it — it used to be created in here,
 * where the scaffold could not see it, which meant the bezel scrolled nothing on
 * this screen at all.
 */
@Composable
fun AttendanceScreen(
    vm: UltronViewModel,
    palette: UltronPalette,
    listState: LazyListState,
) {
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()

    if (summaries.isEmpty() && pending.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.no_courses),
                style = UltronType.head,
                color = palette.textDim,
            )
        }
        return
    }

    val ordered = summaries.sortedWith(
        compareBy({ RISK_ORDER.indexOf(it.risk) }, { it.remainingAbsences })
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // See ScheduleScreen: a plain LazyColumn gets no bezel support from
            // ScreenScaffold, so it has to be connected by hand.
            .rotaryScrollable(
                RotaryScrollableDefaults.behavior(listState),
                focusRequester = rememberActiveFocusRequester(),
            ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        item(key = "title") {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.attendance_title).uppercase(),
                    style = UltronType.head,
                    color = palette.text,
                )
                SectionLabel("%70 zorunlu · 14 hafta", palette)
            }
        }
        if (pending.isNotEmpty()) {
            item(key = "pending_label") {
                SectionLabel("${pending.size} ders kaydedilmedi", palette)
            }
            items(count = pending.size, key = { i -> pending[i].key }) { index ->
                val occ = pending[index]
                PendingCard(
                    occurrence = occ,
                    palette = palette,
                    onAnswer = { status -> vm.answer(occ, status) },
                )
            }
        }

        items(count = ordered.size, key = { i -> ordered[i].courseCode }) { index ->
            AttendanceCard(summary = ordered[index], palette = palette)
        }
    }
}

/**
 * One hole in the ledger, answerable in place.
 *
 * Three taps' worth of buttons rather than a tap-through to a prompt screen:
 * the whole reason these accumulate is that answering was a notification you had
 * to catch at the right moment. If clearing a backlog costs a screen transition
 * per entry, the backlog does not get cleared.
 *
 * Amber, not red — an unanswered hour is missing information, not a failure. Red
 * is reserved for a course you have actually lost.
 */
@Composable
private fun PendingCard(
    occurrence: AttendanceCalculator.Occurrence,
    palette: UltronPalette,
    onAnswer: (AttendanceStatus) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .ultronGlass(
                palette = palette,
                radius = GlassRadius,
                accentRim = palette.amber.copy(alpha = 0.45f),
                accentEdge = palette.amber,
            )
            .padding(start = 13.dp, end = 11.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = occurrence.course.name,
                style = UltronType.title,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${occurrence.date.dayOfMonth}.${occurrence.date.monthValue} · ${occurrence.course.timeString}",
                style = UltronType.readout,
                color = palette.textFaint,
            )

            Spacer(Modifier.height(7.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AnswerButton("VARDIM", palette.green, palette, Modifier.weight(1f)) {
                    onAnswer(AttendanceStatus.ATTENDED)
                }
                AnswerButton("YOK", palette.red, palette, Modifier.weight(1f)) {
                    onAnswer(AttendanceStatus.ABSENT)
                }
                AnswerButton("İPTAL", palette.textFaint, palette, Modifier.weight(1f)) {
                    onAnswer(AttendanceStatus.CANCELLED)
                }
            }
        }
    }
}

@Composable
private fun AnswerButton(
    label: String,
    accent: Color,
    palette: UltronPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .ultronGlass(palette, radius = GlassRadiusSmall, accentRim = accent.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = UltronType.label, color = accent)
    }
}

@Composable
private fun AttendanceCard(summary: AttendanceSummary, palette: UltronPalette) {
    val accent = when (summary.risk) {
        AttendanceRisk.SAFE -> palette.green
        AttendanceRisk.WARNING -> palette.amber
        AttendanceRisk.CRITICAL -> palette.amberBright
        AttendanceRisk.FAILED -> palette.red
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .ultronGlass(
                palette = palette,
                radius = GlassRadius,
                accentRim = accent.copy(alpha = 0.4f),
                accentEdge = accent,
            )
            .padding(start = 13.dp, end = 11.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = summary.courseName,
                style = UltronType.title,
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(5.dp))

            // The headline number. Everything else on this card is supporting
            // evidence for it.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = summary.remainingAbsences.coerceAtLeast(0).toString(),
                    style = UltronType.display,
                    color = accent,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = stringResource(
                        when {
                            summary.remainingAbsences > 0 -> R.string.rights_remaining
                            summary.remainingAbsences == 0 -> R.string.rights_none
                            else -> R.string.rights_over
                        }
                    ),
                    style = UltronType.body,
                    color = palette.textDim,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }

            Spacer(Modifier.height(6.dp))

            BudgetMeter(
                used = summary.budgetUsed,
                track = palette.accentDim,
                fill = accent,
            )

            Spacer(Modifier.height(5.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${summary.absentHours}/${summary.allowedAbsences} devamsız",
                    style = UltronType.readout,
                    color = palette.textFaint,
                )
                Text(
                    text = "${summary.weeklyHours} sa/hafta",
                    style = UltronType.readout,
                    color = palette.textFaint,
                )
            }

            if (summary.risk != AttendanceRisk.SAFE) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = stringResource(
                        when (summary.risk) {
                            AttendanceRisk.FAILED -> R.string.risk_failed
                            AttendanceRisk.CRITICAL -> R.string.risk_critical
                            else -> R.string.risk_warning
                        }
                    ),
                    style = UltronType.label,
                    color = accent,
                )
            }

            if (summary.unansweredHours > 0) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.pending_answers, summary.unansweredHours),
                    style = UltronType.readout,
                    color = palette.amber,
                )
            }
        }
    }
}

/** How much of the absence budget is spent. Fills toward the danger colour. */
@Composable
private fun BudgetMeter(used: Float, track: Color, fill: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .drawBehind {
                val r = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                drawRoundRect(color = track, cornerRadius = r)
                if (used > 0f) {
                    drawRoundRect(
                        color = fill,
                        topLeft = Offset.Zero,
                        size = Size(size.width * used.coerceIn(0f, 1f), size.height),
                        cornerRadius = r,
                    )
                }
            },
    )
}

private val RISK_ORDER = listOf(
    AttendanceRisk.FAILED,
    AttendanceRisk.CRITICAL,
    AttendanceRisk.WARNING,
    AttendanceRisk.SAFE,
)
