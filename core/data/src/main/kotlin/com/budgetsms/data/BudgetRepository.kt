package com.budgetsms.data

import androidx.room.withTransaction
import com.budgetsms.data.db.*
import com.budgetsms.domain.accounting.*
import com.budgetsms.domain.appearance.AppearancePreference
import com.budgetsms.domain.forecast.*
import com.budgetsms.domain.ingest.IncomingSms
import com.budgetsms.domain.ingest.IngestResult
import com.budgetsms.domain.ingest.IngestState
import com.budgetsms.domain.ingest.IngestionPipeline
import com.budgetsms.domain.model.*
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.obligations.*
import com.budgetsms.domain.savings.*
import com.budgetsms.domain.sms.ApprovedSender
import com.budgetsms.domain.sms.ConsentGate
import com.budgetsms.domain.sms.ParserRegistry
import com.budgetsms.domain.sms.SenderDiscovery
import com.budgetsms.domain.sms.SenderNormalizer
import com.budgetsms.domain.sms.LearnedParser
import com.budgetsms.domain.sms.MessageTemplate
import com.budgetsms.domain.sms.SampleType
import com.budgetsms.domain.sms.TemplateLearner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

object SettingKeys {
    const val ACTIVATION_AT = "activationAt"
    const val ONBOARDING_DONE = "onboardingDone"
    const val LOCK_ENABLED = "lockEnabled"
    const val PIN_HASH = "pinHash"
    const val BIOMETRIC = "biometric"
    const val SECURE_SCREEN = "secureScreen"
    const val NOTIFY_AMOUNTS = "notifyAmounts"
    const val PROVISIONAL_APPROVED = "provisionalApproved"
    const val LIQ_MIN = "liquidityMinimum"
    const val LIQ_DROP_BP = "liquidityDropBp"
}

data class ProfileView(val profile: Profile, val colorArgb: Long, val iconKey: String)

data class AppSnapshot(
    val profiles: List<ProfileView> = emptyList(),
    val banks: List<BankEntity> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val events: List<BankEvent> = emptyList(),
    val userDecided: Set<String> = emptySet(),
    val categories: List<Category> = emptyList(),
    val limits: List<CategoryLimit> = emptyList(),
    val ledger: LedgerResult? = null,
    val savingsRequests: List<SavingsRequest> = emptyList(),
    val goals: List<GoalStatus> = emptyList(),
    val savingsTransfers: List<TransferStatus> = emptyList(),
    val installments: List<InstallmentAllocation> = emptyList(),
    val obligations: List<Obligation> = emptyList(),
    val obligationErrors: Map<String, String> = emptyMap(),
    val occurrences: List<OccurrenceStatus> = emptyList(),
    val payments: List<ObligationPayment> = emptyList(),
    val paymentCandidates: Map<String, List<OccurrenceStatus>> = emptyMap(),
    val approvedPlans: List<ForecastEntity> = emptyList(),
    val discoveredSenders: List<DiscoveredSenderEntity> = emptyList(),
    val approvedSenders: List<ApprovedSenderEntity> = emptyList(),
    val settings: Map<String, String> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
) {
    val activationAt: Instant? get() = settings[SettingKeys.ACTIVATION_AT]?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
    fun account(id: String?) = accounts.firstOrNull { it.id == id }
    fun category(id: String?) = categories.firstOrNull { it.id == id }
    fun profile(id: String?) = profiles.firstOrNull { it.profile.id == id }
}

/**
 * Single entry point between UI and storage. The financial engine runs on every change of the
 * underlying tables, so results are always recalculated from originals (idempotent, atomic writes).
 */
