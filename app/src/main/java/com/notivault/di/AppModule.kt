package com.notivault.di

import android.content.Context
import com.notivault.data.crypto.KeyManager
import com.notivault.data.db.AppDatabase
import com.notivault.data.prefs.SettingsStore
import com.notivault.data.repo.BackupManager
import com.notivault.data.repo.VaultRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun keyManager(@ApplicationContext ctx: Context) = KeyManager(ctx)

    @Provides @Singleton
    fun database(@ApplicationContext ctx: Context, keys: KeyManager): AppDatabase =
        AppDatabase.build(ctx) { keys.databasePassphrase(ctx.getDatabasePath(AppDatabase.NAME)) }

    @Provides @Singleton
    fun settings(@ApplicationContext ctx: Context) = SettingsStore(ctx)

    @Provides @Singleton
    fun repository(@ApplicationContext ctx: Context, db: AppDatabase, keys: KeyManager) = VaultRepository(ctx, db, keys)

    @Provides @Singleton
    fun backup(@ApplicationContext ctx: Context, db: AppDatabase, keys: KeyManager) = BackupManager(ctx, db, keys)
}
