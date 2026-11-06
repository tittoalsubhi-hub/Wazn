package com.budgetsms.domain.sms

import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.model.ReviewReason
import com.budgetsms.domain.money.Money
import java.time.ZoneId

/** Sample types the user is asked for during setup (owner request v1.6). */
enum class SampleType(val kind: EventKind, val categoryHint: String?) {
    PURCHASE(EventKind.PURCHASE_DEBIT, null),
    SALARY(EventKind.SALARY, null),
    CARD_REPAYMENT(EventKind.CARD_REPAYMENT, "card_repayment"),
    OUTGOING_TRANSFER(EventKind.OUTGOING_TRANSFER, null),
    INCOMING_TRANSFER(EventKind.INCOMING_TRANSFER, null),
    BILL_PAYMENT(EventKind.PURCHASE_DEBIT, "bills"),
}

/**
 * A message template learned from one sample the user pasted. Only the fixed words and the text around the
 * amount are kept, not the sample's numbers.
 */
data class MessageTemplate(
    val id: String,
    val type: SampleType,
    val tokens: Set<String>,
    val firstLine: String,
    val amountPrefix: String,
    val amountSuffix: String,
    /** Date order confirmed by the user for this bank's messages (e.g. 26/10/26 = day/month/year). */
    val dateOrder: DateOrder? = null,
)

enum class DateOrder { DMY, YMD, MDY }

/** Reads dates such as 26/10/26, 2026-11-05 or 28-10-26 with an explicit day/month/year order, plus the HH:mm time. */
object DateParsing {
    private val dateRe = Regex("""(?<![\d٠-٩])(\d{1,4})[-/.](\d{1,2})[-/.](\d{1,4})(?![\d٠-٩])""")
    private val timeRe = Regex("""(?<![\d٠-٩:])(\d{1,2}):(\d{2})(?![\d٠-٩])""")

    /** Suggested order: 4-digit first part = year/month/day, otherwise day/month/year (common in Saudi bank SMS). */
    fun guessOrder(text: String): DateOrder? {
        val m = dateRe.find(text) ?: return null
        return if (m.groupValues[1].length == 4) DateOrder.YMD else DateOrder.DMY
    }

    fun parse(text: String, order: DateOrder, zone: java.time.ZoneId): java.time.Instant? = runCatching {
        val m = dateRe.find(text) ?: return null
        val (a, b, c) = m.destructured
        val (y, mo, d) = when (order) { DateOrder.DMY -> Triple(c, b, a); DateOrder.YMD -> Triple(a, b, c); DateOrder.MDY -> Triple(c, a, b) }
        val year = y.toInt().let { if (it < 100) 2000 + it else it }
        val t = timeRe.find(text)
        java.time.LocalDateTime.of(year, mo.toInt(), d.toInt(), t?.groupValues?.get(1)?.toInt() ?: 0, t?.groupValues?.get(2)?.toInt() ?: 0).atZone(zone).toInstant()
    }.getOrNull()
}

object TemplateLearner {
    private val digit = Regex("[0-9٠-٩]")
    private val split = Regex("""[\s:：;,،.*()\[\]/\\\-_|+=#]+""")

    fun tokens(text: String): Set<String> =
        text.lowercase().split(split).filter { it.length >= 2 && !digit.containsMatchIn(it) && !it.startsWith("http") && !it.startsWith("www") }.toSet()

    fun firstLine(text: String): String =
        text.trim().lines().firstOrNull { l -> l.isNotBlank() }.orEmpty().replace(digit, "").replace(Regex("""[*:：]"""), " ").trim().lowercase().replace(Regex("\\s+"), " ")

    /** Detects the amount in a sample with the general rules (number next to the currency). */
    fun detectAmount(sample: String, zone: ZoneId): Money? = GenericKeywordParser.parse(RawSms("", "", java.time.Instant.EPOCH, sample), zone).amount

