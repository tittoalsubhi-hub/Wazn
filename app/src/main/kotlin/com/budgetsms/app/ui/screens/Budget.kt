package com.budgetsms.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.*
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.domain.appearance.ACCENT_SWATCHES
import com.budgetsms.domain.model.*
import com.budgetsms.domain.money.BasisPoints
import com.budgetsms.domain.money.Money
import java.util.UUID

@Composable
fun BudgetScreen(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val sel by vm.selectedProfile.collectAsStateWithLifecycle()
    val sem = LocalSemantic.current
    val locale = currentLocale()
    var editLimit by remember { mutableStateOf<String?>(null) }
    var addCategory by remember { mutableStateOf(false) }
    var openCycle by remember { mutableStateOf(false) }
    val profileId = sel ?: s.profiles.firstOrNull()?.profile?.id
    val profile = s.profile(profileId)
    val budget = profileId?.let { s.ledger?.budgetOf(it) }

    ScreenColumn {
        ProfileSelector(vm, allowAll = false)
        if (profile == null) { EmptyState(AppIcons.profile, stringResource(R.string.no_profiles), stringResource(R.string.no_profiles_body)); return@ScreenColumn }
        SectionCard(stringResource(R.string.current_cycle), icon = AppIcons.calendar, action = {
            TextButton(onClick = { openCycle = true }) { Text(stringResource(R.string.open_cycle)) } }) {
            if (budget == null) Text(stringResource(R.string.no_open_cycle_body), style = MaterialTheme.typography.bodyMedium)
            else {
                Text(cycleTypeLabel(budget.cycle.type) + " · " + stringResource(R.string.opened_on, Fmt.dateTime(budget.cycle.openedAt, locale)), style = MaterialTheme.typography.bodyMedium)
                budget.cycle.percentBase?.let { Text(stringResource(R.string.percent_base, money(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Spacer(Modifier.height(12.dp))
                // Planned vs funded are separate budgets (§07, T10)
                val max = maxOf(budget.funded.halalas, budget.planned?.halalas ?: 0, budget.spent.halalas, 1)
                CompareBar(stringResource(R.string.planned), budget.planned ?: Money.ZERO, max, sem.planned, Provenance.PLANNED)
                CompareBar(stringResource(R.string.funded), budget.funded, max, sem.income, Provenance.CONFIRMED)
                CompareBar(stringResource(R.string.spent), budget.spent, max, sem.expense, Provenance.CONFIRMED)
                if (budget.underReview.isPositive) CompareBar(stringResource(R.string.under_review), budget.underReview, max, sem.review, Provenance.UNDER_REVIEW)
                if (budget.reallocatedIn.isPositive || budget.reallocatedOut.isPositive)
                    Text(stringResource(R.string.reallocations, money(budget.reallocatedIn), money(budget.reallocatedOut)), style = MaterialTheme.typography.labelMedium)
                if (budget.incomeOutsideBudget.isPositive) Text(stringResource(R.string.income_outside, money(budget.incomeOutsideBudget)), style = MaterialTheme.typography.labelMedium)
                if (budget.earmarkedForSavings.isPositive) Text(stringResource(R.string.earmarked, money(budget.earmarkedForSavings)), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.unallocated_note, money(budget.unallocated)), style = MaterialTheme.typography.labelMedium)
            }
        }
        if (budget != null && budget.limitWarnings.isNotEmpty()) MessageBanner(stringResource(R.string.child_limits_warning, budget.limitWarnings.joinToString { categoryNameStatic(s, it, locale.language) }), AppIcons.warning, sem.review)

        SectionCard(stringResource(R.string.category_limits), icon = AppIcons.budget.first, action = {
            TextButton(onClick = { addCategory = true }) { Icon(AppIcons.add, null); Text(stringResource(R.string.add_category)) } }) {
            val statuses = budget?.categories.orEmpty().associateBy { it.categoryId }
            s.categories.filter { it.parentId == null }.forEach { c ->
                val st = statuses[c.id]
                Box(Modifier.clickable { editLimit = c.id }) {
                    LimitBar(if (locale.language == "ar") c.nameAr else c.nameEn, AppIcons.category(c.iconKey), c.color(), st?.spent ?: Money.ZERO, st?.limit, st?.overspent == true)
                }
                s.categories.filter { it.parentId == c.id }.forEach { ch ->
                    val cs = statuses[ch.id]
                    Box(Modifier.padding(start = 28.dp).clickable { editLimit = ch.id }) {
                        LimitBar(if (locale.language == "ar") ch.nameAr else ch.nameEn, AppIcons.category(ch.iconKey), ch.color(), cs?.spent ?: Money.ZERO, cs?.limit, cs?.overspent == true)
                    }
                }
            }
        }

        // Closed cycles: surplus/deficit for reports only — never rolled into the next budget (§02)
        val closed = s.ledger?.cycles.orEmpty().filter { it.profileId == profileId && !it.isOpen }.reversed()
        if (closed.isNotEmpty()) SectionCard(stringResource(R.string.past_cycles), icon = AppIcons.history) {
            Text(stringResource(R.string.no_rollover_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            closed.take(12).forEach { c -> val b = s.ledger?.budgets?.get(c.id) ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(Fmt.day(c.openedAt, locale) + " → " + (c.closedAt?.let { Fmt.day(it, locale) } ?: "")); Text(cycleTypeLabel(c.type), style = MaterialTheme.typography.labelSmall) }
                    AmountText(b.remaining, style = MaterialTheme.typography.titleSmall, color = if (b.remaining.isNegative) sem.overspend else sem.income)
                }
            }
        }
    }

    editLimit?.let { cid -> LimitDialog(categoryNameStatic(s, cid, locale.language), s.limits.firstOrNull { it.profileId == profileId && it.categoryId == cid }?.rule, budget?.cycle?.percentBase,
        onDismiss = { editLimit = null }, onSave = { rule -> vm.setLimit(profileId!!, cid, rule); editLimit = null }) }
    if (addCategory) CategoryDialog(s.categories.filter { it.parentId == null }, onDismiss = { addCategory = false }) { vm.saveCategory(it); addCategory = false }
    if (openCycle && profileId != null) MoneyInputDialog(stringResource(R.string.open_cycle), stringResource(R.string.planned_base_hint), allowEmpty = true,
        onDismiss = { openCycle = false }) { vm.openManualCycle(profileId, it); openCycle = false }
}

private fun categoryNameStatic(s: com.budgetsms.data.AppSnapshot, id: String, lang: String) = s.category(id)?.let { if (lang == "ar") it.nameAr else it.nameEn } ?: id

@Composable
private fun CompareBar(label: String, value: Money, max: Long, color: Color, p: Provenance) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); ProvenanceChip(p); Spacer(Modifier.width(8.dp))
            AmountText(value, style = MaterialTheme.typography.labelLarge)
        }
        LinearProgressIndicator(progress = { value.halalas.toFloat() / max }, Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, drawStopIndicator = {})
    }
}

