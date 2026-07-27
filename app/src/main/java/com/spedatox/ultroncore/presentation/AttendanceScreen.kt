package com.spedatox.ultroncore.presentation

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
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.spedatox.ultroncore.R
import com.spedatox.ultroncore.data.AttendanceRisk
import com.spedatox.ultroncore.data.AttendanceSummary
import com.spedatox.ultroncore.design.GlassRadius
import com.spedatox.ultroncore.design.UltronPalette
import com.spedatox.ultroncore.design.UltronType
import com.spedatox.ultroncore.design.accentEdge
import com.spedatox.ultroncore.design.ultronGlass
import com.spedatox.ultroncore.presentation.components.SectionLabel

/**
 * The answer to the only question that matters: how many more classes can I miss
 * before I fail?
 *
 * Sorted by risk, not alphabetically. The course about to fail you belongs at
 * the top of a screen you glance at, and a screen you have to scroll to find bad
 * news on is a screen that delivers bad news late.
 */
@Composable
fun AttendanceScreen(vm: UltronViewModel, palette: UltronPalette) {
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()

    if (summaries.isEmpty()) {
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

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp),
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
        items(count = ordered.size, key = { i -> ordered[i].courseCode }) { index ->
            AttendanceCard(summary = ordered[index], palette = palette)
        }
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
            .ultronGlass(palette, radius = GlassRadius, accentRim = accent.copy(alpha = 0.4f))
            .accentEdge(accent)
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
