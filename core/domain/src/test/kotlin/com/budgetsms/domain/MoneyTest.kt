package com.budgetsms.domain

import com.budgetsms.domain.money.BasisPoints
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.sms.GenericKeywordParser
import com.budgetsms.domain.sms.RawSms
import com.budgetsms.domain.model.EventKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class MoneyTest {
    @Test fun parsesArabicAndEnglishDigits() {
        assertEquals(123450L, Money.parse("١٬٢٣٤٫٥٠").halalas)
        assertEquals(123450L, Money.parse("1,234.50").halalas)
        assertEquals(500L, Money.parse("۵").halalas)
        assertNull(Money.parseOrNull("1.234"))
        assertEquals("-12.05", Money(-1205).toPlainString())
    }

    @Test(expected = ArithmeticException::class) fun overflowIsDetected() { Money(Long.MAX_VALUE) + Money(1) }

    @Test fun percentUsesHalfEven() {
        assertEquals(0L, Money(1).percent(BasisPoints.percent(50)).halalas)
        assertEquals(2L, Money(3).percent(BasisPoints.percent(50)).halalas)
        assertEquals(Money.sar(3000), Money.sar(10000).percent(BasisPoints.parsePercent("30")))
        assertEquals(Money.parse("33.33"), Money.sar(100).percent(BasisPoints.parsePercent("33.33")))
    }

    @Test fun genericParserIsConservative() {
        val p = GenericKeywordParser.parse(RawSms("f", "BANK", Instant.EPOCH, "شراء عبر مدى بطاقة *1234 مبلغ SAR 45.50 الرصيد 1,200.00 SAR"), RIYADH)
        assertEquals(EventKind.PURCHASE_DEBIT, p.kind); assertEquals(Money.parse("45.50"), p.amount)
        assertEquals(Money.parse("1200"), p.balanceAfter); assertEquals("1234", p.accountHint)
        val fx = GenericKeywordParser.parse(RawSms("f", "BANK", Instant.EPOCH, "Purchase USD 20.00 card *9999"), RIYADH)
        assertNull(fx.amount); assertEquals("USD", fx.currency)
    }
}
