package com.spedatox.ultroncore.design

import androidx.compose.ui.graphics.Color

/**
 * ════════════════════════════════════════════════════════════════════════════
 *  THE colour system, ported from profile/theme.ts via the Heartbreaker Android
 *  design system. One accent in → the ENTIRE palette out, re-hued.
 *
 *  PERFORMANCE: unlike the phone client, this app never switches agents — it IS
 *  Ultron. The palette is therefore resolved exactly once per process, lazily,
 *  and handed down as a single `@Immutable` instance. There is no `morphTheme`,
 *  no party cycle, and no per-frame colour math on this device.
 * ════════════════════════════════════════════════════════════════════════════
 */
internal object ThemeEngine {

    /**
     * Ultron's signature slate — verbatim from `Brands.kt` / profile/brands.ts
     * (`"ultron" to Brand(..., accent = "#8a93a6")`) and matching the
     * `DocTheme(accent="#8a93a6")` on Igor's `UltronProfile`.
     *
     * Its hue is ~221°, so every re-hued structural token below lands in cool
     * blue-slate rather than Heartbreaker's default petrol-cyan. That is the
     * intended result: this watch reads as Ultron at a glance.
     */
    const val ULTRON_ACCENT = "#8a93a6"

    /** Derive the bright (active) and dim shades from a single accent hex. */
    private fun deriveAccents(accent: String): Triple<String, String, String> = Triple(
        accent,
        ColorMath.mixWhite(accent, 0.28),
        ColorMath.mixVoid(accent, 0.62),
    )

    /**
     * The one resolved palette for this process. `by lazy` rather than a
     * top-level `val` so the ~30-colour HSL round-trip happens on first theme
     * access rather than at class-load during cold start.
     */
    val palette: UltronPalette by lazy { buildPalette(ULTRON_ACCENT) }

    private fun buildPalette(accent: String): UltronPalette {
        val hue = ColorMath.rgbToHsl(ColorMath.hexToRgb(accent)).h
        val (_, bright, dim) = deriveAccents(accent)
        val a = ColorMath.hexToRgb(accent)

        fun hex(token: String): Color =
            parseHex(ColorMath.rgbToHex(ColorMath.rehue(BaseTokens.BASE_HEX.getValue(token), hue)))

        fun rgba(token: String): Color {
            val (baseHex, alpha) = BaseTokens.BASE_RGBA.getValue(token)
            val c = ColorMath.rehue(baseHex, hue)
            return Color(
                red = ColorMath.clamp(c.r) / 255f,
                green = ColorMath.clamp(c.g) / 255f,
                blue = ColorMath.clamp(c.b) / 255f,
                alpha = alpha.toFloat(),
            )
        }

        return UltronPalette(
            void = hex("--hb-void"),
            base = hex("--hb-base"),
            petrol = hex("--hb-petrol"),
            steel = hex("--hb-steel"),
            text = hex("--hb-text"),
            textDim = hex("--hb-text-dim"),
            textFaint = hex("--hb-text-faint"),
            icon = hex("--hb-icon"),
            iconDim = hex("--hb-icon-dim"),
            iconBright = hex("--hb-icon-bright"),
            line = rgba("--hb-line"),
            lineBright = rgba("--hb-line-bright"),
            edge = rgba("--hb-edge"),
            edgeBright = rgba("--hb-edge-bright"),
            bgHover = rgba("--bg-hover"),
            glassTint = rgba("--glass-tint"),
            glassTintHi = rgba("--glass-tint-hi"),
            glassFill = rgba("--glass-fill"),
            glassMenu = rgba("--glass-menu"),
            // Accent family — the EXACT brand colour (not re-hued, so it stays true).
            accent = parseHex(accent),
            accentBright = parseHex(bright),
            accentDim = parseHex(dim),
            accentMuted = Color(a.r.toInt() / 255f, a.g.toInt() / 255f, a.b.toInt() / 255f, 0.15f),
            bgActive = Color(a.r.toInt() / 255f, a.g.toInt() / 255f, a.b.toInt() / 255f, 0.16f),
            // Semantic — fixed on every agent (theme.ts never re-hues these).
            amber = parseHex(BaseTokens.AMBER),
            amberBright = parseHex(BaseTokens.AMBER_BRIGHT),
            red = parseHex(BaseTokens.RED),
            green = parseHex(BaseTokens.GREEN),
        )
    }

    fun parseHex(hex: String): Color {
        val h = hex.removePrefix("#")
        return Color(
            red = h.substring(0, 2).toInt(16),
            green = h.substring(2, 4).toInt(16),
            blue = h.substring(4, 6).toInt(16),
        )
    }
}
