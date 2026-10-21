package com.notivault.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Hardware-backed (Android Keystore) AES-256-GCM key. It wraps the random SQLCipher passphrase and
 * encrypts attachment thumbnails at rest. The raw key material never leaves the Keystore.
 */
class KeyManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("vault_keys", Context.MODE_PRIVATE)

    /**
     * SQLCipher key in raw-key form (`x'<64 hex>'`): a random 256-bit key needs no PBKDF2 stretching,
     * which otherwise costs ~1s per pooled connection. Vaults created with the older passphrase form
     * are re-keyed once.
     */
    fun databasePassphrase(dbFile: File): ByteArray {
        val key = prefs.getString(PREF_DB_KEY, null)?.let { decrypt(Base64.decode(it, Base64.NO_WRAP)) }
            ?: ByteArray(32).also {
                SecureRandom().nextBytes(it)
                prefs.edit().putString(PREF_DB_KEY, Base64.encodeToString(encrypt(it), Base64.NO_WRAP))
                    .putBoolean(PREF_RAW, true).apply()
            }
        val raw = "x'${key.joinToString("") { "%02x".format(it) }}'"
        if (!prefs.getBoolean(PREF_RAW, false)) {
            if (dbFile.exists()) {
                val legacy = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(dbFile, key, null, null)
                legacy.rawExecSQL("PRAGMA rekey = \"$raw\";")
                legacy.close()
            }
            prefs.edit().putBoolean(PREF_RAW, true).apply()
        }
        return raw.toByteArray()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return cipher.iv + cipher.doFinal(plain)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, blob, 0, IV_LEN))
        return cipher.doFinal(blob, IV_LEN, blob.size - IV_LEN)
    }

    fun writeEncrypted(file: File, plain: ByteArray) {
        file.parentFile?.mkdirs()
        file.writeBytes(encrypt(plain))
    }

    fun readEncrypted(file: File): ByteArray? = runCatching { decrypt(file.readBytes()) }.getOrNull()

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "notivault_master"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_LEN = 12
        const val PREF_DB_KEY = "db_passphrase"
        const val PREF_RAW = "db_raw_key"
    }
}
