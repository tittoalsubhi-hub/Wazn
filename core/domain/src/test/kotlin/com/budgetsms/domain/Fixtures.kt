package com.budgetsms.domain

import com.budgetsms.domain.model.Account
import com.budgetsms.domain.model.AccountType
import com.budgetsms.domain.model.BankEvent
import com.budgetsms.domain.model.Confidence
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.model.EventState
import com.budgetsms.domain.model.IncomeTreatment
import com.budgetsms.domain.model.Profile
import com.budgetsms.domain.model.TimeSource
import com.budgetsms.domain.money.Money
import com.budgetsms.domain.sms.ParsedSms
import com.budgetsms.domain.sms.RawSms
import com.budgetsms.domain.sms.SmsParser
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Anonymised, synthetic test grammar. TEST-ONLY: never shipped as a production parser. */
object FixtureParser : SmsParser {
    override val id = "fixture"
    override val version = 1
    override val verifiedForBank = true
    override fun supports(bankId: String) = bankId.startsWith("TESTBANK")
    override fun parse(sms: RawSms, zone: ZoneId): ParsedSms {
        val kv = sms.body.removePrefix("SYN ").split(" ").associate { it.substringBefore('=') to it.substringAfter('=') }
        return ParsedSms(
            kind = EventKind.valueOf(kv.getValue("kind")),
            amount = kv["amt"]?.let(Money::parse),
            accountHint = kv["acc"], counterpartyHint = kv["to"], reference = kv["ref"],
            executedAt = kv["at"]?.let { LocalDateTime.parse(it).atZone(zone).toInstant() },
            balanceAfter = kv["bal"]?.let(Money::parse), fee = kv["fee"]?.let(Money::parse),
            confidence = Confidence.HIGH,
        )
    }
}

val RIYADH: ZoneId = ZoneId.of("Asia/Riyadh")
fun t(s: String): Instant = LocalDateTime.parse(s).atZone(RIYADH).toInstant()
fun sar(v: Long) = Money.sar(v)

fun profile(id: String, type: com.budgetsms.domain.model.CycleType = com.budgetsms.domain.model.CycleType.SALARY) = Profile(id, id, type)
fun account(id: String, profile: String, type: AccountType = AccountType.CURRENT, masked: String = id.takeLast(4).padStart(4, '0')) =
    Account(id, "TESTBANK", profile, type, masked, id, isMain = type == AccountType.CURRENT)

fun ev(
    id: String, kind: EventKind, acc: String?, amount: Long?, at: String,
    state: EventState = EventState.CONFIRMED, to: String? = null, bal: Long? = null, fee: Long? = null,
    category: String? = null, corrects: String? = null, treatment: IncomeTreatment? = null,
    timeSource: TimeSource = TimeSource.BANK, ref: String? = null,
) = BankEvent(
    id = id, rawSmsIds = listOf("raw-$id"), bankId = "TESTBANK", accountId = acc, kind = kind,
    amount = amount?.let { sar(it) }, fee = fee?.let { sar(it) }, reference = ref, counterpartyAccountId = to,
    executedAt = t(at), timeSource = timeSource, timeConfidence = Confidence.HIGH, balanceAfter = bal?.let { sar(it) },
    state = state, categoryId = category, correctsEventId = corrects, incomeTreatment = treatment,
    parserId = "fixture", parserVersion = 1,
)
