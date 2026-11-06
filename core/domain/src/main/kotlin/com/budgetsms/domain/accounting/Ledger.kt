package com.budgetsms.domain.accounting

import com.budgetsms.domain.model.Account
import com.budgetsms.domain.model.AccountType
import com.budgetsms.domain.model.BankEvent
import com.budgetsms.domain.model.Category
import com.budgetsms.domain.model.CategoryLimit
import com.budgetsms.domain.model.CycleType
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.model.EventState
import com.budgetsms.domain.model.IncomeTreatment
import com.budgetsms.domain.model.LimitRule
import com.budgetsms.domain.model.Profile
import com.budgetsms.domain.model.RenewalMode
import com.budgetsms.domain.model.ReviewReason
import com.budgetsms.domain.model.TimeSource
import com.budgetsms.domain.money.Balance
import com.budgetsms.domain.money.Money
import java.time.Duration
import java.time.Instant

/** Account -> profile assignment with an effective date (spec §03). */
data class AccountAssignment(val accountId: String, val profileId: String, val effectiveFrom: Instant)

/** A user-initiated cycle opening (manual/scheduled). Never a deposit. */
data class ManualOpening(val profileId: String, val openedAt: Instant, val plannedBase: Money?)

/** A transfer pairing decided by the user (outgoing event id -> incoming event id). */
data class ApprovedTransferMatch(val outgoingId: String, val incomingId: String)

/** An approved budget settlement between profiles for a savings installment (Phase 4). */
data class BudgetSettlement(val id: String, val fromProfileId: String, val toProfileId: String, val amount: Money, val at: Instant)

data class LedgerInput(
    val profiles: List<Profile>,
    val accounts: List<Account>,
    val assignments: List<AccountAssignment> = emptyList(),
    val events: List<BankEvent>,
    val categories: List<Category> = emptyList(),
    val limits: List<CategoryLimit> = emptyList(),
    val manualOpenings: List<ManualOpening> = emptyList(),
    val approvedMatches: List<ApprovedTransferMatch> = emptyList(),
    val rejectedMatches: Set<Pair<String, String>> = emptySet(),
    val settlements: List<BudgetSettlement> = emptyList(),
    val now: Instant,
)

data class Cycle(
    val id: String,
    val profileId: String,
    val type: CycleType,
    val openedAt: Instant,
    val closedAt: Instant?,
    val openingEventId: String?,
    val percentBase: Money?,
    val plannedBudget: Money?,
) { val isOpen get() = closedAt == null }

data class CategoryStatus(
    val categoryId: String,
    val parentId: String?,
    val limit: Money?,
    val spent: Money,
    val overspent: Boolean,
)

data class CycleBudget(
    val cycle: Cycle,
    val planned: Money?,
    val funded: Money,
    val incomeOutsideBudget: Money,
    val earmarkedForSavings: Money,
    val reallocatedIn: Money,
    val reallocatedOut: Money,
    val spent: Money,
    val underReview: Money,
    val remaining: Money,
    val unallocated: Money,
    val categories: List<CategoryStatus>,
    val limitWarnings: List<String>,
) {
    val isProvisional get() = underReview.isPositive
}

enum class AlertType { BUDGET_NEGATIVE, LIMIT_EXCEEDED, CHILD_LIMITS_EXCEED_PARENT, TRANSFER_INCOMING_MISSING, BALANCE_MISMATCH, INSTALLMENT_UNFUNDED, OBLIGATION_OVERDUE, LIQUIDITY_THRESHOLD }

data class Alert(val key: String, val type: AlertType, val subjectId: String, val amount: Money? = null)

data class ReviewItem(val eventId: String, val reasons: Set<ReviewReason>, val candidates: List<String> = emptyList())

data class TransferPair(val outgoingId: String, val incomingId: String?, val crossProfile: Boolean, val approvedByUser: Boolean)

data class LedgerResult(
    val cycles: List<Cycle>,
    val assignment: Map<String, String?>,
    val budgets: Map<String, CycleBudget>,
    val balances: Map<String, Balance>,
    val transfers: List<TransferPair>,
    val reviews: List<ReviewItem>,
    val alerts: List<Alert>,
) {
    fun openCycle(profileId: String) = cycles.lastOrNull { it.profileId == profileId && it.isOpen }
    fun budgetOf(profileId: String) = openCycle(profileId)?.let { budgets[it.id] }
}

