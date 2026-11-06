package com.budgetsms.domain.obligations

import com.budgetsms.domain.model.BankEvent
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.money.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class ObligationType { MONTHLY, ONE_TIME }

/**
 * Policy for due days 29-31 in shorter months. The reference leaves this open (spec §15),
 * so the user must choose explicitly; there is no hidden default.
 */
enum class MonthEndPolicy { LAST_DAY_OF_MONTH, FIRST_DAY_OF_NEXT_MONTH }

class PolicyRequiredException(msg: String) : IllegalStateException(msg)

data class OccurrenceOverride(val amount: Money? = null, val dueDate: LocalDate? = null, val cancelled: Boolean = false)
data class ProfileChange(val effectiveFrom: LocalDate, val profileId: String, val includePaidHistory: Boolean = false)
data class PausePeriod(val from: LocalDate, val until: LocalDate?)

data class Obligation(
    val id: String,
    val name: String,
    val description: String = "",
    val categoryId: String? = null,
    val profileId: String,
    val amount: Money,
    val currency: String = "SAR",
    val type: ObligationType,
    val startDate: LocalDate,
    val dayOfMonth: Int = startDate.dayOfMonth,
    val occurrenceCount: Int? = null,
    val endDate: LocalDate? = null,
    val monthEndPolicy: MonthEndPolicy? = null,
    val pauses: List<PausePeriod> = emptyList(),
    val endedAt: LocalDate? = null,
    val overrides: Map<Int, OccurrenceOverride> = emptyMap(),
    val profileChanges: List<ProfileChange> = emptyList(),
    val remindDaysBefore: Int = 3,
    val notes: String = "",
    val version: Int = 1,
)

data class Occurrence(
    val id: String,
    val obligationId: String,
    val index: Int,
    val dueDate: LocalDate,
    val expected: Money,
    val currency: String,
    val profileId: String,
    val paused: Boolean,
    val cancelled: Boolean,
)

enum class OccurrenceState { UPCOMING, DUE, OVERDUE, PARTIALLY_PAID, PAID, CANCELLED, PAUSED }

data class ObligationPayment(val eventId: String, val occurrenceId: String, val amount: Money)

data class OccurrenceStatus(val occurrence: Occurrence, val paid: Money, val remaining: Money, val state: OccurrenceState, val daysToDue: Long)

object ObligationScheduler {
    /** Deterministic, idempotent generation: same obligation -> same occurrence ids (T17, T18). */
    fun generate(o: Obligation, horizonMonths: Int = 24): List<Occurrence> {
        if (o.type == ObligationType.ONE_TIME) return listOf(occ(o, 0, o.startDate))
        if (o.dayOfMonth >= 29 && o.monthEndPolicy == null)
            throw PolicyRequiredException("Choose a month-end policy for day ${o.dayOfMonth}")
        val limit = o.occurrenceCount ?: Int.MAX_VALUE
        val out = mutableListOf<Occurrence>()
        var ym = YearMonth.from(o.startDate)
        var i = 0
        val lastMonth = YearMonth.from(o.startDate).plusMonths(horizonMonths.toLong())
        while (i < limit && !ym.isAfter(lastMonth)) {
            val due = dueIn(ym, o.dayOfMonth, o.monthEndPolicy)
            if (o.endDate != null && due.isAfter(o.endDate)) break
            if (o.endedAt != null && !due.isBefore(o.endedAt)) break
            if (!due.isBefore(o.startDate)) { out += occ(o, i, due); i++ }
            ym = ym.plusMonths(1)
        }
        return out
    }

    fun dueIn(ym: YearMonth, day: Int, policy: MonthEndPolicy?): LocalDate =
        if (day <= ym.lengthOfMonth()) ym.atDay(day) else when (policy) {
            MonthEndPolicy.LAST_DAY_OF_MONTH -> ym.atEndOfMonth()
            MonthEndPolicy.FIRST_DAY_OF_NEXT_MONTH -> ym.plusMonths(1).atDay(1)
            null -> throw PolicyRequiredException("month-end policy required")
        }

