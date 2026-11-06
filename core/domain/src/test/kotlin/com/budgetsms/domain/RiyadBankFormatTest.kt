package com.budgetsms.domain

import com.budgetsms.domain.ingest.ImportWindow
import com.budgetsms.domain.ingest.IncomingSms
import com.budgetsms.domain.ingest.IngestState
import com.budgetsms.domain.ingest.IngestionPipeline
import com.budgetsms.domain.ingest.OneTimeCodeFilter
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.model.EventState
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.sms.ApprovedSender
import com.budgetsms.domain.sms.ConsentGate
import com.budgetsms.domain.sms.GenericKeywordParser
import com.budgetsms.domain.sms.ImportSource
import com.budgetsms.domain.sms.ParserRegistry
import com.budgetsms.domain.sms.RawSms
import com.budgetsms.domain.sms.SmsHeader
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** Anonymised samples shaped like Riyad Bank messages (numbers changed). */
class RiyadBankFormatTest {
    private val purchase = "شراء إنترنت\n1111*مدى\n29.99 SAR\n*123456\nGoogle You\n2026-11-05 21:12"
    private val purchaseTimeFirst = "شراء إنترنت\n1111*مدى\n29.99 SAR\n*123456\nGoogle You\n21:12 2026-11-05"
    private val otp = "لا تشارك رمز المرور 0000\nسداد الفواتير:\n2026-11-05 23:16"
    private val bill = "سداد فاتورة\nSAR 233.71\nموبايلي\nالاتصالات\nفاتورة100000000000000\n2026-11-05 23:17"
    private val salary = "إيداع راتب\nSAR 12,500.00\nحساب *123456\n2026-10-27 08:00"
    private fun raw(b: String) = RawSms("f", "RiyadBank", Instant.EPOCH, b)

    @Test fun purchaseUsesAmountNextToCurrencyAndBankTime() {
        for (b in listOf(purchase, purchaseTimeFirst)) {
            val p = GenericKeywordParser.parse(raw(b), RIYADH)
            assertEquals(EventKind.PURCHASE_DEBIT, p.kind); assertEquals(Money.parse("29.99"), p.amount)
            assertEquals(t("2026-11-05T21:12"), p.executedAt)
        }
    }

    @Test fun billPaymentIsAnExpense() {
        val p = GenericKeywordParser.parse(raw(bill), RIYADH)
        assertEquals(EventKind.PURCHASE_DEBIT, p.kind); assertEquals(Money.parse("233.71"), p.amount)
    }

    @Test fun oneTimeCodesAreIgnoredAndNeverStored() {
        assertTrue(OneTimeCodeFilter.isOneTimeCode(otp)); assertFalse(OneTimeCodeFilter.isOneTimeCode(bill))
        val gate = ConsentGate(listOf(ApprovedSender("RIYADBANK", "rb", Instant.EPOCH)))
        val r = IngestionPipeline(gate, ParserRegistry(emptyList()), listOf(account("main", "A").copy(bankId = "rb", maskedNumber = "")), RIYADH)
            .ingest(listOf(IncomingSms(SmsHeader("RiyadBank", t("2026-11-05T23:16")), ImportSource.DEVICE_INBOX, "1") { otp }), IngestState(), "b")
        assertEquals(1, r.ignoredOneTimeCodes); assertTrue(r.newRaw.isEmpty()); assertTrue(r.newEvents.isEmpty())
    }

    @Test fun autoProcessedAtBankAccountLevel() {
        val gate = ConsentGate(listOf(ApprovedSender("RIYADBANK", "rb", Instant.EPOCH)))
        val acc = account("main", "A").copy(bankId = "rb", maskedNumber = "")
        val p = IngestionPipeline(gate, ParserRegistry(emptyList()), listOf(acc), RIYADH, autoConfirmSenders = setOf("RIYADBANK"))
        val r = p.ingest(listOf(purchase, bill, salary).mapIndexed { i, b -> IncomingSms(SmsHeader("RiyadBank", t("2026-11-05T21:1$i")), ImportSource.DEVICE_INBOX, "$i") { b } }, IngestState(), "b")
        assertEquals(3, r.newEvents.size)
        assertTrue(r.newEvents.all { it.state == EventState.CONFIRMED && it.accountId == "main" })
        assertEquals(EventKind.SALARY, r.newEvents.last().kind); assertEquals(Money.parse("12500"), r.newEvents.last().amount)
    }

