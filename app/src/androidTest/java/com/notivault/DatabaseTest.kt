package com.notivault

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.notivault.capture.ParsedMessage
import com.notivault.data.crypto.KeyManager
import com.notivault.data.db.AppDatabase
import com.notivault.data.repo.VaultRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    @Test fun migratesV1ToV3KeepingMessages() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO apps (packageName, label, captureEnabled, firstSeen) VALUES ('com.whatsapp', 'WhatsApp', 1, 0)")
            execSQL("INSERT INTO conversations (id, packageName, `key`, title, isGroup, lastMessageAt) VALUES (1, 'com.whatsapp', 'squad', 'Squad', 1, 5)")
            execSQL(
                "INSERT INTO messages (id, conversationId, packageName, sender, text, postedAt, msgTimestamp, hash, deletedBySender) " +
                    "VALUES (1, 1, 'com.whatsapp', 'Ada', 'hi', 5, 5, 'h1', 0)",
            )
            close()
        }
        val db = helper.runMigrationsAndValidate("migration-test", 3, true, AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        db.query("SELECT kind, possiblyUnsentAt FROM messages WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Text", c.getString(0))
            assertTrue(c.isNull(1))
        }
        db.query("SELECT COUNT(*) FROM media").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
    }

    @Test fun databaseFileIsEncryptedAtRest(): Unit = runBlocking {
        val name = "enc-test.db"
        context.deleteDatabase(name)
        val keys = KeyManager(context)
        val db = androidx.room.Room.databaseBuilder(context, AppDatabase::class.java, name)
            .openHelperFactory(com.notivault.data.db.LazyCipherFactory { keys.databasePassphrase(context.getDatabasePath(name)) })
            .build()
        val repo = VaultRepository(context, db, keys)
        repo.ingest("WhatsApp", listOf(ParsedMessage("com.whatsapp", "squad", "Squad", true, "Ada", "a very secret sentence", 1, 1)), null)
        assertEquals(1, repo.stats.first().total)
        db.close()

        val bytes = context.getDatabasePath(name).readBytes()
        assertFalse("plain SQLite header found", String(bytes.copyOfRange(0, 15)) == "SQLite format 3")
        assertFalse("plaintext leaked", String(bytes, Charsets.ISO_8859_1).contains("a very secret sentence"))
        context.deleteDatabase(name)
        Unit
    }

    @Test fun keystoreRoundTrip() {
        val keys = KeyManager(context)
        val data = "receipt".toByteArray()
        val sealed = keys.encrypt(data)
        assertFalse(sealed.contentEquals(data))
        assertArrayEquals(data, keys.decrypt(sealed))
    }
}
