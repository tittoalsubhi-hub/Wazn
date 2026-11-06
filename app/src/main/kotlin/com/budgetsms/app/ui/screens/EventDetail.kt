package com.budgetsms.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.*
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.domain.model.*
import com.budgetsms.domain.money.Money
import java.time.Instant
import java.time.ZoneId

/** Generic single-choice picker used across forms. */
@Composable
fun <T> Picker(label: String, options: List<T>, selected: T?, text: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier, allowNone: Boolean = false, noneText: String = "—", onNone: () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) {
                Text(selected?.let { text(it) } ?: noneText, Modifier.weight(1f), maxLines = 1)
                Icon(AppIcons.chevron, null)
            }
            DropdownMenu(open, { open = false }) {
                if (allowNone) DropdownMenuItem(text = { Text(noneText) }, onClick = { onNone(); open = false })
                options.forEach { o -> DropdownMenuItem(text = { Text(text(o)) }, onClick = { onSelect(o); open = false }) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(vm: AppViewModel, id: String, onDone: () -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val e = s.events.firstOrNull { it.id == id } ?: return
    val sem = LocalSemantic.current
    val locale = currentLocale()
    val rawFlow = remember(e.rawSmsIds.first()) { vm.rawSms(e.rawSmsIds.first()) }
    val raw by rawFlow.collectAsStateWithLifecycle(null)
    val review = s.ledger?.reviews.orEmpty().filter { it.eventId == id }
    var kind by remember(id) { mutableStateOf(e.kind) }
    var accountId by remember(id) { mutableStateOf(e.accountId) }
    var counterparty by remember(id) { mutableStateOf(e.counterpartyAccountId) }
    var category by remember(id) { mutableStateOf(e.categoryId) }
    var treatment by remember(id) { mutableStateOf(e.incomeTreatment) }
    var showDate by remember { mutableStateOf(false) }
    var amountText by remember(id) { mutableStateOf(e.amount?.toPlainString() ?: "") }
    val manualAmount = Money.parseOrNull(amountText)
    val editable = !e.isConfirmed

    ScreenColumn {
        SectionCard(null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(AppIcons.kind(e.kind), kindColor(e.kind), 52); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(kindLabel(e.kind), style = MaterialTheme.typography.titleLarge)
                    Text(stateLabel(e.state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (e.amount != null) AmountText(e.amount!!, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = kindColor(e.kind))
                    ProvenanceChip(if (e.isConfirmed) Provenance.CONFIRMED else Provenance.UNDER_REVIEW)
                }
            }
        }
        if (review.isNotEmpty()) SectionCard(stringResource(R.string.why_review), icon = AppIcons.review, container = sem.reviewContainer) {
            review.flatMap { it.reasons }.distinct().forEach { r -> Row(Modifier.padding(vertical = 3.dp)) { Icon(AppIcons.info, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(reasonLabel(r)) } }
        }
        // Original evidence: shown exactly as received, never editable (spec §04).
        SectionCard(stringResource(R.string.original_message), icon = AppIcons.sms) {
            raw?.let { r ->
                Text(r.sender, style = MaterialTheme.typography.labelLarge)
                Text(Fmt.dateTime(Instant.ofEpochMilli(r.receivedAt), locale), style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(r.body, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.parser_info, e.parserId, e.parserVersion, e.rawSmsIds.size), style = MaterialTheme.typography.labelSmall)
        }
        SectionCard(stringResource(R.string.extracted_fields), icon = AppIcons.review) {
            Field(stringResource(R.string.field_account), s.account(e.accountId)?.displayName ?: e.counterpartyHint ?: "—")
            Field(stringResource(R.string.field_reference), e.reference?.let { Fmt.ltr(it) } ?: "—")
            Field(stringResource(R.string.field_time), Fmt.dateTime(e.executedAt, locale) + " · " + stringResource(when (e.timeSource) {
                TimeSource.BANK -> R.string.time_bank; TimeSource.RECEIPT -> R.string.time_receipt; TimeSource.USER_CORRECTED -> R.string.time_user }))
            Field(stringResource(R.string.field_balance_after), e.balanceAfter?.let { money(it) } ?: "—")
            Field(stringResource(R.string.field_fee), e.fee?.let { money(it) } ?: "—")
            if (e.originalCurrency != "SAR") Field(stringResource(R.string.field_original), Fmt.ltr(e.originalAmountText ?: e.originalCurrency))
            TextButton(onClick = { showDate = true }) { Icon(AppIcons.calendar, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.correct_date)) }
        }

        SectionCard(stringResource(R.string.your_decision), icon = AppIcons.check) {
            Picker(stringResource(R.string.field_type), EventKind.entries, kind, { kindLabel(it) }, { kind = it })
            Spacer(Modifier.height(8.dp))
            Picker(stringResource(R.string.field_account), s.accounts, s.account(accountId), { it.displayName + " ••" + it.maskedNumber }, { accountId = it.id }, allowNone = true, onNone = { accountId = null })
            if (kind == EventKind.OUTGOING_TRANSFER || kind == EventKind.INCOMING_TRANSFER || kind == EventKind.CARD_REPAYMENT) {
                Spacer(Modifier.height(8.dp))
                Picker(stringResource(R.string.field_own_counterparty), s.accounts.filter { it.id != accountId }, s.account(counterparty), { it.displayName + " ••" + it.maskedNumber },
                    { counterparty = it.id }, allowNone = true, noneText = stringResource(R.string.external_party), onNone = { counterparty = null })
            }
            if (kind.direction == EventKind.Direction.DEBIT || kind == EventKind.REFUND || kind == EventKind.PURCHASE_CREDIT_CARD) {
                Spacer(Modifier.height(8.dp))
                Picker(stringResource(R.string.field_category), s.categories, s.category(category), { if (locale.language == "ar") it.nameAr else it.nameEn }, { category = it.id }, allowNone = true, onNone = { category = null })
            }
            if (kind == EventKind.INCOMING_TRANSFER && counterparty == null) {
                Spacer(Modifier.height(8.dp))
                Picker(stringResource(R.string.field_income_treatment), IncomeTreatment.entries, treatment, { treatmentLabel(it) }, { treatment = it })
                Text(stringResource(R.string.earmark_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (editable && kind.direction != EventKind.Direction.NONE) {
                Spacer(Modifier.height(8.dp))
                // Manual amount correction for messages under review (owner request); the change is audited.
                OutlinedTextField(amountText, { amountText = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.field_amount_manual)) },
                    singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    suffix = { Text(stringResource(R.string.currency_sar)) }, isError = amountText.isNotEmpty() && manualAmount == null,
                    supportingText = { Text(stringResource(R.string.manual_amount_note)) })
            }
            Spacer(Modifier.height(12.dp))
            val amountOk = kind.direction == EventKind.Direction.NONE || (if (editable) manualAmount != null && !manualAmount.isNegative else e.amount != null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.confirmEvent(id, kind, accountId, counterparty, category, treatment, if (editable && manualAmount != e.amount) manualAmount else null) }, enabled = amountOk) {
                    Icon(AppIcons.check, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.confirm))
                }
                OutlinedButton(onClick = { vm.markDuplicate(id); onDone() }) { Text(stringResource(R.string.mark_duplicate)) }
            }
        }

        // Transfer matching candidates (one-to-one, user decides on ties)
        val candidates = review.flatMap { it.candidates }.distinct()
        if (candidates.isNotEmpty()) SectionCard(stringResource(R.string.match_candidates), icon = AppIcons.transfer) {
            candidates.mapNotNull { c -> s.events.firstOrNull { it.id == c } }.forEach { c ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(s.account(c.accountId)?.displayName ?: "—"); Text(Fmt.dateTime(c.executedAt, locale), style = MaterialTheme.typography.labelSmall) }
                    c.amount?.let { AmountText(it, style = MaterialTheme.typography.labelLarge) }
                    TextButton(onClick = { vm.approveMatch(id, c.id) }) { Text(stringResource(R.string.match)) }
                    TextButton(onClick = { vm.rejectMatch(id, c.id) }) { Text(stringResource(R.string.reject)) }
                }
            }
        }
        // Refund / reversal link to the original transaction (corrects its cycle and category)
        if (e.kind == EventKind.REFUND || e.kind == EventKind.REVERSAL) SectionCard(stringResource(R.string.link_original), icon = AppIcons.history) {
            val originals = s.events.filter { it.id != id && it.accountId == e.accountId && it.kind.direction == EventKind.Direction.DEBIT && it.executedAt.isBefore(e.executedAt) }.take(20)
            Picker(stringResource(R.string.original_transaction), originals, originals.firstOrNull { it.id == e.correctsEventId },
                { (it.amount?.let { a -> Fmt.money(a, locale) } ?: "—") + " · " + Fmt.day(it.executedAt, locale) }, { vm.linkCorrection(id, it.id) }, allowNone = true, onNone = { vm.linkCorrection(id, null) })
        }
        // Obligation payment candidates (each bank movement pays one occurrence only)
        s.paymentCandidates[id]?.let { list ->
            SectionCard(stringResource(R.string.pays_obligation), icon = AppIcons.obligations) {
                if (list.size > 1) Text(stringResource(R.string.choose_one_obligation), style = MaterialTheme.typography.bodySmall)
                list.forEach { o ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((s.obligations.firstOrNull { it.id == o.occurrence.obligationId }?.name ?: "") + " · " + Fmt.date(o.occurrence.dueDate, locale), Modifier.weight(1f))
                        TextButton(onClick = { vm.linkPayment(id, o.occurrence.id, minOf(e.amount ?: Money.ZERO, o.remaining)) }) { Text(stringResource(R.string.link)) }
                    }
                }
            }
        }
    }
    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = e.executedAt.toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDate = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms ->
                    val zone = ZoneId.systemDefault(); val time = e.executedAt.atZone(zone).toLocalTime()
                    val date = Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    vm.correctTime(id, date.atTime(time).atZone(zone).toInstant())
                }
                showDate = false
            }) { Text(stringResource(R.string.save)) }
        }, dismissButton = { TextButton(onClick = { showDate = false }) { Text(stringResource(R.string.cancel)) } }) { DatePicker(state) }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(0.42f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(0.58f), style = MaterialTheme.typography.bodyMedium)
    }
}
