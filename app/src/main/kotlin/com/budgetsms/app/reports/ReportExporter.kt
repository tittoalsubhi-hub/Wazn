package com.budgetsms.app.reports

import android.content.Context
import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.appcompat.app.AppCompatDelegate
import com.budgetsms.app.R
import com.budgetsms.app.ui.components.Fmt
import com.budgetsms.data.AppSnapshot
import com.budgetsms.domain.model.EventKind
import com.budgetsms.domain.model.EventState
import com.budgetsms.domain.money.Money
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * PDF and Excel exports built from the same accounting results. Every amount carries its provenance;
 * internal transfers are excluded from expense totals by the engine, so nothing is duplicated (spec §13).
 */
class ReportExporter(base: Context) {
    private val locale: Locale = AppCompatDelegate.getApplicationLocales()[0] ?: base.resources.configuration.locales[0]
    private val ctx: Context = base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    private val rtl = locale.language == "ar"
    private fun s(id: Int, vararg a: Any) = ctx.getString(id, *a)
    private fun m(v: Money) = Fmt.money(v, locale)

    data class Table(val title: String, val header: List<String>, val rows: List<List<String>>)

    fun build(snap: AppSnapshot, profileId: String?, cycleId: String?, goalNames: Map<String, String>): List<Table> {
        val l = snap.ledger ?: return emptyList()
        val budgets = l.budgets.values.filter { b -> (profileId == null || b.cycle.profileId == profileId) && (cycleId?.let { it == b.cycle.id } ?: b.cycle.isOpen) }
        val prov = { id: Int -> s(id) }
        val summary = Table(s(R.string.report_summary), listOf(s(R.string.report_item), s(R.string.report_amount), s(R.string.report_provenance)), listOf(
            listOf(s(R.string.funded), m(Money.sum(budgets.map { it.funded })), prov(R.string.prov_confirmed)),
            listOf(s(R.string.planned), m(Money.sum(budgets.mapNotNull { it.planned })), prov(R.string.prov_planned)),
            listOf(s(R.string.spent), m(Money.sum(budgets.map { it.spent })), prov(R.string.prov_confirmed)),
            listOf(s(R.string.under_review), m(Money.sum(budgets.map { it.underReview })), prov(R.string.prov_review)),
            listOf(s(R.string.remaining_budget), m(Money.sum(budgets.map { it.remaining })), prov(R.string.prov_calculated)),
            listOf(s(R.string.unallocated), m(Money.sum(budgets.map { it.unallocated })), prov(R.string.prov_calculated)),
        ))
        val cats = Table(s(R.string.category_limits), listOf(s(R.string.field_category), s(R.string.spent), s(R.string.limit)),
            budgets.flatMap { it.categories }.groupBy { it.categoryId }.map { (id, list) ->
                val c = snap.category(id)
                listOf(c?.let { if (rtl) it.nameAr else it.nameEn } ?: s(R.string.uncategorized), m(Money.sum(list.map { it.spent })), list.mapNotNull { it.limit }.let { if (it.isEmpty()) "—" else m(Money.sum(it)) })
            })
        val ids = budgets.map { it.cycle.id }.toSet()
        val tx = Table(s(R.string.transactions), listOf(s(R.string.field_time), s(R.string.field_type), s(R.string.field_category), s(R.string.report_amount), s(R.string.report_state)),
            snap.events.filter { e -> l.assignment[e.id]?.let { a -> a in ids } == true }.sortedBy { it.executedAt }.map { e ->
                listOf(Fmt.dateTime(e.executedAt, locale), kind(e.kind), snap.category(e.categoryId)?.let { if (rtl) it.nameAr else it.nameEn } ?: "—",
                    e.amount?.let { m(it) } ?: (e.originalAmountText ?: "—"), state(e.state))
            })
        val ob = Table(s(R.string.obligations), listOf(s(R.string.name), s(R.string.due_date), s(R.string.remaining), s(R.string.report_provenance)),
            snap.occurrences.filter { (profileId == null || it.occurrence.profileId == profileId) && it.remaining.isPositive && it.daysToDue in -60..90 }.sortedBy { it.occurrence.dueDate }.map { o ->
                listOf(snap.obligations.firstOrNull { it.id == o.occurrence.obligationId }?.name ?: "", Fmt.date(o.occurrence.dueDate, locale), m(o.remaining), s(R.string.prov_forecast))
            })
        val goals = Table(s(R.string.tab_goals), listOf(s(R.string.goal), s(R.string.target), s(R.string.saved), s(R.string.remaining)),
            snap.goals.filter { profileId == null || it.goal.profileId == profileId }.map { g -> listOf(goalNames[g.goal.id] ?: "", m(g.goal.target), m(g.confirmedSaved), m(g.remaining)) })
        return listOf(summary, cats, tx, ob, goals)
    }

