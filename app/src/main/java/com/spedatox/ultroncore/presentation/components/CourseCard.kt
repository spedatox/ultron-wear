package com.spedatox.ultroncore.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import com.spedatox.ultroncore.data.Course
import com.spedatox.ultroncore.design.GlassRadius
import com.spedatox.ultroncore.design.UltronPalette
import com.spedatox.ultroncore.design.UltronType
import com.spedatox.ultroncore.design.accentEdge
import com.spedatox.ultroncore.design.ultronGlass

/** What a card is currently doing. Ordered by visual priority. */
enum class SlotState { ACTIVE, NEXT, IDLE }

/**
 * One teaching hour in the schedule list.
 *
 * ── Why the odd `progress: () -> Float` parameter ───────────────────────────
 * The progress bar under a running lecture changes every minute. Taking a
 * `Float` would make this composable recompose every minute; taking a lambda
 * that is only *invoked inside* [Modifier.drawBehind] defers the state read to
 * the draw phase. Compose then skips composition and layout entirely and just
 * redraws — the difference between re-running the whole card and pushing a
 * rectangle to the display list.
 *
 * Everything else about this card is a stable, `@Immutable` input, so a card
 * that is neither active nor next is skipped completely on every tick.
 */
@Composable
fun CourseCard(
    course: Course,
    state: SlotState,
    palette: UltronPalette,
    progress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (state) {
        SlotState.ACTIVE -> palette.green
        SlotState.NEXT -> palette.accentBright
        SlotState.IDLE -> palette.accentDim
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .ultronGlass(
                palette = palette,
                radius = GlassRadius,
                active = state == SlotState.ACTIVE,
                accentRim = if (state == SlotState.IDLE) null else accent.copy(alpha = 0.45f),
            )
            .accentEdge(accent)
            .clickable(onClick = onClick)
            .padding(start = 13.dp, end = 11.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = course.name,
                    style = UltronType.title,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = course.startString,
                    style = UltronType.numThin,
                    color = accent,
                )
            }

            Spacer(Modifier.height(3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = course.roomNumber,
                    style = UltronType.readout,
                    color = palette.textFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (state != SlotState.IDLE) {
                    Spacer(Modifier.width(6.dp))
                    StatusChip(
                        label = if (state == SlotState.ACTIVE) "ŞİMDİ" else "SONRAKİ",
                        color = accent,
                        palette = palette,
                    )
                }
            }

            if (state == SlotState.ACTIVE) {
                Spacer(Modifier.height(6.dp))
                ProgressRail(progress = progress, track = palette.accentDim, fill = palette.green)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color, palette: UltronPalette) {
    Text(
        text = label,
        style = UltronType.label,
        color = color,
        modifier = Modifier
            .drawBehind {
                drawRoundRect(
                    color = color.copy(alpha = 0.14f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                )
            }
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/**
 * The lecture progress rail. The `progress()` call happens inside the draw
 * lambda — that placement is the entire optimisation, so do not hoist it out
 * into a local `val` above the modifier.
 */
@Composable
private fun ProgressRail(progress: () -> Float, track: Color, fill: Color) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .drawBehind {
                val r = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
                drawRoundRect(color = track, cornerRadius = r)
                val p = progress().coerceIn(0f, 1f)
                if (p > 0f) {
                    drawRoundRect(
                        color = fill,
                        topLeft = Offset.Zero,
                        size = Size(size.width * p, size.height),
                        cornerRadius = r,
                    )
                }
            },
    )
}
