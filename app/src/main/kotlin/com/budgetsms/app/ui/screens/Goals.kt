package com.budgetsms.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetsms.app.AppViewModel
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.*
import com.budgetsms.app.ui.icons.AppIcons
import com.budgetsms.app.ui.icons.ProgressRing
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.domain.model.*
import com.budgetsms.domain.money.BasisPoints
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.savings.*
import java.time.LocalDate

@Composable
fun GoalsScreen(vm: AppViewModel, onOpen: (String) -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val names by vm.goalNames.collectAsStateWithLifecycle()
    val sem = LocalSemantic.current
    val locale = currentLocale()
    var createFor by remember { mutableStateOf<SavingsRequest?>(null) }
    var allocate by remember { mutableStateOf<TransferStatus?>(null) }
    var confirmProvisional by remember { mutableStateOf<SavingsRequest?>(null) }
    val goalCycles = s.goals.mapNotNull { it.goal.requestCycleId }.toSet()

    ScreenColumn {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onOpen("forecast") }, Modifier.weight(1f)) { Icon(AppIcons.forecast, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.forecast)) }
            OutlinedButton(onClick = { onOpen("transfers") }, Modifier.weight(1f)) { Icon(AppIcons.transfer, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.transfers)) }
        }
        // Requests from closed confirmed-salary cycles (§10)
        val pending = s.savingsRequests.filter { it.cycleId !in goalCycles }
        SectionCard(stringResource(R.string.savings_requests), icon = AppIcons.goals.first) {
            if (pending.isEmpty()) Text(stringResource(R.string.no_requests_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            pending.forEach { r ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.profile(r.profileId)?.profile?.name ?: "", style = MaterialTheme.typography.titleSmall)
                            AmountText(r.target, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = sem.savings)
                        }
                        ProvenanceChip(if (r.provisional) Provenance.UNDER_REVIEW else Provenance.CALCULATED)
                    }
                    if (r.provisional) MessageBanner(stringResource(R.string.provisional_warning, money(r.unresolvedAmount)), AppIcons.warning, sem.review)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(onClick = { createFor = r }, enabled = !r.approvalSuspended) { Text(stringResource(R.string.create_goal)) }
                        if (r.approvalSuspended) OutlinedButton(onClick = { confirmProvisional = r }) { Text(stringResource(R.string.approve_anyway)) }
                    }
                }
            }
        }
        // Goals with progress rings
        if (s.goals.isEmpty()) SectionCard(null) { EmptyState(AppIcons.goals.first, stringResource(R.string.no_goals), stringResource(R.string.no_goals_body)) }
        s.goals.forEach { g ->
            val frac = if (g.goal.target.halalas <= 0) 1f else g.confirmedSaved.halalas.toFloat() / g.goal.target.halalas
            SectionCard(names[g.goal.id] ?: stringResource(R.string.goal), icon = AppIcons.goals.first, container = MaterialTheme.colorScheme.surfaceContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressRing(frac, Modifier.size(84.dp), color = sem.savings, track = sem.savingsContainer) { Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.titleSmall) }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.target_x, money(g.goal.target)), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.saved_x, money(g.confirmedSaved)), style = MaterialTheme.typography.bodyMedium, color = sem.savings)
                        Text(stringResource(R.string.remaining_x, money(g.remaining)), style = MaterialTheme.typography.bodySmall)
                        if (g.additionalSavings.isPositive) Text(stringResource(R.string.additional_savings, money(g.additionalSavings)), style = MaterialTheme.typography.bodySmall, color = sem.income)
                        if (g.pendingAllocation.isPositive) Text(stringResource(R.string.pending_allocation, money(g.pendingAllocation)), style = MaterialTheme.typography.bodySmall, color = sem.review)
                        Text(stringResource(R.string.destination_x, s.account(g.goal.destinationAccountId)?.displayName ?: "—"), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        // Verified own-account transfers to savings accounts awaiting allocation (§11)
        val unallocated = s.savingsTransfers.filter { it.unallocated.isPositive }
        if (unallocated.isNotEmpty()) SectionCard(stringResource(R.string.verified_transfers), icon = AppIcons.transfer) {
            unallocated.forEach { t ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text((s.account(t.transfer.sourceAccountId)?.displayName ?: "—") + " → " + (s.account(t.transfer.destinationAccountId)?.displayName ?: "—"), style = MaterialTheme.typography.bodyMedium)
                        Text(Fmt.day(t.transfer.at, locale) + if (!t.transfer.incomingMatched) " · " + stringResource(R.string.incoming_missing) else "", style = MaterialTheme.typography.labelSmall)
                    }
                    AmountText(t.unallocated, style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { allocate = t }, enabled = s.goals.isNotEmpty()) { Text(stringResource(R.string.allocate)) }
                }
            }
        }
    }

    createFor?.let { r -> CreateGoalDialog(s.accounts.filter { it.type == AccountType.SAVINGS }, r.target, onDismiss = { createFor = null }) { name, dest ->
        vm.createGoal(name, r.profileId, r.cycleId, r.target, dest); createFor = null } }
    confirmProvisional?.let { r -> AlertDialog(onDismissRequest = { confirmProvisional = null }, icon = { Icon(AppIcons.warning, null) },
        title = { Text(stringResource(R.string.approve_anyway)) }, text = { Text(stringResource(R.string.provisional_confirm_body, money(r.unresolvedAmount))) },
        confirmButton = { TextButton(onClick = { vm.approveProvisional(r.cycleId); confirmProvisional = null }) { Text(stringResource(R.string.approve)) } },
        dismissButton = { TextButton(onClick = { confirmProvisional = null }) { Text(stringResource(R.string.cancel)) } }) }
    allocate?.let { t -> AllocateDialog(vm, t, onDismiss = { allocate = null }) }
}