@Composable
fun MoneyInputDialog(title: String, hint: String, allowEmpty: Boolean = false, initial: String = "", onDismiss: () -> Unit, onSave: (Money?) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val parsed = Money.parseOrNull(text)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(hint) }, singleLine = true, isError = text.isNotEmpty() && parsed == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text(stringResource(R.string.currency_sar)) }) },
        confirmButton = { TextButton(onClick = { onSave(parsed) }, enabled = parsed != null || (allowEmpty && text.isBlank())) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun LimitDialog(name: String, current: LimitRule?, base: Money?, onDismiss: () -> Unit, onSave: (LimitRule?) -> Unit) {
    var mode by remember { mutableIntStateOf(when (current) { is LimitRule.Fixed -> 1; is LimitRule.Percent -> 2; null -> 0 }) }
    var text by remember { mutableStateOf(when (current) { is LimitRule.Fixed -> current.amount.toPlainString(); is LimitRule.Percent -> java.math.BigDecimal(current.bp.value).movePointLeft(2).toPlainString(); null -> "" }) }
    val rule: LimitRule? = when (mode) {
        1 -> Money.parseOrNull(text)?.let { LimitRule.Fixed(it) }
        2 -> runCatching { BasisPoints.parsePercent(text) }.getOrNull()?.let { LimitRule.Percent(it) }
        else -> null
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.limit_for, name)) }, text = {
        Column {
            ChoiceRow(listOf(0, 1, 2), mode, { stringResource(when (it) { 0 -> R.string.limit_none; 1 -> R.string.limit_fixed; else -> R.string.limit_percent }) }, { mode = it })
            if (mode != 0) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(text, { text = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    suffix = { Text(if (mode == 1) stringResource(R.string.currency_sar) else "%") }, isError = rule == null && text.isNotEmpty())
                if (mode == 2) Text(if (base != null) stringResource(R.string.percent_of_base, money(base)) else stringResource(R.string.percent_no_base), style = MaterialTheme.typography.labelSmall)
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(rule) }, enabled = mode == 0 || rule != null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun CategoryDialog(parents: List<Category>, onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var en by remember { mutableStateOf("") }; var ar by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("category") }; var color by remember { mutableLongStateOf(ACCENT_SWATCHES.first()) }
    var parent by remember { mutableStateOf<Category?>(null) }
    val locale = currentLocale()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.add_category)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(ar, { ar = it }, label = { Text(stringResource(R.string.name_ar)) }, singleLine = true)
            OutlinedTextField(en, { en = it }, label = { Text(stringResource(R.string.name_en)) }, singleLine = true)
            Picker(stringResource(R.string.parent_category), parents, parent, { if (locale.language == "ar") it.nameAr else it.nameEn }, { parent = it }, allowNone = true, noneText = stringResource(R.string.main_category), onNone = { parent = null })
            Text(stringResource(R.string.icon), style = MaterialTheme.typography.labelMedium)
            LazyVerticalGrid(GridCells.Fixed(6), Modifier.height(130.dp)) {
                items(com.budgetsms.app.ui.icons.AppIcons.categoryKeys) { k ->
                    Box(Modifier.padding(3.dp).size(40.dp).clip(MaterialTheme.shapes.small)
                        .background(if (k == icon) Color(color).copy(alpha = 0.25f) else Color.Transparent).clickable { icon = k }, contentAlignment = Alignment.Center) {
                        Icon(com.budgetsms.app.ui.icons.AppIcons.category(k), k, tint = Color(color))
                    }
                }
            }
            Text(stringResource(R.string.color), style = MaterialTheme.typography.labelMedium)
            SwatchRow(ACCENT_SWATCHES.take(10), color) { color = it }
        }
    }, confirmButton = {
        TextButton(onClick = { onSave(Category(UUID.randomUUID().toString(), en.ifBlank { ar }, ar.ifBlank { en }, parent?.id, icon, color)) }, enabled = en.isNotBlank() || ar.isNotBlank()) { Text(stringResource(R.string.save)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
fun SwatchRow(swatches: List<Long>, selected: Long, onPick: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        swatches.forEach { sw ->
            Box(Modifier.size(26.dp).clip(CircleShape).background(Color(sw))
                .border(if (sw == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape).clickable { onPick(sw) })
        }
    }
}
