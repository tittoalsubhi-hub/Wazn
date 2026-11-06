package com.budgetsms.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import com.budgetsms.domain.appearance.BackgroundStyle
import com.budgetsms.domain.appearance.GradientLevel

/** Gradient settings resolved against the active colour scheme (customisable in Appearance). */
@Immutable
data class GradientSpec(val level: GradientLevel, val background: BackgroundStyle, val start: Color, val end: Color)

val LocalGradients = staticCompositionLocalOf { GradientSpec(GradientLevel.OFF, BackgroundStyle.PLAIN, Color.Gray, Color.Gray) }

/** Static (non-animated) app background: plain, soft gradient, or "aurora" colour glows. */
@Composable
fun Modifier.appBackground(): Modifier {
    val g = LocalGradients.current
    val base = MaterialTheme.colorScheme.background
    return this.drawBehind {
        drawRect(base)
        when (g.background) {
            BackgroundStyle.PLAIN -> Unit
            BackgroundStyle.GRADIENT -> drawRect(Brush.verticalGradient(listOf(g.start.copy(alpha = 0.22f), g.end.copy(alpha = 0.10f), Color.Transparent), endY = size.height * 0.75f))
            BackgroundStyle.AURORA -> {
                val w = size.width; val h = size.height
                drawRect(Brush.radialGradient(listOf(g.start.copy(alpha = 0.30f), Color.Transparent), Offset(w * 0.1f, h * 0.02f), w * 0.85f))
                drawRect(Brush.radialGradient(listOf(g.end.copy(alpha = 0.24f), Color.Transparent), Offset(w * 1.0f, h * 0.45f), w * 0.75f))
                drawRect(Brush.radialGradient(listOf(g.start.copy(alpha = 0.14f), Color.Transparent), Offset(w * 0.15f, h * 1.0f), w * 0.8f))
            }
        }
    }
}

/** Card fill: a gentle diagonal blend of the base colour with the gradient colours, or null when gradients are off. */
@Composable
fun cardBrush(base: Color): Brush? {
    val g = LocalGradients.current
    val k = when (g.level) { GradientLevel.OFF -> return null; GradientLevel.SUBTLE -> 0.07f; GradientLevel.VIVID -> 0.17f }
    return Brush.linearGradient(listOf(lerp(base, g.start, k), base, lerp(base, g.end, k * 0.8f)))
}

/** Icon badge fill and the matching icon tint. */
@Composable
fun iconFill(color: Color): Pair<Brush, Color> {
    val g = LocalGradients.current
    return when (g.level) {
        GradientLevel.OFF -> SolidColor(color.copy(alpha = 0.14f)) to color
        GradientLevel.SUBTLE -> Brush.linearGradient(listOf(color.copy(alpha = 0.30f), lerp(color, g.end, 0.5f).copy(alpha = 0.12f))) to color
        GradientLevel.VIVID -> Brush.linearGradient(listOf(lerp(color, Color.White, 0.08f), lerp(color, g.end, 0.55f))) to Color.White
    }
}

/** Hero card: vivid primary-to-accent gradient, or soft containers. Returns brush and content colour. */
@Composable
fun heroFill(): Pair<Brush, Color> {
    val g = LocalGradients.current; val cs = MaterialTheme.colorScheme
    return if (g.level == GradientLevel.VIVID) Brush.linearGradient(listOf(g.start, lerp(g.start, g.end, 0.85f))) to cs.onPrimary
    else Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer.copy(alpha = 0.85f))) to cs.onPrimaryContainer
}
