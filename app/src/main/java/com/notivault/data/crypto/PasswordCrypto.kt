package com.notivault.data.crypto

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Password-based crypto for PIN hashing and portable `.nvault` backups. Pure JVM, unit-tested. */
object PasswordCrypto {
    private val MAGIC = "NVLT1".toByteArray()
    private const val ITERATIONS = 120_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12

    fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }

    fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, iterations, 256))
            .encoded

    fun hashPin(pin: String, salt: ByteArray): ByteArray = deriveKey(pin.toCharArray(), salt, 60_000)

    fun verifyPin(pin: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hashPin(pin, salt), expected)

    /** Layout: MAGIC | salt(16) | iv(12) | AES-256-GCM ciphertext+tag. */
    fun seal(plain: ByteArray, password: CharArray): ByteArray {
        val salt = randomBytes(SALT_LEN)
        val iv = randomBytes(IV_LEN)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(deriveKey(password, salt), "AES"), GCMParameterSpec(128, iv))
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    /** @throws IllegalArgumentException on wrong format; javax.crypto.AEADBadTagException on wrong password. */
    fun open(blob: ByteArray, password: CharArray): ByteArray {
        require(blob.size > MAGIC.size + SALT_LEN + IV_LEN && blob.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            "Not a NotiVault backup"
        }
        var o = MAGIC.size
        val salt = blob.copyOfRange(o, o + SALT_LEN).also { o += SALT_LEN }
        val iv = blob.copyOfRange(o, o + IV_LEN).also { o += IV_LEN }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(deriveKey(password, salt), "AES"), GCMParameterSpec(128, iv))
        return cipher.doFinal(blob, o, blob.size - o)
    }
}
