package com.budgetsms.data

import com.budgetsms.data.db.*
import com.budgetsms.domain.accounting.AccountAssignment
import com.budgetsms.domain.accounting.BudgetSettlement
import com.budgetsms.domain.accounting.ManualOpening
import com.budgetsms.domain.appearance.*
import com.budgetsms.domain.model.*
import com.budgetsms.domain.money.BasisPoints
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.obligations.*
import com.budgetsms.domain.savings.InstallmentAllocation
import com.budgetsms.domain.savings.SavingsGoal
import com.budgetsms.domain.savings.SettlementDecision
import java.time.Instant
import java.time.LocalDate

internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
internal fun Long?.money(): Money? = this?.let { Money(it) }
internal fun LocalDate.epochDay(): Long = toEpochDay()
internal fun Long.date(): LocalDate = LocalDate.ofEpochDay(this)

fun ProfileEntity.toDomain() = Profile(
    id = id, name = name, cycleType = CycleType.valueOf(cycleType), renewalMode = RenewalMode.valueOf(renewalMode),
    scheduledEveryDays = scheduledEveryDays,
    defaultForecastHorizon = ForecastHorizon.Days(if (horizonDays in setOf(7, 14, 30, 60)) horizonDays else 30),
    reserve = when (reserveType) {
        "FIXED" -> ReservePolicy.Fixed(Money(reserveValue))
        "PERCENT" -> ReservePolicy.PercentOfLiquidity(BasisPoints(reserveValue.toInt()))
        else -> ReservePolicy.None
    },
    minimumOpeningTransfer = minOpeningTransfer.money(),
)

fun AccountEntity.toDomain() = Account(id, bankId, profileId, AccountType.valueOf(type), maskedNumber, displayName, isMain)
fun AccountAssignmentEntity.toDomain() = AccountAssignment(accountId, profileId, effectiveFrom.toInstant())

fun BankEventEntity.toDomain() = BankEvent(
    id = id, rawSmsIds = rawSmsIds.split(',').filter { it.isNotBlank() }, bankId = bankId, accountId = accountId,
    kind = EventKind.valueOf(kind), amount = amount.money(), fee = fee.money(), originalCurrency = originalCurrency,
    originalAmountText = originalAmountText, reference = reference, counterpartyHint = counterpartyHint,
    counterpartyAccountId = counterpartyAccountId, executedAt = executedAt.toInstant(),
    timeSource = TimeSource.valueOf(timeSource), timeConfidence = Confidence.valueOf(timeConfidence),
    balanceAfter = balanceAfter.money(), state = EventState.valueOf(state),
    reviewReasons = reviewReasons.split(',').filter { it.isNotBlank() }.mapNotNull { runCatching { ReviewReason.valueOf(it) }.getOrNull() }.toSet(),
    categoryId = categoryId, correctsEventId = correctsEventId,
    incomeTreatment = incomeTreatment?.let { IncomeTreatment.valueOf(it) }, parserId = parserId, parserVersion = parserVersion,
)

fun BankEvent.toEntity(userDecided: Boolean, now: Long) = BankEventEntity(
    id, rawSmsIds.joinToString(","), bankId, accountId, kind.name, amount?.halalas, fee?.halalas, originalCurrency,
    originalAmountText, reference, counterpartyHint, counterpartyAccountId, executedAt.toEpochMilli(), timeSource.name,
    timeConfidence.name, balanceAfter?.halalas, state.name, reviewReasons.joinToString(",") { it.name }, categoryId,
    correctsEventId, incomeTreatment?.name, parserId, parserVersion, userDecided, now,
)

fun CategoryEntity.toDomain() = Category(id, nameEn, nameAr, parentId, iconKey, colorArgb)
fun CategoryLimitEntity.toDomain() = CategoryLimit(profileId, categoryId,
    if (ruleType == "PERCENT") LimitRule.Percent(BasisPoints(value.toInt())) else LimitRule.Fixed(Money(value)))

