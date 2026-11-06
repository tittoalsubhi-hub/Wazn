package com.budgetsms.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// All instants are epoch milliseconds, all money is integer halalas, all enums are stored by name.

/** Original approved message, never modified after storage. */
@Entity(tableName = "raw_sms")
data class RawSmsEntity(
    @PrimaryKey val fingerprint: String,
    val sender: String,
    val receivedAt: Long,
    val body: String,
    val storedAt: Long,
)

@Entity(tableName = "sms_source", primaryKeys = ["fingerprint", "source", "sourceRecordId"])
data class SourceRecordEntity(
    val fingerprint: String,
    val source: String,
    val sourceRecordId: String,
    val importedAt: Long,
    val batchId: String,
)

/** Header metadata only (sender + counts); no bodies. */
@Entity(tableName = "discovered_sender")
data class DiscoveredSenderEntity(
    @PrimaryKey val senderId: String,
    val rawAddresses: String,
    val messageCount: Int,
    val firstSeen: Long,
    val lastSeen: Long,
)

@Entity(tableName = "approved_sender")
data class ApprovedSenderEntity(
    @PrimaryKey val senderId: String,
    val bankId: String,
    val approvedAt: Long,
    val revokedAt: Long?,
    /** User-approved learned rule: auto-confirm events whose only uncertainty is generic confidence. */
    val autoConfirm: Boolean = false,
)

@Entity(tableName = "bank")
data class BankEntity(@PrimaryKey val id: String, val name: String, val colorArgb: Long)

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val cycleType: String,
    val renewalMode: String,
    val scheduledEveryDays: Int?,
    val horizonDays: Int,
    val reserveType: String,
    val reserveValue: Long,
    val minOpeningTransfer: Long?,
    val colorArgb: Long,
    val iconKey: String,
    val createdAt: Long,
)

@Entity(tableName = "account", indices = [Index("profileId"), Index("bankId")])
data class AccountEntity(
    @PrimaryKey val id: String,
    val bankId: String,
    val profileId: String,
    val type: String,
    val maskedNumber: String,
    val displayName: String,
    val isMain: Boolean,
)

@Entity(tableName = "account_assignment", indices = [Index("accountId")])
data class AccountAssignmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val profileId: String,
    val effectiveFrom: Long,
)

/** Manual openings and derived cycle snapshots. */
@Entity(tableName = "financial_cycle", indices = [Index("profileId")])
data class FinancialCycleEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val type: String,
    val openedAt: Long,
    val closedAt: Long?,
    val openingEventId: String?,
    val plannedBase: Long?,
    val isManualOpening: Boolean,
)

@Entity(tableName = "bank_event", indices = [Index("accountId"), Index("executedAt"), Index("state")])
data class BankEventEntity(
    @PrimaryKey val id: String,
    val rawSmsIds: String,
    val bankId: String,
    val accountId: String?,
    val kind: String,
    val amount: Long?,
    val fee: Long?,
    val originalCurrency: String,
    val originalAmountText: String?,
    val reference: String?,
    val counterpartyHint: String?,
    val counterpartyAccountId: String?,
    val executedAt: Long,
    val timeSource: String,
    val timeConfidence: String,
    val balanceAfter: Long?,
    val state: String,
    val reviewReasons: String,
    val categoryId: String?,
    val correctsEventId: String?,
    val incomeTreatment: String?,
    val parserId: String,
    val parserVersion: Int,
    val userDecided: Boolean,
    val updatedAt: Long,
)

@Entity(tableName = "category")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val nameEn: String,
    val nameAr: String,
    val parentId: String?,
    val iconKey: String,
    val colorArgb: Long?,
    val sortOrder: Int,
)

@Entity(tableName = "category_limit", primaryKeys = ["profileId", "categoryId"])
data class CategoryLimitEntity(val profileId: String, val categoryId: String, val ruleType: String, val value: Long)

/** Budget allocation changes and approved settlements between profiles (audited). */
@Entity(tableName = "budget_allocation")
data class BudgetAllocationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val fromProfileId: String?,
    val toProfileId: String?,
    val categoryId: String?,
    val amount: Long,
    val at: Long,
    val approved: Boolean,
)

@Entity(tableName = "transfer_match", indices = [Index(value = ["outgoingId"], unique = true), Index(value = ["incomingId"], unique = true)])
data class TransferMatchEntity(@PrimaryKey val id: String, val outgoingId: String, val incomingId: String, val decidedAt: Long)

@Entity(tableName = "transfer_rejection", primaryKeys = ["outgoingId", "incomingId"])
data class TransferRejectionEntity(val outgoingId: String, val incomingId: String, val decidedAt: Long)

@Entity(tableName = "savings_goal")
data class SavingsGoalEntity(
    @PrimaryKey val id: String,
    val name: String,
    val profileId: String,
    val requestCycleId: String?,
    val target: Long,
    val createdAt: Long,
    val destinationAccountId: String?,
    val provisionalApproved: Boolean,
)

@Entity(tableName = "savings_installment", primaryKeys = ["transferId", "goalId"])
data class SavingsInstallmentEntity(
    val transferId: String,
    val goalId: String,
    val amount: Long,
    val userConfirmed: Boolean,
    val settlement: String,
    val decidedAt: Long,
)

/** Saved liquidity estimates and approved savings plans (never bank money). */
@Entity(tableName = "forecast")
data class ForecastEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val createdAt: Long,
    val horizonDays: Int?,
    val horizonDate: Long?,
    val liquidity: Long,
    val reserve: Long,
    val available: Long,
    /** goalId=amount;goalId=amount */
    val plan: String,
    val approved: Boolean,
)

@Entity(tableName = "recurring_obligation")
data class RecurringObligationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val categoryId: String?,
    val profileId: String,
    val amount: Long,
    val currency: String,
    val type: String,
    val startDate: Long,
    val dayOfMonth: Int,
    val occurrenceCount: Int?,
    val endDate: Long?,
    val monthEndPolicy: String?,
    val endedAt: Long?,
    val remindDaysBefore: Int,
    val notes: String,
    val version: Int,
    /** from:until;from:  (epoch days) */
    val pauses: String,
    /** effectiveFrom:profileId:includeHistory; ... */
    val profileChanges: String,
    val createdAt: Long,
)

@Entity(tableName = "obligation_occurrence", primaryKeys = ["obligationId", "occIndex"])
data class ObligationOccurrenceEntity(
    val obligationId: String,
    val occIndex: Int,
    val overrideAmount: Long?,
    val overrideDueDate: Long?,
    val cancelled: Boolean,
)

@Entity(tableName = "obligation_payment_match", indices = [Index("occurrenceId")])
data class ObligationPaymentMatchEntity(@PrimaryKey val eventId: String, val occurrenceId: String, val amount: Long, val linkedAt: Long)

@Entity(tableName = "appearance_pref")
data class UserAppearancePreferenceEntity(
    @PrimaryKey val id: Int = 1,
    val mode: String,
    val palette: String,
    val customSeed: Long,
    val dynamicColor: Boolean,
    val contrast: String,
    val corners: String,
    val pureBlack: Boolean,
    val animation: String,
    val language: String,
    val maskAmounts: Boolean,
    val fontScale: Int,
    val updatedAt: Long,
)

@Entity(tableName = "audit_log", indices = [Index("at")])
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val at: Long,
    val actor: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val detail: String,
)

@Entity(tableName = "app_setting")
data class AppSettingEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "alert_sent")
data class AlertSentEntity(@PrimaryKey val key: String, val at: Long)
