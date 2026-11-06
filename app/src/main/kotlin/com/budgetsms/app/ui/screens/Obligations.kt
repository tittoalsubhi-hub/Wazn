package com.budgetsms.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.*
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.obligations.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.UUID

private enum class ObFilter(val label: Int) { ALL(R.string.filter_all), MONTHLY(R.string.ob_monthly), ONE_TIME(R.string.ob_one_time), OVERDUE(R.string.occ_overdue), PAID(R.string.occ_paid) }

@Composable
fun ObligationsScreen(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val sel by vm.selectedProfile.collectAsStateWithLifecycle()
    val sem = LocalSemantic.current; val locale = currentLocale()
    var filter by remember { mutableStateOf(ObFilter.ALL) }
    var editing by remember { mutableStateOf<Obligation?>(null) }
    var creating by remember { mutableStateOf(false) }
    var occ by remember { mutableStateOf<OccurrenceStatus?>(null) }
    val obById = s.obligations.associateBy { it.id }
    val list = s.occurrences.filter { o ->
        val ob = obById[o.occurrence.obligationId]
        (sel == null || o.occurrence.profileId == sel) && when (filter) {
            ObFilter.ALL -> true; ObFilter.MONTHLY -> ob?.type == ObligationType.MONTHLY; ObFilter.ONE_TIME -> ob?.type == ObligationType.ONE_TIME
            ObFilter.OVERDUE -> o.state == OccurrenceState.OVERDUE; ObFilter.PAID -> o.state == OccurrenceState.PAID
        }
    }.sortedBy { it.occurrence.dueDate }

    Box(Modifier.fillMaxSize()) {
        ScreenColumn {
            ProfileSelector(vm)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ObFilter.entries.forEach { f -> FilterChip(filter == f, { filter = f }, { Text(stringResource(f.label)) }) }
            }
            // Known total commitments and a 12-month heatmap of future amounts (§15, §18)
            val months = (0 until 12).map { YearMonth.from(s.today).plusMonths(it.toLong()) }
            val perMonth = months.map { ym -> ym to Money.sum(list.filter { YearMonth.from(it.occurrence.dueDate) == ym && it.occurrence.currency == "SAR" }.map { it.remaining }) }
            SectionCard(stringResource(R.string.commitments_12m), icon = AppIcons.calendar) {
                AmountText(Money.sum(perMonth.map { it.second }), style = MaterialTheme.typography.headlineSmall)
                ProvenanceChip(com.budgetsms.domain.model.Provenance.FORECAST)
                Spacer(Modifier.height(10.dp))
                val max = perMonth.maxOf { it.second.halalas }.coerceAtLeast(1)
                val c = sem.forecast; val track = MaterialTheme.colorScheme.surfaceContainerHighest
                Row(Modifier.fillMaxWidth().height(70.dp).semantics { contentDescription = perMonth.joinToString { "${it.first}: ${it.second.toPlainString()}" } }, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                    perMonth.forEach { (ym, m) -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(Modifier.fillMaxWidth().height(52.dp)) {
                            drawRoundRect(track, cornerRadius = CornerRadius(8f))
                            val h = size.height * (m.halalas.toFloat() / max)
                            drawRoundRect(c.copy(alpha = 0.25f + 0.75f * (m.halalas.toFloat() / max)), Offset(0f, size.height - h), Size(size.width, h), CornerRadius(8f))
                        }
                        Text(ym.month.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelSmall)
                    } }
                }
                ExactValues(perMonth.map { (ym, m) -> ym.month.getDisplayName(TextStyle.SHORT, locale) + " " + ym.year to m })
            }
            s.obligationErrors.forEach { (id, _) -> MessageBanner(stringResource(R.string.month_end_policy_needed, obById[id]?.name ?: ""), AppIcons.warning, sem.review) }
            if (list.isEmpty()) EmptyState(AppIcons.obligations, stringResource(R.string.no_obligations), stringResource(R.string.no_obligations_body))
            // Timeline grouped by month
            list.groupBy { YearMonth.from(it.occurrence.dueDate) }.forEach { (ym, items) ->
                Text(ym.month.getDisplayName(TextStyle.FULL_STANDALONE, locale) + " " + ym.year, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                items.forEach { o ->
                    val ob = obById[o.occurrence.obligationId]
                    Card(onClick = { occ = o }, shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(AppIcons.category(s.category(ob?.categoryId)?.iconKey ?: "bolt"), occStateColor(o.state), 40); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ob?.name ?: "", style = MaterialTheme.typography.bodyLarge)
                                Text(Fmt.date(o.occurrence.dueDate, locale) + " · " + when {
                                    o.state == OccurrenceState.PAID -> occStateLabel(o.state)
                                    o.daysToDue < 0 -> stringResource(R.string.days_overdue, -o.daysToDue)
                                    else -> stringResource(R.string.days_left, o.daysToDue) }, style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                AmountText(o.remaining, style = MaterialTheme.typography.titleSmall)
                                Text(occStateLabel(o.state), style = MaterialTheme.typography.labelSmall, color = occStateColor(o.state))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(72.dp))
        }
        ExtendedFloatingActionButton(onClick = { creating = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp), icon = { Icon(AppIcons.add, null) }, text = { Text(stringResource(R.string.add_obligation)) })
    }

    occ?.let { o -> OccurrenceSheet(vm, o, obById[o.occurrence.obligationId], onEdit = { editing = it; occ = null }, onDismiss = { occ = null }) }
    if (creating || editing != null) ObligationEditor(vm, editing, onDismiss = { creating = false; editing = null })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OccurrenceSheet(vm: AppViewModel, o: OccurrenceStatus, ob: Obligation?, onEdit: (Obligation) -> Unit, onDismiss: () -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var newAmount by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(ob?.name ?: "", style = MaterialTheme.typography.titleLarge)
            Text(Fmt.date(o.occurrence.dueDate, locale) + " · " + occStateLabel(o.state), color = occStateColor(o.state))
            Row { Text(stringResource(R.string.expected), Modifier.weight(1f)); AmountText(o.occurrence.expected) }
            Row { Text(stringResource(R.string.confirmed_paid), Modifier.weight(1f)); AmountText(o.paid) }
            Row { Text(stringResource(R.string.remaining), Modifier.weight(1f)); AmountText(o.remaining) }
            Text(stringResource(R.string.due_not_proof), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Candidate bank movements (confirmed SMS only); each can pay one occurrence.
            val cands = s.paymentCandidates.filterValues { l -> l.any { it.occurrence.id == o.occurrence.id } }.keys.mapNotNull { id -> s.events.firstOrNull { it.id == id } }
            if (cands.isNotEmpty()) {
                Text(stringResource(R.string.payment_candidates), style = MaterialTheme.typography.titleSmall)
                cands.forEach { e -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.dateTime(e.executedAt, locale), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    e.amount?.let { AmountText(it, style = MaterialTheme.typography.labelLarge) }
                    TextButton(onClick = { vm.linkPayment(e.id, o.occurrence.id, minOf(e.amount ?: Money.ZERO, o.remaining)); onDismiss() }) { Text(stringResource(R.string.link)) }
                } }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                AssistChip({ newAmount = true }, { Text(stringResource(R.string.change_this_amount)) }, leadingIcon = { Icon(AppIcons.edit, null) })
                AssistChip({ vm.overrideOccurrence(o.occurrence.obligationId, o.occurrence.index, null, null, true); onDismiss() }, { Text(stringResource(R.string.cancel_occurrence)) }, leadingIcon = { Icon(AppIcons.close, null) })
                if (ob != null) {
                    val paused = ob.pauses.any { it.until == null }
                    AssistChip({ vm.saveObligation(if (paused) ob.copy(pauses = ob.pauses.map { if (it.until == null) it.copy(until = s.today) else it }) else ob.copy(pauses = ob.pauses + PausePeriod(s.today, null))); onDismiss() },
                        { Text(stringResource(if (paused) R.string.resume else R.string.pause)) }, leadingIcon = { Icon(if (paused) AppIcons.play else AppIcons.pause, null) })
                    AssistChip({ vm.saveObligation(ob.copy(endedAt = s.today)); onDismiss() }, { Text(stringResource(R.string.end_series)) }, leadingIcon = { Icon(AppIcons.stop, null) })
                    AssistChip({ onEdit(ob) }, { Text(stringResource(R.string.edit)) }, leadingIcon = { Icon(AppIcons.edit, null) })
                }
            }
        }
    }
    if (newAmount) MoneyInputDialog(stringResource(R.string.change_this_amount), stringResource(R.string.amount), initial = o.occurrence.expected.toPlainString(), onDismiss = { newAmount = false }) {
        vm.overrideOccurrence(o.occurrence.obligationId, o.occurrence.index, it, null, false); newAmount = false
    }
}

