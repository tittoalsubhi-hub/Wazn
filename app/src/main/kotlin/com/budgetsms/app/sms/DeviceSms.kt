package com.budgetsms.app.sms

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.budgetsms.app.BudgetApp
import com.budgetsms.domain.ingest.IncomingSms
import com.budgetsms.domain.sms.ImportSource
import com.budgetsms.domain.sms.SenderDiscovery
import com.budgetsms.domain.sms.SenderNormalizer
import com.budgetsms.domain.sms.SmsHeader
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Device inbox access. Header discovery projects ADDRESS and DATE only; bodies are queried only for
 * the raw addresses of approved senders, so an unapproved body is never read (spec §04, T02).
 */
class DeviceSmsSource(private val context: Context) {
    fun hasReadPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    data class Discovery(val senders: List<SenderDiscovery.DiscoveredSender>, val rawAddresses: Map<String, Set<String>>)

    fun discoverHeaders(): Discovery {
        if (!hasReadPermission()) return Discovery(emptyList(), emptyMap())
        val headers = ArrayList<SmsHeader>()
        val raw = HashMap<String, MutableSet<String>>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE), null, null, null,
        )?.use { c ->
            val ia = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS); val id = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val addr = c.getString(ia) ?: continue
                headers += SmsHeader(addr, Instant.ofEpochMilli(c.getLong(id)))
                raw.getOrPut(SenderNormalizer.normalize(addr)) { mutableSetOf() } += addr
            }
        }
        return Discovery(SenderDiscovery.summarize(headers.asSequence()), raw)
    }

    /** Reads bodies only for the given raw addresses (all belonging to approved senders). */
    fun readApproved(rawAddresses: Collection<String>): List<IncomingSms> {
        if (!hasReadPermission() || rawAddresses.isEmpty()) return emptyList()
        val out = ArrayList<IncomingSms>()
        rawAddresses.chunked(50).forEach { chunk ->
            val sel = "${Telephony.Sms.ADDRESS} IN (${chunk.joinToString(",") { "?" }})"
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.DATE, Telephony.Sms.BODY),
                sel, chunk.toTypedArray(), "${Telephony.Sms.DATE} ASC",
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(Telephony.Sms._ID); val iA = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val iD = c.getColumnIndexOrThrow(Telephony.Sms.DATE); val iB = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                while (c.moveToNext()) {
                    val body = c.getString(iB) ?: continue
                    out += IncomingSms(SmsHeader(c.getString(iA), Instant.ofEpochMilli(c.getLong(iD))), ImportSource.DEVICE_INBOX, "inbox:${c.getLong(iId)}") { body }
                }
            }
        }
        return out
    }
}

/**
 * New-message reception. The sender is checked against the approved allowlist first; for other
 * senders the message body is not analysed or stored.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val container = (context.applicationContext as BudgetApp).container ?: return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val sender = parts[0].originatingAddress ?: return
        val receivedAt = Instant.ofEpochMilli(parts[0].timestampMillis)
        val pending = goAsync()
        container.scope.launch {
            try {
                val approved = container.repository.approvedSenders().map { it.senderId }.toSet()
                if (SenderNormalizer.normalize(sender) in approved) {
                    val msg = IncomingSms(SmsHeader(sender, receivedAt), ImportSource.DEVICE_RECEIVER, null) {
                        parts.joinToString("") { it.messageBody ?: "" }
                    }
                    val r = container.repository.ingest(listOf(msg))
                    if (r.newEvents.isNotEmpty()) container.notifier.newTransactions(r.newEvents.size, r.newEvents.count { it.state.name == "UNDER_REVIEW" })
                }
            } finally { pending.finish() }
        }
    }
}
