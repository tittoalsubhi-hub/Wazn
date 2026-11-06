package com.budgetsms.app.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.budgetsms.domain.appearance.AnimationLevel
import com.budgetsms.domain.appearance.AppearancePreference
import com.budgetsms.domain.appearance.ColorMath
import com.budgetsms.domain.appearance.ContrastLevel
import com.budgetsms.domain.appearance.CornerStyle
import com.budgetsms.domain.appearance.ThemeMode

/** Semantic colours keep the same meaning in every palette and mode (addendum; spec §16). */
@Immutable
data class SemanticColors(
    val income: Color, val expense: Color, val overspend: Color, val savings: Color,
    val forecast: Color, val review: Color, val planned: Color, val transfer: Color,
    val incomeContainer: Color, val expenseContainer: Color, val reviewContainer: Color, val savingsContainer: Color,
)

private fun semantic(dark: Boolean): SemanticColors {
    fun t(seed: Long, light: Int, darkTone: Int) = Color(ColorMath.tone(seed, if (dark) darkTone else light))
    return SemanticColors(
        income = t(0xFF1E9E63, 36, 72), expense = t(0xFFD84343, 42, 74), overspend = t(0xFFB3261E, 38, 70),
        savings = t(0xFF7B4BD6, 42, 78), forecast = t(0xFF1F6FD1, 42, 76), review = t(0xFFC98A0B, 38, 72),
        planned = t(0xFF56636E, 40, 74), transfer = t(0xFF0F8A8F, 36, 72),
        incomeContainer = t(0xFF1E9E63, 92, 22), expenseContainer = t(0xFFD84343, 93, 24),
        reviewContainer = t(0xFFC98A0B, 92, 24), savingsContainer = t(0xFF7B4BD6, 93, 24),
    )
}

val LocalSemantic = staticCompositionLocalOf { semantic(false) }
val LocalMotion = staticCompositionLocalOf { AnimationLevel.FULL }
val LocalMaskAmounts = staticCompositionLocalOf { false }

/** Builds a full Material 3 scheme from a seed. Every on-colour pair is contrast-checked (>= 4.5:1, 7:1 for high). */
fun buildScheme(seed: Long, dark: Boolean, contrast: ContrastLevel, pureBlack: Boolean): ColorScheme {
    val boost = when (contrast) { ContrastLevel.STANDARD -> 0; ContrastLevel.MEDIUM -> 6; ContrastLevel.HIGH -> 12 }
    val ratio = if (contrast == ContrastLevel.HIGH) 7.0 else 4.5
    fun p(t: Int) = ColorMath.tone(seed, t)
    fun s(t: Int) = ColorMath.tone(seed, t, 0.38f)
    fun te(t: Int) = ColorMath.tone(seed, t, 0.7f, 60f)
    fun n(t: Int) = ColorMath.tone(seed, t, 0.06f)
    fun nv(t: Int) = ColorMath.tone(seed, t, 0.14f)
    fun pair(fg: Long, bg: Long): Pair<Color, Color> = ColorMath.ensurePair(fg, bg, ratio).let { (f, b) -> Color(f) to Color(b) }
    return if (!dark) {
        val (onPrimary, primary) = pair(0xFFFFFFFF, p(40 - boost)); val (onPc, pc) = pair(p(10), p(90))
        val (onSec, sec) = pair(0xFFFFFFFF, s(40 - boost)); val (onSc, sc) = pair(s(10), s(90))
        val (onTer, ter) = pair(0xFFFFFFFF, te(40 - boost)); val (onTc, tc) = pair(te(10), te(90))
        val (onSv, sv) = pair(nv(30 - boost), nv(90)); val bg = n(98)
        lightColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = pc, onPrimaryContainer = onPc, inversePrimary = Color(p(80)),
            secondary = sec, onSecondary = onSec, secondaryContainer = sc, onSecondaryContainer = onSc,
            tertiary = ter, onTertiary = onTer, tertiaryContainer = tc, onTertiaryContainer = onTc,
            background = Color(bg), onBackground = Color(n(10)), surface = Color(bg), onSurface = Color(n(10)),
            surfaceVariant = sv, onSurfaceVariant = onSv, surfaceTint = primary,
            inverseSurface = Color(n(20)), inverseOnSurface = Color(n(95)), outline = Color(nv(50 - boost)), outlineVariant = Color(nv(80)),
            surfaceBright = Color(n(98)), surfaceDim = Color(n(87)), surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(n(96)), surfaceContainer = Color(n(94)), surfaceContainerHigh = Color(n(92)), surfaceContainerHighest = Color(n(90)),
        )
    } else {
        val (onPrimary, primary) = pair(p(20), p(80 + boost / 2)); val (onPc, pc) = pair(p(90), p(30))
        val (onSec, sec) = pair(s(20), s(80)); val (onSc, sc) = pair(s(90), s(30))
        val (onTer, ter) = pair(te(20), te(80)); val (onTc, tc) = pair(te(90), te(30))
        val (onSv, sv) = pair(nv(80 + boost / 2), nv(30)); val bg = if (pureBlack) 0xFF000000 else n(6)
        darkColorScheme(
            primary = primary, onPrimary = onPrimary, primaryContainer = pc, onPrimaryContainer = onPc, inversePrimary = Color(p(40)),
            secondary = sec, onSecondary = onSec, secondaryContainer = sc, onSecondaryContainer = onSc,
            tertiary = ter, onTertiary = onTer, tertiaryContainer = tc, onTertiaryContainer = onTc,
            background = Color(bg), onBackground = Color(n(90)), surface = Color(bg), onSurface = Color(n(90 + boost / 3)),
            surfaceVariant = sv, onSurfaceVariant = onSv, surfaceTint = primary,
            inverseSurface = Color(n(90)), inverseOnSurface = Color(n(20)), outline = Color(nv(60 + boost)), outlineVariant = Color(nv(30)),
            surfaceBright = Color(n(24)), surfaceDim = Color(bg),
            surfaceContainerLowest = Color(if (pureBlack) 0xFF000000 else n(4)), surfaceContainerLow = Color(if (pureBlack) n(4) else n(10)),
            surfaceContainer = Color(if (pureBlack) n(7) else n(12)), surfaceContainerHigh = Color(if (pureBlack) n(11) else n(17)),
            surfaceContainerHighest = Color(if (pureBlack) n(15) else n(22)),
        )
    }
}