    /**
     * Learns where the amount sits: the words just before and after it on the same line.
     * [amount] is the value the user confirmed for this sample (detected or typed).
     */
    fun learn(id: String, type: SampleType, sample: String, amount: Money?, dateOrder: DateOrder? = null): MessageTemplate {
        var prefix = ""; var suffix = ""
        if (amount != null) {
            val numRe = Regex("""(?<![*\d٠-٩.,٬٫])${GenericKeywordParser.NUMBER}(?![*\d٠-٩])""")
            for (line in sample.lines()) {
                val m = numRe.findAll(line).firstOrNull { Money.parseOrNull(it.value) == amount } ?: continue
                // Words between the amount and the nearest other number on the same line.
                prefix = line.substring(0, m.range.first).split(digit).last().trim().takeLast(16).trim()
                suffix = line.substring(m.range.last + 1).split(digit).first().trim().take(16).trim()
                break
            }
        }
        return MessageTemplate(id, type, tokens(sample), firstLine(sample), prefix, suffix, dateOrder)
    }

    /** Per-mille share of the template's fixed words found in the message, plus a bonus for the same first line (integer maths). */
    fun score(t: MessageTemplate, message: String): Int {
        if (t.tokens.isEmpty()) return 0
        val m = tokens(message)
        val overlap = t.tokens.count { it in m } * 1000 / t.tokens.size
        val bonus = if (t.firstLine.isNotEmpty() && firstLine(message) == t.firstLine) 350 else 0
        return overlap + bonus
    }

    fun extractAmount(t: MessageTemplate, message: String): Money? {
        val n = GenericKeywordParser.NUMBER
        val guard = """(?<![*\d٠-٩.,٬٫])"""; val end = """(?![*\d٠-٩])"""
        val patterns = buildList {
            if (t.amountPrefix.isNotEmpty()) add(Regex(Regex.escape(t.amountPrefix) + """[ \t ]*""" + guard + n + end))
            if (t.amountSuffix.isNotEmpty()) add(Regex(guard + n + end + """[ \t ]*""" + Regex.escape(t.amountSuffix)))
        }
        for (p in patterns) p.find(message)?.let { m -> Money.parseOrNull(m.groupValues[1])?.let { return it } }
        return null
    }
}

/**
 * Parser that uses the user's learned samples first and the general rules second.
 * The best-matching template decides the transaction type; its amount anchor finds the amount.
 */
class LearnedParser(private val templates: List<MessageTemplate>, private val base: SmsParser = GenericKeywordParser, private val threshold: Int = 600) : SmsParser {
    override val id = "learned"
    override val version = 1
    override val verifiedForBank = false
    override fun supports(bankId: String) = true

    /** The date order the user confirmed most often; used for messages that match no template. */
    private val defaultOrder: DateOrder? = templates.mapNotNull { it.dateOrder }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    fun match(text: String): MessageTemplate? =
        templates.map { it to TemplateLearner.score(it, text) }.filter { it.second >= threshold }.maxByOrNull { it.second }?.first

    override fun parse(sms: RawSms, zone: ZoneId): ParsedSms {
        val g = base.parse(sms, zone)
        val t = match(sms.body) ?: return defaultOrder?.let { o -> DateParsing.parse(sms.body, o, zone)?.let { g.copy(executedAt = it, reasons = g.reasons - ReviewReason.UNTRUSTED_TIMESTAMP) } } ?: g
        val amount = TemplateLearner.extractAmount(t, sms.body) ?: g.amount
        val at = t.dateOrder?.let { DateParsing.parse(sms.body, it, zone) } ?: g.executedAt
        val reasons = g.reasons - ReviewReason.UNSUPPORTED_FORMAT - (if (amount != null) setOf(ReviewReason.MISSING_AMOUNT) else emptySet()) -
            (if (at != null) setOf(ReviewReason.UNTRUSTED_TIMESTAMP) else emptySet())
        return g.copy(kind = t.type.kind, amount = if (g.currency == "SAR") amount else g.amount, reasons = reasons, executedAt = at,
            categoryHint = t.type.categoryHint ?: g.categoryHint)
    }
}