    @Test fun importStartsAtLatestSalary() {
        val gate = ConsentGate(listOf(ApprovedSender("RIYADBANK", "rb", Instant.EPOCH)))
        fun m(at: String, b: String, sender: String = "RiyadBank") = IncomingSms(SmsHeader(sender, t(at)), ImportSource.DEVICE_INBOX, at) { b }
        val batch = listOf(m("2026-09-27T08:00", salary), m("2026-10-01T10:00", purchase), m("2026-10-27T08:00", salary),
            m("2026-11-05T21:12", purchase), m("2026-11-05T23:17", bill), m("2026-11-05T09:00", "شراء SAR 5.00", "OtherBank"))
        val (window, salaryAt) = ImportWindow.sinceLastSalary(batch, gate, RIYADH, t("2026-11-06T12:00"))
        assertEquals(t("2026-10-27T08:00"), salaryAt)
        assertEquals(listOf("2026-10-27T08:00", "2026-11-05T21:12", "2026-11-05T23:17"), window.map { it.sourceRecordId })
        val (fallback, none) = ImportWindow.sinceLastSalary(batch.filter { it.header.receivedAt != t("2026-10-27T08:00") && it.header.receivedAt != t("2026-09-27T08:00") }, gate, RIYADH, t("2026-11-06T12:00"))
        assertNull(none); assertEquals(2, fallback.size) // last 30 days only
    }

    // ---- v1.4 owner rules (samples anonymised) ----
    private val codePayment = "الرمز:0000\nمدفوعات انترنت\nSAR 240.67\nwww.example.com\n12:53 2026-11-04"
    private val ccPurchase = "شراء إنترنت دولي\nبطاقة: 1111* ائتمانية;\nمبلغ: SAR 65.99\nسعر الصرف: 1.0\nرسوم + ضريبة القيمة المضافة (15%): 1.52\nإجمالي المبلغ: 67.51\nرصيد: SAR 986.77\nمن: Example Store\nدولة: USA\nفي: 2026-11-03 18:08"
    private val notice = "عميلنا العزيز،\nيرجى زيارة الموقع الالكتروني للبنك للاطلاع على الدليل الارشادي للتمويل.\nللاستفسارات : 8000000000"
    private val ccRepayment = "سداد بطاقة ائتمانية\nبطاقة: 1111*\nمبلغ: SAR 500.00\n2026-11-04 10:00"

    @Test fun ownerIgnoreRules() {
        assertEquals(com.budgetsms.domain.ingest.IgnoreReason.ONE_TIME_CODE, com.budgetsms.domain.ingest.IgnoreFilter.reason(codePayment))
        assertEquals(com.budgetsms.domain.ingest.IgnoreReason.CREDIT_CARD_MOVEMENT, com.budgetsms.domain.ingest.IgnoreFilter.reason(ccPurchase))
        assertEquals(com.budgetsms.domain.ingest.IgnoreReason.INFORMATIONAL, com.budgetsms.domain.ingest.IgnoreFilter.reason(notice))
        assertNull(com.budgetsms.domain.ingest.IgnoreFilter.reason(ccRepayment))
        assertNull(com.budgetsms.domain.ingest.IgnoreFilter.reason(purchase)); assertNull(com.budgetsms.domain.ingest.IgnoreFilter.reason(bill)); assertNull(com.budgetsms.domain.ingest.IgnoreFilter.reason(salary))
    }

    @Test fun onlyCardRepaymentCountsAgainstBudget() {
        val gate = ConsentGate(listOf(ApprovedSender("RIYADBANK", "rb", Instant.EPOCH)))
        val acc = account("main", "A").copy(bankId = "rb", maskedNumber = "")
        val pipeline = IngestionPipeline(gate, ParserRegistry(emptyList()), listOf(acc), RIYADH, autoConfirmSenders = setOf("RIYADBANK"))
        val msgs = listOf(salary, ccPurchase, codePayment, notice, ccRepayment).mapIndexed { i, b -> IncomingSms(SmsHeader("RiyadBank", t("2026-11-0${i + 1}T09:00")), ImportSource.DEVICE_INBOX, "$i") { b } }
        val r = pipeline.ingest(msgs, IngestState(), "b")
        assertEquals(listOf(EventKind.SALARY, EventKind.CARD_REPAYMENT), r.newEvents.map { it.kind })
        assertEquals(3, r.ignored.values.sum())
        val l = com.budgetsms.domain.accounting.LedgerEngine().compute(com.budgetsms.domain.accounting.LedgerInput(listOf(profile("A")), listOf(acc), events = r.newEvents, now = t("2026-11-06T12:00")))
        assertEquals(Money.parse("500"), l.budgetOf("A")!!.spent)
    }

    @Test fun outgoingTransferAmountIsTheOneNextToSar() {
        for (b in listOf("حوالة داخلية\nلـ اسم المستفيد\n*1234\nSAR 1,000\n28-10-26 07:55", "حوالة داخلية\nلـ اسم المستفيد\n1234*\nSAR 1,000\n28-10-26 07:55",
                "حوالة داخلية\nلـ اسم المستفيد\n1234\nSAR 1,000\n28-10-26 07:55")) {
            val p = GenericKeywordParser.parse(raw(b), RIYADH)
            assertEquals(b, EventKind.OUTGOING_TRANSFER, p.kind); assertEquals(b, Money.parse("1000"), p.amount)
            assertEquals(t("2026-10-28T07:55"), p.executedAt)
        }
        assertEquals(EventKind.INCOMING_TRANSFER, GenericKeywordParser.parse(raw("حوالة واردة\nمن اسم\n*1234\nSAR 300.00"), RIYADH).kind)
    }
}
