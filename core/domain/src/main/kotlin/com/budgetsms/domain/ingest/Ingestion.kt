package com.budgetsms.domain.ingest

import com.budgetsms.domain.model.Account
import com.budgetsms.domain.model.BankEvent
import com.budgetsms.domain.model.Confidence
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.model.EventState
import com.budgetsms.domain.model.ReviewReason
import com.budgetsms.domain.model.TimeSource
import com.budgetsms.domain.sms.ConsentGate
import com.budgetsms.domain.sms.Fingerprint
import com.budgetsms.domain.sms.ImportSource
import com.budgetsms.domain.sms.ParserRegistry
import com.budgetsms.domain.sms.RawSms
import com.budgetsms.domain.sms.SmsHeader
import com.budgetsms.domain.sms.SourceRecord
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A message offered by any source. The body is supplied lazily so the consent gate can refuse to read it. */
class IncomingSms(
    val header: SmsHeader,
    val source: ImportSource,
    val sourceRecordId: String?,
    val bodySupplier: () -> String,
)

/** Existing persisted state needed for idempotent, duplicate-free ingestion. */
data class IngestState(
    val knownFingerprints: Set<String> = emptySet(),
    val sourceKeys: Set<String> = emptySet(),
    val events: Map<String, BankEvent> = emptyMap(),
    /** Learned classification rules approved by the user: senderId -> allowed auto-confirm. */
    val approvedAutoConfirmSenders: Set<String> = emptySet(),
)

data class IngestResult(
    val newRaw: List<RawSms>,
    val newSourceRecords: List<SourceRecord>,
    val newEvents: List<BankEvent>,
    val updatedEvents: List<BankEvent>,
    /** Headers of unapproved senders: stored as metadata only, body never read. */
    val skippedUnapproved: List<SmsHeader>,
    val rejected: List<Pair<SmsHeader, String>>,
)

/**
 * Shared pipeline for direct reading and backup import (spec §04).
 * Deduplication levels:
 *  1. Same original message (fingerprint) from several sources -> one raw message, many source records.
 *  2. Same bank reference -> extra evidence attached to the existing event.
 *  3. Similar but not provably identical (account, amount, kind, time) -> new event under review, never deleted.
 */
