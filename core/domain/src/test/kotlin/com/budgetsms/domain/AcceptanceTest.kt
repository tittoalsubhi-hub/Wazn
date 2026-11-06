package com.budgetsms.domain

import com.budgetsms.domain.accounting.AlertType
import com.budgetsms.domain.accounting.ApprovedTransferMatch
import com.budgetsms.domain.accounting.LedgerEngine
import com.budgetsms.domain.accounting.LedgerInput
import com.budgetsms.domain.accounting.ManualOpening
import com.budgetsms.domain.appearance.ColorMath
import com.budgetsms.domain.appearance.Palette
import com.budgetsms.domain.forecast.ForecastInput
import com.budgetsms.domain.forecast.LiquidityForecaster
import com.budgetsms.domain.forecast.OpenGoal
import com.budgetsms.domain.forecast.SavingsPlanner
import com.budgetsms.domain.ingest.IncomingSms
import com.budgetsms.domain.ingest.IngestState
import com.budgetsms.domain.ingest.IngestionPipeline
import com.budgetsms.domain.model.AccountType
import com.budgetsms.domain.model.Category
import com.budgetsms.domain.model.CategoryLimit
import com.budgetsms.domain.model.CycleType
import com.budgetsms.domain.model.EventKind.*
import com.budgetsms.domain.model.EventState
import com.budgetsms.domain.model.ForecastHorizon
import com.budgetsms.domain.model.LimitRule
import com.budgetsms.domain.model.ReservePolicy
import com.budgetsms.domain.model.ReviewReason
import com.budgetsms.domain.money.Balance
import com.budgetsms.domain.money.BasisPoints
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.obligations.ObligationLedger
import com.budgetsms.domain.obligations.ObligationPayment
import com.budgetsms.domain.obligations.ObligationScheduler
import com.budgetsms.domain.obligations.ObligationType
import com.budgetsms.domain.obligations.Obligation
import com.budgetsms.domain.obligations.OccurrenceState
import com.budgetsms.domain.obligations.PaymentLinkException
import com.budgetsms.domain.obligations.ProfileChange
import com.budgetsms.domain.savings.InstallmentAllocation
import com.budgetsms.domain.savings.SavingsEligibility
import com.budgetsms.domain.savings.SavingsEngine
import com.budgetsms.domain.savings.SavingsGoal
import com.budgetsms.domain.savings.SettlementDecision
import com.budgetsms.domain.savings.VerifiedSavingsTransfer
import com.budgetsms.domain.sms.ApprovedSender
import com.budgetsms.domain.sms.ConsentGate
import com.budgetsms.domain.sms.ImportSource
import com.budgetsms.domain.sms.ParserRegistry
import com.budgetsms.domain.sms.SmsHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class AcceptanceTest {
    private val engine = LedgerEngine()
    private val now = t("2026-03-10T12:00")

    private fun pipeline(accounts: List<com.budgetsms.domain.model.Account> = listOf(account("a1111", "A"))) = IngestionPipeline(
        ConsentGate(listOf(ApprovedSender("TESTBANK-SMS", "TESTBANK", now))), ParserRegistry(listOf(FixtureParser)), accounts, RIYADH, { now })

    // ---------------- Phase 2 ----------------
    @Test fun t01_sameMessageFromPhoneAndBackup_oneTransaction_bothSources() {
        val p = pipeline()
        val body = "SYN kind=PURCHASE_DEBIT acc=1111 amt=120.00 ref=R100 at=2026-01-05T10:00"
        val h = SmsHeader("TESTBANK-SMS", t("2026-01-05T10:00"))
        val r1 = p.ingest(listOf(IncomingSms(h, ImportSource.DEVICE_INBOX, "inbox:55") { body }), IngestState(), "b1")
        val state = IngestState(r1.newRaw.map { it.fingerprint }.toSet(), r1.newSourceRecords.map { "${it.fingerprint}|${it.source}|${it.sourceRecordId}" }.toSet(), r1.newEvents.associateBy { it.id })
        val r2 = p.ingest(listOf(IncomingSms(h, ImportSource.BACKUP_FILE, "xml:9") { body }, IncomingSms(h, ImportSource.BACKUP_FILE, "xml:9") { body }), state, "b2")
        assertEquals(1, r1.newEvents.size); assertEquals(0, r2.newEvents.size); assertEquals(0, r2.newRaw.size)
        assertEquals(1, r2.newSourceRecords.size) // backup source preserved once, re-import idempotent
        assertEquals(EventState.CONFIRMED, r1.newEvents.single().state)
    }

    @Test fun t02_unapprovedSender_bodyNeverRead_noMovement() {
        var reads = 0
        val r = pipeline().ingest(listOf(IncomingSms(SmsHeader("FAKEBANK", now), ImportSource.DEVICE_RECEIVER, null) {
            reads++; "SYN kind=SALARY acc=1111 amt=9000.00" }), IngestState(), "b")
        assertEquals(0, reads); assertTrue(r.newEvents.isEmpty()); assertEquals(1, r.skippedUnapproved.size)
    }

    @Test fun twoValidTransfersWithSameTextButDifferentRefs_areBothKept() {
        val p = pipeline()
        val a = IncomingSms(SmsHeader("TESTBANK-SMS", t("2026-01-05T10:00")), ImportSource.DEVICE_INBOX, "1") { "SYN kind=OUTGOING_TRANSFER acc=1111 amt=50.00 ref=X1 at=2026-01-05T10:00" }
        val b = IncomingSms(SmsHeader("TESTBANK-SMS", t("2026-01-05T10:01")), ImportSource.DEVICE_INBOX, "2") { "SYN kind=OUTGOING_TRANSFER acc=1111 amt=50.00 ref=X2 at=2026-01-05T10:01" }
        val c = IncomingSms(SmsHeader("TESTBANK-SMS", t("2026-01-05T10:01")), ImportSource.DEVICE_INBOX, "3") { "SYN kind=OUTGOING_TRANSFER acc=1111 amt=50.00 at=2026-01-05T10:01" }
        val r = p.ingest(listOf(a, b, c), IngestState(), "b")
        assertEquals(3, r.newEvents.size)
        assertEquals(2, r.newEvents.count { it.state == EventState.CONFIRMED })
        assertTrue(r.newEvents.last().reviewReasons.contains(ReviewReason.POSSIBLE_DUPLICATE)) // flagged, not deleted
    }

    // ---------------- Phase 3 ----------------
    @Test fun t03_lateSalary_oldCycleStaysOpenUntilSalaryArrives() {
        val base = listOf(ev("s1", SALARY, "a1", 10000, "2026-01-27T09:00"))
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = base, now = t("2026-03-02T00:00")))
        assertEquals(1, r.cycles.size); assertTrue(r.cycles.single().isOpen)
        val r2 = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = base + ev("s2", SALARY, "a1", 10000, "2026-03-03T09:00"), now = now))
        assertEquals(2, r2.cycles.size); assertEquals(t("2026-03-03T09:00"), r2.cycles[0].closedAt)
    }

    @Test fun t04_allowance_increasesBudget_notPercentLimits() {
        val cats = listOf(Category("food", "Food", "طعام"))
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")),
            events = listOf(ev("s1", SALARY, "a1", 10000, "2026-02-01T09:00"), ev("al", ALLOWANCE, "a1", 2000, "2026-02-03T09:00")),
            categories = cats, limits = listOf(CategoryLimit("A", "food", LimitRule.Percent(BasisPoints.percent(30)))), now = now))
        val b = r.budgetOf("A")!!
        assertEquals(1, r.cycles.size); assertEquals(sar(12000), b.funded)
        assertEquals(sar(3000), b.categories.single { it.categoryId == "food" }.limit)
    }

    private val twoProfiles = listOf(profile("A"), profile("B"))
    private val twoAccounts = listOf(account("a1", "A"), account("b1", "B"))
    private val salaries = listOf(ev("sa", SALARY, "a1", 10000, "2026-02-01T09:00", bal = 10000), ev("sb", SALARY, "b1", 3000, "2026-02-01T09:00", bal = 3000))

    @Test fun t05_crossProfileTransfer_reallocatesOnce_incomingUpdatesBalance() {
        val out = ev("o1", OUTGOING_TRANSFER, "a1", 2000, "2026-02-05T10:00", to = "b1")
        val r1 = engine.compute(LedgerInput(twoProfiles, twoAccounts, events = salaries + out, now = now))
        assertEquals(sar(8000), r1.budgetOf("A")!!.funded); assertEquals(sar(5000), r1.budgetOf("B")!!.funded)
        assertEquals(Balance.Known(sar(3000), sar(3000), t("2026-02-01T09:00")), r1.balances["b1"]) // unproven incoming not assumed
        val inc = ev("i1", INCOMING_TRANSFER, "b1", 2000, "2026-02-05T10:02")
        val r2 = engine.compute(LedgerInput(twoProfiles, twoAccounts, events = salaries + out + inc, now = now))
        assertEquals(sar(5000), r2.budgetOf("B")!!.funded) // not reallocated twice
        assertEquals(sar(5000), (r2.balances["b1"] as Balance.Known).amount)
        assertEquals("i1", r2.transfers.single().incomingId)
        // conservation: consolidated funding unchanged by internal transfer
        assertEquals(sar(13000), r2.budgetOf("A")!!.funded + r2.budgetOf("B")!!.funded)
    }

    @Test fun t06_transferMakesSourceNegative_completedWithAlert() {
        val evs = listOf(ev("sa", SALARY, "a1", 1000, "2026-02-01T09:00"), ev("sb", SALARY, "b1", 1000, "2026-02-01T09:00"),
            ev("p", PURCHASE_DEBIT, "a1", 500, "2026-02-02T09:00"), ev("o", OUTGOING_TRANSFER, "a1", 2000, "2026-02-03T09:00", to = "b1"))
        val r = engine.compute(LedgerInput(twoProfiles, twoAccounts, events = evs, now = now))
        assertEquals(sar(-1500), r.budgetOf("A")!!.remaining)
        assertTrue(r.alerts.any { it.type == AlertType.BUDGET_NEGATIVE })
    }

    @Test fun t07_twoSimilarTransfers_requireOneToOneReview() {
        val acc = listOf(account("a1", "A"), account("a2", "A", AccountType.SAVINGS))
        val evs = listOf(ev("o1", OUTGOING_TRANSFER, "a1", 1000, "2026-02-05T10:00", to = "a2"), ev("o2", OUTGOING_TRANSFER, "a1", 1000, "2026-02-05T10:05", to = "a2"),
            ev("i1", INCOMING_TRANSFER, "a2", 1000, "2026-02-05T10:06"), ev("i2", INCOMING_TRANSFER, "a2", 1000, "2026-02-05T10:07"))
        val r = engine.compute(LedgerInput(listOf(profile("A")), acc, events = evs, now = now))
        assertTrue(r.transfers.all { it.incomingId == null })
        assertEquals(2, r.reviews.count { ReviewReason.TRANSFER_TIE in it.reasons })
        val r2 = engine.compute(LedgerInput(listOf(profile("A")), acc, events = evs, now = now,
            approvedMatches = listOf(ApprovedTransferMatch("o1", "i2"), ApprovedTransferMatch("o2", "i1"))))
        assertEquals(setOf("i1", "i2"), r2.transfers.mapNotNull { it.incomingId }.toSet())
    }

    @Test fun t08_cardRepayment_onlyExpense_notCardPurchases() {
        val acc = listOf(account("a1", "A"), account("c1", "A", AccountType.CREDIT_CARD))
        val evs = listOf(ev("s", SALARY, "a1", 10000, "2026-02-01T09:00"), ev("cp1", PURCHASE_CREDIT_CARD, "c1", 300, "2026-02-02T09:00"),
            ev("cp2", PURCHASE_CREDIT_CARD, "c1", 200, "2026-02-03T09:00"), ev("rp", CARD_REPAYMENT, "a1", 500, "2026-02-10T09:00", to = "c1"),
            ev("cin", INCOMING_TRANSFER, "c1", 500, "2026-02-10T09:01"))
        val b = engine.compute(LedgerInput(listOf(profile("A")), acc, events = evs, now = now)).budgetOf("A")!!
        assertEquals(sar(500), b.spent); assertEquals(sar(10000), b.funded)
    }

    @Test fun t09_refundForClosedCycle_correctsOriginalCycle() {
        val evs = listOf(ev("s1", SALARY, "a1", 5000, "2026-01-01T09:00"), ev("p", PURCHASE_DEBIT, "a1", 400, "2026-01-10T09:00", category = "shop"),
            ev("s2", SALARY, "a1", 5000, "2026-02-01T09:00"), ev("rf", REFUND, "a1", 100, "2026-02-05T09:00", corrects = "p"))
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = evs, now = now))
        val c1 = r.budgets.getValue(r.cycles[0].id); val c2 = r.budgets.getValue(r.cycles[1].id)
        assertEquals(sar(300), c1.spent); assertEquals(sar(4700), c1.remaining); assertEquals(Money.ZERO, c2.spent)
        assertTrue(c2.reallocatedIn.isZero && c2.reallocatedOut.isZero) // no fictional savings movement
    }

    @Test fun t10_manualCyclePlanned5000_noFunding() {
        val r = engine.compute(LedgerInput(listOf(profile("M", CycleType.MANUAL)), listOf(account("m1", "M")), events = emptyList(),
            manualOpenings = listOf(ManualOpening("M", t("2026-02-01T00:00"), sar(5000))), now = now))
        val b = r.budgetOf("M")!!
        assertEquals(sar(5000), b.planned); assertEquals(Money.ZERO, b.funded)
        assertNull(SavingsEligibility.requestFor(b, t("2026-01-01T00:00")))
    }

    @Test fun t11_childLimitsAboveParent_warnWithoutDuplicatedFunding() {
        val cats = listOf(Category("food", "Food", "طعام"), Category("rest", "Restaurants", "مطاعم", "food"), Category("groc", "Groceries", "بقالة", "food"))
        val limits = listOf(CategoryLimit("A", "food", LimitRule.Fixed(sar(5000))), CategoryLimit("A", "rest", LimitRule.Fixed(sar(4000))), CategoryLimit("A", "groc", LimitRule.Fixed(sar(2500))))
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")),
            events = listOf(ev("s", SALARY, "a1", 10000, "2026-02-01T09:00"), ev("p", PURCHASE_DEBIT, "a1", 4200, "2026-02-03T09:00", category = "rest")),
            categories = cats, limits = limits, now = now))
        val b = r.budgetOf("A")!!
        assertEquals(listOf("food"), b.limitWarnings)
        assertEquals(sar(5000), b.unallocated) // 10000 - parent cap only, not 11500
        assertTrue(b.categories.single { it.categoryId == "rest" }.overspent)
        assertFalse(b.categories.single { it.categoryId == "food" }.overspent)
    }

    @Test fun t15_lateMessageWithEarlierExecutionDate_assignedToEarlierCycle() {
        val evs = listOf(ev("s1", SALARY, "a1", 5000, "2026-01-01T09:00"), ev("s2", SALARY, "a1", 5000, "2026-02-01T10:00"),
            ev("late", PURCHASE_DEBIT, "a1", 250, "2026-01-31T23:00"))
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = evs, now = now))
        assertEquals(r.cycles[0].id, r.assignment["late"])
        val receiptOnly = evs.dropLast(1) + ev("late", PURCHASE_DEBIT, "a1", 250, "2026-02-01T11:00", timeSource = com.budgetsms.domain.model.TimeSource.RECEIPT)
        val r2 = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = receiptOnly, now = now))
        assertTrue(r2.reviews.any { it.eventId == "late" && ReviewReason.CYCLE_BOUNDARY in it.reasons })
    }

    @Test fun idempotentRecalculation_andNoDuplicatesInConsolidation() {
        val input = LedgerInput(twoProfiles, twoAccounts, events = salaries + ev("o1", OUTGOING_TRANSFER, "a1", 2000, "2026-02-05T10:00", to = "b1") + ev("i1", INCOMING_TRANSFER, "b1", 2000, "2026-02-05T10:02"), now = now)
        assertEquals(engine.compute(input), engine.compute(input))
        val r = engine.compute(input)
        val consolidatedSpent = r.budgets.values.fold(Money.ZERO) { a, b -> a + b.spent }
        assertEquals(Money.ZERO, consolidatedSpent)
    }

    @Test fun fxWithoutSarDebit_isPendingNotSpent_andFeeSeparate() {
        val evs = listOf(ev("s", SALARY, "a1", 5000, "2026-02-01T09:00"), ev("fx", PURCHASE_DEBIT, "a1", null, "2026-02-02T09:00", state = EventState.PENDING_FX),
            ev("p", PURCHASE_DEBIT, "a1", 100, "2026-02-03T09:00", fee = 2))
        val b = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = evs, now = now)).budgetOf("A")!!
        assertEquals(sar(102), b.spent); assertTrue(b.categories.any { it.categoryId == LedgerEngine.FEES_CATEGORY && it.spent == sar(2) })
    }

    // ---------------- Phase 4 ----------------
    @Test fun t12_crossProfileSavings_settlementRejected_userChooses() {
        val engineS = SavingsEngine()
        val goal = SavingsGoal("g1", "B", "cycB", sar(1000), t("2026-02-01T00:00"), "sv1")
        val tr = VerifiedSavingsTransfer("o1", sar(1000), "a1", "A", "sv1", t("2026-02-05T10:00"), incomingMatched = true)
        val allocate = InstallmentAllocation("o1", "g1", sar(1000), true, SettlementDecision.REJECTED_ALLOCATE_WITHOUT_SETTLEMENT)
        val (gs, _) = engineS.status(listOf(goal), listOf(tr), listOf(allocate))
        assertEquals(sar(1000), gs.single().confirmedSaved); assertNull(engineS.settlementFor(allocate, tr, goal))
        val pending = allocate.copy(settlement = SettlementDecision.REJECTED_LEAVE_PENDING)
        val (gs2, ts2) = engineS.status(listOf(goal), listOf(tr), listOf(pending))
        assertEquals(Money.ZERO, gs2.single().confirmedSaved); assertEquals(sar(1000), gs2.single().pendingAllocation)
        assertEquals(sar(1000), ts2.single().transfer.amount) // real bank transfer preserved
        assertEquals(SettlementDecision.PROPOSED, engineS.proposeAllocation(tr, engineS.status(listOf(goal), listOf(tr), emptyList()).first).single().settlement)
    }

    @Test fun t13_provisionalSurplus_suspendedUnlessApproved() {
        val evs = listOf(ev("s1", SALARY, "a1", 10000, "2026-01-01T09:00"), ev("p", PURCHASE_DEBIT, "a1", 6000, "2026-01-05T09:00"),
            ev("u", PURCHASE_DEBIT, "a1", 700, "2026-01-06T09:00", state = EventState.UNDER_REVIEW), ev("s2", SALARY, "a1", 10000, "2026-02-01T09:00"))
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = evs, now = now))
        val b = r.budgets.getValue(r.cycles[0].id)
        val req = SavingsEligibility.requestFor(b, activationAt = t("2026-01-03T00:00"))!!
        assertEquals(sar(4000), req.target); assertTrue(req.provisional); assertTrue(req.approvalSuspended); assertEquals(sar(700), req.unresolvedAmount)
        assertFalse(SavingsEligibility.requestFor(b, t("2026-01-03T00:00"), setOf(r.cycles[0].id))!!.approvalSuspended)
        assertNull(SavingsEligibility.requestFor(b, activationAt = t("2026-02-02T00:00"))) // historical cycle
    }

    @Test fun savings_splitOldestFirst_andTargetCorrectionShowsExtra() {
        val s = SavingsEngine()
        val g1 = SavingsGoal("g1", "A", null, sar(600), t("2026-01-01T00:00"), "sv"); val g2 = SavingsGoal("g2", "A", null, sar(1000), t("2026-02-01T00:00"), "sv")
        val tr = VerifiedSavingsTransfer("o", sar(1000), "a1", "A", "sv", t("2026-02-05T00:00"), true)
        val prop = s.proposeAllocation(tr, s.status(listOf(g2, g1), listOf(tr), emptyList()).first)
        assertEquals(listOf("g1" to sar(600), "g2" to sar(400)), prop.map { it.goalId to it.amount })
        val confirmed = prop.map { it.copy(userConfirmed = true) }
        val (gs, _) = s.status(listOf(s.retarget(g1, sar(500)), g2), listOf(tr), confirmed)
        assertEquals(sar(100), gs.first { it.goal.id == "g1" }.additionalSavings)
        assertTrue(s.validate(listOf(tr), confirmed + InstallmentAllocation("o", "g3", sar(1), true, SettlementDecision.NOT_NEEDED)).isNotEmpty())
    }

    // ---------------- Phase 5 ----------------
    @Test fun t14_liquidityDrop_proposesNewPlan_keepsApproved() {
        val approved = SavingsPlanner.propose(listOf(OpenGoal("g", sar(6000), 0)), sar(5000))
        val bal = mapOf("a1" to Balance.Known(sar(3500), sar(3500), now))
        val f = LiquidityForecaster.forecast(ForecastInput(LocalDate.of(2026, 3, 10), ForecastHorizon.Days(30), bal, setOf("a1"), emptyList(), null, ReservePolicy.None))
        val proposed = SavingsPlanner.propose(listOf(OpenGoal("g", sar(6000), 0)), f.availableForSavings)
        val cmp = SavingsPlanner.compare(approved, proposed)
        assertEquals(sar(5000), cmp.approved.total); assertEquals(sar(3500), cmp.proposed.total); assertTrue(cmp.needsRenewedApproval)
    }

    @Test fun t16_reserve20Percent_suggests4000_keeps6000() {
        val bal = mapOf("a1" to Balance.Known(sar(5000), sar(5000), now))
        val f = LiquidityForecaster.forecast(ForecastInput(LocalDate.of(2026, 3, 10), ForecastHorizon.Days(30), bal, setOf("a1"), emptyList(), null, ReservePolicy.PercentOfLiquidity(BasisPoints.percent(20))))
        val plan = SavingsPlanner.propose(listOf(OpenGoal("g", sar(6000), 0)), f.availableForSavings)
        assertEquals(sar(4000), plan.total); assertEquals(sar(6000), plan.totalTarget); assertEquals(sar(2000), plan.gap)
    }

    private val rent = Obligation("rent", "Rent", profileId = "A", amount = sar(1000), type = ObligationType.MONTHLY, startDate = LocalDate.of(2026, 1, 5), occurrenceCount = 12)

    @Test fun t17_monthlyObligation_12Occurrences_noBankExpense() {
        val occ = ObligationScheduler.generate(rent)
        assertEquals(12, occ.size); assertEquals(12, occ.map { it.dueDate }.toSet().size)
        assertEquals(occ, ObligationScheduler.generate(rent)) // idempotent
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = listOf(ev("s", SALARY, "a1", 5000, "2026-01-01T09:00")), now = now))
        assertEquals(Money.ZERO, r.budgetOf("A")!!.spent)
    }

    @Test fun monthEndPolicy_mustBeChosenExplicitly() {
        try { ObligationScheduler.generate(rent.copy(dayOfMonth = 31)); fail() } catch (e: com.budgetsms.domain.obligations.PolicyRequiredException) {}
        val occ = ObligationScheduler.generate(rent.copy(startDate = LocalDate.of(2026, 1, 31), dayOfMonth = 31, monthEndPolicy = com.budgetsms.domain.obligations.MonthEndPolicy.LAST_DAY_OF_MONTH))
        assertEquals(LocalDate.of(2026, 2, 28), occ[1].dueDate)
    }

    @Test fun t18_oneTimeObligation_singleOccurrence() {
        val today = LocalDate.of(2026, 3, 10)
        val o = Obligation("fee", "Annual fee", profileId = "A", amount = sar(800), type = ObligationType.ONE_TIME, startDate = today.plusMonths(6), remindDaysBefore = 7)
        val occ = ObligationScheduler.generate(o).single()
        val st = ObligationLedger.status(occ, emptyList(), today)
        assertEquals(OccurrenceState.UPCOMING, st.state); assertEquals(today.plusMonths(6).minusDays(7), occ.dueDate.minusDays(o.remindDaysBefore.toLong()))
    }

    @Test fun t19_partialPaymentsInTwoInstallments_linkedOnce() {
        val occ = ObligationScheduler.generate(rent).first()
        val e1 = ev("p1", OUTGOING_TRANSFER, "a1", 400, "2026-01-04T10:00"); val e2 = ev("p2", OUTGOING_TRANSFER, "a1", 600, "2026-01-06T10:00")
        var pays = ObligationLedger.link(emptyList(), ObligationPayment("p1", occ.id, sar(400)), e1)
        assertEquals(OccurrenceState.PARTIALLY_PAID, ObligationLedger.status(occ, pays, LocalDate.of(2026, 1, 4)).state)
        pays = ObligationLedger.link(pays, ObligationPayment("p2", occ.id, sar(600)), e2)
        val st = ObligationLedger.status(occ, pays, LocalDate.of(2026, 1, 6))
        assertEquals(OccurrenceState.PAID, st.state); assertEquals(Money.ZERO, st.remaining)
        try { ObligationLedger.link(pays, ObligationPayment("p2", "other#0", sar(600)), e2); fail() } catch (e: PaymentLinkException) {}
    }

    @Test fun t20_dueWithoutPaymentSms_overdueNoExpense() {
        val occ = ObligationScheduler.generate(rent).first()
        val st = ObligationLedger.status(occ, emptyList(), LocalDate.of(2026, 1, 9))
        assertEquals(OccurrenceState.OVERDUE, st.state); assertEquals(sar(1000), st.remaining)
    }

    @Test fun t21_paymentMatchingSeveralObligations_userChooses_linkedOnce() {
        val other = rent.copy(id = "gym", name = "Gym")
        val today = LocalDate.of(2026, 1, 5)
        val open = (ObligationScheduler.generate(rent) + ObligationScheduler.generate(other)).map { ObligationLedger.status(it, emptyList(), today) }
        val e = ev("pay", OUTGOING_TRANSFER, "a1", 1000, "2026-01-05T10:00")
        val c = ObligationLedger.candidates(e, "A", open)
        assertEquals(2, c.size)
        val linked = ObligationLedger.link(emptyList(), ObligationPayment("pay", c[1].occurrence.id, sar(1000)), e)
        try { ObligationLedger.link(linked, ObligationPayment("pay", c[0].occurrence.id, sar(1000)), e); fail() } catch (x: PaymentLinkException) {}
    }

    @Test fun t22_profileChangeAfterThreePaid_isProspective() {
        val occ = ObligationScheduler.generate(rent)
        val pays = occ.take(3).mapIndexed { i, o -> ObligationPayment("e$i", o.id, sar(1000)) }
        val statuses = occ.map { ObligationLedger.status(it, pays, LocalDate.of(2026, 3, 10)) }
        val changed = ObligationScheduler.changeProfile(rent, ProfileChange(LocalDate.of(2026, 1, 1), "B"), statuses)
        val regen = ObligationScheduler.generate(changed)
        assertEquals(listOf("A", "A", "A"), regen.take(3).map { it.profileId }); assertTrue(regen.drop(3).all { it.profileId == "B" })
        assertEquals(2, changed.version)
    }

    @Test fun t23_paymentForForecastObligation_notDeductedTwice() {
        val occ = ObligationScheduler.generate(rent.copy(startDate = LocalDate.of(2026, 3, 15), dayOfMonth = 15, occurrenceCount = 1)).single()
        val today = LocalDate.of(2026, 3, 10)
        fun liq(bal: Long, pays: List<ObligationPayment>) = LiquidityForecaster.forecast(ForecastInput(today, ForecastHorizon.Days(14),
            mapOf("a1" to Balance.Known(sar(bal), sar(bal), now)), setOf("a1"), listOf(ObligationLedger.status(occ, pays, today)), null, ReservePolicy.None)).liquidityAfterObligations
        assertEquals(sar(4000), liq(5000, emptyList()))
        assertEquals(sar(4000), liq(4000, listOf(ObligationPayment("pay", occ.id, sar(1000)))))
    }

    @Test fun unknownBalanceIsNeverZero() {
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = listOf(ev("p", PURCHASE_DEBIT, "a1", 50, "2026-02-01T09:00")), now = now))
        assertEquals(Balance.Unknown(sar(-50)), r.balances["a1"])
    }

    @Test fun balanceMismatch_requestsReview_noAdjustment() {
        val evs = listOf(ev("s", SALARY, "a1", 1000, "2026-02-01T09:00", bal = 1000), ev("p", PURCHASE_DEBIT, "a1", 100, "2026-02-02T09:00", bal = 850))
        val r = engine.compute(LedgerInput(listOf(profile("A")), listOf(account("a1", "A")), events = evs, now = now))
        assertTrue(r.reviews.any { ReviewReason.BALANCE_MISMATCH in it.reasons })
        assertEquals(sar(850), (r.balances["a1"] as Balance.Known).amount)
    }

    // ---------------- Appearance addendum ----------------
    @Test fun t33_customAccentWithPoorContrast_derivedTonesFixed_seedKept() {
        val seed = 0xFFF4C430 // light yellow: white text fails
        assertTrue(ColorMath.contrast(0xFFFFFFFF, seed) < 4.5)
        val on = ColorMath.ensureContrast(0xFFFFFFFF, seed)
        assertTrue(ColorMath.contrast(on, seed) >= 4.5)
        Palette.values().forEach { p ->
            val primary = ColorMath.tone(p.seed, 40)
            assertTrue(p.name, ColorMath.contrast(ColorMath.ensureContrast(0xFFFFFFFF, primary), primary) >= 4.5)
        }
    }

    @Test fun t29_everyPaletteAndAccent_meetsContrastAtAllLevels() {
        val seeds = Palette.values().map { it.seed } + com.budgetsms.domain.appearance.ACCENT_SWATCHES
        for (seed in seeds) for (tone in listOf(20, 28, 30, 34, 40, 80, 86, 90)) for (ratio in listOf(4.5, 7.0)) {
            val bg = ColorMath.tone(seed, tone)
            val (f, b) = ColorMath.ensurePair(if (tone < 50) 0xFFFFFFFF else 0xFF000000, bg, ratio)
            assertTrue("seed=$seed tone=$tone", ColorMath.contrast(f, b) >= ratio - 0.01)
        }
    }
}
