package com.budgetsms.domain.forecast

import com.budgetsms.domain.model.ForecastHorizon
import com.budgetsms.domain.model.ReservePolicy
import com.budgetsms.domain.money.Balance
import com.budgetsms.domain.money.BasisPoints
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.obligations.OccurrenceState
import com.budgetsms.domain.obligations.OccurrenceStatus
import java.time.LocalDate

data class ForecastInput(
    val today: LocalDate,
    val horizon: ForecastHorizon,
    val balances: Map<String, Balance>,
    val selectedAccountIds: Set<String>,
    val occurrences: List<OccurrenceStatus>,
    val selectedObligationIds: Set<String>?,
    val reserve: ReservePolicy,
)

data class ForecastResult(
    val horizonEnd: LocalDate,
    /** Calculated balances of selected accounts with a known reference. Never reduced by obligations. */
    val accountBalances: Money,
    val accountsWithUnknownBalance: List<String>,
    val obligationsInHorizon: Money,
    val nonSarObligationsExcluded: Int,
    val liquidityAfterObligations: Money,
    val reserve: Money,
    val availableForSavings: Money,
)

/** Liquidity estimate separate from bank balances (spec §12, §15). An expected salary is never income here. */
object LiquidityForecaster {
    fun horizonEnd(today: LocalDate, h: ForecastHorizon): LocalDate = when (h) {
        is ForecastHorizon.Days -> today.plusDays(h.days.toLong())
        is ForecastHorizon.UntilExpectedSalary -> h.expected
    }

    fun forecast(i: ForecastInput): ForecastResult {
        val end = horizonEnd(i.today, i.horizon)
        var total = Money.ZERO; val unknown = mutableListOf<String>()
        for (id in i.selectedAccountIds.sorted()) when (val b = i.balances[id]) {
            is Balance.Known -> total += b.amount
            else -> unknown += id
        }
        val relevant = i.occurrences.filter {
            (i.selectedObligationIds == null || it.occurrence.obligationId in i.selectedObligationIds) &&
                it.state !in setOf(OccurrenceState.PAID, OccurrenceState.CANCELLED, OccurrenceState.PAUSED) &&
                !it.occurrence.dueDate.isAfter(end)
        }
        val sar = relevant.filter { it.occurrence.currency == "SAR" }
        // Only the unpaid remainder is forecast, so a payment SMS moves money from forecast to actual once (T23).
        val obligations = Money.sum(sar.map { it.remaining })
        val after = total - obligations
        val reserve = when (val r = i.reserve) {
            ReservePolicy.None -> Money.ZERO
            is ReservePolicy.Fixed -> r.amount
            is ReservePolicy.PercentOfLiquidity -> after.coerceAtLeast(Money.ZERO).percent(r.bp)
        }
        return ForecastResult(end, total, unknown, obligations, relevant.size - sar.size, after, reserve,
            (after - reserve).coerceAtLeast(Money.ZERO))
    }
}

data class PlannedInstallment(val goalId: String, val amount: Money)
data class SavingsPlan(val installments: List<PlannedInstallment>, val totalTarget: Money, val gap: Money) {
    val total get() = Money.sum(installments.map { it.amount })
}

data class OpenGoal(val goalId: String, val remaining: Money, val order: Int)

object SavingsPlanner {
    /** Oldest-first distribution of shared liquidity; the full targets are retained and the gap shown (T16). */
    fun propose(goals: List<OpenGoal>, available: Money): SavingsPlan {
        var left = available
        val items = goals.sortedBy { it.order }.mapNotNull { g ->
            val take = g.remaining.coerceAtMost(left).coerceAtLeast(Money.ZERO)
            left -= take
            if (take.isPositive) PlannedInstallment(g.goalId, take) else null
        }
        val target = Money.sum(goals.map { it.remaining })
        return SavingsPlan(items, target, (target - Money.sum(items.map { it.amount })).coerceAtLeast(Money.ZERO))
    }

    data class Comparison(val approved: SavingsPlan, val proposed: SavingsPlan, val changed: Boolean, val needsRenewedApproval: Boolean)

    /** The approved plan is never changed implicitly; a revised proposal is compared for approval (T14). */
    fun compare(approved: SavingsPlan, proposed: SavingsPlan) =
        Comparison(approved, proposed, approved.installments != proposed.installments, approved.installments != proposed.installments)
}

data class ThresholdPolicy(val absoluteMinimum: Money? = null, val dropPercent: BasisPoints? = null, val smallChange: Money = Money.sar(50))

sealed interface LiquidityAlert { val key: String
    data class BelowMinimum(override val key: String, val liquidity: Money) : LiquidityAlert
    data class SharpDrop(override val key: String, val from: Money, val to: Money) : LiquidityAlert
    data class InstallmentUnfunded(override val key: String, val installment: Money, val available: Money) : LiquidityAlert
    data class Summary(override val key: String, val changes: Int, val net: Money) : LiquidityAlert
}

object LiquidityAlerts {
    /** Emits each alert key once; small changes are aggregated into a single summary. */
    fun evaluate(previous: Money, current: Money, nextInstallment: Money?, policy: ThresholdPolicy, alreadySent: Set<String>, eventKey: String, smallChanges: List<Money> = emptyList()): List<LiquidityAlert> {
        val out = mutableListOf<LiquidityAlert>()
        if (nextInstallment != null && current < nextInstallment) out += LiquidityAlert.InstallmentUnfunded("unfunded:$eventKey", nextInstallment, current)
        policy.absoluteMinimum?.let { if (current < it) out += LiquidityAlert.BelowMinimum("min:$eventKey", current) }
        policy.dropPercent?.let { bp -> if (previous.isPositive && previous - current > previous.percent(bp)) out += LiquidityAlert.SharpDrop("drop:$eventKey", previous, current) }
        val small = smallChanges.filter { it.abs() < policy.smallChange }
        if (small.size > 1) out += LiquidityAlert.Summary("summary:$eventKey", small.size, Money.sum(small))
        return out.filter { it.key !in alreadySent }
    }
}
