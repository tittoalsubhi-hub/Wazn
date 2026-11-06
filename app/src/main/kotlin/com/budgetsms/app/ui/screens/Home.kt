package com.budgetsms.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.*
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.icons.ProgressRing
import com.budgetsms.app.ui.icons.PulseDot
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.app.ui.theme.amountLarge
import com.budgetsms.data.AppSnapshot
import com.budgetsms.domain.accounting.AlertType
import com.budgetsms.domain.accounting.CycleBudget
import com.budgetsms.domain.model.Provenance
import com.budgetsms.domain.money.Balance
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.obligations.OccurrenceState
import java.time.ZoneId

/** Aggregated figures for one profile or the consolidated view (internal transfers cancel out). */
data class BudgetView(val funded: Money, val planned: Money?, val spent: Money, val remaining: Money, val underReview: Money, val unallocated: Money, val budgets: List<CycleBudget>)

fun AppSnapshot.budgetView(profileId: String?): BudgetView? {
    val l = ledger ?: return null
    val list = profiles.mapNotNull { p -> if (profileId == null || p.profile.id == profileId) l.budgetOf(p.profile.id) else null }
    if (list.isEmpty()) return null
    val planned = list.mapNotNull { it.planned }.takeIf { it.isNotEmpty() }?.let { Money.sum(it) }
    return BudgetView(Money.sum(list.map { it.funded }), planned, Money.sum(list.map { it.spent }), Money.sum(list.map { it.remaining }),
        Money.sum(list.map { it.underReview }), Money.sum(list.map { it.unallocated }), list)
}

