package com.budgetsms.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT fingerprint FROM raw_sms") suspend fun allFingerprints(): List<String>
    @Query("SELECT * FROM raw_sms WHERE fingerprint IN (:ids)") suspend fun rawByIds(ids: List<String>): List<RawSmsEntity>
    @Query("SELECT * FROM raw_sms WHERE fingerprint = :id") fun observeRaw(id: String): Flow<RawSmsEntity?>
    @Query("SELECT * FROM raw_sms") suspend fun allRaw(): List<RawSmsEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRaw(items: List<RawSmsEntity>)
    @Query("SELECT fingerprint || '|' || source || '|' || sourceRecordId FROM sms_source") suspend fun allSourceKeys(): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertSources(items: List<SourceRecordEntity>)
    @Query("SELECT COUNT(*) FROM sms_source WHERE fingerprint = :fp") suspend fun sourceCount(fp: String): Int

    @Query("SELECT * FROM discovered_sender ORDER BY messageCount DESC") fun observeDiscovered(): Flow<List<DiscoveredSenderEntity>>
    @Upsert suspend fun upsertDiscovered(items: List<DiscoveredSenderEntity>)
    @Query("SELECT * FROM approved_sender") fun observeApproved(): Flow<List<ApprovedSenderEntity>>
    @Query("SELECT * FROM approved_sender") suspend fun approved(): List<ApprovedSenderEntity>
    @Upsert suspend fun upsertApproved(item: ApprovedSenderEntity)
}

@Dao
interface LedgerDao {
    @Query("SELECT * FROM bank ORDER BY name") fun observeBanks(): Flow<List<BankEntity>>
    @Upsert suspend fun upsertBank(b: BankEntity)
    @Query("SELECT * FROM bank") suspend fun banks(): List<BankEntity>
    @Query("SELECT * FROM profile ORDER BY createdAt") suspend fun profiles(): List<ProfileEntity>
    @Query("SELECT * FROM profile ORDER BY createdAt") fun observeProfiles(): Flow<List<ProfileEntity>>
    @Upsert suspend fun upsertProfile(p: ProfileEntity)
    @Query("SELECT * FROM account ORDER BY displayName") fun observeAccounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM account") suspend fun accounts(): List<AccountEntity>
    @Upsert suspend fun upsertAccount(a: AccountEntity)
    @Query("SELECT * FROM account_assignment") fun observeAssignments(): Flow<List<AccountAssignmentEntity>>
    @Insert suspend fun insertAssignment(a: AccountAssignmentEntity)

    @Query("SELECT * FROM bank_event ORDER BY executedAt DESC") fun observeEvents(): Flow<List<BankEventEntity>>
    @Query("SELECT * FROM bank_event") suspend fun events(): List<BankEventEntity>
    @Query("SELECT * FROM bank_event WHERE id = :id") suspend fun event(id: String): BankEventEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEvents(items: List<BankEventEntity>)
    @Upsert suspend fun upsertEvent(e: BankEventEntity)

    @Query("SELECT * FROM financial_cycle WHERE isManualOpening = 1") fun observeManualCycles(): Flow<List<FinancialCycleEntity>>
    @Upsert suspend fun upsertCycle(c: FinancialCycleEntity)

    @Query("SELECT * FROM category ORDER BY sortOrder, nameEn") fun observeCategories(): Flow<List<CategoryEntity>>
    @Query("SELECT COUNT(*) FROM category") suspend fun categoryCount(): Int
    @Upsert suspend fun upsertCategory(c: CategoryEntity)
    @Upsert suspend fun upsertCategories(c: List<CategoryEntity>)
    @Query("SELECT * FROM category_limit") fun observeLimits(): Flow<List<CategoryLimitEntity>>
    @Upsert suspend fun upsertLimit(l: CategoryLimitEntity)
    @Query("DELETE FROM category_limit WHERE profileId = :profileId AND categoryId = :categoryId") suspend fun deleteLimit(profileId: String, categoryId: String)

    @Query("SELECT * FROM budget_allocation") fun observeAllocations(): Flow<List<BudgetAllocationEntity>>
    @Upsert suspend fun upsertAllocation(a: BudgetAllocationEntity)
    @Query("DELETE FROM budget_allocation WHERE id = :id") suspend fun deleteAllocation(id: String)

    @Query("SELECT * FROM transfer_match") fun observeMatches(): Flow<List<TransferMatchEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMatch(m: TransferMatchEntity)
    @Query("SELECT * FROM transfer_rejection") fun observeRejections(): Flow<List<TransferRejectionEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertRejection(r: TransferRejectionEntity)
}

@Dao
interface PlanDao {
    @Query("SELECT * FROM savings_goal ORDER BY createdAt") fun observeGoals(): Flow<List<SavingsGoalEntity>>
    @Upsert suspend fun upsertGoal(g: SavingsGoalEntity)
    @Query("SELECT * FROM savings_goal WHERE id = :id") suspend fun goal(id: String): SavingsGoalEntity?
    @Query("SELECT * FROM savings_installment") fun observeInstallments(): Flow<List<SavingsInstallmentEntity>>
    @Upsert suspend fun upsertInstallment(i: SavingsInstallmentEntity)
    @Query("DELETE FROM savings_installment WHERE transferId = :transferId AND goalId = :goalId") suspend fun deleteInstallment(transferId: String, goalId: String)

    @Query("SELECT * FROM forecast ORDER BY createdAt DESC") fun observeForecasts(): Flow<List<ForecastEntity>>
    @Upsert suspend fun upsertForecast(f: ForecastEntity)

    @Query("SELECT * FROM recurring_obligation ORDER BY createdAt") fun observeObligations(): Flow<List<RecurringObligationEntity>>
    @Query("SELECT * FROM recurring_obligation WHERE id = :id") suspend fun obligation(id: String): RecurringObligationEntity?
    @Upsert suspend fun upsertObligation(o: RecurringObligationEntity)
    @Query("SELECT * FROM obligation_occurrence") fun observeOverrides(): Flow<List<ObligationOccurrenceEntity>>
    @Upsert suspend fun upsertOverride(o: ObligationOccurrenceEntity)
    @Query("SELECT * FROM obligation_payment_match") fun observePayments(): Flow<List<ObligationPaymentMatchEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertPayment(p: ObligationPaymentMatchEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM appearance_pref WHERE id = 1") fun observeAppearance(): Flow<UserAppearancePreferenceEntity?>
    @Query("SELECT * FROM appearance_pref WHERE id = 1") suspend fun appearance(): UserAppearancePreferenceEntity?
    @Upsert suspend fun upsertAppearance(p: UserAppearancePreferenceEntity)
    @Query("SELECT * FROM app_setting") fun observeSettings(): Flow<List<AppSettingEntity>>
    @Query("SELECT value FROM app_setting WHERE `key` = :key") suspend fun setting(key: String): String?
    @Upsert suspend fun upsertSetting(s: AppSettingEntity)
    @Insert suspend fun insertAudit(a: AuditLogEntity)
    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT :limit") fun observeAudit(limit: Int): Flow<List<AuditLogEntity>>
    @Query("SELECT `key` FROM alert_sent") suspend fun sentAlerts(): List<String>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun markAlertSent(a: AlertSentEntity)
}