class IngestionPipeline(
    private val consent: ConsentGate,
    private val parsers: ParserRegistry,
    private val accounts: List<Account>,
    private val zone: ZoneId,
    private val clock: () -> Instant = Instant::now,
    private val maxBodyLength: Int = 2_000,
) {
    fun ingest(batch: List<IncomingSms>, state: IngestState, batchId: String): IngestResult {
        val raws = state.knownFingerprints.toMutableSet()
        val sourceKeys = state.sourceKeys.toMutableSet()
        val events = state.events.toMutableMap()
        val newRaw = mutableListOf<RawSms>()
        val newSources = mutableListOf<SourceRecord>()
        val newEvents = linkedMapOf<String, BankEvent>()
        val updated = linkedMapOf<String, BankEvent>()
        val skipped = mutableListOf<SmsHeader>()
        val rejected = mutableListOf<Pair<SmsHeader, String>>()

        for (msg in batch) {
            val body = consent.readBodyIfApproved(msg.header.sender) { msg.bodySupplier() }
            if (body == null) { skipped += msg.header; continue }
            if (body.length > maxBodyLength) { rejected += msg.header to "body too long"; continue }
            val fp = Fingerprint.of(msg.header.sender, msg.header.receivedAt, body)
            val sourceKey = "$fp|${msg.source}|${msg.sourceRecordId}"
            if (sourceKey !in sourceKeys) {
                sourceKeys += sourceKey
                newSources += SourceRecord(fp, msg.source, msg.sourceRecordId, clock(), batchId)
            }
            if (fp in raws) continue // level 1: already known message -> source preserved, no new event
            val raw = RawSms(fp, msg.header.sender, msg.header.receivedAt, body)
            raws += fp; newRaw += raw

            val event = toEvent(raw)
            val existing = events[event.id]
            if (existing != null) {
                // level 2: same bank reference -> attach evidence
                val merged = existing.copy(rawSmsIds = (existing.rawSmsIds + fp).distinct())
                events[event.id] = merged
                if (event.id in newEvents) newEvents[event.id] = merged else updated[event.id] = merged
                continue
            }
            val similar = events.values.any { isSimilar(it, event) }
            val finalEvent = if (similar) event.copy(
                state = EventState.UNDER_REVIEW,
                reviewReasons = event.reviewReasons + ReviewReason.POSSIBLE_DUPLICATE,
            ) else applyAutoConfirm(event, raw.sender, state)
            events[finalEvent.id] = finalEvent
            newEvents[finalEvent.id] = finalEvent
        }
        return IngestResult(newRaw, newSources, newEvents.values.toList(), updated.values.toList(), skipped, rejected)
    }

    private fun applyAutoConfirm(e: BankEvent, sender: String, state: IngestState): BankEvent {
        if (e.state != EventState.UNDER_REVIEW) return e
        val onlySoft = e.reviewReasons.all { it == ReviewReason.LOW_CONFIDENCE }
        return if (onlySoft && sender.uppercase() in state.approvedAutoConfirmSenders)
            e.copy(state = EventState.CONFIRMED, reviewReasons = emptySet()) else e
    }

    fun toEvent(raw: RawSms): BankEvent {
        val bankId = consent.bankFor(raw.sender) ?: error("consent gate bypassed")
        val parser = parsers.forBank(bankId)
        val p = parser.parse(raw, zone)
        val account = p.accountHint?.let { hint -> accounts.firstOrNull { it.bankId == bankId && it.maskedNumber.endsWith(hint.takeLast(4)) } }
        val counterparty = p.counterpartyHint?.let { hint -> accounts.firstOrNull { it.maskedNumber.endsWith(hint.takeLast(4)) && it.id != account?.id } }
        val reasons = p.reasons.toMutableSet()
        if (account == null && p.accountHint != null) reasons += ReviewReason.MISSING_ACCOUNT
        val trustedTime = p.executedAt != null
        val state = when {
            p.currency != "SAR" && p.sarDebit == null -> EventState.PENDING_FX
            !parser.verifiedForBank || p.confidence != Confidence.HIGH || reasons.isNotEmpty() -> EventState.UNDER_REVIEW
            else -> EventState.CONFIRMED
        }
        val id = eventId(bankId, p.reference, p.kind, raw.fingerprint)
        return BankEvent(
            id = id,
            rawSmsIds = listOf(raw.fingerprint),
            bankId = bankId,
            accountId = account?.id,
            kind = p.kind,
            amount = p.amount,
            fee = p.fee,
            originalCurrency = p.currency,
            originalAmountText = p.originalAmountText,
            reference = p.reference,
            counterpartyHint = p.counterpartyHint,
            counterpartyAccountId = counterparty?.id,
            executedAt = p.executedAt ?: raw.receivedAt,
            timeSource = if (trustedTime) TimeSource.BANK else TimeSource.RECEIPT,
            timeConfidence = if (trustedTime) p.confidence else Confidence.LOW,
            balanceAfter = p.balanceAfter,
            state = state,
            reviewReasons = reasons,
            parserId = parser.id,
            parserVersion = parser.version,
        )
    }

    companion object {
        /** Stable identity: strong bank reference when present, otherwise the original message fingerprint. */
        fun eventId(bankId: String, reference: String?, kind: EventKind, fingerprint: String): String =
            if (!reference.isNullOrBlank()) "ref:" + Fingerprint.sha256("$bankId|${kind.direction}|${reference.uppercase()}").take(32)
            else "raw:" + fingerprint.take(32)

        fun isSimilar(a: BankEvent, b: BankEvent): Boolean =
            a.id != b.id && a.accountId != null && a.accountId == b.accountId && a.kind == b.kind &&
                a.amount != null && a.amount == b.amount &&
                Duration.between(a.executedAt, b.executedAt).abs() <= Duration.ofMinutes(2) &&
                (a.reference == null || b.reference == null)
    }
}
