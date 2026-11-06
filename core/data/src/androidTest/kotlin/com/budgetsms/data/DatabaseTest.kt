package com.budgetsms.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.budgetsms.data.db.*
import com.budgetsms.data.security.BackupCrypto
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Phase 1 gate: encryption, reopen without loss, missing key fails safely, uniqueness, backup roundtrip. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun clean() { BudgetDatabase.closeForTests(); ctx.deleteDatabase(BudgetDatabase.NAME); File(ctx.noBackupFilesDir, "db.key.v1").delete() }
    @After fun close() { BudgetDatabase.closeForTests() }

    private fun raw(fp: String) = RawSmsEntity(fp, "TESTBANK", 1L, "SYN synthetic", 2L)

    @Test fun encryptedReopenKeepsData() = runBlocking {
        BudgetDatabase.open(ctx).sms().insertRaw(listOf(raw("a")))
        BudgetDatabase.closeForTests()
        assertEquals(listOf("a"), BudgetDatabase.open(ctx).sms().allFingerprints())
        val header = ctx.getDatabasePath(BudgetDatabase.NAME).inputStream().use { s -> ByteArray(16).also { s.read(it) } }
        assertFalse(String(header).startsWith("SQLite format 3")) // file is not plaintext SQLite
    }

    @Test fun missingKeyNeverRecreatesDatabase() = runBlocking {
        BudgetDatabase.open(ctx).sms().insertRaw(listOf(raw("keep")))
        BudgetDatabase.closeForTests()
        val keyFile = File(ctx.noBackupFilesDir, "db.key.v1"); val saved = keyFile.readBytes(); keyFile.delete()
        val e = runCatching { BudgetDatabase.open(ctx) }.exceptionOrNull()
        assertTrue(e is DatabaseUnavailableException)
        assertTrue(ctx.getDatabasePath(BudgetDatabase.NAME).exists())
        keyFile.writeBytes(saved)
        assertEquals(listOf("keep"), BudgetDatabase.open(ctx).sms().allFingerprints())
    }

    @Test fun bankMovementPaysOnlyOneOccurrence() = runBlocking {
        val plan = BudgetDatabase.open(ctx).plan()
        plan.insertPayment(ObligationPaymentMatchEntity("ev1", "rent#0", 100, 1))
        assertTrue(runCatching { plan.insertPayment(ObligationPaymentMatchEntity("ev1", "gym#0", 100, 1)) }.isFailure)
    }

    @Test fun backupRoundTripPreventsDuplicates() = runBlocking {
        val db = BudgetDatabase.open(ctx); db.sms().insertRaw(listOf(raw("x"), raw("y")))
        val m = BackupManager(db); val blob = m.export("correct horse".toCharArray())
        val r = m.restore(blob, "correct horse".toCharArray())
        assertTrue(r.skippedDuplicates >= 2) // the two messages already existed
        assertEquals(2, db.sms().allFingerprints().size)
        assertTrue(runCatching { m.restore(blob, "wrong".toCharArray()) }.exceptionOrNull() is BackupCrypto.WrongPasswordOrCorrupted)
        val corrupted = blob.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertTrue(runCatching { m.restore(corrupted, "correct horse".toCharArray()) }.exceptionOrNull() is BackupCrypto.WrongPasswordOrCorrupted)
    }
}
