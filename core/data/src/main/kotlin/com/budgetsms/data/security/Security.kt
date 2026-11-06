package com.budgetsms.data.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.budgetsms.data.db.DatabaseUnavailableException
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * The SQLCipher passphrase is 32 random bytes, wrapped with an AES-256-GCM key held in the
 * Android Keystore and stored in no-backup storage. If the database exists but the key cannot be
 * recovered, we refuse to open it instead of recreating it (prompt Phase 1).
 */
class DatabaseKeyManager(private val context: Context) {
    private val alias = "budgetsms.db.wrap.v1"
    private val file get() = File(context.noBackupFilesDir, "db.key.v1")

    fun passphrase(databaseExists: Boolean): ByteArray {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (file.exists()) {
            val key = (ks.getKey(alias, null) as? SecretKey)
                ?: throw DatabaseUnavailableException(DatabaseUnavailableException.Reason.KEY_INVALIDATED)
            return try { unwrap(key, file.readBytes()) } catch (t: Throwable) {
                throw DatabaseUnavailableException(DatabaseUnavailableException.Reason.KEY_INVALIDATED, t)
            }
        }
        if (databaseExists) throw DatabaseUnavailableException(DatabaseUnavailableException.Reason.KEY_MISSING)
        val key = (ks.getKey(alias, null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
            generateKey()
        }
        val pass = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(wrap(key, pass))
        check(tmp.renameTo(file)) { "cannot persist key" }
        return pass
    }

    private fun wrap(key: SecretKey, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key)
        return byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(data)
    }

    private fun unwrap(key: SecretKey, blob: ByteArray): ByteArray {
        val ivLen = blob[0].toInt()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob, 1, ivLen))
        return c.doFinal(blob, 1 + ivLen, blob.size - 1 - ivLen)
    }
}

/** PIN is stored only as a salted PBKDF2 hash; never in plaintext. */
object PinHasher {
    private const val ITERATIONS = 120_000
    fun hash(pin: CharArray, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val h = pbkdf2(pin, salt, ITERATIONS, 32)
        return "pbkdf2$$ITERATIONS$${salt.b64()}$${h.b64()}"
    }
    fun verify(pin: CharArray, stored: String): Boolean {
        val p = stored.split('$'); if (p.size != 4) return false
        val expected = p[3].unb64()
        return MessageDigest.isEqual(pbkdf2(pin, p[2].unb64(), p[1].toInt(), expected.size), expected)
    }
}

/**
 * Password-protected backup container:
 * magic "BSMSBK1" | salt(16) | iv(12) | AES-256-GCM(payload). The payload starts with its own
 * SHA-256 so integrity is checked twice (GCM tag + digest). Key: PBKDF2-HMAC-SHA256, 210k rounds.
 */
object BackupCrypto {
    private val MAGIC = "BSMSBK1".toByteArray()
    private const val ITERATIONS = 210_000

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = SecretKeySpec(pbkdf2(password, salt, ITERATIONS, 32), "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val digest = MessageDigest.getInstance("SHA-256").digest(plain)
        return ByteArrayOutputStream().apply { write(MAGIC); write(salt); write(iv); write(c.doFinal(digest + plain)) }.toByteArray()
    }

    class WrongPasswordOrCorrupted : Exception("wrong password or corrupted backup")

    fun decrypt(blob: ByteArray, password: CharArray): ByteArray {
        if (blob.size < MAGIC.size + 28 + 16 || !blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) throw WrongPasswordOrCorrupted()
        val salt = blob.copyOfRange(MAGIC.size, MAGIC.size + 16)
        val iv = blob.copyOfRange(MAGIC.size + 16, MAGIC.size + 28)
        val key = SecretKeySpec(pbkdf2(password, salt, ITERATIONS, 32), "AES")
        val out = try {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            c.doFinal(blob, MAGIC.size + 28, blob.size - MAGIC.size - 28)
        } catch (t: Throwable) { throw WrongPasswordOrCorrupted() }
        val digest = out.copyOfRange(0, 32); val plain = out.copyOfRange(32, out.size)
        if (!MessageDigest.isEqual(digest, MessageDigest.getInstance("SHA-256").digest(plain))) throw WrongPasswordOrCorrupted()
        return plain
    }
}

private fun pbkdf2(pw: CharArray, salt: ByteArray, iterations: Int, bytes: Int): ByteArray =
    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pw, salt, iterations, bytes * 8)).encoded

private fun ByteArray.b64(): String = android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
private fun String.unb64(): ByteArray = android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
