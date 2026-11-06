package com.budgetsms.domain.model

import com.budgetsms.domain.money.BasisPoints
import com.budgetsms.domain.money.Money
import java.time.Instant
import java.time.LocalDate

/** Provenance attached to every displayed amount (spec §02). */
enum class Provenance { CONFIRMED, CALCULATED, PLANNED, FORECAST, UNDER_REVIEW }

data class Amount(val value: Money, val provenance: Provenance)

enum class CycleType { SALARY, TRANSFER, MANUAL, SCHEDULED }
enum class RenewalMode { MANUAL, SCHEDULED, NEXT_APPROVED_TRANSFER }

data class Profile(
    val id: String,
    val name: String,
    val cycleType: CycleType = CycleType.SALARY,
    val renewalMode: RenewalMode = RenewalMode.MANUAL,
    val scheduledEveryDays: Int? = null,
    val defaultForecastHorizon: ForecastHorizon = ForecastHorizon.Days(30),
    val reserve: ReservePolicy = ReservePolicy.None,
    val minimumOpeningTransfer: Money? = null,
)

enum class AccountType { CURRENT, SAVINGS, CREDIT_CARD }

data class Account(
    val id: String,
    val bankId: String,
    val profileId: String,
    val type: AccountType,
    /** Last digits as they appear in bank SMS (e.g. "1234"); used to link messages to the account. */
    val maskedNumber: String,
    val displayName: String,
    val isMain: Boolean = false,
)

enum class EventKind(val direction: Direction) {
    SALARY(Direction.CREDIT),
    ALLOWANCE(Direction.CREDIT),
    INCOMING_TRANSFER(Direction.CREDIT),
    REFUND(Direction.CREDIT),
    PURCHASE_DEBIT(Direction.DEBIT),
    PURCHASE_CREDIT_CARD(Direction.CARD),
    ATM_WITHDRAWAL(Direction.DEBIT),
    OUTGOING_TRANSFER(Direction.DEBIT),
    CARD_REPAYMENT(Direction.DEBIT),
    FEE(Direction.DEBIT),
    REVERSAL(Direction.NONE),
    FAILED(Direction.NONE),
    BALANCE_ONLY(Direction.NONE),
    UNKNOWN(Direction.NONE);

    enum class Direction { CREDIT, DEBIT, CARD, NONE }
}

/** States for messages and transactions (spec §04). Only CONFIRMED contributes to spending. */
enum class EventState { DISCOVERED, UNAPPROVED, UNDER_REVIEW, CONFIRMED, REVERSED, DUPLICATE, PENDING_FX, IGNORED }

enum class TimeSource { BANK, RECEIPT, USER_CORRECTED }
enum class Confidence { HIGH, MEDIUM, LOW }

enum class IncomeTreatment { FUND_BUDGET, OUTSIDE_BUDGET, EARMARK_SAVINGS }

enum class ReviewReason {
    UNSUPPORTED_FORMAT, LOW_CONFIDENCE, MISSING_AMOUNT, MISSING_ACCOUNT, POSSIBLE_DUPLICATE,
    UNTRUSTED_TIMESTAMP, CYCLE_BOUNDARY, FX_WITHOUT_SAR_DEBIT, BALANCE_MISMATCH, TRANSFER_TIE,
    TRANSFER_AMOUNT_MISMATCH, INCOME_TREATMENT_NEEDED, OBLIGATION_MATCH_TIE,
}

data class BankEvent(
    val id: String,
    val rawSmsIds: List<String>,
    val bankId: String,
    val accountId: String?,
    val kind: EventKind,
    /** Actual SAR amount (for FX: the SAR debit stated by the bank). Fee excluded. */
    val amount: Money?,
    val fee: Money? = null,
    val originalCurrency: String = "SAR",
    val originalAmountText: String? = null,
    val reference: String? = null,
    val counterpartyHint: String? = null,
    /** Own account on the other side of a transfer, if identified. */
    val counterpartyAccountId: String? = null,
    val executedAt: Instant,
    val timeSource: TimeSource,
    val timeConfidence: Confidence,
    val balanceAfter: Money? = null,
    val state: EventState,
    val reviewReasons: Set<ReviewReason> = emptySet(),
    val categoryId: String? = null,
    val correctsEventId: String? = null,
    val incomeTreatment: IncomeTreatment? = null,
    val parserId: String,
    val parserVersion: Int,
) {
    val isConfirmed get() = state == EventState.CONFIRMED
    val sarAmount: Money get() = amount ?: Money.ZERO
}

data class Category(
    val id: String,
    val nameEn: String,
    val nameAr: String,
    val parentId: String? = null,
    val iconKey: String = "category",
    val colorArgb: Long? = null,
)

sealed interface LimitRule {
    data class Fixed(val amount: Money) : LimitRule
    data class Percent(val bp: BasisPoints) : LimitRule
}

data class CategoryLimit(val profileId: String, val categoryId: String, val rule: LimitRule)

sealed interface ForecastHorizon {
    data class Days(val days: Int) : ForecastHorizon { init { require(days in setOf(7, 14, 30, 60)) } }
    data class UntilExpectedSalary(val expected: LocalDate) : ForecastHorizon
}

sealed interface ReservePolicy {
    data object None : ReservePolicy
    data class Fixed(val amount: Money) : ReservePolicy
    data class PercentOfLiquidity(val bp: BasisPoints) : ReservePolicy
}

data class AuditEntry(
    val at: Instant,
    val actor: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val detail: String,
)