fun shapesFor(style: CornerStyle): Shapes = when (style) {
    CornerStyle.ROUNDED -> Shapes(RoundedCornerShape(8.dp), RoundedCornerShape(14.dp), RoundedCornerShape(22.dp), RoundedCornerShape(28.dp), RoundedCornerShape(36.dp))
    CornerStyle.SOFT -> Shapes(RoundedCornerShape(6.dp), RoundedCornerShape(10.dp), RoundedCornerShape(14.dp), RoundedCornerShape(18.dp), RoundedCornerShape(22.dp))
    CornerStyle.SHARP -> Shapes(RoundedCornerShape(2.dp), RoundedCornerShape(3.dp), RoundedCornerShape(4.dp), RoundedCornerShape(6.dp), RoundedCornerShape(8.dp))
}

private val AmountFeatures = "tnum, lnum"
val Typography.amount: TextStyle get() = headlineMedium.copy(fontFeatureSettings = AmountFeatures)
val Typography.amountLarge: TextStyle get() = displaySmall.copy(fontFeatureSettings = AmountFeatures)
val Typography.amountSmall: TextStyle get() = titleMedium.copy(fontFeatureSettings = AmountFeatures)

@Composable
fun isDark(pref: AppearancePreference): Boolean = when (pref.mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true
}

/** Effective motion: the system "remove animations" setting always wins (spec §18, T27, T31). */
@Composable
fun effectiveMotion(pref: AppearancePreference): AnimationLevel {
    val ctx = LocalContext.current
    val systemOff = remember { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    return if (systemOff) AnimationLevel.OFF else pref.animation
}

@Composable
fun BudgetTheme(pref: AppearancePreference, content: @Composable () -> Unit) {
    val dark = isDark(pref)
    val ctx = LocalContext.current
    val scheme = when {
        pref.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(ctx).let { if (pref.pureBlack) it.copy(background = Color.Black, surface = Color.Black) else it } else dynamicLightColorScheme(ctx)
        else -> remember(pref.seed, dark, pref.contrast, pref.pureBlack) { buildScheme(pref.seed, dark, pref.contrast, pref.pureBlack) }
    }
    CompositionLocalProvider(
        LocalSemantic provides remember(dark) { semantic(dark) },
        LocalMotion provides effectiveMotion(pref),
        LocalMaskAmounts provides pref.maskAmounts,
    ) {
        val end = if (pref.gradientSeed != 0L) Color(ColorMath.tone(pref.gradientSeed, if (dark) 76 else 44)) else scheme.tertiary
        val gradients = GradientSpec(pref.gradient, pref.background, scheme.primary, end)
        MaterialTheme(colorScheme = scheme, shapes = shapesFor(pref.corners), typography = Typography()) {
            CompositionLocalProvider(LocalGradients provides gradients, content = content)
        }
    }
}