/**
 * Pure, deterministic accounting engine (spec §02–§09). Every result is derived from confirmed
 * bank events plus user decisions, so recalculation is idempotent by construction.
 */
class LedgerEngine(private val boundaryWindow: Duration = Duration.ofHours(12)) {

    fun compute(input: LedgerInput): LedgerResult {
        val accounts = input.accounts.associateBy { it.id }
        val events = input.events.sortedWith(compareBy({ it.executedAt }, { it.id }))
        val reversedIds = events.filter { it.kind == EventKind.REVERSAL && it.isConfirmed }.mapNotNull { it.correctsEventId }.toSet() +
            events.filter { it.state == EventState.REVERSED }.map { it.id }
        val effective = events.filter { it.isConfirmed && it.id !in reversedIds }
        fun profileOf(accountId: String?, at: Instant): String? {
            if (accountId == null) return null
            return input.assignments.filter { it.accountId == accountId && it.effectiveFrom <= at }
                .maxByOrNull { it.effectiveFrom }?.profileId ?: accounts[accountId]?.profileId
        }

        val reviews = mutableListOf<ReviewItem>()
        val alerts = linkedMapOf<String, Alert>()
        fun alert(a: Alert) { alerts.putIfAbsent(a.key, a) }

        events.filter { it.state == EventState.UNDER_REVIEW || it.state == EventState.PENDING_FX }
            .forEach { reviews += ReviewItem(it.id, it.reviewReasons) }

        // ---- transfer matching (§08) ----
        val transfers = matchTransfers(effective, accounts, input, ::profileOf, reviews, ::alert)
        val matchedIncoming = transfers.mapNotNull { it.incomingId }.toSet()

        // ---- cycles (§03) ----
        val cycles = input.profiles.flatMap { buildCycles(it, effective, input, ::profileOf, accounts) }
        val byProfile = cycles.groupBy { it.profileId }
        fun cycleAt(profileId: String?, at: Instant): Cycle? =
            byProfile[profileId]?.lastOrNull { it.openedAt <= at && (it.closedAt == null || at < it.closedAt) }

        val assignment = linkedMapOf<String, String?>()
        for (e in events) {
            val pid = profileOf(e.accountId, e.executedAt)
            val c = cycleAt(pid, e.executedAt)
            assignment[e.id] = c?.id
            if (c != null && e.timeSource == TimeSource.RECEIPT) {
                val boundaries = byProfile[pid].orEmpty().flatMap { listOfNotNull(it.openedAt, it.closedAt) }
                if (boundaries.any { Duration.between(it, e.executedAt).abs() <= boundaryWindow })
                    reviews += ReviewItem(e.id, setOf(ReviewReason.CYCLE_BOUNDARY))
            }
        }
        // Refunds correct the original transaction's cycle and category (§06, T09).
        val byId = events.associateBy { it.id }
        fun effectiveCycleOf(e: BankEvent): String? =
            if (e.kind == EventKind.REFUND && e.correctsEventId != null) assignment[e.correctsEventId] ?: assignment[e.id] else assignment[e.id]
        fun effectiveCategoryOf(e: BankEvent): String? =
            if (e.kind == EventKind.REFUND && e.correctsEventId != null) byId[e.correctsEventId]?.categoryId ?: e.categoryId else e.categoryId

        // ---- budgets (§07) ----
        val budgets = linkedMapOf<String, CycleBudget>()
        for (c in cycles) {
            var funded = Money.ZERO; var outside = Money.ZERO; var earmarked = Money.ZERO
            var reIn = Money.ZERO; var reOut = Money.ZERO; var spent = Money.ZERO; var review = Money.ZERO
            val catSpent = mutableMapOf<String, Money>()
            fun addSpend(cat: String?, m: Money) { spent += m; val k = cat ?: UNCATEGORIZED; catSpent[k] = (catSpent[k] ?: Money.ZERO) + m }

            for (e in effective) {
                val cid = effectiveCycleOf(e) ?: continue
                if (cid != c.id) continue
                val acc = accounts[e.accountId]
                val amt = e.sarAmount
                when (e.kind) {
                    EventKind.SALARY, EventKind.ALLOWANCE -> funded += amt
                    EventKind.INCOMING_TRANSFER -> {
                        if (e.id in matchedIncoming || e.counterpartyAccountId != null || acc?.type == AccountType.CREDIT_CARD) Unit // internal: never income
                        else when (e.incomeTreatment) {
                            IncomeTreatment.FUND_BUDGET -> funded += amt
                            IncomeTreatment.OUTSIDE_BUDGET -> outside += amt
                            IncomeTreatment.EARMARK_SAVINGS -> earmarked += amt
                            null -> if (e.id != c.openingEventId) reviews += ReviewItem(e.id, setOf(ReviewReason.INCOME_TREATMENT_NEEDED))
                        }
                        if (e.id == c.openingEventId && e.incomeTreatment == null) funded += amt
                    }
                    EventKind.REFUND -> addSpend(effectiveCategoryOf(e), -amt)
                    EventKind.PURCHASE_DEBIT, EventKind.ATM_WITHDRAWAL, EventKind.FEE ->
                        if (acc?.type != AccountType.CREDIT_CARD) addSpend(e.categoryId, amt)
                    EventKind.CARD_REPAYMENT -> if (acc?.type != AccountType.CREDIT_CARD) addSpend(e.categoryId ?: CARD_REPAYMENT_CATEGORY, amt)
                    EventKind.OUTGOING_TRANSFER -> {
                        val target = e.counterpartyAccountId?.let { accounts[it] }
                        when {
                            target == null -> addSpend(e.categoryId, amt)                       // external party: expense
                            target.type == AccountType.CREDIT_CARD -> addSpend(CARD_REPAYMENT_CATEGORY, amt) // repayment exception
                            else -> Unit                                                          // own account: handled below
                        }
                    }
                    EventKind.PURCHASE_CREDIT_CARD, EventKind.REVERSAL, EventKind.FAILED, EventKind.BALANCE_ONLY, EventKind.UNKNOWN -> Unit
                }
                if (e.kind != EventKind.FEE && e.fee != null && e.fee.isPositive && acc?.type != AccountType.CREDIT_CARD) addSpend(FEES_CATEGORY, e.fee)
            }
            // Inter-profile reallocation happens once, at the confirmed outgoing leg (§08, T05, T06).
            for (t in transfers.filter { it.crossProfile }) {
                val out = byId[t.outgoingId] ?: continue
                val src = profileOf(out.accountId, out.executedAt); val dst = profileOf(out.counterpartyAccountId, out.executedAt)
                if (src == c.profileId && cycleAt(src, out.executedAt)?.id == c.id) reOut += out.sarAmount
                if (dst == c.profileId && cycleAt(dst, out.executedAt)?.id == c.id) reIn += out.sarAmount
            }
            for (s in input.settlements) {
                if (s.fromProfileId == c.profileId && cycleAt(s.fromProfileId, s.at)?.id == c.id) reOut += s.amount
                if (s.toProfileId == c.profileId && cycleAt(s.toProfileId, s.at)?.id == c.id) reIn += s.amount
            }
            for (e in events) if ((e.state == EventState.UNDER_REVIEW || e.state == EventState.PENDING_FX) && assignment[e.id] == c.id && e.kind.direction != EventKind.Direction.CREDIT)
                review += e.amount ?: Money.ZERO

            val fundedTotal = funded + reIn - reOut
            val remaining = fundedTotal - spent
            val (cats, warnings) = categoryStatus(c, input, catSpent)
            val topAllocated = Money.sum(cats.filter { it.parentId == null }.mapNotNull { it.limit })
            val budget = CycleBudget(c, c.plannedBudget, fundedTotal, outside, earmarked, reIn, reOut, spent, review, remaining,
                fundedTotal - topAllocated, cats, warnings)
            budgets[c.id] = budget
            if (remaining.isNegative) alert(Alert("neg:${c.id}", AlertType.BUDGET_NEGATIVE, c.id, remaining))
            cats.filter { it.overspent }.forEach { alert(Alert("limit:${c.id}:${it.categoryId}", AlertType.LIMIT_EXCEEDED, it.categoryId, it.spent)) }
            warnings.forEach { alert(Alert("childlimits:${c.id}:$it", AlertType.CHILD_LIMITS_EXCEED_PARENT, it)) }
        }

        // ---- balances (§09) ----
        val balances = input.accounts.associate { it.id to balanceOf(it, effective, reviews, ::alert) }

        return LedgerResult(cycles, assignment, budgets, balances, transfers, reviews.distinctBy { it.eventId to it.reasons }, alerts.values.toList())
    }