@Composable
private fun CreateGoalDialog(savingsAccounts: List<Account>, target: Money, onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dest by remember { mutableStateOf<Account?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.create_goal)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.target_x, money(target)))
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.goal_name)) }, singleLine = true)
            // No silent default: the destination must be chosen explicitly (§11).
            Picker(stringResource(R.string.destination_account), savingsAccounts, dest, { it.displayName + " ••" + it.maskedNumber }, { dest = it }, noneText = stringResource(R.string.choose))
            if (savingsAccounts.isEmpty()) Text(stringResource(R.string.no_savings_accounts), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(onClick = { onCreate(name.ifBlank { "—" }, dest!!.id) }, enabled = dest != null) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun AllocateDialog(vm: AppViewModel, t: TransferStatus, onDismiss: () -> Unit) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val names by vm.goalNames.collectAsStateWithLifecycle()
    val proposal = remember(t.transfer.transferId) { SavingsEngine().proposeAllocation(t.transfer, s.goals, t.allocated) }
    val amounts = remember(t.transfer.transferId) { mutableStateMapOf<String, String>().apply { proposal.forEach { put(it.goalId, it.amount.toPlainString()) } } }
    val decisions = remember(t.transfer.transferId) { mutableStateMapOf<String, SettlementDecision>() }
    val total = Money.sum(amounts.values.mapNotNull { Money.parseOrNull(it) })
    val valid = total <= t.unallocated && amounts.values.all { it.isBlank() || Money.parseOrNull(it) != null }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.allocate)) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.available_to_allocate, money(t.unallocated)))
            Text(stringResource(R.string.oldest_first_note), style = MaterialTheme.typography.labelSmall)
            s.goals.filter { it.remaining.isPositive }.forEach { g ->
                val cross = g.goal.profileId != t.transfer.sourceProfileId
                Column {
                    OutlinedTextField(amounts[g.goal.id] ?: "", { amounts[g.goal.id] = it }, label = { Text(names[g.goal.id] ?: stringResource(R.string.goal)) },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), supportingText = { Text(stringResource(R.string.remaining_x, money(g.remaining))) })
                    if (cross) {
                        Text(stringResource(R.string.cross_profile_settlement), style = MaterialTheme.typography.labelMedium)
                        val opts = listOf(SettlementDecision.APPROVED, SettlementDecision.REJECTED_ALLOCATE_WITHOUT_SETTLEMENT, SettlementDecision.REJECTED_LEAVE_PENDING)
                        opts.forEach { o -> Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(decisions[g.goal.id] == o, { decisions[g.goal.id] = o })
                            Text(stringResource(when (o) { SettlementDecision.APPROVED -> R.string.settle_approve; SettlementDecision.REJECTED_ALLOCATE_WITHOUT_SETTLEMENT -> R.string.settle_reject_allocate; else -> R.string.settle_reject_pending }), style = MaterialTheme.typography.bodySmall)
                        } }
                    }
                }
            }
            if (!valid) Text(stringResource(R.string.allocation_exceeds), color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = {
        val ready = valid && s.goals.filter { g -> (Money.parseOrNull(amounts[g.goal.id] ?: "")?.isPositive == true) && g.goal.profileId != t.transfer.sourceProfileId }.all { decisions[it.goal.id] != null }
        TextButton(enabled = ready, onClick = {
            val items = s.goals.mapNotNull { g -> val a = Money.parseOrNull(amounts[g.goal.id] ?: "") ?: return@mapNotNull null
                if (!a.isPositive) return@mapNotNull null
                val cross = g.goal.profileId != t.transfer.sourceProfileId
                InstallmentAllocation(t.transfer.transferId, g.goal.id, a, userConfirmed = true, settlement = if (cross) decisions[g.goal.id]!! else SettlementDecision.NOT_NEEDED) }
            vm.saveAllocations(items, t.transfer, s.goals.map { it.goal }); onDismiss()
        }) { Text(stringResource(R.string.confirm)) }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Liquidity estimate kept separate from bank balances (§12). */
@Composable
fun ForecastScreen(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val sel by vm.selectedProfile.collectAsStateWithLifecycle()
    val names by vm.goalNames.collectAsStateWithLifecycle()
    val sem = LocalSemantic.current
    val profileId = sel ?: s.profiles.firstOrNull()?.profile?.id
    val profile = s.profile(profileId)?.profile
    var days by remember(profileId) { mutableIntStateOf((profile?.defaultForecastHorizon as? ForecastHorizon.Days)?.days ?: 30) }
    var reserveMode by remember(profileId) { mutableIntStateOf(when (profile?.reserve) { is ReservePolicy.Fixed -> 1; is ReservePolicy.PercentOfLiquidity -> 2; else -> 0 }) }
    var reserveText by remember(profileId) { mutableStateOf(when (val r = profile?.reserve) { is ReservePolicy.Fixed -> r.amount.toPlainString(); is ReservePolicy.PercentOfLiquidity -> java.math.BigDecimal(r.bp.value).movePointLeft(2).toPlainString(); else -> "" }) }
    val accounts = s.accounts.filter { profileId == null || it.profileId == profileId }
    val selected = remember(profileId) { mutableStateListOf<String>().apply { addAll(accounts.filter { it.type != AccountType.CREDIT_CARD }.map { it.id }) } }
    val reserve = when (reserveMode) {
        1 -> Money.parseOrNull(reserveText)?.let { ReservePolicy.Fixed(it) } ?: ReservePolicy.None
        2 -> runCatching { ReservePolicy.PercentOfLiquidity(BasisPoints.parsePercent(reserveText)) }.getOrDefault(ReservePolicy.None)
        else -> ReservePolicy.None
    }
    val (f, plan) = vm.forecast(profileId, ForecastHorizon.Days(days), selected.toSet(), reserve)
    val approved = s.approvedPlans.firstOrNull { it.profileId == profileId }

    ScreenColumn {
        ProfileSelector(vm, allowAll = false)
        SectionCard(stringResource(R.string.horizon), icon = AppIcons.calendar) {
            ChoiceRow(listOf(7, 14, 30, 60), days, { stringResource(R.string.days_n, it) }, { days = it })
        }
        SectionCard(stringResource(R.string.accounts_in_estimate), icon = AppIcons.bank) {
            accounts.forEach { a -> Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(a.id in selected, { if (it) selected.add(a.id) else selected.remove(a.id) }); Text(a.displayName, Modifier.weight(1f))
            } }
            if (f.accountsWithUnknownBalance.isNotEmpty()) Text(stringResource(R.string.unknown_balances_excluded, f.accountsWithUnknownBalance.size), style = MaterialTheme.typography.labelSmall, color = sem.review)
        }
        SectionCard(stringResource(R.string.reserve), icon = AppIcons.security) {
            ChoiceRow(listOf(0, 1, 2), reserveMode, { stringResource(when (it) { 0 -> R.string.reserve_none; 1 -> R.string.reserve_fixed; else -> R.string.reserve_percent }) }, { reserveMode = it })
            if (reserveMode != 0) OutlinedTextField(reserveText, { reserveText = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                suffix = { Text(if (reserveMode == 1) stringResource(R.string.currency_sar) else "%") }, modifier = Modifier.padding(top = 8.dp))
            Text(stringResource(R.string.override_note), style = MaterialTheme.typography.labelSmall)
        }
        SectionCard(stringResource(R.string.estimate), icon = AppIcons.forecast) {
            ForecastLine(stringResource(R.string.calculated_balances), f.accountBalances, Provenance.CALCULATED)
            ForecastLine(stringResource(R.string.obligations_in_horizon), -f.obligationsInHorizon, Provenance.FORECAST)
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            ForecastLine(stringResource(R.string.liquidity_after), f.liquidityAfterObligations, Provenance.FORECAST)
            ForecastLine(stringResource(R.string.reserve), -f.reserve, Provenance.PLANNED)
            ForecastLine(stringResource(R.string.available_for_savings), f.availableForSavings, Provenance.FORECAST)
            if (f.nonSarObligationsExcluded > 0) Text(stringResource(R.string.non_sar_excluded, f.nonSarObligationsExcluded), style = MaterialTheme.typography.labelSmall, color = sem.review)
            Text(stringResource(R.string.expected_salary_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(stringResource(R.string.suggested_plan), icon = AppIcons.goals.first) {
            if (plan.installments.isEmpty()) Text(stringResource(R.string.no_plan_items))
            plan.installments.forEach { i -> ForecastLine(names[i.goalId] ?: stringResource(R.string.goal), i.amount, Provenance.PLANNED) }
            if (plan.gap.isPositive) MessageBanner(stringResource(R.string.liquidity_gap, money(plan.gap), money(plan.totalTarget)), AppIcons.info, sem.forecast)
            approved?.let { a ->
                Spacer(Modifier.height(8.dp))
                val approvedTotal = Money(a.plan.split(';').filter { it.isNotBlank() }.sumOf { it.substringAfter('=').toLong() })
                Text(stringResource(R.string.approved_plan_compare, money(approvedTotal), money(plan.total)), style = MaterialTheme.typography.bodySmall)
                if (approvedTotal != plan.total) Text(stringResource(R.string.plan_changed_needs_approval), style = MaterialTheme.typography.labelSmall, color = sem.review)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { vm.approvePlan(profileId ?: "", f, plan, days) }, enabled = plan.installments.isNotEmpty() && profileId != null) { Text(stringResource(R.string.approve_plan)) }
            Text(stringResource(R.string.no_bank_transfer_note), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ForecastLine(label: String, v: Money, p: Provenance) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium); ProvenanceChip(p); Spacer(Modifier.width(8.dp))
        AmountText(v, style = MaterialTheme.typography.titleSmall)
    }
}

/** Internal transfers and their one-to-one matching state (§08). */
@Composable
fun TransfersScreen(vm: AppViewModel) {
    val s by vm.snapshot.collectAsStateWithLifecycle()
    val locale = currentLocale(); val sem = LocalSemantic.current
    ScreenColumn {
        val pairs = s.ledger?.transfers.orEmpty()
        if (pairs.isEmpty()) EmptyState(AppIcons.transfer, stringResource(R.string.no_transfers), stringResource(R.string.no_transfers_body))
        pairs.forEach { p ->
            val out = s.events.firstOrNull { it.id == p.outgoingId } ?: return@forEach
            SectionCard(null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(AppIcons.transfer, if (p.incomingId != null) sem.income else sem.review)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text((s.account(out.accountId)?.displayName ?: "—") + " → " + (s.account(out.counterpartyAccountId)?.displayName ?: "—"))
                        Text(Fmt.dateTime(out.executedAt, locale) + if (p.crossProfile) " · " + stringResource(R.string.cross_profile) else "", style = MaterialTheme.typography.labelSmall)
                        Text(stringResource(if (p.incomingId != null) R.string.incoming_confirmed else R.string.incoming_missing), style = MaterialTheme.typography.labelMedium,
                            color = if (p.incomingId != null) sem.income else sem.review)
                    }
                    out.amount?.let { AmountText(it) }
                }
            }
        }
    }
}
