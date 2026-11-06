package com.budgetsms.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.budgetsms.app.R
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.icons.FloatingIcon
import com.budgetsms.app.ui.theme.LocalMaskAmounts
import com.budgetsms.app.ui.theme.LocalMotion
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.app.ui.theme.cardBrush
import com.budgetsms.app.ui.theme.iconFill
import com.budgetsms.app.ui.icons.RiyalSymbol
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import com.budgetsms.domain.appearance.AnimationLevel
import com.budgetsms.domain.model.Provenance
import com.budgetsms.domain.money.Money
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Locale-aware presentation only; values are never changed by formatting (spec §17). */
object Fmt {
    private val LRI = Char(0x2066).toString(); private val PDI = Char(0x2069).toString()
    fun isArabic(locale: Locale) = locale.language == "ar"

    fun money(m: Money, locale: Locale, masked: Boolean = false, currency: Boolean = true): String {
        val digits = if (masked) "••••" else DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(BigDecimal.valueOf(m.halalas, 2))
        val c = if (!currency) "" else if (isArabic(locale)) " ر.س" else " SAR"
        // Isolated LTR run so signs, digits and separators never reorder inside Arabic text (T26).
        return LRI + digits + PDI + c
    }

    fun ltr(text: String) = LRI + text + PDI
    fun date(d: LocalDate, locale: Locale): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))
    fun dateTime(i: Instant, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String =
        i.atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))
    fun day(i: Instant, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String = date(i.atZone(zone).toLocalDate(), locale)
}

@Composable fun currentLocale(): Locale = LocalConfiguration.current.locales[0]

@Composable
fun money(m: Money, currency: Boolean = true): String = Fmt.money(m, currentLocale(), LocalMaskAmounts.current, currency)

/** Amount with an optional count-up when it changes (display only; the final value is exact). */
@Composable
fun AmountText(value: Money, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.titleMedium,
               color: Color = LocalContentColor.current, animate: Boolean = true) {
    val motion = LocalMotion.current
    var shown by remember { mutableStateOf(value) }
    val anim = remember { Animatable(1f) }
    var from by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (motion == AnimationLevel.FULL && animate && value != shown) {
            from = shown; anim.snapTo(0f); anim.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        shown = value
    }
    val display = if (anim.value >= 1f || motion != AnimationLevel.FULL) value
        else Money(from.halalas + ((value.halalas - from.halalas) * anim.value.toDouble()).toLong())
    val iconSize = with(LocalDensity.current) { if (style.fontSize.isSpecified) (style.fontSize * 0.92f).toDp() else 16.dp }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(money(display, currency = false), style = style.copy(fontFeatureSettings = "tnum"), color = color, maxLines = 1, overflow = TextOverflow.Visible)
        Spacer(Modifier.width(iconSize * 0.25f))
        Icon(RiyalSymbol, stringResource(R.string.currency_sar), Modifier.size(iconSize), tint = color)
    }
}

@Composable
fun provenanceLabel(p: Provenance) = stringResource(when (p) {
    Provenance.CONFIRMED -> R.string.prov_confirmed; Provenance.CALCULATED -> R.string.prov_calculated
    Provenance.PLANNED -> R.string.prov_planned; Provenance.FORECAST -> R.string.prov_forecast; Provenance.UNDER_REVIEW -> R.string.prov_review
})

/** Provenance is always shown with an icon and a label, never by colour alone (spec §02, §16). */
@Composable
fun ProvenanceChip(p: Provenance, modifier: Modifier = Modifier) {
    val s = LocalSemantic.current
    val (icon, color) = when (p) {
        Provenance.CONFIRMED -> AppIcons.check to s.income
        Provenance.CALCULATED -> AppIcons.sync to s.transfer
        Provenance.PLANNED -> AppIcons.edit to s.planned
        Provenance.FORECAST -> AppIcons.forecast to s.forecast
        Provenance.UNDER_REVIEW -> AppIcons.review to s.review
    }
    Row(modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = color)
        Spacer(Modifier.width(4.dp))
        Text(provenanceLabel(p), style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, icon: ImageVector? = null, action: (@Composable () -> Unit)? = null,
                container: Color = MaterialTheme.colorScheme.surfaceContainerLow, content: @Composable ColumnScope.() -> Unit) {
    val brush = cardBrush(container)
    Card(modifier.fillMaxWidth().animateContentSize(), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = if (brush != null) Color.Transparent else container, contentColor = contentColorFor(container).takeOrElse { MaterialTheme.colorScheme.onSurface })) {
        Column((if (brush != null) Modifier.background(brush) else Modifier).padding(16.dp)) {
            if (title != null) Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { IconBadge(icon, MaterialTheme.colorScheme.primary); Spacer(Modifier.width(10.dp)) }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            if (title != null) Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Int = 36) {
    val (fill, tint) = iconFill(color)
    Box(Modifier.size(size.dp).clip(MaterialTheme.shapes.medium).background(fill), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size((size * 0.56f).dp), tint = tint)
    }
}

@Composable
fun StatTile(label: String, value: Money?, provenance: Provenance, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    val brush = cardBrush(base)
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = if (brush != null) Color.Transparent else base, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column((if (brush != null) Modifier.background(brush) else Modifier).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(18.dp), tint = color); Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            if (value != null) AmountText(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            else Text(stringResource(R.string.not_available), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp)); ProvenanceChip(provenance)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        FloatingIcon(icon, MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** Limit bar: state is conveyed by text and icon as well as colour. */
@Composable
fun LimitBar(label: String, icon: ImageVector, color: Color, spent: Money, limit: Money?, overspent: Boolean) {
    val s = LocalSemantic.current
    val frac = if (limit == null || limit.halalas <= 0) 0f else (spent.halalas.toFloat() / limit.halalas).coerceIn(0f, 1f)
    val motion = LocalMotion.current
    val p by animateFloatAsState(frac, if (motion == AnimationLevel.OFF) snap() else tween(700), label = "bar")
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, 30); Spacer(Modifier.width(10.dp))
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (overspent) { Icon(AppIcons.warning, null, Modifier.size(16.dp), tint = s.overspend); Spacer(Modifier.width(4.dp)) }
            Text(money(spent, false) + (limit?.let { " / " + money(it, false) } ?: ""), style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"))
        }
        if (limit != null) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                color = if (overspent) s.overspend else color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
            if (overspent) Text(stringResource(R.string.overspent_by, money(spent - limit)), style = MaterialTheme.typography.labelSmall, color = s.overspend)
        }
    }
}