    private fun buildCycles(p: Profile, effective: List<BankEvent>, input: LedgerInput,
                            profileOf: (String?, Instant) -> String?, accounts: Map<String, Account>): List<Cycle> {
        data class Opening(val at: Instant, val eventId: String?, val base: Money?, val planned: Money?, val type: CycleType)
        val openings = mutableListOf<Opening>()
        input.manualOpenings.filter { it.profileId == p.id }.forEach { openings += Opening(it.openedAt, null, it.plannedBase, it.plannedBase, CycleType.MANUAL) }
        val mine = effective.filter { profileOf(it.accountId, it.executedAt) == p.id }
        when (p.cycleType) {
            CycleType.SALARY -> mine.filter { it.kind == EventKind.SALARY }
                .forEach { openings += Opening(it.executedAt, it.id, it.sarAmount, null, CycleType.SALARY) }
            CycleType.TRANSFER -> {
                val qualifying = mine.filter {
                    it.kind == EventKind.INCOMING_TRANSFER && it.counterpartyAccountId == null &&
                        it.incomeTreatment == IncomeTreatment.FUND_BUDGET &&
                        (p.minimumOpeningTransfer == null || it.sarAmount >= p.minimumOpeningTransfer)
                }
                val used = if (p.renewalMode == RenewalMode.NEXT_APPROVED_TRANSFER) qualifying else qualifying.take(1)
                used.forEach { openings += Opening(it.executedAt, it.id, it.sarAmount, null, CycleType.TRANSFER) }
            }
            CycleType.SCHEDULED -> {
                val first = openings.minByOrNull { it.at }
                val every = p.scheduledEveryDays
                if (first != null && every != null && every > 0) {
                    var t = first.at.plus(Duration.ofDays(every.toLong()))
                    while (t <= input.now) { openings += Opening(t, null, first.base, first.planned, CycleType.SCHEDULED); t = t.plus(Duration.ofDays(every.toLong())) }
                }
            }
            CycleType.MANUAL -> Unit
        }
        val sorted = openings.sortedBy { it.at }.distinctBy { it.at }
        return sorted.mapIndexed { i, o ->
            val next = sorted.getOrNull(i + 1)
            Cycle("${p.id}:${o.at.epochSecond}:${o.eventId ?: "m"}", p.id, o.type, o.at, next?.at, o.eventId, o.base, o.planned)
        }
    }

