package com.spedatox.ultroncore.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.spedatox.ultroncore.design.UltronPalette
import com.spedatox.ultroncore.design.UltronType
import com.spedatox.ultroncore.design.etchedSeam
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The day divider — a Rajdhani all-caps label over an etched seam, with a live
 * dot on today. This is the design language's structural boundary: two hairlines
 * (dark groove + light catch), not a border.
 */
@Composable
fun DayHeader(
    day: DayOfWeek,
    isToday: Boolean,
    palette: UltronPalette,
    modifier: Modifier = Modifier,
) {
    // The schedule is Turkish regardless of the watch's locale — these are the
    // names on the actual timetable, and a watch set to English should not
    // rename the user's Tuesday.
    val label = remember(day) {
        day.getDisplayName(TextStyle.FULL, TURKISH).uppercase(TURKISH)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .padding(top = 10.dp, bottom = 5.dp)
            .etchedSeam(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 5.dp),
        ) {
            if (isToday) {
                Box(
                    Modifier
                        .size(5.dp)
                        .drawBehind { drawCircle(color = liveDot) },
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = label,
                style = UltronType.head,
                color = if (isToday) palette.accentBright else palette.textFaint,
            )
        }
    }
}

/** Small uppercase section label used above lists and readouts. */
@Composable
fun SectionLabel(text: String, palette: UltronPalette, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(TURKISH),
        style = UltronType.label,
        color = palette.textFaint,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/**
 * The app's identity plate: "ULTRON" over its model number, with the week
 * readout. Mirrors the agent hero in the phone client's welcome view, minus the
 * typewriter — a watch screen that animates text on every open is a watch screen
 * you stop opening.
 */
@Composable
fun UltronHeader(week: Int?, palette: UltronPalette, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "ULTRON", style = UltronType.head, color = palette.text)
        Spacer(Modifier.height(1.dp))
        Text(
            text = if (week != null) "MARK III · HAFTA $week" else "MARK III",
            style = UltronType.label,
            color = palette.textFaint,
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
fun Hairline(palette: UltronPalette, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .drawBehind { drawRect(palette.line) },
    )
}

private val TURKISH: Locale = Locale.forLanguageTag("tr")

/** The one hard-coded colour in the UI: the "today" pulse. Semantic green from
 *  BaseTokens, which is never re-hued. */
private val liveDot = Color(0xFF4FA377)
