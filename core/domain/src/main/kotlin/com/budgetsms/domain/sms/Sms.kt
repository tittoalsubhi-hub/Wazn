package com.budgetsms.domain.sms

import java.security.MessageDigest
import java.time.Instant

enum class ImportSource { DEVICE_INBOX, DEVICE_RECEIVER, BACKUP_FILE }

/** Header metadata only. Discovery never touches the message body (spec §04). */
data class SmsHeader(val sender: String, val receivedAt: Instant)

/** Original message, preserved unchanged once its sender is approved. */
data class RawSms(
    val fingerprint: String,
    val sender: String,
    val receivedAt: Instant,
    val body: String,
)

/** One way a raw message reached the app. Several records can point to the same fingerprint (T01). */
data class SourceRecord(
    val fingerprint: String,
    val source: ImportSource,
    val sourceRecordId: String?,
    val importedAt: Instant,
    val importBatchId: String,
)

data class ApprovedSender(val senderId: String, val bankId: String, val approvedAt: Instant, val revokedAt: Instant? = null) {
    val active get() = revokedAt == null
}

object SenderNormalizer {
    fun normalize(sender: String): String = sender.trim().uppercase().replace(" ", "")
}

object Fingerprint {
    fun of(sender: String, receivedAt: Instant, body: String): String {
        val norm = SenderNormalizer.normalize(sender) + "|" + receivedAt.epochSecond + "|" +
            body.replace("\r\n", "\n").trim()
        return sha256(norm)
    }

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * Consent gate between header discovery and body access. The body supplier is invoked
 * only for approved, non-revoked senders, so unapproved bodies are never read (T02).
 * It is independent from Android runtime permissions, which are checked by the source.
 */
class ConsentGate(approved: Collection<ApprovedSender>) {
    private val active: Map<String, ApprovedSender> =
        approved.filter { it.active }.associateBy { SenderNormalizer.normalize(it.senderId) }

    fun isApproved(sender: String): Boolean = SenderNormalizer.normalize(sender) in active
    fun bankFor(sender: String): String? = active[SenderNormalizer.normalize(sender)]?.bankId

    inline fun readBodyIfApproved(sender: String, bodySupplier: () -> String): String? =
        if (isApproved(sender)) bodySupplier() else null
}

/** Groups discovered headers by sender for the setup screen. */
object SenderDiscovery {
    data class DiscoveredSender(val senderId: String, val messageCount: Int, val firstSeen: Instant, val lastSeen: Instant)

    fun summarize(headers: Sequence<SmsHeader>): List<DiscoveredSender> =
        headers.groupBy { SenderNormalizer.normalize(it.sender) }
            .map { (id, hs) -> DiscoveredSender(id, hs.size, hs.minOf { it.receivedAt }, hs.maxOf { it.receivedAt }) }
            .sortedByDescending { it.messageCount }
}
