package com.budgetsms.app

import androidx.compose.ui.graphics.toArgb
import com.budgetsms.app.ui.theme.buildScheme
import com.budgetsms.domain.appearance.ACCENT_SWATCHES
import com.budgetsms.domain.appearance.ColorMath
import com.budgetsms.domain.appearance.ContrastLevel
import com.budgetsms.domain.appearance.Palette
import org.junit.Assert.assertTrue
import org.junit.Test

/** T29 / T33: every palette and every custom accent keeps readable on-colours in light and dark. */
class ThemeContrastTest {
    private fun argb(c: androidx.compose.ui.graphics.Color) = c.toArgb().toLong() and 0xFFFFFFFFL

    @Test fun allPalettesAndAccents_meetContrast() {
        val seeds = Palette.entries.map { it.seed } + ACCENT_SWATCHES
        for (seed in seeds) for (dark in listOf(false, true)) for (level in ContrastLevel.entries) for (black in listOf(false, true)) {
            val s = buildScheme(seed, dark, level, black)
            val min = if (level == ContrastLevel.HIGH) 7.0 else 4.5
            val pairs = listOf(s.onPrimary to s.primary, s.onPrimaryContainer to s.primaryContainer, s.onSecondary to s.secondary,
                s.onTertiaryContainer to s.tertiaryContainer, s.onSurfaceVariant to s.surfaceVariant)
            pairs.forEach { (fg, bg) ->
                val r = ColorMath.contrast(argb(fg), argb(bg))
                assertTrue("seed=${seed.toString(16)} dark=$dark level=$level ratio=$r", r >= min - 0.01)
            }
        }
    }
}