    private fun categoryStatus(c: Cycle, input: LedgerInput, catSpent: Map<String, Money>): Pair<List<CategoryStatus>, List<String>> {
        val cats = input.categories.associateBy { it.id }
        val limits = input.limits.filter { it.profileId == c.profileId }.associateBy { it.categoryId }
        fun limitAmount(id: String): Money? = when (val r = limits[id]?.rule) {
            is LimitRule.Fixed -> r.amount
            is LimitRule.Percent -> c.percentBase?.percent(r.bp) // based on first salary/opening only (T04)
            null -> null
        }
        val ids = (limits.keys + catSpent.keys).toSortedSet()
        // Parent aggregates its own and its children's spending; it monitors a cap, it never adds money (T11).
        fun spentFor(id: String): Money = (catSpent[id] ?: Money.ZERO) + Money.sum(cats.values.filter { it.parentId == id }.map { catSpent[it.id] ?: Money.ZERO })
        val statuses = ids.map { id ->
            val lim = limitAmount(id); val s = if (cats[id]?.parentId == null) spentFor(id) else catSpent[id] ?: Money.ZERO
            CategoryStatus(id, cats[id]?.parentId, lim, s, lim != null && s > lim)
        }
        val warnings = cats.values.filter { it.parentId == null }.mapNotNull { parent ->
            val pl = limitAmount(parent.id) ?: return@mapNotNull null
            val childSum = Money.sum(cats.values.filter { it.parentId == parent.id }.mapNotNull { limitAmount(it.id) })
            if (childSum > pl) parent.id else null
        }
        return statuses to warnings
    }

