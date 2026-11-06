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
)

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
    fun learn(id: String, type: SampleType, sample: String, amount: Money?): MessageTemplate {
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
        return MessageTemplate(id, type, tokens(sample), firstLine(sample), prefix, suffix)
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

    fun match(text: String): MessageTemplate? =
        templates.map { it to TemplateLearner.score(it, text) }.filter { it.second >= threshold }.maxByOrNull { it.second }?.first

    override fun parse(sms: RawSms, zone: ZoneId): ParsedSms {
        val g = base.parse(sms, zone)
        val t = match(sms.body) ?: return g
        val amount = TemplateLearner.extractAmount(t, sms.body) ?: g.amount
        val reasons = g.reasons - ReviewReason.UNSUPPORTED_FORMAT - (if (amount != null) setOf(ReviewReason.MISSING_AMOUNT) else emptySet())
        return g.copy(kind = t.type.kind, amount = if (g.currency == "SAR") amount else g.amount, reasons = reasons,
            categoryHint = t.type.categoryHint ?: g.categoryHint)
    }
}
