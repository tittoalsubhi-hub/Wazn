package com.budgetsms.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.currentLocale
import com.budgetsms.app.ui.theme.LocalSemantic
import com.budgetsms.domain.appearance.Palette
import com.budgetsms.domain.model.*
import com.budgetsms.domain.obligations.OccurrenceState
import com.budgetsms.data.AppSnapshot

/** Every enum shown to the user is translated here (spec §17: no hard-coded strings). */
@Composable fun kindLabel(k: EventKind) = stringResource(when (k) {
    EventKind.SALARY -> R.string.kind_salary; EventKind.ALLOWANCE -> R.string.kind_allowance
    EventKind.INCOMING_TRANSFER -> R.string.kind_incoming; EventKind.REFUND -> R.string.kind_refund
    EventKind.PURCHASE_DEBIT -> R.string.kind_purchase; EventKind.PURCHASE_CREDIT_CARD -> R.string.kind_cc_purchase
    EventKind.ATM_WITHDRAWAL -> R.string.kind_atm; EventKind.OUTGOING_TRANSFER -> R.string.kind_outgoing
    EventKind.CARD_REPAYMENT -> R.string.kind_repayment; EventKind.FEE -> R.string.kind_fee
    EventKind.REVERSAL -> R.string.kind_reversal; EventKind.FAILED -> R.string.kind_failed
    EventKind.BALANCE_ONLY -> R.string.kind_balance; EventKind.UNKNOWN -> R.string.kind_unknown
})

@Composable fun stateLabel(s: EventState) = stringResource(when (s) {
    EventState.DISCOVERED -> R.string.state_discovered; EventState.UNAPPROVED -> R.string.state_unapproved
    EventState.UNDER_REVIEW -> R.string.state_review; EventState.CONFIRMED -> R.string.state_confirmed
    EventState.REVERSED -> R.string.state_reversed; EventState.DUPLICATE -> R.string.state_duplicate
    EventState.PENDING_FX -> R.string.state_pending_fx
})

@Composable fun reasonLabel(r: ReviewReason) = stringResource(when (r) {
    ReviewReason.UNSUPPORTED_FORMAT -> R.string.reason_unsupported; ReviewReason.LOW_CONFIDENCE -> R.string.reason_low_confidence
    ReviewReason.MISSING_AMOUNT -> R.string.reason_missing_amount; ReviewReason.MISSING_ACCOUNT -> R.string.reason_missing_account
    ReviewReason.POSSIBLE_DUPLICATE -> R.string.reason_duplicate; ReviewReason.UNTRUSTED_TIMESTAMP -> R.string.reason_timestamp
    ReviewReason.CYCLE_BOUNDARY -> R.string.reason_boundary; ReviewReason.FX_WITHOUT_SAR_DEBIT -> R.string.reason_fx
    ReviewReason.BALANCE_MISMATCH -> R.string.reason_balance; ReviewReason.TRANSFER_TIE -> R.string.reason_tie
    ReviewReason.TRANSFER_AMOUNT_MISMATCH -> R.string.reason_amount_mismatch; ReviewReason.INCOME_TREATMENT_NEEDED -> R.string.reason_income
    ReviewReason.OBLIGATION_MATCH_TIE -> R.string.reason_obligation_tie
})

@Composable fun treatmentLabel(t: IncomeTreatment) = stringResource(when (t) {
    IncomeTreatment.FUND_BUDGET -> R.string.treat_fund; IncomeTreatment.OUTSIDE_BUDGET -> R.string.treat_outside; IncomeTreatment.EARMARK_SAVINGS -> R.string.treat_earmark
})

@Composable fun cycleTypeLabel(t: CycleType) = stringResource(when (t) {
    CycleType.SALARY -> R.string.cycle_salary; CycleType.TRANSFER -> R.string.cycle_transfer; CycleType.MANUAL -> R.string.cycle_manual; CycleType.SCHEDULED -> R.string.cycle_scheduled
})

@Composable fun renewalLabel(t: RenewalMode) = stringResource(when (t) {
    RenewalMode.MANUAL -> R.string.renew_manual; RenewalMode.SCHEDULED -> R.string.renew_scheduled; RenewalMode.NEXT_APPROVED_TRANSFER -> R.string.renew_next_transfer
})

@Composable fun accountTypeLabel(t: AccountType) = stringResource(when (t) {
    AccountType.CURRENT -> R.string.acc_current; AccountType.SAVINGS -> R.string.acc_savings; AccountType.CREDIT_CARD -> R.string.acc_card
})

@Composable fun occStateLabel(s: OccurrenceState) = stringResource(when (s) {
    OccurrenceState.UPCOMING -> R.string.occ_upcoming; OccurrenceState.DUE -> R.string.occ_due; OccurrenceState.OVERDUE -> R.string.occ_overdue
    OccurrenceState.PARTIALLY_PAID -> R.string.occ_partial; OccurrenceState.PAID -> R.string.occ_paid
    OccurrenceState.CANCELLED -> R.string.occ_cancelled; OccurrenceState.PAUSED -> R.string.occ_paused
})

@Composable fun occStateColor(s: OccurrenceState): Color { val c = LocalSemantic.current; return when (s) {
    OccurrenceState.PAID -> c.income; OccurrenceState.OVERDUE -> c.overspend; OccurrenceState.DUE -> c.expense
    OccurrenceState.PARTIALLY_PAID -> c.review; OccurrenceState.UPCOMING -> c.forecast; else -> c.planned } }

@Composable fun paletteName(p: Palette) = if (currentLocale().language == "ar") p.nameAr else p.nameEn

@Composable fun categoryName(s: AppSnapshot, id: String?): String {
    val c = s.category(id) ?: return stringResource(R.string.uncategorized)
    return if (currentLocale().language == "ar") c.nameAr else c.nameEn
}

@Composable fun kindColor(k: EventKind): Color { val c = LocalSemantic.current; return when (k.direction) {
    EventKind.Direction.CREDIT -> c.income; EventKind.Direction.DEBIT -> c.expense; EventKind.Direction.CARD -> c.planned; EventKind.Direction.NONE -> c.planned } }

fun Category.color(): Color = Color(colorArgb ?: 0xFF9E9E9E)
