package com.spedatox.ultroncore.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * `staticCompositionLocalOf`, not `compositionLocalOf` — deliberate.
 *
 * The dynamic variant subscribes every reader for invalidation when the value
 * changes. Ultron's palette is resolved once per process and never changes, so
 * that bookkeeping buys nothing and costs a subscription per read site. The
 * static variant recomposes the whole subtree if the value ever *did* change,
 * which is the correct trade when it cannot.
 */
val LocalUltronPalette = staticCompositionLocalOf { ThemeEngine.palette }

/**
 * The Speda design language on Wear OS.
 *
 * Wear's [MaterialTheme] is still wired underneath because Wear components
 * (Picker, ToggleChip, the scroll indicators) read from it; app surfaces should
 * read [LocalUltronPalette] instead. The Material colours below are the palette
 * projected onto Wear's slots, so a stray Material component never renders
 * off-brand teal.
 */
@Composable
fun UltronTheme(content: @Composable () -> Unit) {
    val p = ThemeEngine.palette
    MaterialTheme(
        colors = Colors(
            primary = p.accent,
            primaryVariant = p.accentBright,
            secondary = p.iconBright,
            secondaryVariant = p.icon,
            background = p.body,
            surface = p.petrol,
            error = p.red,
            onPrimary = p.void,
            onSecondary = p.text,
            onBackground = p.text,
            onSurface = p.text,
            onError = p.void,
        ),
        content = content,
    )
}

/** Palette access for themed composables. */
val UltronThemeTokens: UltronPalette
    @Composable @ReadOnlyComposable get() = LocalUltronPalette.current