class BudgetRepository(
    private val db: BudgetDatabase,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val clock: () -> Instant = Instant::now,
    private val parsers: ParserRegistry = ParserRegistry(emptyList()),
) {
    private val sms = db.sms(); private val ledger = db.ledger(); private val plan = db.plan(); private val settings = db.settings()
    private val ingestMutex = Mutex()
    /** Name used for the profile created automatically on first sender approval (set by the app, localised). */
    var defaultProfileName: String = "Main"
    private val MIGRATION_AUTO = "migration.autoProcess.v13"
    private val MIGRATION_IGNORE = "migration.ignoreRules.v14"
    private val K_GRADIENT = "appearance.gradient"
    private val K_BACKGROUND = "appearance.background"
    private val K_GRADIENT_SEED = "appearance.gradientSeed"
    private val engine = LedgerEngine()
    private val savingsEngine = SavingsEngine()
    @Volatile var lastInput: LedgerInput? = null; private set

    val appearance: Flow<AppearancePreference> = combine(settings.observeAppearance(), settings.observeSettings()) { e, st ->
        val m = st.associate { it.key to it.value }
        e.toDomain().copy(
            gradient = m[K_GRADIENT]?.let { v -> runCatching { com.budgetsms.domain.appearance.GradientLevel.valueOf(v) }.getOrNull() } ?: com.budgetsms.domain.appearance.GradientLevel.VIVID,
            background = m[K_BACKGROUND]?.let { v -> runCatching { com.budgetsms.domain.appearance.BackgroundStyle.valueOf(v) }.getOrNull() } ?: com.budgetsms.domain.appearance.BackgroundStyle.AURORA,
            gradientSeed = m[K_GRADIENT_SEED]?.toLongOrNull() ?: 0L,
        )
    }
    val audit: Flow<List<AuditLogEntity>> = settings.observeAudit(200)

    @Suppress("UNCHECKED_CAST")
    val snapshot: Flow<AppSnapshot> = combine(
        listOf(
            ledger.observeProfiles(), ledger.observeBanks(), ledger.observeAccounts(), ledger.observeAssignments(),
            ledger.observeEvents(), ledger.observeCategories(), ledger.observeLimits(), ledger.observeManualCycles(),
            ledger.observeMatches(), ledger.observeRejections(), ledger.observeAllocations(), plan.observeGoals(),
            plan.observeInstallments(), plan.observeObligations(), plan.observeOverrides(), plan.observePayments(),
            plan.observeForecasts(), sms.observeDiscovered(), sms.observeApproved(), settings.observeSettings(),
        ),
    ) { a ->
        build(
            a[0] as List<ProfileEntity>, a[1] as List<BankEntity>, a[2] as List<AccountEntity>, a[3] as List<AccountAssignmentEntity>,
            a[4] as List<BankEventEntity>, a[5] as List<CategoryEntity>, a[6] as List<CategoryLimitEntity>, a[7] as List<FinancialCycleEntity>,
            a[8] as List<TransferMatchEntity>, a[9] as List<TransferRejectionEntity>, a[10] as List<BudgetAllocationEntity>,
            a[11] as List<SavingsGoalEntity>, a[12] as List<SavingsInstallmentEntity>, a[13] as List<RecurringObligationEntity>,
            a[14] as List<ObligationOccurrenceEntity>, a[15] as List<ObligationPaymentMatchEntity>, a[16] as List<ForecastEntity>,
            a[17] as List<DiscoveredSenderEntity>, a[18] as List<ApprovedSenderEntity>, a[19] as List<AppSettingEntity>,
        )
    }.conflate().flowOn(Dispatchers.Default)

    private fun build(
        profiles: List<ProfileEntity>, banks: List<BankEntity>, accountsE: List<AccountEntity>, assignments: List<AccountAssignmentEntity>,
        eventsE: List<BankEventEntity>, cats: List<CategoryEntity>, limitsE: List<CategoryLimitEntity>, manual: List<FinancialCycleEntity>,
        matches: List<TransferMatchEntity>, rejections: List<TransferRejectionEntity>, allocations: List<BudgetAllocationEntity>,
        goalsE: List<SavingsGoalEntity>, installmentsE: List<SavingsInstallmentEntity>, obligationsE: List<RecurringObligationEntity>,
        overrides: List<ObligationOccurrenceEntity>, paymentsE: List<ObligationPaymentMatchEntity>, forecasts: List<ForecastEntity>,
        discovered: List<DiscoveredSenderEntity>, approved: List<ApprovedSenderEntity>, settingsE: List<AppSettingEntity>,
    ): AppSnapshot {
        val now = clock(); val today = now.atZone(zone).toLocalDate()
        val settingsMap = settingsE.associate { it.key to it.value }
        val accounts = accountsE.map { it.toDomain() }
        val events = eventsE.map { it.toDomain() }
        val categories = cats.map { it.toDomain() }
        val limits = limitsE.map { it.toDomain() }
        val input = LedgerInput(
            profiles = profiles.map { it.toDomain() }, accounts = accounts, assignments = assignments.map { it.toDomain() },
            events = events, categories = categories, limits = limits, manualOpenings = manual.map { it.toManualOpening() },
            approvedMatches = matches.map { ApprovedTransferMatch(it.outgoingId, it.incomingId) },
            rejectedMatches = rejections.map { it.outgoingId to it.incomingId }.toSet(),
            settlements = allocations.mapNotNull { it.toSettlement() }, now = now,
        )
        lastInput = input
        val result = engine.compute(input)

        // ---- savings (§10, §11) ----
        val activation = settingsMap[SettingKeys.ACTIVATION_AT]?.toLongOrNull()?.toInstant() ?: now
        val approvedProvisional = settingsMap[SettingKeys.PROVISIONAL_APPROVED].orEmpty().split(',').filter { it.isNotBlank() }.toSet() +
            goalsE.filter { it.provisionalApproved }.mapNotNull { it.requestCycleId }
        val requests = result.budgets.values.mapNotNull { SavingsEligibility.requestFor(it, activation, approvedProvisional) }
        val goals = goalsE.map { g -> g.toDomain().let { d -> requests.firstOrNull { it.cycleId == g.requestCycleId }?.let { d.copy(target = it.target) } ?: d } }
        val accById = accounts.associateBy { it.id }
        val eventById = events.associateBy { it.id }
        val savingsTransfers = result.transfers.mapNotNull { t ->
            val out = eventById[t.outgoingId] ?: return@mapNotNull null
            val dest = accById[out.counterpartyAccountId] ?: return@mapNotNull null
            val src = out.accountId ?: return@mapNotNull null
            if (dest.type != AccountType.SAVINGS) return@mapNotNull null
            VerifiedSavingsTransfer(out.id, out.sarAmount, src, input.profileOf(src, out.executedAt) ?: "", dest.id, out.executedAt, t.incomingId != null)
        }
        val installments = installmentsE.map { it.toDomain() }
        val (goalStatus, transferStatus) = runCatching { savingsEngine.status(goals, savingsTransfers, installments) }
            .getOrElse { savingsEngine.status(goals, savingsTransfers, emptyList()) }

        // ---- obligations (§15) ----
        val obligations = obligationsE.map { it.toDomain(overrides) }
        val errors = mutableMapOf<String, String>()
        val payments = paymentsE.map { ObligationPayment(it.eventId, it.occurrenceId, Money(it.amount)) }
        val occurrences = obligations.flatMap { o ->
            runCatching { ObligationScheduler.generate(o) }.getOrElse { errors[o.id] = it.message ?: "policy"; emptyList() }
        }.map { ObligationLedger.status(it, payments, today) }
        val linked = payments.map { it.eventId }.toSet()
        val candidates = events.filter { it.isConfirmed && it.id !in linked && it.executedAt.isAfter(now.minusSeconds(60L * 86400)) }
            .associate { e -> e.id to ObligationLedger.candidates(e, input.profileOf(e.accountId, e.executedAt), occurrences) }
            .filterValues { it.isNotEmpty() }

        return AppSnapshot(
            profiles = profiles.map { ProfileView(it.toDomain(), it.colorArgb, it.iconKey) }, banks = banks, accounts = accounts,
            events = events, userDecided = eventsE.filter { it.userDecided }.map { it.id }.toSet(), categories = categories, limits = limits,
            ledger = result, savingsRequests = requests, goals = goalStatus, savingsTransfers = transferStatus, installments = installments,
            obligations = obligations, obligationErrors = errors, occurrences = occurrences, payments = payments, paymentCandidates = candidates,
            approvedPlans = forecasts.filter { it.approved }, discoveredSenders = discovered, approvedSenders = approved,
            settings = settingsMap, today = today,
        )
    }

    private fun LedgerInput.profileOf(accountId: String?, at: Instant): String? =
        assignments.filter { it.accountId == accountId && it.effectiveFrom <= at }.maxByOrNull { it.effectiveFrom }?.profileId
            ?: accounts.firstOrNull { it.id == accountId }?.profileId

    private fun ms() = clock().toEpochMilli()
    private fun id() = UUID.randomUUID().toString()
    private suspend fun audit(action: String, type: String, entityId: String, detail: String = "") =
        settings.insertAudit(AuditLogEntity(at = ms(), actor = "user", action = action, entityType = type, entityId = entityId, detail = detail))

    // ---------------- setup & settings ----------------
    suspend fun ensureDefaults(defaultCategories: List<CategoryEntity>) = db.withTransaction {
        if (ledger.categoryCount() == 0) ledger.upsertCategories(defaultCategories)
        if (settings.setting(SettingKeys.ACTIVATION_AT) == null) settings.upsertSetting(AppSettingEntity(SettingKeys.ACTIVATION_AT, ms().toString()))
        if (settings.appearance() == null) settings.upsertAppearance(AppearancePreference().toEntity(ms()))
        // v1.3: approved senders are processed automatically at bank-account level (user request).
        if (settings.setting(MIGRATION_AUTO) == null) {
            sms.approved().filter { it.revokedAt == null }.forEach { sms.upsertApproved(it.copy(autoConfirm = true)); ensureBankAccount(it.bankId) }
            settings.upsertSetting(AppSettingEntity(MIGRATION_AUTO, ms().toString()))
        }
    }

    suspend fun setSetting(key: String, value: String) { settings.upsertSetting(AppSettingEntity(key, value)); if (key != SettingKeys.PIN_HASH) audit("setting", "setting", key, value) else audit("setting", "setting", key, "***") }
    suspend fun setting(key: String): String? = settings.setting(key)

    suspend fun updateAppearance(transform: (AppearancePreference) -> AppearancePreference) = db.withTransaction {
        val m = mapOf(K_GRADIENT to settings.setting(K_GRADIENT), K_BACKGROUND to settings.setting(K_BACKGROUND), K_GRADIENT_SEED to settings.setting(K_GRADIENT_SEED))
        val current = settings.appearance().toDomain().copy(
            gradient = m[K_GRADIENT]?.let { runCatching { com.budgetsms.domain.appearance.GradientLevel.valueOf(it) }.getOrNull() } ?: com.budgetsms.domain.appearance.GradientLevel.VIVID,
            background = m[K_BACKGROUND]?.let { runCatching { com.budgetsms.domain.appearance.BackgroundStyle.valueOf(it) }.getOrNull() } ?: com.budgetsms.domain.appearance.BackgroundStyle.AURORA,
            gradientSeed = m[K_GRADIENT_SEED]?.toLongOrNull() ?: 0L)
        val next = transform(current)
        if (next != current) {
            settings.upsertAppearance(next.toEntity(ms()))
            settings.upsertSetting(AppSettingEntity(K_GRADIENT, next.gradient.name))
            settings.upsertSetting(AppSettingEntity(K_BACKGROUND, next.background.name))
            settings.upsertSetting(AppSettingEntity(K_GRADIENT_SEED, next.gradientSeed.toString()))
            audit("appearance", "UserAppearancePreference", "1", next.toString())
        }
    }

    // ---------------- senders & ingestion (§04) ----------------
    suspend fun saveDiscovered(found: List<SenderDiscovery.DiscoveredSender>, rawAddresses: Map<String, Set<String>>) =
        sms.upsertDiscovered(found.map { DiscoveredSenderEntity(it.senderId, rawAddresses[it.senderId].orEmpty().joinToString("\u001F"), it.messageCount, it.firstSeen.toEpochMilli(), it.lastSeen.toEpochMilli()) })

    suspend fun addBank(name: String, color: Long): String { val b = BankEntity(id(), name.trim(), color); ledger.upsertBank(b); audit("create", "bank", b.id, name); return b.id }

    /** Approval permits historical and future processing; it never creates a bank event by itself. */
    suspend fun approveSender(senderId: String, bankId: String, profileId: String? = null) = db.withTransaction {
        val id = SenderNormalizer.normalize(senderId)
        sms.upsertApproved(ApprovedSenderEntity(id, bankId, ms(), null, autoConfirm = true)); audit("approve", "sender", id, bankId)
        ensureBankAccount(bankId, profileId)
    }

    /**
     * Deletes a profile with everything that belongs only to it: its bank-level accounts and their messages and
     * transactions, limits, cycles, goals, obligations and plans. Senders of banks left without accounts are revoked.
     */
    suspend fun deleteProfile(profileId: String) = db.withTransaction {
        val accounts = ledger.accounts().filter { it.profileId == profileId }
        for (a in accounts) {
            val raws = ledger.eventsOfAccount(a.id).flatMap { it.rawSmsIds.split(',') }.filter { it.isNotBlank() }
            ledger.deleteEventsOfAccount(a.id); sms.deleteSources(raws); sms.deleteRaw(raws)
            ledger.deleteAssignments(a.id, profileId); ledger.deleteAccount(a.id)
        }
        val remaining = ledger.accounts()
        accounts.map { it.bankId }.distinct().filter { b -> remaining.none { it.bankId == b } }.forEach { b ->
            sms.approved().filter { it.bankId == b && it.revokedAt == null }.forEach { sms.upsertApproved(it.copy(revokedAt = ms())) }
        }
        ledger.deleteLimitsOf(profileId); ledger.deleteCyclesOf(profileId); ledger.deleteAllocationsOf(profileId)
        plan.deleteInstallmentsOf(profileId); plan.deleteGoalsOf(profileId); plan.deletePaymentsOf(profileId)
        plan.deleteOverridesOf(profileId); plan.deleteObligationsOf(profileId); plan.deleteForecastsOf(profileId)
        ledger.deleteProfile(profileId)
        audit("delete", "profile", profileId, "accounts=${accounts.size}")
    }

    /** Every message of an approved bank is booked to one bank-level account; created once, with a default profile if needed. */
    private suspend fun ensureBankAccount(bankId: String, forProfile: String? = null) {
        if (ledger.accounts().any { it.bankId == bankId && it.type != AccountType.CREDIT_CARD.name }) return
        var profileId = forProfile ?: ledger.profiles().firstOrNull()?.id
        if (profileId == null) {
            profileId = id()
            ledger.upsertProfile(ProfileEntity(profileId, defaultProfileName, CycleType.SALARY.name, RenewalMode.MANUAL.name, null, 30, "NONE", 0, null, 0xFF1B6B2A, "person", ms()))
        }
        val bankName = ledger.banks().firstOrNull { it.id == bankId }?.name ?: bankId
        ledger.upsertAccount(AccountEntity(id(), bankId, profileId, AccountType.CURRENT.name, "", bankName, true))
        audit("create", "account", bankId, "bank-level account")
    }

    /** Imports only the latest salary message and everything after it (fallback: last 30 days). */
    suspend fun ingestFromLastSalary(batch: List<IncomingSms>): Pair<IngestResult, Instant?> {
        val gate = ConsentGate(sms.approved().map { ApprovedSender(it.senderId, it.bankId, it.approvedAt.toInstant(), it.revokedAt?.toInstant()) })
        val (window, salaryAt) = com.budgetsms.domain.ingest.ImportWindow.sinceLastSalary(batch, gate, zone, clock(), parser = registry().forBank(""))
        return ingest(window) to salaryAt
    }
    suspend fun revokeSender(senderId: String) {
        val e = sms.approved().firstOrNull { it.senderId == senderId } ?: return
        sms.upsertApproved(e.copy(revokedAt = ms())); audit("revoke", "sender", senderId)
    }
    suspend fun setAutoConfirm(senderId: String, enabled: Boolean) {
        val e = sms.approved().firstOrNull { it.senderId == senderId } ?: return
        sms.upsertApproved(e.copy(autoConfirm = enabled)); audit("learned_rule", "sender", senderId, enabled.toString())
    }
    suspend fun approvedSenders(): List<ApprovedSenderEntity> = sms.approved().filter { it.revokedAt == null }

    suspend fun ingest(batch: List<IncomingSms>, batchId: String = id()): IngestResult = ingestMutex.withLock {
        val approved = sms.approved()
        val gate = ConsentGate(approved.map { ApprovedSender(it.senderId, it.bankId, it.approvedAt.toInstant(), it.revokedAt?.toInstant()) })
        val pipeline = IngestionPipeline(gate, registry(), ledger.accounts().map { it.toDomain() }, zone, clock,
            autoConfirmSenders = approved.filter { it.autoConfirm && it.revokedAt == null }.map { it.senderId }.toSet())
        val existing = ledger.events()
        val state = IngestState(sms.allFingerprints().toSet(), sms.allSourceKeys().toSet(), existing.associate { it.id to it.toDomain() },
            approved.filter { it.autoConfirm && it.revokedAt == null }.map { it.senderId }.toSet())
        val r = pipeline.ingest(batch, state, batchId)
        val decided = existing.filter { it.userDecided }.map { it.id }.toSet()
        db.withTransaction {
            sms.insertRaw(r.newRaw.map { RawSmsEntity(it.fingerprint, it.sender, it.receivedAt.toEpochMilli(), it.body, ms()) })
            sms.insertSources(r.newSourceRecords.map { SourceRecordEntity(it.fingerprint, it.source.name, it.sourceRecordId ?: "", it.importedAt.toEpochMilli(), it.importBatchId) })
            ledger.insertEvents(r.newEvents.map { it.toEntity(false, ms()) })
            r.updatedEvents.forEach { ledger.upsertEvent(it.toEntity(it.id in decided, ms())) }
            if (r.newEvents.isNotEmpty()) audit("ingest", "batch", batchId, "events=${r.newEvents.size} sources=${r.newSourceRecords.size}")
        }
        r
    }

    /** Re-runs the current parser over undecided events (idempotent; user decisions are never overwritten). */
    // ---------------- learned message samples (v1.6) ----------------
    private val K_SAMPLES = "samples.v1"

    /** Templates learned from the user's samples (fixed words and amount anchors only; sample numbers are not kept). */
    val templates: Flow<List<MessageTemplate>> = settings.observeSettings().map { l -> decodeTemplates(l.firstOrNull { it.key == K_SAMPLES }?.value) }

    private suspend fun registry(): ParserRegistry = ParserRegistry(emptyList(), LearnedParser(decodeTemplates(settings.setting(K_SAMPLES))))

    suspend fun saveSample(type: SampleType, sample: String, amount: Money?, dateOrder: com.budgetsms.domain.sms.DateOrder? = null) {
        val list = decodeTemplates(settings.setting(K_SAMPLES)) + TemplateLearner.learn(id(), type, sample, amount, dateOrder)
        settings.upsertSetting(AppSettingEntity(K_SAMPLES, encodeTemplates(list))); audit("learn_sample", "MessageTemplate", type.name)
    }
    suspend fun deleteSample(templateId: String) {
        val list = decodeTemplates(settings.setting(K_SAMPLES)).filter { it.id != templateId }
        settings.upsertSetting(AppSettingEntity(K_SAMPLES, encodeTemplates(list))); audit("delete_sample", "MessageTemplate", templateId)
    }

    private fun encodeTemplates(l: List<MessageTemplate>): String = org.json.JSONArray().apply {
        l.forEach { t -> put(org.json.JSONObject().put("id", t.id).put("type", t.type.name).put("first", t.firstLine)
            .put("pre", t.amountPrefix).put("suf", t.amountSuffix).put("date", t.dateOrder?.name ?: "").put("tokens", org.json.JSONArray(t.tokens.toList()))) }
    }.toString()

    private fun decodeTemplates(json: String?): List<MessageTemplate> = runCatching {
        val a = org.json.JSONArray(json ?: return emptyList())
        (0 until a.length()).mapNotNull { i -> val o = a.getJSONObject(i)
            val type = runCatching { SampleType.valueOf(o.getString("type")) }.getOrNull() ?: return@mapNotNull null
            val tk = o.getJSONArray("tokens")
            MessageTemplate(o.getString("id"), type, (0 until tk.length()).map { tk.getString(it) }.toSet(), o.optString("first"), o.optString("pre"), o.optString("suf"),
                runCatching { com.budgetsms.domain.sms.DateOrder.valueOf(o.optString("date")) }.getOrNull())
        }
    }.getOrDefault(emptyList())

    suspend fun reprocessUndecided(all: Boolean = false): Int = ingestMutex.withLock {
        val approved = sms.approved()
        val gate = ConsentGate(approved.map { ApprovedSender(it.senderId, it.bankId, it.approvedAt.toInstant(), it.revokedAt?.toInstant()) })
        val pipeline = IngestionPipeline(gate, registry(), ledger.accounts().map { it.toDomain() }, zone, clock,
            autoConfirmSenders = approved.filter { it.autoConfirm && it.revokedAt == null }.map { it.senderId }.toSet())
        val targets = ledger.events().filter { !it.userDecided && it.state != EventState.IGNORED.name && it.state != EventState.DUPLICATE.name }
        var n = 0
        db.withTransaction {
            val raws = sms.rawByIds(targets.flatMap { it.rawSmsIds.split(',') }).associateBy { it.fingerprint }
            for (t in targets) {
                val raw = raws[t.rawSmsIds.substringBefore(',')] ?: continue
                if (!gate.isApproved(raw.sender)) continue
                if (com.budgetsms.domain.ingest.IgnoreFilter.reason(raw.body) != null) {
                    ledger.upsertEvent(t.copy(state = EventState.IGNORED.name, reviewReasons = "", updatedAt = ms())); n++; continue
                }
                // Re-read events under review, and auto-processed events read by an older parser version.
                val outdated = t.parserId == com.budgetsms.domain.sms.GenericKeywordParser.id && t.parserVersion < com.budgetsms.domain.sms.GenericKeywordParser.version
                if (t.state != EventState.UNDER_REVIEW.name && !outdated && !all) continue
                val e = pipeline.toEvent(com.budgetsms.domain.sms.RawSms(raw.fingerprint, raw.sender, raw.receivedAt.toInstant(), raw.body))
                if (e.id == t.id) { ledger.upsertEvent(e.copy(rawSmsIds = t.toDomain().rawSmsIds).toEntity(false, ms())); n++ }
            }
        }
        n
    }

    fun observeRaw(fingerprint: String) = sms.observeRaw(fingerprint)
    suspend fun sourceCount(fingerprint: String) = sms.sourceCount(fingerprint)

    // ---------------- review decisions (no manual bank postings) ----------------
    /** Confirms a reviewed event. [amountOverride] is a manual correction by the user (owner request v1.5), audited with the original value. */
    suspend fun confirmEvent(id: String, kind: EventKind, accountId: String?, counterpartyAccountId: String?, categoryId: String?, treatment: IncomeTreatment?,
                             amountOverride: Money? = null) = db.withTransaction {
        val e = ledger.event(id) ?: return@withTransaction
        val d = e.toDomain()
        val amount = amountOverride ?: d.amount
        require(amount != null || kind.direction == EventKind.Direction.NONE) { "amount missing" }
        require(amount == null || !amount.isNegative) { "negative amount" }
        val next = d.copy(kind = kind, accountId = accountId, counterpartyAccountId = counterpartyAccountId, categoryId = categoryId,
            incomeTreatment = treatment, state = EventState.CONFIRMED, reviewReasons = emptySet(), amount = amount)
        ledger.upsertEvent(next.toEntity(true, ms()))
        audit("confirm", "BankEvent", id, "kind=$kind acc=$accountId cat=$categoryId treat=$treatment" +
            if (amountOverride != null && amountOverride != d.amount) " amount=${d.amount?.toPlainString() ?: "-"}->${amountOverride.toPlainString()} (manual)" else "")
    }
    suspend fun markDuplicate(id: String) = updateEvent(id, "duplicate") { it.copy(state = EventState.DUPLICATE) }
    suspend fun setCategory(id: String, categoryId: String?) = updateEvent(id, "category") { it.copy(categoryId = categoryId) }
    suspend fun setTreatment(id: String, t: IncomeTreatment?) = updateEvent(id, "income_treatment") { it.copy(incomeTreatment = t) }
    suspend fun linkCorrection(id: String, originalId: String?) = updateEvent(id, "link_correction") { it.copy(correctsEventId = originalId) }
    suspend fun correctTime(id: String, at: Instant) = updateEvent(id, "time_correction") {
        it.copy(executedAt = at, timeSource = TimeSource.USER_CORRECTED, timeConfidence = Confidence.HIGH, reviewReasons = it.reviewReasons - ReviewReason.CYCLE_BOUNDARY)
    }
    private suspend fun updateEvent(id: String, action: String, f: (BankEvent) -> BankEvent) = db.withTransaction {
        val e = ledger.event(id) ?: return@withTransaction
        ledger.upsertEvent(f(e.toDomain()).toEntity(true, ms())); audit(action, "BankEvent", id)
    }

    suspend fun approveTransferMatch(outgoingId: String, incomingId: String) { ledger.insertMatch(TransferMatchEntity(id(), outgoingId, incomingId, ms())); audit("match", "TransferMatch", outgoingId, incomingId) }
    suspend fun rejectTransferMatch(outgoingId: String, incomingId: String) { ledger.insertRejection(TransferRejectionEntity(outgoingId, incomingId, ms())); audit("reject_match", "TransferMatch", outgoingId, incomingId) }

    // ---------------- profiles, accounts, categories (§03, §07) ----------------
    suspend fun saveProfile(p: Profile, color: Long, icon: String, horizonDays: Int = 30): String {
        val (rt, rv) = when (val r = p.reserve) { ReservePolicy.None -> "NONE" to 0L; is ReservePolicy.Fixed -> "FIXED" to r.amount.halalas; is ReservePolicy.PercentOfLiquidity -> "PERCENT" to r.bp.value.toLong() }
        ledger.upsertProfile(ProfileEntity(p.id, p.name, p.cycleType.name, p.renewalMode.name, p.scheduledEveryDays, horizonDays, rt, rv, p.minimumOpeningTransfer?.halalas, color, icon, ms()))
        audit("save", "profile", p.id, p.name); return p.id
    }
    suspend fun saveAccount(a: Account) { ledger.upsertAccount(AccountEntity(a.id, a.bankId, a.profileId, a.type.name, a.maskedNumber, a.displayName, a.isMain)); audit("save", "account", a.id) }

    /** Impact preview: open-cycle results per profile before and after a hypothetical reassignment. */
    fun previewAccountProfileChange(accountId: String, profileId: String, effectiveFrom: Instant): Pair<Map<String, CycleBudget?>, Map<String, CycleBudget?>>? {
        val input = lastInput ?: return null
        val before = engine.compute(input); val after = engine.compute(input.copy(assignments = input.assignments + AccountAssignment(accountId, profileId, effectiveFrom)))
        val ids = input.profiles.map { it.id }
        return ids.associateWith { before.budgetOf(it) } to ids.associateWith { after.budgetOf(it) }
    }
    suspend fun changeAccountProfile(accountId: String, profileId: String, effectiveFrom: Instant) {
        ledger.insertAssignment(AccountAssignmentEntity(accountId = accountId, profileId = profileId, effectiveFrom = effectiveFrom.toEpochMilli()))
        audit("reassign", "account", accountId, "$profileId from $effectiveFrom")
    }
    suspend fun saveCategory(c: Category, order: Int = 100) { ledger.upsertCategory(CategoryEntity(c.id, c.nameEn, c.nameAr, c.parentId, c.iconKey, c.colorArgb, order)); audit("save", "category", c.id) }
    suspend fun setLimit(profileId: String, categoryId: String, rule: LimitRule?) {
        if (rule == null) ledger.deleteLimit(profileId, categoryId) else ledger.upsertLimit(CategoryLimitEntity(profileId, categoryId,
            if (rule is LimitRule.Percent) "PERCENT" else "FIXED", when (rule) { is LimitRule.Percent -> rule.bp.value.toLong(); is LimitRule.Fixed -> rule.amount.halalas }))
        audit("limit", "CategoryLimit", "$profileId/$categoryId", rule.toString())
    }
    suspend fun openManualCycle(profileId: String, plannedBase: Money?) {
        val at = ms(); ledger.upsertCycle(FinancialCycleEntity("$profileId:m:$at", profileId, CycleType.MANUAL.name, at, null, null, plannedBase?.halalas, true))
        audit("open_cycle", "FinancialCycle", profileId, plannedBase.toString())
    }

    // ---------------- savings (§10–§12) ----------------
    suspend fun approveProvisional(cycleId: String) {
        val cur = settings.setting(SettingKeys.PROVISIONAL_APPROVED).orEmpty()
        settings.upsertSetting(AppSettingEntity(SettingKeys.PROVISIONAL_APPROVED, (cur.split(',') + cycleId).filter { it.isNotBlank() }.distinct().joinToString(",")))
        audit("approve_provisional", "FinancialCycle", cycleId)
    }
    suspend fun createGoal(name: String, profileId: String, requestCycleId: String?, target: Money, destinationAccountId: String): String {
        val g = SavingsGoalEntity(id(), name, profileId, requestCycleId, target.halalas, ms(), destinationAccountId, false)
        plan.upsertGoal(g); audit("create", "SavingsGoal", g.id, "target=$target dest=$destinationAccountId"); return g.id
    }
    suspend fun setGoalDestination(goalId: String, accountId: String) {
        val g = plan.goal(goalId) ?: return
        plan.upsertGoal(g.copy(destinationAccountId = accountId)); audit("destination", "SavingsGoal", goalId, accountId)
    }
    suspend fun goalName(goalId: String): String? = plan.goal(goalId)?.name
    fun observeGoalNames(): Flow<Map<String, String>> = plan.observeGoals().map { l -> l.associate { it.id to it.name } }
    suspend fun saveAllocations(items: List<InstallmentAllocation>, transfer: VerifiedSavingsTransfer, goals: List<SavingsGoal>) = db.withTransaction {
        require(Money.sum(items.map { it.amount }) <= transfer.amount) { "allocation exceeds transfer" }
        for (a in items) {
            plan.upsertInstallment(SavingsInstallmentEntity(a.transferId, a.goalId, a.amount.halalas, a.userConfirmed, a.settlement.name, ms()))
            val goal = goals.first { it.id == a.goalId }
            val settlementId = "settle:${a.transferId}:${a.goalId}"
            val s = savingsEngine.settlementFor(a, transfer, goal)
            if (s != null) ledger.upsertAllocation(BudgetAllocationEntity(settlementId, "SETTLEMENT", s.fromProfileId, s.toProfileId, null, s.amount.halalas, s.at.toEpochMilli(), true))
            else ledger.deleteAllocation(settlementId)
            audit("allocate", "SavingsInstallment", "${a.transferId}/${a.goalId}", "${a.amount} ${a.settlement}")
        }
    }

    suspend fun approvePlan(profileId: String, f: ForecastResult, p: SavingsPlan, horizonDays: Int?) {
        plan.upsertForecast(ForecastEntity(id(), profileId, ms(), horizonDays, f.horizonEnd.toEpochDay(), f.liquidityAfterObligations.halalas,
            f.reserve.halalas, f.availableForSavings.halalas, p.installments.joinToString(";") { "${it.goalId}=${it.amount.halalas}" }, true))
        audit("approve_plan", "Forecast", profileId, p.total.toString())
    }

    // ---------------- obligations (§15) ----------------
    suspend fun saveObligation(o: Obligation) {
        val existing = plan.obligation(o.id)
        plan.upsertObligation(o.copy(version = (existing?.version ?: 0) + 1).toEntity(existing?.createdAt ?: ms()))
        audit(if (existing == null) "create" else "update", "RecurringObligation", o.id, "v${(existing?.version ?: 0) + 1}")
    }
    suspend fun overrideOccurrence(obligationId: String, index: Int, amount: Money?, date: LocalDate?, cancelled: Boolean) {
        plan.upsertOverride(ObligationOccurrenceEntity(obligationId, index, amount?.halalas, date?.toEpochDay(), cancelled))
        audit("override", "ObligationOccurrence", "$obligationId#$index")
    }
    suspend fun linkPayment(eventId: String, occurrenceId: String, amount: Money, snapshot: AppSnapshot) = db.withTransaction {
        val e = snapshot.events.first { it.id == eventId }
        ObligationLedger.link(snapshot.payments, ObligationPayment(eventId, occurrenceId, amount), e) // validation
        plan.insertPayment(ObligationPaymentMatchEntity(eventId, occurrenceId, amount.halalas, ms()))
        audit("link_payment", "ObligationPaymentMatch", eventId, occurrenceId)
    }

    suspend fun alreadySentAlerts(): Set<String> = settings.sentAlerts().toSet()
    suspend fun markAlertSent(key: String) = settings.markAlertSent(AlertSentEntity(key, ms()))
}
