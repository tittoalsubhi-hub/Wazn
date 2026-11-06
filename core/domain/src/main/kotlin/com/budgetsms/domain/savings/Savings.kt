package com.budgetsms.domain.savings

import com.budgetsms.domain.accounting.BudgetSettlement
import com.budgetsms.domain.accounting.CycleBudget
import com.budgetsms.domain.model.CycleType
import com.budgetsms.domain.money.Money
import java.time.Instant

/** A savings request derived from a closed confirmed-salary cycle surplus (spec §10). Not bank money. */
data class SavingsRequest(
    val cycleId: String,
    val profileId: String,
    val target: Money,
    val provisional: Boolean,
    val unresolvedAmount: Money,
    val approvalSuspended: Boolean,
)

object SavingsEligibility {
    /**
     * @param activationAt when the user started using the app. Cycles closed before it never create requests.
     * @param approvedProvisional cycles whose provisional surplus the user explicitly approved after a warning.
     */
    fun requestFor(b: CycleBudget, activationAt: Instant, approvedProvisional: Set<String> = emptySet()): SavingsRequest? {
        val c = b.cycle
        if (c.type != CycleType.SALARY) return null                 // other cycle surpluses: reports only
        val closed = c.closedAt ?: return null
        if (!closed.isAfter(activationAt)) return null               // historical cycle: no retroactive request
        if (!b.remaining.isPositive) return null
        val provisional = b.isProvisional
        return SavingsRequest(c.id, c.profileId, b.remaining, provisional, b.underReview,
            approvalSuspended = provisional && c.id !in approvedProvisional)
    }
}

data class SavingsGoal(
    val id: String,
    val profileId: String,
    val requestCycleId: String?,
    val target: Money,
    val createdAt: Instant,
    /** Must be chosen manually by the user; never defaulted silently (spec §11). */
    val destinationAccountId: String?,
)

/** A bank-verified own-account transfer to a savings account (outgoing and incoming legs matched). */
data class VerifiedSavingsTransfer(
    val transferId: String,
    val amount: Money,
    val sourceAccountId: String,
    val sourceProfileId: String,
    val destinationAccountId: String,
    val at: Instant,
    val incomingMatched: Boolean,
)

enum class SettlementDecision { NOT_NEEDED, PROPOSED, APPROVED, REJECTED_ALLOCATE_WITHOUT_SETTLEMENT, REJECTED_LEAVE_PENDING }

data class InstallmentAllocation(
    val transferId: String,
    val goalId: String,
    val amount: Money,
    val userConfirmed: Boolean,
    val settlement: SettlementDecision,
)

data class GoalStatus(val goal: SavingsGoal, val confirmedSaved: Money, val remaining: Money, val additionalSavings: Money, val pendingAllocation: Money)
data class TransferStatus(val transfer: VerifiedSavingsTransfer, val allocated: Money, val unallocated: Money)

class SavingsEngine {
    fun validate(transfers: List<VerifiedSavingsTransfer>, allocations: List<InstallmentAllocation>): List<String> {
        val byId = transfers.associateBy { it.transferId }
        return allocations.groupBy { it.transferId }.mapNotNull { (tid, list) ->
            val t = byId[tid] ?: return@mapNotNull "allocation for unknown transfer $tid"
            if (Money.sum(list.map { it.amount }) > t.amount) "allocations exceed transfer $tid" else null
        } + allocations.groupBy { it.transferId to it.goalId }.filter { it.value.size > 1 }.map { "duplicate allocation ${it.key}" }
    }

    /** Counts an allocation as saved only with both legs matched, user confirmation, and a settled or waived settlement. */
    private fun counts(a: InstallmentAllocation, t: VerifiedSavingsTransfer?): Boolean =
        t != null && t.incomingMatched && a.userConfirmed &&
            a.settlement in setOf(SettlementDecision.NOT_NEEDED, SettlementDecision.APPROVED, SettlementDecision.REJECTED_ALLOCATE_WITHOUT_SETTLEMENT)

    fun status(goals: List<SavingsGoal>, transfers: List<VerifiedSavingsTransfer>, allocations: List<InstallmentAllocation>): Pair<List<GoalStatus>, List<TransferStatus>> {
        require(validate(transfers, allocations).isEmpty()) { validate(transfers, allocations).joinToString() }
        val tById = transfers.associateBy { it.transferId }
        val gs = goals.map { g ->
            val mine = allocations.filter { it.goalId == g.id }
            val saved = Money.sum(mine.filter { counts(it, tById[it.transferId]) }.map { it.amount })
            val pending = Money.sum(mine.filterNot { counts(it, tById[it.transferId]) }.map { it.amount })
            GoalStatus(g, saved, (g.target - saved).coerceAtLeast(Money.ZERO), (saved - g.target).coerceAtLeast(Money.ZERO), pending)
        }
        val ts = transfers.map { t ->
            val alloc = Money.sum(allocations.filter { it.transferId == t.transferId }.map { it.amount })
            TransferStatus(t, alloc, t.amount - alloc)
        }
        return gs to ts
    }

    /** Oldest goal first; the user may reorder or split before approving. Never exceeds the transfer amount. */
    fun proposeAllocation(t: VerifiedSavingsTransfer, open: List<GoalStatus>, alreadyAllocated: Money = Money.ZERO): List<InstallmentAllocation> {
        var left = t.amount - alreadyAllocated
        val out = mutableListOf<InstallmentAllocation>()
        for (g in open.filter { it.remaining.isPositive }.sortedBy { it.goal.createdAt }) {
            if (!left.isPositive) break
            val take = g.remaining.coerceAtMost(left)
            val settlement = if (g.goal.profileId != t.sourceProfileId) SettlementDecision.PROPOSED else SettlementDecision.NOT_NEEDED
            out += InstallmentAllocation(t.transferId, g.goal.id, take, userConfirmed = false, settlement = settlement)
            left -= take
        }
        return out
    }

    /** Settlement is produced only from an explicit approval; rejection never changes budget allocations (T12). */
    fun settlementFor(a: InstallmentAllocation, t: VerifiedSavingsTransfer, goal: SavingsGoal): BudgetSettlement? =
        if (a.settlement == SettlementDecision.APPROVED && goal.profileId != t.sourceProfileId)
            BudgetSettlement("settle:${a.transferId}:${a.goalId}", fromProfileId = goal.profileId, toProfileId = t.sourceProfileId, amount = a.amount, at = t.at)
        else null

    /** Recalculated surplus updates the target; confirmed transfers are never altered (spec §10). */
    fun retarget(goal: SavingsGoal, newTarget: Money): SavingsGoal = goal.copy(target = newTarget)
}
