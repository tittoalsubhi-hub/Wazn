package com.budgetsms.domain.appearance

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ContrastLevel { STANDARD, MEDIUM, HIGH }
enum class CornerStyle { ROUNDED, SOFT, SHARP }
enum class AnimationLevel { FULL, REDUCED, OFF }
enum class GradientLevel { OFF, SUBTLE, VIVID }
enum class BackgroundStyle { PLAIN, GRADIENT, AURORA }
enum class AppLanguage(val tag: String) { SYSTEM(""), ARABIC("ar"), ENGLISH("en") }

/** Built-in palettes. Seeds are sRGB ARGB values; full light/dark schemes are generated from them. */
enum class Palette(val seed: Long, val nameEn: String, val nameAr: String) {
    FOREST(0xFF1B6B2A, "Wazn green", "أخضر وازن"),
    TEAL(0xFF0F8A8F, "Teal", "فيروزي"),
    EMERALD(0xFF1E9E63, "Emerald", "زمردي"),
    OCEAN(0xFF1F6FD1, "Ocean", "محيطي"),
    INDIGO(0xFF4B4FD8, "Indigo", "نيلي"),
    VIOLET(0xFF8A47D6, "Violet", "بنفسجي"),
    ROSE(0xFFD23F73, "Rose", "وردي"),
    AMBER(0xFFD9901A, "Amber", "كهرماني"),
    SUNSET(0xFFE2582B, "Sunset", "غروب"),
    GRAPHITE(0xFF56636E, "Graphite", "رصاصي"),
    CUSTOM(0xFF0F8A8F, "Custom", "مخصص"),
}

/** Persisted per device as UserAppearancePreference; included in backups (addendum, T32). */
data class AppearancePreference(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.FOREST,
    val customSeed: Long = 0xFF1B6B2A,
    val dynamicColor: Boolean = false,
    val contrast: ContrastLevel = ContrastLevel.STANDARD,
    val corners: CornerStyle = CornerStyle.ROUNDED,
    val pureBlack: Boolean = false,
    val animation: AnimationLevel = AnimationLevel.FULL,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val maskAmounts: Boolean = false,
    val fontScale: Int = 100,
    /** Gradient intensity for icons and cards. */
    val gradient: GradientLevel = GradientLevel.VIVID,
    /** Static app background. */
    val background: BackgroundStyle = BackgroundStyle.AURORA,
    /** Second gradient colour; 0 = automatic (tertiary colour of the palette). */
    val gradientSeed: Long = 0,
) {
    val seed: Long get() = if (palette == Palette.CUSTOM) customSeed else palette.seed
}

/** Curated swatches for the custom accent picker. */
val ACCENT_SWATCHES: List<Long> = listOf(
    0xFF0F8A8F, 0xFF00A3A3, 0xFF1E9E63, 0xFF4CAF50, 0xFF7CB342, 0xFF1F6FD1, 0xFF0288D1, 0xFF4B4FD8,
    0xFF673AB7, 0xFF8A47D6, 0xFFB5389F, 0xFFD23F73, 0xFFE53935, 0xFFE2582B, 0xFFD9901A, 0xFFF4C430,
    0xFF8D6E63, 0xFF56636E, 0xFF37474F, 0xFF212121,
)

/** Colour math used by the theme engine: HSL tonal palettes and WCAG contrast enforcement. */
object ColorMath {
    data class Hsl(val h: Float, val s: Float, val l: Float)

    fun argb(r: Int, g: Int, b: Int, a: Int = 255): Long =
        ((a.toLong() and 0xFF) shl 24) or ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF)

    fun red(c: Long) = ((c shr 16) and 0xFF).toInt()
    fun green(c: Long) = ((c shr 8) and 0xFF).toInt()
    fun blue(c: Long) = (c and 0xFF).toInt()

    fun toHsl(c: Long): Hsl {
        val r = red(c) / 255f; val g = green(c) / 255f; val b = blue(c) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val l = (mx + mn) / 2f
        if (mx == mn) return Hsl(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) { r -> ((g - b) / d + (if (g < b) 6f else 0f)); g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } * 60f
        return Hsl(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Long {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt()) { 0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x); 3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x) }
        val m = l - c / 2f
        fun ch(v: Float) = ((v + m) * 255f).roundToInt().coerceIn(0, 255)
        return argb(ch(r1), ch(g1), ch(b1))
    }

    /** Tone 0..100 of the seed hue (0 = black, 100 = white). Saturation tapers near extremes. */
    fun tone(seed: Long, tone: Int, saturationScale: Float = 1f, hueShift: Float = 0f): Long {
        val hsl = toHsl(seed)
        val l = tone / 100f
        val taper = 1f - abs(l - 0.5f) * 0.6f
        return fromHsl(hsl.h + hueShift, (hsl.s * saturationScale * taper).coerceIn(0f, 1f), l)
    }

    fun luminance(c: Long): Double {
        fun lin(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(red(c)) + 0.7152 * lin(green(c)) + 0.0722 * lin(blue(c))
    }

    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Adjusts only the derived foreground lightness until the ratio is met; the seed itself is untouched (T33). */
    fun ensureContrast(fg: Long, bg: Long, ratio: Double = 4.5): Long {
        if (contrast(fg, bg) >= ratio) return fg
        val hsl = toHsl(fg)
        val goDark = luminance(bg) > 0.18
        var l = hsl.l
        repeat(100) {
            l = if (goDark) (l - 0.01f).coerceAtLeast(0f) else (l + 0.01f).coerceAtMost(1f)
            val c = fromHsl(hsl.h, hsl.s, l)
            if (contrast(c, bg) >= ratio) return c
        }
        return if (goDark) 0xFF000000 else 0xFFFFFFFF
    }

    /**
     * Ensures a foreground/background pair meets [ratio]. If no foreground can reach it (mid-luminance
     * background), the derived background tone is pushed away from mid-grey. Seeds are never modified.
     */
    fun ensurePair(fg: Long, bg: Long, ratio: Double = 4.5): Pair<Long, Long> {
        val hsl = toHsl(bg)
        val darker = luminance(bg) < 0.18
        var l = hsl.l; var b = bg
        repeat(101) {
            val f = ensureContrast(fg, b, ratio)
            if (contrast(f, b) >= ratio) return f to b
            l = if (darker) (l - 0.01f).coerceAtLeast(0f) else (l + 0.01f).coerceAtMost(1f)
            b = fromHsl(hsl.h, hsl.s, l)
        }
        return ensureContrast(fg, b, ratio) to b
    }
}
