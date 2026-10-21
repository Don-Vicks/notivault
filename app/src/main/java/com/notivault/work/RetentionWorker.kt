package com.notivault.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notivault.capture.NotiListenerService
import com.notivault.data.prefs.SettingsStore
import com.notivault.data.repo.VaultRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Daily: applies the retention policy and re-binds the listener if the system dropped it. */
@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: VaultRepository,
    private val settings: SettingsStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val days = settings.current().retentionDays
        if (days > 0) repo.purgeOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
        if (!settings.current().listenerConnected) NotiListenerService.rebind(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "retention",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RetentionWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
