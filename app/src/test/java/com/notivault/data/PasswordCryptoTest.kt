package com.notivault.data

import com.notivault.data.crypto.PasswordCrypto
import com.notivault.data.repo.BackupManager
import com.notivault.data.repo.VaultRepository
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.AEADBadTagException

class PasswordCryptoTest {
    @Test fun `seal and open round trip with gzip`() {
        val data = "{\"messages\":[]}".repeat(100).toByteArray()
        val sealed = PasswordCrypto.seal(BackupManager.gzip(data), "correct horse".toCharArray())
        assertArrayEquals(data, BackupManager.gunzip(PasswordCrypto.open(sealed, "correct horse".toCharArray())))
    }

    @Test(expected = AEADBadTagException::class)
    fun `wrong password fails authentication`() {
        val sealed = PasswordCrypto.seal("secret".toByteArray(), "pw-one-123".toCharArray())
        PasswordCrypto.open(sealed, "pw-two-123".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects non backup files`() { PasswordCrypto.open("hello world, not a backup at all!!".toByteArray(), "x".toCharArray()) }

    @Test fun `pin hashing verifies only the right pin`() {
        val salt = PasswordCrypto.randomBytes(16)
        val h = PasswordCrypto.hashPin("123456", salt)
        assertTrue(PasswordCrypto.verifyPin("123456", salt, h))
        assertFalse(PasswordCrypto.verifyPin("123457", salt, h))
    }

    @Test fun `fts query escapes and prefixes terms`() {
        assertEquals("\"hello\"* \"wor\"*", VaultRepository.toFtsQuery("  hello  wor "))
        assertEquals("\"ab\"*", VaultRepository.toFtsQuery("a\"b*"))
        assertNull(VaultRepository.toFtsQuery("   "))
    }
}
