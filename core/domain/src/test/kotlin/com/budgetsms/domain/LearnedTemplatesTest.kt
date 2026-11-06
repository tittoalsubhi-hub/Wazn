package com.budgetsms.domain

import com.budgetsms.domain.ingest.IncomingSms
import com.budgetsms.domain.ingest.IngestState
import com.budgetsms.domain.ingest.IngestionPipeline
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.model.EventState
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.sms.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/** A bank whose messages the general rules cannot read: no currency code next to the amount. */
class LearnedTemplatesTest {
    private val purchaseSample = "تم خصم 99.00 من حسابك 5555 لدى متجر الأمل\n2026-11-01 10:00"
    private val incomingSample = "تم ايداع 300.00 في حسابك 5555 من سعيد\n2026-11-01 11:00"
    private val salarySample = "تم ايداع راتبك 9,000.00 في حسابك 5555\n2026-10-27 08:00"
    private fun templates() = listOf(
        TemplateLearner.learn("p", SampleType.PURCHASE, purchaseSample, Money.parse("99")),
        TemplateLearner.learn("i", SampleType.INCOMING_TRANSFER, incomingSample, Money.parse("300")),
        TemplateLearner.learn("s", SampleType.SALARY, salarySample, Money.parse("9000")),
    )

    @Test fun learnsAmountAnchorWithoutCurrency() {
        val t = templates()[0]
        assertEquals("تم خصم", t.amountPrefix); assertEquals("من حسابك", t.amountSuffix)
        assertNull(TemplateLearner.detectAmount(purchaseSample, RIYADH)) // general rules cannot read it
    }

    @Test fun parsesNewMessagesOfThatBank() {
        val p = LearnedParser(templates())
        fun parse(b: String) = p.parse(RawSms("f", "OTHERBANK", Instant.EPOCH, b), RIYADH)
        val a = parse("تم خصم 150.25 من حسابك 1234 لدى متجر النور\n2026-11-05 09:00")
        assertEquals(EventKind.PURCHASE_DEBIT, a.kind); assertEquals(Money.parse("150.25"), a.amount)
        val b = parse("تم ايداع 1,200.00 في حسابك 1234 من خالد\n2026-11-05 12:00")
        assertEquals(EventKind.INCOMING_TRANSFER, b.kind); assertEquals(Money.parse("1200"), b.amount)
        val c = parse("تم ايداع راتبك 9,500.00 في حسابك 1234\n2026-11-27 08:00")
        assertEquals(EventKind.SALARY, c.kind); assertEquals(Money.parse("9500"), c.amount)
    }

    @Test fun pipelineProcessesThemAutomaticallyAndStillIgnoresNotices() {
        val gate = ConsentGate(listOf(ApprovedSender("OTHERBANK", "ob", Instant.EPOCH)))
        val acc = account("main", "A").copy(bankId = "ob", maskedNumber = "")
        val pipeline = IngestionPipeline(gate, ParserRegistry(emptyList(), LearnedParser(templates())), listOf(acc), RIYADH, autoConfirmSenders = setOf("OTHERBANK"))
        val msgs = listOf("تم خصم 150.25 من حسابك 1234 لدى متجر النور\n2026-11-05 09:00", "عميلنا العزيز، سيتم إيقاف الخدمة للصيانة الليلة")
            .mapIndexed { i, b -> IncomingSms(SmsHeader("OtherBank", t("2026-11-05T0${i + 1}:00")), ImportSource.DEVICE_INBOX, "$i") { b } }
        val r = pipeline.ingest(msgs, IngestState(), "b")
        assertEquals(1, r.newEvents.size); assertEquals(EventState.CONFIRMED, r.newEvents[0].state); assertEquals(Money.parse("150.25"), r.newEvents[0].amount)
        assertEquals(1, r.ignored.values.sum())
    }

    @Test fun dateOrderLearnedFromSample() {
        val sample = "حوالة محلية صادرة بـ200 SR\nمن1111\nلـ2222;اسم المستفيد\nرسوم:SR 0.58\n26/10/26 11:04"
        assertEquals(DateOrder.DMY, DateParsing.guessOrder(sample))
        assertEquals(t("2026-10-26T11:04"), DateParsing.parse(sample, DateOrder.DMY, RIYADH))
        assertEquals(t("2026-10-26T11:04"), DateParsing.parse("2026-10-26 11:04", DateOrder.YMD, RIYADH))
        assertEquals(t("2026-10-26T11:04"), DateParsing.parse("10/26/2026 11:04", DateOrder.MDY, RIYADH))
        val g = GenericKeywordParser.parse(RawSms("f", "B", Instant.EPOCH, sample), RIYADH)
        assertEquals(EventKind.OUTGOING_TRANSFER, g.kind); assertEquals(Money.parse("200"), g.amount); assertEquals(Money.parse("0.58"), g.fee)
        // A bank that writes year/month/day with two-digit years: only the learned order reads it correctly.
        val tpl = TemplateLearner.learn("o", SampleType.OUTGOING_TRANSFER, "حوالة صادرة SAR 50.00\n26/10/05 09:30", Money.parse("50"), DateOrder.YMD)
        val p = LearnedParser(listOf(tpl)).parse(RawSms("f", "B", Instant.EPOCH, "حوالة صادرة SAR 75.00\n26/11/03 08:15"), RIYADH)
        assertEquals(t("2026-11-03T08:15"), p.executedAt)
    }
}
