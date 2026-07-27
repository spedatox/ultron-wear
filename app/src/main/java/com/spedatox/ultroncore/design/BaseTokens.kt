package com.spedatox.ultroncore.design

/**
 * The structural palette — copied value-for-value from the BASE_HEX / BASE_RGBA
 * tables in the Heartbreaker design system (which mirror profile/theme.ts).
 * These ARE the palette: backgrounds, surfaces, text, lines, glass rims and the
 * dim icon scale, all re-hued to Ultron's accent at runtime by [ThemeEngine].
 *
 * Do not edit these values here. They are a mirror; the source of truth is
 * profile/theme.ts in the Heartbreaker renderer. Changing one in isolation
 * forks Ultron Wear off the ecosystem palette.
 */
internal object BaseTokens {

    /** `--hb-*` hex tokens, re-hued (hue swapped, S/L preserved). */
    val BASE_HEX: Map<String, String> = linkedMapOf(
        "--hb-void" to "#04080a",
        "--hb-base" to "#060c0f",
        "--hb-petrol" to "#0b1a22",
        "--hb-steel" to "#13303b",
        "--hb-text" to "#cadbe2",
        "--hb-text-dim" to "#7a96a1",
        "--hb-text-faint" to "#46626d",
        "--hb-icon" to "#3a6472",
        "--hb-icon-dim" to "#2e5260",
        "--hb-icon-bright" to "#5d7f8a",
    )

    /** rgba tokens — [base colour for hue/sat/light, alpha-as-emitted-string]. */
    val BASE_RGBA: Map<String, Pair<String, String>> = linkedMapOf(
        "--hb-line" to ("#5fa5bc" to "0.26"),
        "--hb-line-bright" to ("#6ec8e4" to "0.55"),
        "--hb-edge" to ("#96cdf5" to "0.22"),
        "--hb-edge-bright" to ("#aae1ff" to "0.55"),
        "--bg-hover" to ("#4696af" to "0.12"),
        "--glass-tint" to ("#bed7eb" to "0.06"),
        "--glass-tint-hi" to ("#bed7eb" to "0.13"),
        "--glass-fill" to ("#081018" to "0.62"),
        "--glass-menu" to ("#0a141b" to "0.94"),
    )

    /**
     * Semantic colours — meaning-bearing, NEVER re-hued (theme.ts leaves them
     * untouched; they live only in :root of heartbreaker.css). Same on every
     * agent, and on this watch they carry the attendance verdict: green = safe,
     * amber = close to the limit, red = you will fail on absence.
     */
    const val AMBER = "#d99c44"
    const val AMBER_BRIGHT = "#f2b75c"
    const val RED = "#c84a3a"
    const val GREEN = "#4fa377"

    /**
     * WEAR DEVIATION (deliberate): the phone port already flattens the web's
     * 160° body gradient to #000000 because an OLED pixel at true black is
     * switched off. On a watch that argument is stronger, not weaker — the panel
     * is always-on-adjacent and the battery is a tenth the size. Ultron Wear
     * therefore has NO body gradient and NO ambient blobs at all: the background
     * is literal black, and every pixel of colour is spent on content.
     */
    const val BODY = "#000000"
}