    private fun matchTransfers(
        effective: List<BankEvent>, accounts: Map<String, Account>, input: LedgerInput,
        profileOf: (String?, Instant) -> String?, reviews: MutableList<ReviewItem>, alert: (Alert) -> Unit,
    ): List<TransferPair> {
        val outs = effective.filter { it.kind == EventKind.OUTGOING_TRANSFER && it.counterpartyAccountId != null &&
            accounts[it.counterpartyAccountId]?.type != AccountType.CREDIT_CARD }
        val incomings = effective.filter { it.kind == EventKind.INCOMING_TRANSFER }
        val used = mutableSetOf<String>()
        val result = mutableListOf<TransferPair>()
        val approved = input.approvedMatches.associateBy { it.outgoingId }
        approved.values.forEach { used += it.incomingId }
        for (o in outs) {
            val cross = profileOf(o.accountId, o.executedAt) != profileOf(o.counterpartyAccountId, o.executedAt)
            val userMatch = approved[o.id]
            if (userMatch != null) { result += TransferPair(o.id, userMatch.incomingId, cross, true); continue }
            val window = incomings.filter {
                it.id !in used && (o.id to it.id) !in input.rejectedMatches &&
                    (it.accountId == null || it.accountId == o.counterpartyAccountId) &&
                    !it.executedAt.isBefore(o.executedAt.minus(Duration.ofMinutes(10))) &&
                    it.executedAt <= o.executedAt.plus(Duration.ofHours(72))
            }
            val exact = window.filter { it.amount == o.amount }
            when {
                exact.size == 1 && window.count { it.amount == o.amount } == 1 && !isTied(o, exact[0], outs, used) -> {
                    used += exact[0].id; result += TransferPair(o.id, exact[0].id, cross, false)
                }
                exact.size > 1 || (exact.size == 1 && isTied(o, exact[0], outs, used)) -> {
                    reviews += ReviewItem(o.id, setOf(ReviewReason.TRANSFER_TIE), exact.map { it.id })
                    result += TransferPair(o.id, null, cross, false)
                }
                window.isNotEmpty() -> {
                    reviews += ReviewItem(o.id, setOf(ReviewReason.TRANSFER_AMOUNT_MISMATCH), window.map { it.id })
                    result += TransferPair(o.id, null, cross, false)
                }
                else -> {
                    result += TransferPair(o.id, null, cross, false)
                    if (Duration.between(o.executedAt, input.now) >= Duration.ofHours(24))
                        alert(Alert("missing-in:${o.id}", AlertType.TRANSFER_INCOMING_MISSING, o.id, o.amount))
                }
            }
        }
        return result
    }

    /** Another unmatched outgoing with the same amount competing for the same incoming leg -> tie (T07). */
    private fun isTied(o: BankEvent, incoming: BankEvent, outs: List<BankEvent>, used: Set<String>): Boolean =
        outs.any { it.id != o.id && it.amount == o.amount && it.counterpartyAccountId == o.counterpartyAccountId &&
            !incoming.executedAt.isBefore(it.executedAt.minus(Duration.ofMinutes(10))) && incoming.executedAt <= it.executedAt.plus(Duration.ofHours(72)) }

    private fun balanceOf(a: Account, effective: List<BankEvent>, reviews: MutableList<ReviewItem>, alert: (Alert) -> Unit): Balance {
        val mine = effective.filter { it.accountId == a.id }
        var ref: Money? = null; var refAt: Instant? = null; var calc: Money? = null; var net = Money.ZERO
        for (e in mine) {
            val delta = movement(e, a)
            net += delta
            if (e.balanceAfter != null) {
                val expected = calc?.plus(delta)
                if (expected != null && expected != e.balanceAfter && a.type != AccountType.CREDIT_CARD) {
                    reviews += ReviewItem(e.id, setOf(ReviewReason.BALANCE_MISMATCH))
                    alert(Alert("balance:${e.id}", AlertType.BALANCE_MISMATCH, a.id, e.balanceAfter - expected))
                }
                ref = e.balanceAfter; refAt = e.executedAt; calc = e.balanceAfter
            } else if (calc != null) calc += delta
        }
        return if (ref != null && calc != null && refAt != null) Balance.Known(if (a.type == AccountType.CREDIT_CARD) ref else calc, ref, refAt)
        else Balance.Unknown(net)
    }

    private fun movement(e: BankEvent, a: Account): Money {
        val amt = e.sarAmount; val fee = if (e.kind == EventKind.FEE) Money.ZERO else e.fee ?: Money.ZERO
        return when (e.kind.direction) {
            EventKind.Direction.CREDIT -> amt
            EventKind.Direction.DEBIT -> -(amt + fee)
            else -> Money.ZERO
        }.let { if (a.type == AccountType.CREDIT_CARD) Money.ZERO else it }
    }

    companion object {
        const val UNCATEGORIZED = "uncategorized"
        const val FEES_CATEGORY = "bank_fees"
        const val CARD_REPAYMENT_CATEGORY = "card_repayment"
    }
}
