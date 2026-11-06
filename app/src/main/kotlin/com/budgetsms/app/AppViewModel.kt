package com.budgetsms.app

import android.app.Application
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.budgetsms.app.reports.ReportExporter
import com.budgetsms.app.sms.DeviceSmsSource
import com.budgetsms.data.AppSnapshot
import com.budgetsms.data.SettingKeys
import com.budgetsms.data.SmsBackupReader
import com.budgetsms.data.security.BackupCrypto
import com.budgetsms.data.security.PinHasher
import com.budgetsms.domain.accounting.AlertType
import com.budgetsms.domain.appearance.AppearancePreference
import com.budgetsms.domain.forecast.ForecastInput
import com.budgetsms.domain.forecast.ForecastResult
import com.budgetsms.domain.forecast.LiquidityForecaster
import com.budgetsms.domain.forecast.OpenGoal
import com.budgetsms.domain.forecast.SavingsPlan
import com.budgetsms.domain.forecast.SavingsPlanner
import com.budgetsms.domain.model.*
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.obligations.Obligation
import com.budgetsms.domain.savings.InstallmentAllocation
import com.budgetsms.domain.savings.SavingsGoal
import com.budgetsms.domain.savings.VerifiedSavingsTransfer
import com.budgetsms.domain.sms.SenderNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class UiMessage(@StringRes val res: Int, val arg: String? = null, val id: Long = System.nanoTime())

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as BudgetApp).container!!
    private val repo = container.repository
    private val device = DeviceSmsSource(app)

    val snapshot: StateFlow<AppSnapshot> = repo.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, AppSnapshot())
    val appearance: StateFlow<AppearancePreference?> = repo.appearance.map<AppearancePreference, AppearancePreference?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val goalNames: StateFlow<Map<String, String>> = repo.observeGoalNames().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val audit = repo.audit.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val templates = repo.templates.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Learns a sample, then re-reads stored messages and re-imports from the device so similar messages are processed. */
    fun saveSample(type: com.budgetsms.domain.sms.SampleType, text: String, amount: Money?, dateOrder: com.budgetsms.domain.sms.DateOrder?) = act(R.string.msg_sample_saved) {
        repo.saveSample(type, text, amount, dateOrder); repo.reprocessUndecided(all = true)
        if (device.hasReadPermission()) syncDeviceInternal()
    }
    fun deleteSample(id: String) = act { repo.deleteSample(id); repo.reprocessUndecided(all = true) }

    val selectedProfile = MutableStateFlow<String?>(null)
    private val _busy = MutableStateFlow(false); val busy: StateFlow<Boolean> = _busy
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8); val messages: SharedFlow<UiMessage> = _messages
    private val _locked = MutableStateFlow(true); val locked: StateFlow<Boolean> = _locked
    private val _success = MutableStateFlow(0); val success: StateFlow<Int> = _success
    var backgroundedAt: Long = 0L

    init {
        repo.defaultProfileName = app.getString(R.string.default_profile_name)
        viewModelScope.launch {
            snapshot.filter { it.ledger != null }.collect { dispatchAlerts(it) }
        }
        viewModelScope.launch {
            val enabled = repo.setting(SettingKeys.LOCK_ENABLED) == "true"
            if (!enabled) _locked.value = false
        }
    }

    private fun say(@StringRes res: Int, arg: String? = null) { _messages.tryEmit(UiMessage(res, arg)) }
    private fun celebrate() { _success.value++ }

    private fun act(@StringRes ok: Int? = null, block: suspend () -> Unit) = viewModelScope.launch {
        try { _busy.value = true; block(); if (ok != null) { say(ok); celebrate() } }
        catch (t: Throwable) { say(R.string.msg_error, t.message ?: t.javaClass.simpleName) }
        finally { _busy.value = false }
    }

    private suspend fun dispatchAlerts(s: AppSnapshot) {
        val sent = repo.alreadySentAlerts()
        val ctx = getApplication<Application>()
        for (a in s.ledger!!.alerts) {
            if (a.key in sent) continue
            val title = ctx.getString(when (a.type) {
                AlertType.BUDGET_NEGATIVE -> R.string.alert_budget_negative; AlertType.LIMIT_EXCEEDED -> R.string.alert_limit
                AlertType.CHILD_LIMITS_EXCEED_PARENT -> R.string.alert_child_limits; AlertType.TRANSFER_INCOMING_MISSING -> R.string.alert_transfer_missing
                AlertType.BALANCE_MISMATCH -> R.string.alert_balance_mismatch; AlertType.INSTALLMENT_UNFUNDED -> R.string.alert_installment
                AlertType.OBLIGATION_OVERDUE -> R.string.alert_overdue; AlertType.LIQUIDITY_THRESHOLD -> R.string.alert_liquidity
            })
            container.notifier.alert(a.key, title, ctx.getString(R.string.alert_open_app))
            repo.markAlertSent(a.key)
        }
        for (o in s.occurrences.filter { it.state == com.budgetsms.domain.obligations.OccurrenceState.OVERDUE }) {
            val key = "overdue:${o.occurrence.id}"
            if (key in sent) continue
            container.notifier.alert(key, ctx.getString(R.string.alert_overdue), ctx.getString(R.string.alert_open_app)); repo.markAlertSent(key)
        }
    }

    // ---------------- lock ----------------
    fun unlock() { _locked.value = false }
    fun lockIfNeeded() = viewModelScope.launch { if (repo.setting(SettingKeys.LOCK_ENABLED) == "true") _locked.value = true }
    suspend fun verifyPin(pin: String): Boolean = withContext(Dispatchers.Default) { repo.setting(SettingKeys.PIN_HASH)?.let { PinHasher.verify(pin.toCharArray(), it) } == true }
    fun setPin(pin: String) = act(R.string.msg_saved) {
        val h = withContext(Dispatchers.Default) { PinHasher.hash(pin.toCharArray()) }
        repo.setSetting(SettingKeys.PIN_HASH, h); repo.setSetting(SettingKeys.LOCK_ENABLED, "true")
    }
    fun disableLock() = act(R.string.msg_saved) { repo.setSetting(SettingKeys.LOCK_ENABLED, "false") }
    fun setSetting(key: String, value: String) = act { repo.setSetting(key, value) }

    // ---------------- appearance ----------------
    fun updateAppearance(f: (AppearancePreference) -> AppearancePreference) = viewModelScope.launch { repo.updateAppearance(f) }

    // ---------------- senders & SMS ----------------
    fun discoverSenders() = act(R.string.msg_discovered) {
        val d = withContext(Dispatchers.IO) { device.discoverHeaders() }
        repo.saveDiscovered(d.senders, d.rawAddresses)
    }

    fun approveSender(senderId: String, bankName: String?, bankId: String?) = act(R.string.msg_sender_approved) {
        val bid = bankId ?: repo.addBank(bankName!!.ifBlank { senderId }, BANK_COLORS[(senderId.hashCode() and 0x7fffffff) % BANK_COLORS.size])
        repo.approveSender(senderId, bid)
        syncDeviceInternal()
    }
    fun revokeSender(senderId: String) = act(R.string.msg_sender_revoked) { repo.revokeSender(senderId) }

    /** New profile linked to a newly approved bank sender; its messages are imported into this profile only. */
    fun addProfileWithBank(name: String, type: CycleType, senderId: String, bankName: String) = act(R.string.msg_profile_added) {
        val pid = repo.saveProfile(Profile(newProfileId(), name, type), BANK_COLORS[(name.hashCode() and 0x7fffffff) % BANK_COLORS.size], "person")
        val bid = repo.addBank(bankName.ifBlank { senderId }, BANK_COLORS[(senderId.hashCode() and 0x7fffffff) % BANK_COLORS.size])
        repo.approveSender(senderId, bid, pid)
        selectedProfile.value = pid
        syncDeviceInternal()
    }
    fun deleteProfile(profileId: String) = act(R.string.msg_profile_deleted) {
        if (selectedProfile.value == profileId) selectedProfile.value = null
        repo.deleteProfile(profileId)
    }
    fun setAutoConfirm(senderId: String, on: Boolean) = act(R.string.msg_saved) { repo.setAutoConfirm(senderId, on) }

    fun syncDevice() = act { syncDeviceInternal() }

    private suspend fun syncDeviceInternal() {
        val approved = repo.approvedSenders().map { it.senderId }.toSet()
        val raw = snapshot.value.discoveredSenders.filter { it.senderId in approved }
            .flatMap { it.rawAddresses.split('\u001F') }.filter { it.isNotBlank() && SenderNormalizer.normalize(it) in approved }
        val batch = withContext(Dispatchers.IO) { device.readApproved(raw) }
        reportImport(repo.ingestFromLastSalary(batch))
    }

    private fun reportImport(r: Pair<com.budgetsms.domain.ingest.IngestResult, Instant?>) {
        val (res, salaryAt) = r
        val locale = getApplication<Application>().resources.configuration.locales[0]
        if (salaryAt != null) say(R.string.msg_import_from_salary, com.budgetsms.app.ui.components.Fmt.day(salaryAt, locale) + " · " + res.newEvents.size)
        else say(R.string.msg_import_no_salary, res.newEvents.size.toString())
    }

    fun importSmsBackup(uri: Uri) = act {
        val ctx = getApplication<Application>()
        val size = ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
        val batch = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { SmsBackupReader.read(it, size, uri.lastPathSegment ?: "file") } }
        // Discover headers from the file too, so the user can approve new senders (bodies are only read once approved).
        val grouped = batch.groupBy { SenderNormalizer.normalize(it.header.sender) }
        repo.saveDiscovered(com.budgetsms.domain.sms.SenderDiscovery.summarize(batch.asSequence().map { it.header }),
            grouped.mapValues { (_, v) -> v.map { it.header.sender }.toSet() })
        reportImport(repo.ingestFromLastSalary(batch))
    }

    fun reprocess() = act(R.string.msg_reprocessed) { repo.reprocessUndecided() }

    // ---------------- review decisions ----------------
    fun confirmEvent(id: String, kind: EventKind, accountId: String?, counterparty: String?, category: String?, treatment: IncomeTreatment?, amount: Money? = null) =
        act(R.string.msg_confirmed) { repo.confirmEvent(id, kind, accountId, counterparty, category, treatment, amount) }
    fun markDuplicate(id: String) = act(R.string.msg_saved) { repo.markDuplicate(id) }
    fun setCategory(id: String, cat: String?) = act { repo.setCategory(id, cat) }
    fun setTreatment(id: String, t: IncomeTreatment?) = act { repo.setTreatment(id, t) }
    fun linkCorrection(id: String, original: String?) = act(R.string.msg_saved) { repo.linkCorrection(id, original) }
    fun correctTime(id: String, at: Instant) = act(R.string.msg_saved) { repo.correctTime(id, at) }
    fun approveMatch(out: String, inc: String) = act(R.string.msg_matched) { repo.approveTransferMatch(out, inc) }
    fun rejectMatch(out: String, inc: String) = act(R.string.msg_saved) { repo.rejectTransferMatch(out, inc) }
    fun rawSms(fp: String) = repo.observeRaw(fp)

    // ---------------- profiles / accounts / categories ----------------
    fun saveProfile(p: Profile, color: Long, icon: String, horizonDays: Int = 30) = act(R.string.msg_saved) { repo.saveProfile(p, color, icon, horizonDays) }
    fun newProfileId() = UUID.randomUUID().toString()
    fun saveAccount(a: Account) = act(R.string.msg_saved) { repo.saveAccount(a) }
    fun addBank(name: String) = act(R.string.msg_saved) { repo.addBank(name, BANK_COLORS[(name.hashCode() and 0x7fffffff) % BANK_COLORS.size]) }
    fun previewReassign(accountId: String, profileId: String, from: Instant) = repo.previewAccountProfileChange(accountId, profileId, from)
    fun reassignAccount(accountId: String, profileId: String, from: Instant) = act(R.string.msg_saved) { repo.changeAccountProfile(accountId, profileId, from) }
    fun saveCategory(c: Category) = act(R.string.msg_saved) { repo.saveCategory(c) }
    fun setLimit(profileId: String, categoryId: String, rule: LimitRule?) = act(R.string.msg_saved) { repo.setLimit(profileId, categoryId, rule) }
    fun openManualCycle(profileId: String, planned: Money?) = act(R.string.msg_cycle_opened) { repo.openManualCycle(profileId, planned) }

    // ---------------- savings ----------------
    fun approveProvisional(cycleId: String) = act(R.string.msg_saved) { repo.approveProvisional(cycleId) }
    fun createGoal(name: String, profileId: String, cycleId: String?, target: Money, destination: String) = act(R.string.msg_goal_created) { repo.createGoal(name, profileId, cycleId, target, destination) }
    fun setGoalDestination(goalId: String, accountId: String) = act(R.string.msg_saved) { repo.setGoalDestination(goalId, accountId) }
    fun saveAllocations(items: List<InstallmentAllocation>, t: VerifiedSavingsTransfer, goals: List<SavingsGoal>) = act(R.string.msg_allocated) { repo.saveAllocations(items, t, goals) }

    fun forecast(profileId: String?, horizon: ForecastHorizon, accounts: Set<String>, reserve: ReservePolicy): Pair<ForecastResult, SavingsPlan> {
        val s = snapshot.value
        val balances = s.ledger?.balances.orEmpty()
        val occ = s.occurrences.filter { profileId == null || it.occurrence.profileId == profileId }
        val f = LiquidityForecaster.forecast(ForecastInput(s.today, horizon, balances, accounts, occ, null, reserve))
        val goals = s.goals.filter { profileId == null || it.goal.profileId == profileId }.sortedBy { it.goal.createdAt }
            .mapIndexed { i, g -> OpenGoal(g.goal.id, g.remaining, i) }
        return f to SavingsPlanner.propose(goals, f.availableForSavings)
    }
    fun approvePlan(profileId: String, f: ForecastResult, p: SavingsPlan, horizonDays: Int?) = act(R.string.msg_plan_approved) { repo.approvePlan(profileId, f, p, horizonDays) }

    // ---------------- obligations ----------------
    fun saveObligation(o: Obligation) = act(R.string.msg_saved) { repo.saveObligation(o) }
    fun overrideOccurrence(obligationId: String, index: Int, amount: Money?, date: LocalDate?, cancelled: Boolean) = act(R.string.msg_saved) { repo.overrideOccurrence(obligationId, index, amount, date, cancelled) }
    fun linkPayment(eventId: String, occurrenceId: String, amount: Money) = act(R.string.msg_payment_linked) { repo.linkPayment(eventId, occurrenceId, amount, snapshot.value) }

    // ---------------- backup & reports ----------------
    fun exportBackup(uri: Uri, password: String) = act(R.string.msg_backup_done) {
        val bytes = container.backup.export(password.toCharArray())
        withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openOutputStream(uri)!!.use { it.write(bytes) } }
    }
    fun restoreBackup(uri: Uri, password: String) = viewModelScope.launch {
        _busy.value = true
        try {
            val bytes = withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
            val r = container.backup.restore(bytes, password.toCharArray())
            say(R.string.msg_restored, "${r.inserted}/${r.skippedDuplicates}"); celebrate()
        } catch (e: BackupCrypto.WrongPasswordOrCorrupted) { say(R.string.msg_wrong_password) }
        catch (t: Throwable) { say(R.string.msg_error, t.message ?: t.javaClass.simpleName) }
        finally { _busy.value = false }
    }

    suspend fun exportReport(pdf: Boolean, profileId: String?, cycleId: String?): File = withContext(Dispatchers.IO) {
        val ctx = getApplication<Application>()
        ReportExporter(ctx).export(snapshot.value, profileId, cycleId, pdf, goalNames.value)
    }

    fun completeOnboarding() = act { repo.setSetting(SettingKeys.ONBOARDING_DONE, "true") }

    companion object {
        val BANK_COLORS = listOf(0xFF0F8A8F, 0xFF1F6FD1, 0xFF8A47D6, 0xFFD23F73, 0xFFD9901A, 0xFF1E9E63, 0xFF4B4FD8, 0xFF56636E)
    }
}
