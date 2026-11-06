package com.budgetsms.domain.sms

import com.budgetsms.domain.model.Confidence
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.model.ReviewReason
import com.budgetsms.domain.money.Money
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class ParsedSms(
    val kind: EventKind,
    val amount: Money?,
    val currency: String = "SAR",
    val originalAmountText: String? = null,
    val sarDebit: Money? = null,
    val fee: Money? = null,
    val accountHint: String? = null,
    val counterpartyHint: String? = null,
    val reference: String? = null,
    val executedAt: Instant? = null,
    val balanceAfter: Money? = null,
    val confidence: Confidence,
    val reasons: Set<ReviewReason> = emptySet(),
    val categoryHint: String? = null,
)

interface SmsParser {
    val id: String
    val version: Int
    /** True when this parser is verified for the bank (built from anonymised samples of that bank). */
    val verifiedForBank: Boolean
    fun supports(bankId: String): Boolean
    fun parse(sms: RawSms, zone: ZoneId): ParsedSms
}

class ParserRegistry(private val parsers: List<SmsParser>, private val fallback: SmsParser = GenericKeywordParser) {
    fun forBank(bankId: String): SmsParser = parsers.firstOrNull { it.supports(bankId) } ?: fallback
}

/**
 * Conservative, bank-agnostic keyword extractor (Arabic + English).
 * It is NOT a production parser for any specific bank: its results are capped at MEDIUM
 * confidence and always routed to human review, unless the user approved a learned rule.
 * Bank-specific parsers must be built from anonymised samples (see docs/DECISIONS.md, D-07).
 */
object GenericKeywordParser : SmsParser {
    override val id = "generic-keyword"
    override val version = 2
    override val verifiedForBank = false
    override fun supports(bankId: String) = true

    /** Public number pattern (one capturing group), used by learned templates. */
    val NUMBER: String get() = num
    private val num = """([0-9٠-٩]{1,3}(?:[,٬][0-9٠-٩]{3})*(?:[.٫][0-9٠-٩]{1,2})?|[0-9٠-٩]+(?:[.٫][0-9٠-٩]{1,2})?)"""
    private val cur = """(SAR|SR|ر\.?س|ريال|USD|EUR|GBP|AED|KWD|BHD|QAR|OMR|EGP|JOD)"""
    // The amount is the number written next to the currency on the same line. Numbers touching "*" are
    // masked card/account digits and are never amounts (e.g. "*1234\nSAR 1,000" -> 1,000).
    private val nb = """(?<![*\d٠-٩.,٬٫])"""
    private val na = """(?![*\d٠-٩])"""
    private val amountRe = Regex("""(?:$cur[ \t\u00A0]*$nb$num$na|$nb$num$na[ \t\u00A0]*$cur)""", RegexOption.IGNORE_CASE)
    private val balanceRe = Regex("""(?:الرصيد|رصيد|balance|bal\.?|avail(?:able)?)\s*[:：]?\s*(?:$cur\s*)?$num""", RegexOption.IGNORE_CASE)
    private val feeRe = Regex("""(?:رسوم|عمولة|fees?|charges?)\s*[:：]?\s*(?:$cur\s*)?$num""", RegexOption.IGNORE_CASE)
    private val accountRe = Regex("""(?:\*{1,}|x{2,}|حساب\s*(?:رقم)?\s*[:：]?\s*\**|acc(?:oun)?t?\s*(?:no\.?)?\s*[:：]?\s*\**|بطاقة\s*\**|card\s*\**)\s*([0-9٠-٩]{3,4})\b""", RegexOption.IGNORE_CASE)
    private val toRe = Regex("""(?:إلى|الى|to)\s*(?:حساب|acc(?:oun)?t?)?\s*\**\s*([0-9٠-٩]{3,4})\b""", RegexOption.IGNORE_CASE)
    private val refRe = Regex("""(?:ref(?:erence)?\.?|المرجع|مرجع|رقم العملية|رقم المرجع|trx|txn)\s*(?:no\.?|#)?\s*[:：]?\s*([A-Za-z0-9]{5,})""", RegexOption.IGNORE_CASE)
    private val dateRe = Regex("""\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})[ T]+(\d{1,2}):(\d{2})""")
    private val timeDateRe = Regex("""\b(\d{1,2}):(\d{2})\s+(\d{4})[-/](\d{1,2})[-/](\d{1,2})\b""")
    private val dmyRe = Regex("""\b(\d{1,2})[-/](\d{1,2})[-/](\d{2,4})\s+(\d{1,2}):(\d{2})""")