data class ChartPoint(val label: String, val value: Money)

/** Cumulative spending line with an optional reference line; exact values available below (spec §18). */
@Composable
fun TrendChart(points: List<ChartPoint>, reference: Money?, color: Color, modifier: Modifier = Modifier) {
    if (points.isEmpty()) return
    val motion = LocalMotion.current
    val reveal = remember { Animatable(if (motion == AnimationLevel.OFF) 1f else 0f) }
    LaunchedEffect(points.size, motion) { if (motion != AnimationLevel.OFF) reveal.animateTo(1f, tween(if (motion == AnimationLevel.FULL) 900 else 300)) }
    val grid = MaterialTheme.colorScheme.outlineVariant; val refColor = LocalSemantic.current.planned
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val loc = currentLocale(); val masked = LocalMaskAmounts.current
    val desc = points.joinToString { "${it.label}: ${Fmt.money(it.value, loc, masked)}" }
    val max = maxOf(points.maxOf { it.value.halalas }, reference?.halalas ?: 0L, 1L).toFloat()
    Canvas(modifier.fillMaxWidth().height(150.dp).semantics { contentDescription = desc }) {
        val w = size.width; val h = size.height
        for (i in 0..3) drawLine(grid, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 1f)
        fun x(i: Int) = (if (points.size == 1) w / 2 else w * i / (points.size - 1)).let { if (rtl) w - it else it }
        fun y(v: Long) = h - (v / max) * h * 0.92f
        reference?.let { val ry = y(it.halalas); drawLine(refColor, Offset(0f, ry), Offset(w, ry), strokeWidth = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 10f))) }
        val n = (points.size * reveal.value).toInt().coerceAtLeast(1)
        val line = Path(); val fill = Path()
        points.take(n).forEachIndexed { i, p -> val px = x(i); val py = y(p.value.halalas); if (i == 0) { line.moveTo(px, py); fill.moveTo(px, h); fill.lineTo(px, py) } else { line.lineTo(px, py); fill.lineTo(px, py) } }
        fill.lineTo(x(n - 1), h); fill.close()
        drawPath(fill, color.copy(alpha = 0.16f))
        drawPath(line, color, style = Stroke(5f, cap = StrokeCap.Round))
        drawCircle(color, 7f, Offset(x(n - 1), y(points[n - 1].value.halalas)))
    }
}

data class Slice(val label: String, val value: Money, val color: Color)

@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, center: @Composable () -> Unit = {}) {
    val total = slices.sumOf { it.value.halalas.coerceAtLeast(0) }.coerceAtLeast(1).toFloat()
    val motion = LocalMotion.current
    val sweep by animateFloatAsState(1f, if (motion == AnimationLevel.OFF) snap() else tween(900), label = "donut")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val loc = currentLocale(); val masked = LocalMaskAmounts.current
    val desc = slices.joinToString { "${it.label}: ${Fmt.money(it.value, loc, masked)}" }
    Box(modifier.size(170.dp).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = 26.dp.toPx(); val arc = Size(size.width - s, size.height - s); val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, arc, style = Stroke(s))
            var start = -90f
            slices.forEach { sl -> val a = 360f * (sl.value.halalas.coerceAtLeast(0) / total) * sweep
                drawArc(sl.color, start, (a - 1.5f).coerceAtLeast(0.5f), false, tl, arc, style = Stroke(s, cap = StrokeCap.Butt)); start += a }
        }
        center()
    }
}

/** Expandable list of the exact values behind a chart. */
@Composable
fun ExactValues(rows: List<Pair<String, Money>>) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(stringResource(if (open) R.string.hide_values else R.string.show_values)) }
    AnimatedVisibility(open) {
        Column { rows.forEach { (l, v) -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(l, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text(money(v), style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")) } } }
    }
}

@Composable
fun <T> ChoiceRow(options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T) -> Unit, icon: ((T) -> ImageVector?)? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { o ->
            val ic = icon?.invoke(o)
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o), maxLines = 1) },
                leadingIcon = if (ic != null) { { Icon(ic, null, Modifier.size(18.dp)) } } else null)
        }
    }
}

@Composable
fun SettingRow(icon: ImageVector, title: String, subtitle: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).let { if (onClick != null) it.clickable(onClick = onClick) else it }
        .padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(AppIcons.chevron, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
}


@Composable
fun MessageBanner(text: String, icon: ImageVector, color: Color) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(color.copy(alpha = 0.12f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color); Spacer(Modifier.width(10.dp)); Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
