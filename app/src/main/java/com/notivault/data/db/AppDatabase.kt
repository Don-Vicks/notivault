package com.notivault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppEntity::class, ConversationEntity::class, MessageEntity::class, MessageFts::class],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao

    companion object {
        const val NAME = "vault.db"

        /** v2: message kind (photo, voice, reaction…) for richer social-app captures. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN kind TEXT NOT NULL DEFAULT 'Text'")
            }
        }

        fun build(context: Context, passphrase: () -> ByteArray): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(LazyCipherFactory(passphrase))
                .addMigrations(MIGRATION_1_2)
                .build()
        }
    }
}