@Composable
fun ProfileSelector(vm: AppViewModel, allowAll: Boolean = true) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val sel by vm.selectedProfile.collectAsStateWithLifecycle()
    LaunchedEffect(allowAll, s.profiles.size) { if (!allowAll && sel == null) vm.selectedProfile.value = s.profiles.firstOrNull()?.profile?.id }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (allowAll) FilterChip(sel == null, { vm.selectedProfile.value = null }, { Text(stringResource(R.string.all_profiles)) }, leadingIcon = { Icon(AppIcons.more.first, null, Modifier.size(18.dp)) })
        s.profiles.forEach { p ->
            FilterChip(sel == p.profile.id, { vm.selectedProfile.value = p.profile.id }, { Text(p.profile.name) },
                leadingIcon = { Icon(AppIcons.profile, null, Modifier.size(18.dp), tint = Color(p.colorArgb)) })
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val sel by vm.selectedProfile.collectAsStateWithLifecycle()
    val sem = LocalSemantic.current
    val locale = currentLocale()
    val view = s.budgetView(sel)
    ScreenColumn {
        ProfileSelector(vm)
        if (view == null) {
            SectionCard(null) {
                EmptyState(AppIcons.sms, stringResource(R.string.no_cycle_title), stringResource(R.string.no_cycle_body)) {
                    Button(onClick = { onOpen("profiles") }) { Text(stringResource(R.string.manage_profiles)) }
                }
            }
        } else HeroCard(view)

        // Review queue and alerts
        val reviewIds = s.ledger?.reviews?.map { it.eventId }?.distinct().orEmpty()
        if (reviewIds.isNotEmpty()) Card(onClick = { onOpen("activity") }, colors = CardDefaults.cardColors(containerColor = sem.reviewContainer), shape = MaterialTheme.shapes.large) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                PulseDot(sem.review); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.review_needed, reviewIds.size), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.review_needed_body), style = MaterialTheme.typography.bodySmall)
                }
                Icon(AppIcons.chevron, null)
            }
        }
        val alerts = s.ledger?.alerts.orEmpty().filter { a -> sel == null || a.subjectId.startsWith(sel!!) || a.type != AlertType.BUDGET_NEGATIVE }
        if (alerts.isNotEmpty()) SectionCard(stringResource(R.string.alerts), icon = AppIcons.bell) {
            alerts.take(5).forEach { a ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.warning, null, tint = sem.overspend); Spacer(Modifier.width(10.dp))
                    Text(alertText(a.type), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    a.amount?.let { AmountText(it, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        // Category breakdown donut
        if (view != null) {
            val cats = view.budgets.flatMap { it.categories }.filter { it.parentId == null && it.spent.isPositive }
                .groupBy { it.categoryId }.map { (id, l) -> id to Money.sum(l.map { it.spent }) }.sortedByDescending { it.second.halalas }
            if (cats.isNotEmpty()) SectionCard(stringResource(R.string.spending_by_category), icon = AppIcons.budget.first) {
                val slices = cats.take(6).map { (id, m) -> Slice(categoryName(s, id), m, s.category(id)?.color() ?: sem.planned) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DonutChart(slices) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.spent), style = MaterialTheme.typography.labelSmall); AmountText(view.spent, style = MaterialTheme.typography.titleSmall) } }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) { slices.forEach { sl ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            Surface(Modifier.size(10.dp), shape = MaterialTheme.shapes.extraSmall, color = sl.color) {}
                            Spacer(Modifier.width(8.dp)); Text(sl.label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        } } }
                }
                ExactValues(slices.map { it.label to it.value })
            }
            // Spending trend for the open cycle(s)
            val open = view.budgets.map { it.cycle }
            val zone = ZoneId.systemDefault()
            val openIds = open.map { it.id }.toSet()
            val days = s.events.filter { e -> e.isConfirmed && s.ledger?.assignment?.get(e.id)?.let { it in openIds } == true && e.kind.direction == com.budgetsms.domain.model.EventKind.Direction.DEBIT }
                .groupBy { it.executedAt.atZone(zone).toLocalDate() }.toSortedMap()
            if (days.isNotEmpty()) SectionCard(stringResource(R.string.spending_trend), icon = AppIcons.forecast) {
                var run = Money.ZERO
                val pts = days.map { (d, l) -> run += Money.sum(l.map { it.sarAmount }); ChartPoint(Fmt.date(d, locale), run) }
                TrendChart(pts, view.funded, sem.expense)
                Row { ProvenanceChip(Provenance.CONFIRMED); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.dashed_is_funded), style = MaterialTheme.typography.labelSmall) }
                ExactValues(pts.map { it.label to it.value })
            }
        }

        // Accounts: calculated balance with last stated bank balance and its date (§09)
        val accounts = s.accounts.filter { sel == null || it.profileId == sel }
        if (accounts.isNotEmpty()) SectionCard(stringResource(R.string.accounts), icon = AppIcons.bank) {
            accounts.forEach { a ->
                val b = s.ledger?.balances?.get(a.id)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (a.type == com.budgetsms.domain.model.AccountType.CREDIT_CARD) AppIcons.kind(com.budgetsms.domain.model.EventKind.PURCHASE_CREDIT_CARD) else AppIcons.bank,
                        Color(s.banks.firstOrNull { it.id == a.bankId }?.colorArgb ?: 0xFF56636E))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(Fmt.ltr("•••• ${a.maskedNumber}") + " · " + accountTypeLabel(a.type), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (b is Balance.Known) Text(stringResource(R.string.last_stated, money(b.referenceAmount), Fmt.dateTime(b.referenceAt, locale)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        when (b) {
                            is Balance.Known -> { AmountText(b.amount, style = MaterialTheme.typography.titleMedium); ProvenanceChip(Provenance.CALCULATED) }
                            is Balance.Unknown -> { Text(stringResource(R.string.balance_unavailable), style = MaterialTheme.typography.labelLarge)
                                Text(stringResource(R.string.net_movement, money(b.knownNetMovement)), style = MaterialTheme.typography.labelSmall) }
                            null -> {}
                        }
                    }
                }
            }
        }

        // Upcoming obligations
        val upcoming = s.occurrences.filter { (sel == null || it.occurrence.profileId == sel) && it.state in setOf(OccurrenceState.DUE, OccurrenceState.OVERDUE, OccurrenceState.UPCOMING, OccurrenceState.PARTIALLY_PAID) && it.daysToDue <= 14 }
            .sortedBy { it.occurrence.dueDate }
        if (upcoming.isNotEmpty()) SectionCard(stringResource(R.string.upcoming_obligations), icon = AppIcons.obligations, action = { TextButton(onClick = { onOpen("obligations") }) { Text(stringResource(R.string.see_all)) } }) {
            upcoming.take(4).forEach { o ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.obligations.firstOrNull { it.id == o.occurrence.obligationId }?.name ?: "", Modifier.weight(1f))
                    AssistChip(onClick = {}, label = { Text(occStateLabel(o.state)) }, colors = AssistChipDefaults.assistChipColors(labelColor = occStateColor(o.state)))
                    Spacer(Modifier.width(8.dp)); AmountText(o.remaining, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        if (s.savingsRequests.isNotEmpty()) SectionCard(stringResource(R.string.savings_requests), icon = AppIcons.goals.first,
            action = { TextButton(onClick = { onOpen("goals") }) { Text(stringResource(R.string.open)) } }) {
            Text(stringResource(R.string.savings_requests_body, s.savingsRequests.size), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HeroCard(v: BudgetView) {
    val sem = LocalSemantic.current
    val cs = MaterialTheme.colorScheme
    val frac = if (v.funded.halalas <= 0) 0f else (v.spent.halalas.toFloat() / v.funded.halalas)
    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer.copy(alpha = 0.85f)))).padding(20.dp)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.remaining_budget), style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                        AmountText(v.remaining, style = MaterialTheme.typography.amountLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (v.remaining.isNegative) sem.overspend else cs.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Row { ProvenanceChip(if (v.underReview.isPositive) Provenance.UNDER_REVIEW else Provenance.CALCULATED) }
                    }
                    ProgressRing(frac, Modifier.size(92.dp), color = if (frac > 1f) sem.overspend else cs.primary, track = cs.surface.copy(alpha = 0.5f)) {
                        Text("${(frac * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.titleMedium, color = cs.onPrimaryContainer)
                    }
                }
                if (v.remaining.isNegative) { Spacer(Modifier.height(8.dp)); MessageBanner(stringResource(R.string.budget_negative_note), AppIcons.warning, sem.overspend) }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.funded), v.funded, Provenance.CONFIRMED, AppIcons.kind(com.budgetsms.domain.model.EventKind.SALARY), sem.income, Modifier.weight(1f))
                    StatTile(stringResource(R.string.spent), v.spent, Provenance.CONFIRMED, AppIcons.kind(com.budgetsms.domain.model.EventKind.PURCHASE_DEBIT), sem.expense, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.planned), v.planned, Provenance.PLANNED, AppIcons.edit, sem.planned, Modifier.weight(1f))
                    StatTile(stringResource(R.string.under_review), v.underReview, Provenance.UNDER_REVIEW, AppIcons.review, sem.review, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.unallocated_note, money(v.unallocated)), style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer)
            }
        }
    }
}

@Composable
fun alertText(t: AlertType) = stringResource(when (t) {
    AlertType.BUDGET_NEGATIVE -> R.string.alert_budget_negative; AlertType.LIMIT_EXCEEDED -> R.string.alert_limit
    AlertType.CHILD_LIMITS_EXCEED_PARENT -> R.string.alert_child_limits; AlertType.TRANSFER_INCOMING_MISSING -> R.string.alert_transfer_missing
    AlertType.BALANCE_MISMATCH -> R.string.alert_balance_mismatch; AlertType.INSTALLMENT_UNFUNDED -> R.string.alert_installment
    AlertType.OBLIGATION_OVERDUE -> R.string.alert_overdue; AlertType.LIQUIDITY_THRESHOLD -> R.string.alert_liquidity
})
