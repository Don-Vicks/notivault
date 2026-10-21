package com.notivault.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AppEntity::class, ConversationEntity::class, MessageEntity::class, MessageFts::class, MediaEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): VaultDao
    abstract fun mediaDao(): MediaDao

    companion object {
        const val NAME = "vault.db"

        /** v2: message kind (photo, voice, reaction…) for richer social-app captures. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN kind TEXT NOT NULL DEFAULT 'Text'")
            }
        }

        /** v3: "possibly unsent" flag and the media vault. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN possiblyUnsentAt INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `media` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sourceUri` TEXT NOT NULL, `treeUri` TEXT NOT NULL, `displayName` TEXT NOT NULL, `folder` TEXT NOT NULL, " +
                        "`mime` TEXT, `size` INTEGER NOT NULL, `sourceModified` INTEGER NOT NULL, `capturedAt` INTEGER NOT NULL, " +
                        "`vaultPath` TEXT, `removedFromSourceAt` INTEGER)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_media_sourceUri` ON `media` (`sourceUri`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_capturedAt` ON `media` (`capturedAt`)")
            }
        }

        fun build(context: Context, passphrase: () -> ByteArray): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(LazyCipherFactory(passphrase))
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }
    }
}
