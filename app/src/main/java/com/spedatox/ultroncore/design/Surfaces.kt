package com.spedatox.ultroncore.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * ════════════════════════════════════════════════════════════════════════════
 *  THE ONE SURFACE MATERIAL — Speda glass, rebuilt for a watch.
 *
 *  WEAR DEVIATION (deliberate, and the single biggest perf decision in the app):
 *  the web/phone material is `backdrop-filter: blur(28px) saturate(140%)` plus a
 *  five-part shadow stack. Neither survives here.
 *
 *    · Blur. A backdrop blur forces the composition into an offscreen layer and
 *      runs a RenderEffect every frame it is visible. On the Exynos W930 in a
 *      scrolling list that is a guaranteed dropped-frame generator, and it burns
 *      battery continuously for an effect that is blurring *pure black* — the
 *      watch background has no content to refract. It buys literally nothing
 *      here. Removed, not approximated.
 *    · Shadows. `RoundedCornerShape` + elevation means a shadow layer per card.
 *      Against a true-black background a drop shadow is invisible. Removed.
 *
 *  What survives is what actually carries the design language: the occluding
 *  dark fill, the milky tint on top of it, and the 1px rim light. Heartbreaker's
 *  own CSS documents this exact combination as the blur-less fallback used
 *  "wherever nested backdrop roots cancel blur" — so this is the design system's
 *  sanctioned degradation path, not a shortcut invented here.
 *
 *  Every modifier below uses `drawWithCache`: the Brush and geometry are built
 *  once per size change and replayed on each draw, instead of being reallocated
 *  per frame the way a naive `drawBehind` would.
 * ════════════════════════════════════════════════════════════════════════════
 */

/** Standard glass radius, mirroring the web's 14px. */
val GlassRadius: Dp = 14.dp
val GlassRadiusSmall: Dp = 9.dp

val GlassShape = RoundedCornerShape(GlassRadius)
val GlassShapeSmall = RoundedCornerShape(GlassRadiusSmall)

/** Width and inset of the left status bar drawn by [ultronGlass]'s `accentEdge`. */
private val AccentEdgeWidth: Dp = 3.dp
private val AccentEdgeInset: Dp = 9.dp

/**
 * The one glass material — body, rim and left status bar in a single draw node.
 *
 * ── Why this is one modifier and not three ──────────────────────────────────
 * Each `Modifier.drawWithCache` is its own node in the modifier chain: its own
 * draw entry, its own cache, its own traversal on every frame. This used to be
 * `.ultronGlass(...).accentEdge(...)`, which is two nodes on every card. Folding
 * the accent bar in makes it one, and in a list where five cards are on screen
 * during a fling that is five fewer draw nodes walked per frame.
 *
 * ── Why the body is one rect and not two ────────────────────────────────────
 * `glassFill` and `glassTint` are both translucent and both land on the same
 * opaque black ground, so `tint.compositeOver(fill)` is *exactly* the colour the
 * two stacked draws produced — same pixels, half the fill rate. On a 450×450
 * OLED driven by a Mali-G68 MP2 the card body is the largest single blit in the
 * frame, so halving it is the cheapest real win available here. The composite is
 * done inside `drawWithCache`, so it is computed once per size change and never
 * per frame.
 *
 * @param accentRim when non-null, the rim is drawn in this colour instead of the
 *   palette edge. Used to let a card's status (now / next / at-risk) read from
 *   the rim without adding a second border layer.
 * @param accentEdge when non-null, draws the left status bar — the web's
 *   `border-left` accent — in this colour.
 */
fun Modifier.ultronGlass(
    palette: UltronPalette,
    radius: Dp = GlassRadius,
    active: Boolean = false,
    accentRim: Color? = null,
    accentEdge: Color? = null,
): Modifier = this.drawWithCache {
    val r = CornerRadius(radius.toPx(), radius.toPx())
    val fill = if (active) palette.glassMenu else palette.glassFill
    val tint = if (active) palette.glassTintHi else palette.glassTint
    // Pixel-identical to drawing `fill` then `tint`; see the note above.
    val body = tint.compositeOver(fill)
    val rim = accentRim ?: if (active) palette.edgeBright else palette.edge
    val rimStroke = Stroke(width = 1.dp.toPx())
    // Inset the rim by half a stroke so the 1px line lands inside the bounds
    // rather than straddling them and rendering as a 2px smear.
    val half = rimStroke.width / 2f
    val rimSize = Size(size.width - rimStroke.width, size.height - rimStroke.width)
    val rimRadius = CornerRadius(radius.toPx() - half, radius.toPx() - half)

    val edgeW = AccentEdgeWidth.toPx()
    val edgeInset = AccentEdgeInset.toPx()
    val edgeRadius = CornerRadius(edgeW / 2f, edgeW / 2f)
    val edgeSize = Size(edgeW, (size.height - edgeInset * 2f).coerceAtLeast(0f))

    onDrawBehind {
        drawRoundRect(color = body, cornerRadius = r)
        drawRoundRect(
            color = rim,
            topLeft = Offset(half, half),
            size = rimSize,
            cornerRadius = rimRadius,
            style = rimStroke,
        )
        if (accentEdge != null) {
            drawRoundRect(
                color = accentEdge,
                topLeft = Offset(0f, edgeInset),
                size = edgeSize,
                cornerRadius = edgeRadius,
            )
        }
    }
}

/**
 * Etched seam — a structural boundary drawn as TWO 1px lines: a black groove
 * and a white light-catch below it, both dissolving toward the ends via a
 * gradient. This is the design language's way of separating regions without
 * spending an accent colour, and it is why day headers read as engraved rather
 * than underlined.
 */
fun Modifier.etchedSeam(fadeEnds: Boolean = true): Modifier = this.drawWithCache {
    val px = 1.dp.toPx()
    val grooveStops = if (fadeEnds) {
        Brush.horizontalGradient(
            0f to Color.Transparent,
            0.15f to Color.Black.copy(alpha = 0.40f),
            0.85f to Color.Black.copy(alpha = 0.40f),
            1f to Color.Transparent,
        )
    } else {
        Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.40f), Color.Black.copy(alpha = 0.40f)))
    }
    val catchStops = if (fadeEnds) {
        Brush.horizontalGradient(
            0f to Color.Transparent,
            0.15f to Color.White.copy(alpha = 0.08f),
            0.85f to Color.White.copy(alpha = 0.08f),
            1f to Color.Transparent,
        )
    } else {
        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.08f)))
    }

    onDrawBehind {
        val y = size.height - px * 2f
        drawRect(brush = grooveStops, topLeft = Offset(0f, y), size = Size(size.width, px))
        drawRect(brush = catchStops, topLeft = Offset(0f, y + px), size = Size(size.width, px))
    }
}
