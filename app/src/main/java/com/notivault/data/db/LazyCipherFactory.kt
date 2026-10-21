package com.notivault.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Defers everything expensive (Keystore unwrap, native library load, keying) until Room actually
 * opens the database, which happens on its background executors rather than at injection time.
 */
class LazyCipherFactory(private val passphrase: () -> ByteArray) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper =
        object : SupportSQLiteOpenHelper {
            private var wal: Boolean? = null
            private val real: SupportSQLiteOpenHelper by lazy {
                System.loadLibrary("sqlcipher")
                SupportOpenHelperFactory(passphrase()).create(configuration).also { h -> wal?.let(h::setWriteAheadLoggingEnabled) }
            }
            private var created = false

            override val databaseName: String? get() = configuration.name

            override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
                if (created) real.setWriteAheadLoggingEnabled(enabled) else wal = enabled
            }

            override val writableDatabase: SupportSQLiteDatabase get() = real.also { created = true }.writableDatabase
            override val readableDatabase: SupportSQLiteDatabase get() = real.also { created = true }.readableDatabase

            override fun close() { if (created) real.close() }
        }
}
