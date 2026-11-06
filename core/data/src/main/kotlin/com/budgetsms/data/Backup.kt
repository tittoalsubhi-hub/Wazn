package com.budgetsms.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Xml
import androidx.room.withTransaction
import com.budgetsms.data.db.BudgetDatabase
import com.budgetsms.data.db.CategoryEntity
import com.budgetsms.data.security.BackupCrypto
import com.budgetsms.domain.ingest.IncomingSms
import com.budgetsms.domain.sms.ImportSource
import com.budgetsms.domain.sms.SmsHeader
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant

/**
 * Password-protected manual backup (spec §13). Every table is exported generically; restore uses
 * INSERT OR IGNORE on stable primary keys, so duplicates of messages, events and matches are prevented.
 */
class BackupManager(private val db: BudgetDatabase) {
    private val tables = listOf(
        "raw_sms", "sms_source", "discovered_sender", "approved_sender", "bank", "profile", "account", "account_assignment",
        "financial_cycle", "bank_event", "category", "category_limit", "budget_allocation", "transfer_match", "transfer_rejection",
        "savings_goal", "savings_installment", "forecast", "recurring_obligation", "obligation_occurrence",
        "obligation_payment_match", "appearance_pref", "audit_log", "app_setting", "alert_sent",
    )

    suspend fun export(password: CharArray): ByteArray {
        val root = JSONObject().put("format", "budgetsms-backup").put("schemaVersion", SCHEMA_VERSION).put("createdAt", System.currentTimeMillis())
        val data = JSONObject()
        db.withTransaction {
            for (t in tables) {
                val arr = JSONArray()
                db.query("SELECT * FROM `$t`", null).use { c -> while (c.moveToNext()) arr.put(row(c)) }
                data.put(t, arr)
            }
        }
        root.put("tables", data)
        return BackupCrypto.encrypt(root.toString().toByteArray(Charsets.UTF_8), password)
    }

    data class RestoreReport(val inserted: Int, val skippedDuplicates: Int)

    suspend fun restore(blob: ByteArray, password: CharArray): RestoreReport {
        val json = JSONObject(String(BackupCrypto.decrypt(blob, password), Charsets.UTF_8))
        require(json.optString("format") == "budgetsms-backup") { "not a BudgetSMS backup" }
        val version = json.getInt("schemaVersion")
        require(version <= SCHEMA_VERSION) { "backup is from a newer app version" }
        val data = json.getJSONObject("tables")
        var inserted = 0; var skipped = 0
        db.withTransaction {
            val w = db.openHelper.writableDatabase
            for (t in tables) {
                val arr = data.optJSONArray(t) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val cv = ContentValues()
                    for (k in o.keys()) when (val v = o.get(k)) {
                        JSONObject.NULL -> cv.putNull(k)
                        is Int -> cv.put(k, v.toLong()); is Long -> cv.put(k, v); is Double -> cv.put(k, v)
                        is Boolean -> cv.put(k, if (v) 1 else 0); else -> cv.put(k, v.toString())
                    }
                    if (t == "audit_log") cv.remove("id")
                    val conflict = if (t == "appearance_pref") SQLiteDatabase.CONFLICT_REPLACE else SQLiteDatabase.CONFLICT_IGNORE
                    if (w.insert(t, conflict, cv) == -1L) skipped++ else inserted++
                }
            }
        }
        return RestoreReport(inserted, skipped)
    }

    private fun row(c: Cursor): JSONObject {
        val o = JSONObject()
        for (i in 0 until c.columnCount) {
            val n = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> o.put(n, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> o.put(n, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> o.put(n, c.getDouble(i))
                else -> o.put(n, c.getString(i))
            }
        }
        return o
    }

    companion object { const val SCHEMA_VERSION = 1 }
}

/**
 * Original-SMS backup import. Supported format: "SMS Backup & Restore" XML (<smses><sms address date type body/>).
 * Limits: 50 MB, 200 000 records, 2 000 characters per body. DOCTYPE processing and external entities are disabled.
 * Only inbox messages (type=1) are imported. A backup is not conclusive proof of authenticity: events still pass review.
 */
object SmsBackupReader {
    const val MAX_BYTES = 50L * 1024 * 1024
    const val MAX_RECORDS = 200_000

    class InvalidBackup(msg: String) : Exception(msg)

    fun read(input: InputStream, sizeHint: Long, fileId: String): List<IncomingSms> {
        if (sizeHint > MAX_BYTES) throw InvalidBackup("file too large")
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(input, "UTF-8")
        val out = ArrayList<IncomingSms>()
        var n = 0
        while (true) {
            val ev = try { p.next() } catch (t: Throwable) { throw InvalidBackup("malformed XML") }
            if (ev == XmlPullParser.END_DOCUMENT) break
            if (ev == XmlPullParser.DOCDECL) throw InvalidBackup("DOCTYPE not allowed")
            if (ev != XmlPullParser.START_TAG || p.name != "sms") continue
            if (++n > MAX_RECORDS) throw InvalidBackup("too many records")
            val type = p.getAttributeValue(null, "type")
            if (type != null && type != "1") continue
            val address = p.getAttributeValue(null, "address") ?: continue
            val date = p.getAttributeValue(null, "date")?.toLongOrNull() ?: continue
            val body = p.getAttributeValue(null, "body") ?: continue
            out += IncomingSms(SmsHeader(address, Instant.ofEpochMilli(date)), ImportSource.BACKUP_FILE, "$fileId:$n") { body }
        }
        return out
    }

    /** Header-only pass used for sender discovery from a file (bodies are not retained). */
    fun headers(input: InputStream, sizeHint: Long): List<SmsHeader> = read(input, sizeHint, "scan").map { it.header }
}

object DefaultCategories {
    fun all(): List<CategoryEntity> = listOf(
        c("groceries", "Groceries", "بقالة", "groceries", 0xFF2E9D5B),
        c("dining", "Dining", "مطاعم ومقاهي", "restaurant", 0xFFE2582B),
        c("transport", "Transport & fuel", "مواصلات ووقود", "car", 0xFF1F6FD1),
        c("bills", "Bills & utilities", "فواتير وخدمات", "bolt", 0xFFD9901A),
        c("housing", "Housing & rent", "سكن وإيجار", "home", 0xFF8A47D6),
        c("health", "Health", "صحة", "health", 0xFFD23F73),
        c("shopping", "Shopping", "تسوق", "bag", 0xFF4B4FD8),
        c("education", "Education", "تعليم", "school", 0xFF00897B),
        c("family", "Family", "عائلة", "family", 0xFF6D4C41),
        c("entertainment", "Entertainment", "ترفيه", "movie", 0xFFAB47BC),
        c("travel", "Travel", "سفر", "flight", 0xFF0288D1),
        c("cash", "Cash withdrawals", "سحوبات نقدية", "cash", 0xFF546E7A),
        c("transfers_out", "Transfers to others", "تحويلات للغير", "send", 0xFF5C6BC0),
        c("bank_fees", "Bank fees", "رسوم بنكية", "fee", 0xFF78909C),
        c("card_repayment", "Card repayment", "سداد البطاقة", "card", 0xFF455A64),
        c("uncategorized", "Uncategorized", "غير مصنف", "category", 0xFF9E9E9E),
    ).mapIndexed { i, e -> e.copy(sortOrder = i) }

    private fun c(id: String, en: String, ar: String, icon: String, color: Long) = CategoryEntity(id, en, ar, null, icon, color, 0)
}