    private val rules: List<Pair<EventKind, List<String>>> = listOf(
        EventKind.FAILED to listOf("فشل", "مرفوض", "رفض", "declined", "failed", "unsuccessful", "غير ناجح"),
        EventKind.REVERSAL to listOf("عكس", "reversal", "reversed", "إلغاء العملية", "ملغاة"),
        EventKind.REFUND to listOf("استرجاع", "مسترد", "refund", "استرداد"),
        EventKind.SALARY to listOf("راتب", "salary", "payroll", "رواتب"),
        EventKind.ALLOWANCE to listOf("بدل", "allowance", "مكافأة", "bonus"),
        EventKind.CARD_REPAYMENT to listOf("سداد بطاقة", "سداد البطاقة", "سداد مستحقات البطاقة", "سداد مديونية البطاقة", "تسديد بطاقة", "تسديد البطاقة",
            "credit card payment", "card payment", "card repayment", "repayment"),
        EventKind.PURCHASE_CREDIT_CARD to listOf("بطاقة ائتمان", "بطاقة الائتمان", "ائتمانية", "credit card"),
        EventKind.ATM_WITHDRAWAL to listOf("سحب نقدي", "سحب", "atm", "صراف", "cash withdrawal"),
        EventKind.FEE to listOf("رسوم خدمة", "service fee", "annual fee", "رسوم سنوية"),
        EventKind.INCOMING_TRANSFER to listOf("حوالة واردة", "حوالة داخلية واردة", "حوالة محلية واردة", "تحويل وارد", "incoming transfer"),
        EventKind.OUTGOING_TRANSFER to listOf("حوالة داخلية", "حوالة محلية", "حوالة دولية", "حوالة سريعة", "تحويل داخلي", "تحويل محلي", "حوالة صادرة", "تحويل صادر", "outgoing transfer", "transfer to", "تحويل إلى", "حوالة إلى", "sent", "حوالة", "تحويل"),
        EventKind.INCOMING_TRANSFER to listOf("حوالة واردة", "تحويل وارد", "incoming transfer", "received", "إيداع", "deposit", "credited"),
        EventKind.PURCHASE_DEBIT to listOf("شراء", "purchase", "pos", "نقاط البيع", "مدى", "mada", "apple pay",
            "سداد فاتورة", "سداد الفواتير", "سداد", "دفع فاتورة", "مدفوعات", "bill payment", "payment"),
    )

    /** True when the text contains an amount written next to a currency code (e.g. "SAR 240.67"). */
    fun hasCurrencyAmount(text: String): Boolean = amountRe.containsMatchIn(text)

    override fun parse(sms: RawSms, zone: ZoneId): ParsedSms {
        val text = sms.body
        val lower = text.lowercase()
        val kind = rules.firstOrNull { (_, kws) -> kws.any { lower.contains(it.lowercase()) } }?.first
        val reasons = mutableSetOf<ReviewReason>()
        if (kind == null) reasons += ReviewReason.UNSUPPORTED_FORMAT

        val balanceMatch = balanceRe.find(text)
        val feeMatch = feeRe.find(text)
        val amountMatch = amountRe.findAll(text).firstOrNull { m ->
            (balanceMatch == null || m.range.first !in balanceMatch.range) && (feeMatch == null || m.range.first !in feeMatch.range)
        }
        val currencyRaw = amountMatch?.let { it.groupValues[1].ifEmpty { it.groupValues[4] } }
        val numberRaw = amountMatch?.let { it.groupValues[2].ifEmpty { it.groupValues[3] } }
        val currency = normalizeCurrency(currencyRaw)
        val amount = numberRaw?.let { Money.parseOrNull(it) }
        if (amount == null && kind != EventKind.BALANCE_ONLY) reasons += ReviewReason.MISSING_AMOUNT

        val balance = balanceMatch?.let { Money.parseOrNull(it.groupValues.last()) }
        val fee = feeMatch?.let { Money.parseOrNull(it.groupValues.last()) }
        val account = accountRe.find(text)?.groupValues?.get(1)
        if (account == null) reasons += ReviewReason.MISSING_ACCOUNT
        val counterparty = toRe.find(text)?.groupValues?.get(1)
        val reference = refRe.find(text)?.groupValues?.get(1)
        val executedAt = parseDate(text, zone)
        if (executedAt == null) reasons += ReviewReason.UNTRUSTED_TIMESTAMP

        var sarDebit: Money? = null
        var sarAmount = amount
        if (currency != "SAR") {
            // Look for a second amount explicitly in SAR (actual debit); never convert with an assumed rate.
            sarDebit = amountRe.findAll(text).firstOrNull { m ->
                normalizeCurrency(m.groupValues[1].ifEmpty { m.groupValues[4] }) == "SAR" &&
                    (balanceMatch == null || m.range.first !in balanceMatch.range)
            }?.let { Money.parseOrNull(it.groupValues[2].ifEmpty { it.groupValues[3] }) }
            sarAmount = sarDebit
            if (sarDebit == null) reasons += ReviewReason.FX_WITHOUT_SAR_DEBIT
        }
        reasons += ReviewReason.LOW_CONFIDENCE
        return ParsedSms(
            kind = kind ?: EventKind.UNKNOWN,
            amount = sarAmount,
            currency = currency,
            originalAmountText = if (currency != "SAR") "$numberRaw $currency" else null,
            sarDebit = sarDebit,
            fee = fee,
            accountHint = account?.let { com.budgetsms.domain.money.NumberInput.normalize(it) },
            counterpartyHint = counterparty?.let { com.budgetsms.domain.money.NumberInput.normalize(it) },
            reference = reference,
            executedAt = executedAt,
            balanceAfter = balance,
            confidence = if (kind == null) Confidence.LOW else Confidence.MEDIUM,
            reasons = reasons,
        )
    }

    private fun normalizeCurrency(raw: String?): String = when {
        raw == null -> "SAR"
        raw.uppercase() in setOf("SAR", "SR") || raw.startsWith("ر") || raw == "ريال" -> "SAR"
        else -> raw.uppercase()
    }

    private fun parseDate(text: String, zone: ZoneId): Instant? = runCatching {
        dateRe.find(text)?.let { m ->
            val (y, mo, d, h, mi) = m.destructured
            return LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt()).atZone(zone).toInstant()
        }
        timeDateRe.find(text)?.let { m ->
            val (h, mi, y, mo, d) = m.destructured
            return LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt()).atZone(zone).toInstant()
        }
        dmyRe.find(text)?.let { m ->
            val (d, mo, yRaw, h, mi) = m.destructured
            val y = if (yRaw.length == 2) 2000 + yRaw.toInt() else yRaw.toInt()
            return LocalDateTime.of(y, mo.toInt(), d.toInt(), h.toInt(), mi.toInt()).atZone(zone).toInstant()
        }
        null
    }.getOrNull()
}
