package com.spedatox.ultroncore.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The fully-resolved token set for Ultron's accent — the `--hb-*` / `--glass-*`
 * custom properties that survive the trip to a watch face, as Compose [Color]s.
 *
 * `@Immutable` is load-bearing, not decoration: it tells the Compose compiler
 * every composable taking a palette parameter can be skipped when the instance
 * is referentially equal. Since [UltronTheme] hands down one process-wide
 * instance, that means "always". Dropping this annotation silently un-skips
 * every themed composable in the app.
 */
@Immutable
data class UltronPalette(
    // ── Re-hued structural (BASE_HEX) ──────────────────────────────────────
    val void: Color,
    val base: Color,
    val petrol: Color,
    val steel: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val icon: Color,
    val iconDim: Color,
    val iconBright: Color,
    // ── Re-hued rgba (BASE_RGBA) ───────────────────────────────────────────
    val line: Color,
    val lineBright: Color,
    val edge: Color,
    val edgeBright: Color,
    val bgHover: Color,
    val glassTint: Color,
    val glassTintHi: Color,
    val glassFill: Color,
    val glassMenu: Color,
    // ── Accent family (exact brand colour, never re-hued) ──────────────────
    val accent: Color,
    val accentBright: Color,
    val accentDim: Color,
    val accentMuted: Color,
    val bgActive: Color,
    // ── Semantic (fixed across agents; the attendance verdict scale) ───────
    val amber: Color,
    val amberBright: Color,
    val red: Color,
    val green: Color,
) {
    /** Pure black. The watch background is never anything else — see the
     *  WEAR DEVIATION note in [BaseTokens]. */
    val body: Color get() = Color.Black
}
