package com.spedatox.ultroncore.design

import android.content.Context
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import com.spedatox.ultroncore.R

/**
 * ════════════════════════════════════════════════════════════════════════════
 *  Typography — the Speda type ramp, re-scaled for a 1.5" round display.
 *
 *  The two-family split is inherited verbatim and is the whole point:
 *    · Rajdhani (`--font-ui`) — condensed, geometric. HUD chrome only: all-caps
 *      letter-spaced labels, day headers, clock readouts, numerals. Its narrow
 *      counters are an asset for a 12-character uppercase label and a liability
 *      for a 40-character course title.
 *    · Inter (`--font-read`) — the reading face. Course names, room numbers,
 *      instructor names, prompt copy. Anything the eye has to parse rather than
 *      scan.
 *
 *  Sizes are NOT the web's rem values scaled by a constant. The web ramp bottoms
 *  out at 0.62rem ≈ 10sp on a monitor held 60cm away; a watch is held at 35cm on
 *  a display with ~320dpi, so the floor here is 9sp for chrome and 11sp for
 *  anything readable, chosen against Wear OS's own minimum legible sizes.
 * ════════════════════════════════════════════════════════════════════════════
 */
object UltronFonts {

    /**
     * Rajdhani — HUD chrome, all-caps letter-spaced labels (`--font-ui`).
     *
     * PERFORMANCE: `FontFamily` construction is cheap, but the first *use*
     * blocks on parsing the TTF off disk. Both families are touched during
     * [preload] at app start so no frame in the schedule list ever pays for it.
     */
    val Ui: FontFamily = FontFamily(
        Font(R.font.rajdhani_light, FontWeight.Light),        // 300 — numerals
        Font(R.font.rajdhani_regular, FontWeight.Normal),     // 400
        Font(R.font.rajdhani_medium, FontWeight.Medium),      // 500
        Font(R.font.rajdhani_semibold, FontWeight.SemiBold),  // 600 — labels
        Font(R.font.rajdhani_bold, FontWeight.Bold),          // 700 — headers
    )

    /** Inter — reading face (`--font-read`). One variable file covers every
     *  weight; Compose drives the `wght` axis from the requested FontWeight. */
    val Read: FontFamily = FontFamily(
        Font(R.font.inter_variable, FontWeight.Normal),
        Font(R.font.inter_variable, FontWeight.Medium),
        Font(R.font.inter_variable, FontWeight.SemiBold),
        Font(R.font.inter_variable, FontWeight.Bold),
    )

    private val fontResIds = intArrayOf(
        R.font.rajdhani_light,
        R.font.rajdhani_regular,
        R.font.rajdhani_medium,
        R.font.rajdhani_semibold,
        R.font.rajdhani_bold,
        R.font.inter_variable,
    )

    /**
     * Parses every TTF once, off the main thread, and leaves the result in the
     * platform font cache keyed by resource id. Call once, early, from
     * [com.spedatox.ultroncore.UltronWear.onCreate] — the doc comments above
     * have promised this happens since the original build, it just never
     * existed, so the schedule list's first render of each weight parsed its
     * font mid-scroll instead.
     */
    fun preload(context: Context) {
        fontResIds.forEach { ResourcesCompat.getFont(context, it) }
    }
}

object UltronType {

    /** `.hb-label` — the all-caps letter-spaced HUD label. Uppercase at the
     *  call site, never here; the tracking assumes caps. */
    val label = TextStyle(
        fontFamily = UltronFonts.Ui,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.18.em,
    )

    /** Day headers / panel heads — `.hb-head-cyan`. */
    val head = TextStyle(
        fontFamily = UltronFonts.Ui,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.16.em,
    )

    /** Course titles — the one thing on screen that must survive a glance at
     *  arm's length while walking. Inter SemiBold, generous line height. */
    val title = TextStyle(
        fontFamily = UltronFonts.Read,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 16.sp,
        letterSpacing = 0.em,
    )

    /** Secondary reading copy — room, instructor, prompt body. */
    val body = TextStyle(
        fontFamily = UltronFonts.Read,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 14.sp,
    )

    /** `.hb-readout` — small tracked Inter, accent-bright at the call site. */
    val readout = TextStyle(
        fontFamily = UltronFonts.Read,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.04.em,
    )

    /** `.hb-num-thin` — Rajdhani Light numerals for clock/countdown readouts.
     *  Tight line height so a time never pushes a row taller. */
    val numThin = TextStyle(
        fontFamily = UltronFonts.Ui,
        fontSize = 13.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = 0.06.em,
        lineHeight = 13.sp,
    )

    /** The big attendance figure ("3 hak kaldı") and prompt headline. */
    val display = TextStyle(
        fontFamily = UltronFonts.Ui,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.02.em,
        lineHeight = 28.sp,
    )
}