    private fun occ(o: Obligation, index: Int, baseDue: LocalDate): Occurrence {
        val ov = o.overrides[index]
        val due = ov?.dueDate ?: baseDue
        val profile = o.profileChanges.filter { !due.isBefore(it.effectiveFrom) }.maxByOrNull { it.effectiveFrom }?.profileId ?: o.profileId
        return Occurrence("${o.id}#$index", o.id, index, due, ov?.amount ?: o.amount, o.currency, profile,
            paused = o.pauses.any { !due.isBefore(it.from) && (it.until == null || due.isBefore(it.until)) },
            cancelled = ov?.cancelled == true)
    }

    /** Applies a profile change prospectively; paid occurrences keep their profile unless explicitly included (T22). */
    fun changeProfile(o: Obligation, change: ProfileChange, statuses: List<OccurrenceStatus>): Obligation {
        val firstUnpaid = statuses.filter { it.occurrence.obligationId == o.id && it.state != OccurrenceState.PAID }
            .minByOrNull { it.occurrence.dueDate }?.occurrence?.dueDate
        val effective = if (change.includePaidHistory) change.effectiveFrom
            else listOfNotNull(change.effectiveFrom, firstUnpaid).maxOrNull()!!
        return o.copy(profileChanges = o.profileChanges + change.copy(effectiveFrom = effective), version = o.version + 1)
    }
}

class PaymentLinkException(msg: String) : IllegalArgumentException(msg)

object ObligationLedger {
    /** One bank movement can pay only one occurrence (T19, T21). */
    fun link(existing: List<ObligationPayment>, new: ObligationPayment, event: BankEvent): List<ObligationPayment> {
        if (!event.isConfirmed) throw PaymentLinkException("only confirmed SMS transactions can pay an obligation")
        if (existing.any { it.eventId == new.eventId }) throw PaymentLinkException("bank movement already linked")
        if (event.amount == null || new.amount > event.amount) throw PaymentLinkException("payment exceeds bank movement")
        return existing + new
    }

    fun status(occ: Occurrence, payments: List<ObligationPayment>, today: LocalDate): OccurrenceStatus {
        val paid = Money.sum(payments.filter { it.occurrenceId == occ.id }.map { it.amount })
        val remaining = (occ.expected - paid).coerceAtLeast(Money.ZERO)
        val days = ChronoUnit.DAYS.between(today, occ.dueDate)
        val state = when {
            occ.cancelled -> OccurrenceState.CANCELLED
            !remaining.isPositive -> OccurrenceState.PAID
            occ.paused -> OccurrenceState.PAUSED
            paid.isPositive -> OccurrenceState.PARTIALLY_PAID
            days < 0 -> OccurrenceState.OVERDUE     // due date reached is not proof of payment (T20)
            days == 0L -> OccurrenceState.DUE
            else -> OccurrenceState.UPCOMING
        }
        return OccurrenceStatus(occ, paid, remaining, state, days)
    }

    /** Candidate occurrences for a debit, using profile, amount, and date evidence. Multiple -> user chooses. */
    fun candidates(event: BankEvent, eventProfileId: String?, open: List<OccurrenceStatus>, windowDays: Long = 10): List<OccurrenceStatus> {
        if (!event.isConfirmed || event.kind.direction != EventKind.Direction.DEBIT || event.amount == null) return emptyList()
        val day = event.executedAt.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        return open.filter {
            it.remaining.isPositive && it.state !in setOf(OccurrenceState.CANCELLED, OccurrenceState.PAUSED) &&
                (eventProfileId == null || it.occurrence.profileId == eventProfileId) &&
                it.occurrence.currency == "SAR" &&
                (event.amount == it.remaining || event.amount == it.occurrence.expected) &&
                kotlin.math.abs(ChronoUnit.DAYS.between(it.occurrence.dueDate, day)) <= windowDays
        }
    }
}