    fun export(snap: AppSnapshot, profileId: String?, cycleId: String?, pdf: Boolean, goalNames: Map<String, String>): File {
        val dir = File(ctx.cacheDir, "reports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // never leave old exports around
        val tables = build(snap, profileId, cycleId, goalNames)
        val name = "BudgetSMS-report-${System.currentTimeMillis()}"
        return if (pdf) File(dir, "$name.pdf").also { writePdf(tables, it) } else File(dir, "$name.xlsx").also { writeXlsx(tables, it) }
    }

    private fun kind(k: EventKind) = s(when (k) {
        EventKind.SALARY -> R.string.kind_salary; EventKind.ALLOWANCE -> R.string.kind_allowance; EventKind.INCOMING_TRANSFER -> R.string.kind_incoming
        EventKind.REFUND -> R.string.kind_refund; EventKind.PURCHASE_DEBIT -> R.string.kind_purchase; EventKind.PURCHASE_CREDIT_CARD -> R.string.kind_cc_purchase
        EventKind.ATM_WITHDRAWAL -> R.string.kind_atm; EventKind.OUTGOING_TRANSFER -> R.string.kind_outgoing; EventKind.CARD_REPAYMENT -> R.string.kind_repayment
        EventKind.FEE -> R.string.kind_fee; EventKind.REVERSAL -> R.string.kind_reversal; EventKind.FAILED -> R.string.kind_failed
        EventKind.BALANCE_ONLY -> R.string.kind_balance; EventKind.UNKNOWN -> R.string.kind_unknown })
    private fun state(st: EventState) = s(when (st) {
        EventState.CONFIRMED -> R.string.state_confirmed; EventState.UNDER_REVIEW -> R.string.state_review; EventState.REVERSED -> R.string.state_reversed
        EventState.DUPLICATE -> R.string.state_duplicate; EventState.PENDING_FX -> R.string.state_pending_fx; EventState.DISCOVERED -> R.string.state_discovered
        EventState.UNAPPROVED -> R.string.state_unapproved })

    /** StaticLayout shapes Arabic correctly and handles RTL paragraphs; pages break automatically. */
    private fun writePdf(tables: List<Table>, file: File) {
        val doc = PdfDocument(); val W = 595; val H = 842; val M = 36
        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create()); var y = M.toFloat()
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF0F5C60.toInt() }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = 0xFF1F3A4D.toInt() }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = 0xFF33444F.toInt() }
        val line = Paint().apply { color = 0xFFD5DDE2.toInt(); strokeWidth = 0.6f }
        val align = if (rtl) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
        fun layout(t: String, p: TextPaint, w: Int) = StaticLayout.Builder.obtain(t, 0, t.length, p, w).setAlignment(align).build()
        fun ensure(h: Float) { if (y + h > H - M) { doc.finishPage(page); page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create()); y = M.toFloat() } }
        fun draw(l: StaticLayout, x: Float) { page.canvas.save(); page.canvas.translate(x, y); l.draw(page.canvas); page.canvas.restore() }
        val t = layout(s(R.string.report_title) + "\n" + Fmt.dateTime(Instant.now(), locale), title, W - 2 * M); draw(t, M.toFloat()); y += t.height + 14
        for (tb in tables) {
            ensure(60f)
            val h = layout(tb.title, title.apply { textSize = 13f }, W - 2 * M); draw(h, M.toFloat()); y += h.height + 6
            val cols = tb.header.size; val cw = (W - 2 * M) / cols
            fun row(cells: List<String>, p: TextPaint) {
                val ls = cells.map { layout(it, p, cw - 6) }; val rh = ls.maxOf { it.height }.toFloat() + 6
                ensure(rh)
                ls.forEachIndexed { i, l -> val col = if (rtl) cols - 1 - i else i; draw(l, (M + col * cw + 3).toFloat()) }
                y += rh; page.canvas.drawLine(M.toFloat(), y - 2, (W - M).toFloat(), y - 2, line)
            }
            row(tb.header, head)
            if (tb.rows.isEmpty()) row(listOf("—") + List(cols - 1) { "" }, body) else tb.rows.forEach { row(it, body) }
            y += 12
        }
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }; doc.close()
    }

    /** Minimal, dependency-free SpreadsheetML (.xlsx) writer: one sheet per table, inline strings. */
    private fun writeXlsx(tables: List<Table>, file: File) {
        fun esc(x: String) = x.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("⁦", "").replace("⁩", "")
        fun colName(i: Int): String { var n = i + 1; val sb = StringBuilder(); while (n > 0) { val r = (n - 1) % 26; sb.insert(0, ('A' + r)); n = (n - 1) / 26 }; return sb.toString() }
        ZipOutputStream(FileOutputStream(file)).use { z ->
            fun put(path: String, content: String) { z.putNextEntry(ZipEntry(path)); z.write(content.toByteArray(Charsets.UTF_8)); z.closeEntry() }
            val names = tables.mapIndexed { i, t -> esc(t.title.take(28)).ifBlank { "Sheet${i + 1}" } }
            put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
                tables.indices.joinToString("") { """<Override PartName="/xl/worksheets/sheet${it + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } + "</Types>")
            put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            put("xl/workbook.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                names.mapIndexed { i, n -> """<sheet name="$n" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") + "</sheets></workbook>")
            put("xl/_rels/workbook.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                tables.indices.joinToString("") { """<Relationship Id="rId${it + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${it + 1}.xml"/>""" } + "</Relationships>")
            tables.forEachIndexed { i, t ->
                val rows = (listOf(t.header) + t.rows).mapIndexed { r, cells -> "<row r=\"${r + 1}\">" + cells.mapIndexed { c, v ->
                    "<c r=\"${colName(c)}${r + 1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${esc(v)}</t></is></c>" }.joinToString("") + "</row>" }.joinToString("")
                put("xl/worksheets/sheet${i + 1}.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetViews><sheetView workbookViewId="0" rightToLeft="${if (rtl) 1 else 0}"/></sheetViews><sheetData>$rows</sheetData></worksheet>""")
            }
        }
    }
}