@Composable
private fun ObligationEditor(vm: AppViewModel, existing: Obligation?, onDismiss: () -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val locale = currentLocale()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var amount by remember { mutableStateOf(existing?.amount?.toPlainString() ?: "") }
    var currency by remember { mutableStateOf(existing?.currency ?: "SAR") }
    var type by remember { mutableStateOf(existing?.type ?: ObligationType.MONTHLY) }
    val currentProfile = existing?.let { e -> e.profileChanges.maxByOrNull { it.effectiveFrom }?.profileId ?: e.profileId }
    var profileId by remember { mutableStateOf(currentProfile ?: s.profiles.firstOrNull()?.profile?.id) }
    var category by remember { mutableStateOf(existing?.categoryId) }
    var start by remember { mutableStateOf(existing?.startDate?.toString() ?: s.today.plusDays(1).toString()) }
    var count by remember { mutableStateOf(existing?.occurrenceCount?.toString() ?: "12") }
    var remind by remember { mutableStateOf((existing?.remindDaysBefore ?: 3).toString()) }
    var policy by remember { mutableStateOf(existing?.monthEndPolicy) }
    val date = runCatching { LocalDate.parse(start) }.getOrNull()
    val money = Money.parseOrNull(amount)
    val needsPolicy = type == ObligationType.MONTHLY && (date?.dayOfMonth ?: 1) >= 29
    val valid = name.isNotBlank() && money != null && date != null && profileId != null && (!needsPolicy || policy != null)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().imePadding()) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(AppIcons.close, stringResource(R.string.cancel)) }
                    Text(stringResource(if (existing == null) R.string.add_obligation else R.string.edit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(enabled = valid, onClick = {
                        val base = existing ?: Obligation(UUID.randomUUID().toString(), name, profileId = profileId!!, amount = money!!, type = type, startDate = date!!)
                        var next = base.copy(name = name, notes = notes, amount = money!!, currency = currency.uppercase(), type = type,
                            categoryId = category, startDate = date!!, dayOfMonth = date.dayOfMonth, monthEndPolicy = policy,
                            occurrenceCount = if (type == ObligationType.MONTHLY) count.toIntOrNull() else null, remindDaysBefore = remind.toIntOrNull() ?: 3)
                        // Profile changes apply prospectively; paid history keeps its profile (§15, T22).
                        if (existing != null && currentProfile != profileId) next = ObligationScheduler.changeProfile(next, ProfileChange(s.today, profileId!!), s.occurrences)
                        vm.saveObligation(next)
                        onDismiss()
                    }) { Text(stringResource(R.string.save)) }
                }
                ScreenColumn {
                    ChoiceRow(ObligationType.entries, type, { stringResource(if (it == ObligationType.MONTHLY) R.string.ob_monthly else R.string.ob_one_time) }, { type = it })
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.name)) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(amount, { amount = it }, Modifier.weight(2f), label = { Text(stringResource(R.string.expected_amount)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = amount.isNotEmpty() && money == null)
                        OutlinedTextField(currency, { currency = it.take(3) }, Modifier.weight(1f), label = { Text(stringResource(R.string.currency)) }, singleLine = true)
                    }
                    if (currency.uppercase() != "SAR") Text(stringResource(R.string.non_sar_note), style = MaterialTheme.typography.labelSmall, color = LocalSemantic.current.review)
                    OutlinedTextField(start, { start = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(if (type == ObligationType.MONTHLY) R.string.first_due_date else R.string.due_date)) },
                        supportingText = { Text(stringResource(R.string.date_format_hint)) }, isError = date == null, singleLine = true)
                    if (type == ObligationType.MONTHLY) OutlinedTextField(count, { count = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.installments_count)) },
                        supportingText = { Text(stringResource(R.string.empty_open_ended)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (needsPolicy) {
                        // Explicit decision required: no hidden date-shifting rule (§15).
                        Text(stringResource(R.string.month_end_policy), style = MaterialTheme.typography.titleSmall)
                        MonthEndPolicy.entries.forEach { p -> Row(Modifier.fillMaxWidth().clickable { policy = p }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(policy == p, { policy = p }); Text(stringResource(if (p == MonthEndPolicy.LAST_DAY_OF_MONTH) R.string.policy_last_day else R.string.policy_next_first))
                        } }
                    }
                    Picker(stringResource(R.string.profile), s.profiles, s.profile(profileId), { it.profile.name }, { profileId = it.profile.id })
                    Picker(stringResource(R.string.field_category), s.categories, s.category(category), { if (locale.language == "ar") it.nameAr else it.nameEn }, { category = it.id }, allowNone = true, onNone = { category = null })
                    OutlinedTextField(remind, { remind = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.remind_days_before)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.notes)) })
                    if (existing != null && currentProfile != profileId) Text(stringResource(R.string.profile_change_prospective), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