fun FinancialCycleEntity.toManualOpening() = ManualOpening(profileId, openedAt.toInstant(), plannedBase.money())

fun BudgetAllocationEntity.toSettlement(): BudgetSettlement? =
    if (kind == "SETTLEMENT" && approved && fromProfileId != null && toProfileId != null)
        BudgetSettlement(id, fromProfileId, toProfileId, Money(amount), at.toInstant()) else null

fun SavingsGoalEntity.toDomain() = SavingsGoal(id, profileId, requestCycleId, Money(target), createdAt.toInstant(), destinationAccountId)
fun SavingsInstallmentEntity.toDomain() = InstallmentAllocation(transferId, goalId, Money(amount), userConfirmed, SettlementDecision.valueOf(settlement))

fun RecurringObligationEntity.toDomain(overrides: List<ObligationOccurrenceEntity>) = Obligation(
    id = id, name = name, description = description, categoryId = categoryId, profileId = profileId, amount = Money(amount),
    currency = currency, type = ObligationType.valueOf(type), startDate = startDate.date(), dayOfMonth = dayOfMonth,
    occurrenceCount = occurrenceCount, endDate = endDate?.date(), monthEndPolicy = monthEndPolicy?.let { MonthEndPolicy.valueOf(it) },
    pauses = pauses.split(';').filter { it.isNotBlank() }.map { p -> val (f, u) = p.split(':'); PausePeriod(f.toLong().date(), u.toLongOrNull()?.date()) },
    endedAt = endedAt?.date(),
    overrides = overrides.filter { it.obligationId == id }.associate { it.occIndex to OccurrenceOverride(it.overrideAmount.money(), it.overrideDueDate?.date(), it.cancelled) },
    profileChanges = profileChanges.split(';').filter { it.isNotBlank() }.map { c -> val p = c.split(':'); ProfileChange(p[0].toLong().date(), p[1], p.getOrNull(2) == "1") },
    remindDaysBefore = remindDaysBefore, notes = notes, version = version,
)

fun Obligation.toEntity(createdAt: Long) = RecurringObligationEntity(
    id, name, description, categoryId, profileId, amount.halalas, currency, type.name, startDate.epochDay(), dayOfMonth,
    occurrenceCount, endDate?.epochDay(), monthEndPolicy?.name, endedAt?.epochDay(), remindDaysBefore, notes, version,
    pauses.joinToString(";") { "${it.from.epochDay()}:${it.until?.epochDay() ?: ""}" },
    profileChanges.joinToString(";") { "${it.effectiveFrom.epochDay()}:${it.profileId}:${if (it.includePaidHistory) 1 else 0}" },
    createdAt,
)

fun UserAppearancePreferenceEntity?.toDomain(): AppearancePreference = if (this == null) AppearancePreference() else AppearancePreference(
    mode = runCatching { ThemeMode.valueOf(mode) }.getOrDefault(ThemeMode.SYSTEM),
    palette = runCatching { Palette.valueOf(palette) }.getOrDefault(Palette.TEAL),
    customSeed = customSeed, dynamicColor = dynamicColor,
    contrast = runCatching { ContrastLevel.valueOf(contrast) }.getOrDefault(ContrastLevel.STANDARD),
    corners = runCatching { CornerStyle.valueOf(corners) }.getOrDefault(CornerStyle.ROUNDED),
    pureBlack = pureBlack,
    animation = runCatching { AnimationLevel.valueOf(animation) }.getOrDefault(AnimationLevel.FULL),
    language = runCatching { AppLanguage.valueOf(language) }.getOrDefault(AppLanguage.SYSTEM),
    maskAmounts = maskAmounts, fontScale = fontScale,
)

fun AppearancePreference.toEntity(now: Long) = UserAppearancePreferenceEntity(
    1, mode.name, palette.name, customSeed, dynamicColor, contrast.name, corners.name, pureBlack, animation.name,
    language.name, maskAmounts, fontScale, now,
)
