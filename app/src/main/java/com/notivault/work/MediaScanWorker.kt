package com.notivault.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.notivault.data.media.MediaVault
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Copies new chat media into the vault and flags files the app removed. */
@HiltWorker
class MediaScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val vault: MediaVault,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        vault.scanAll()
        return Result.success()
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "media-scan", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MediaScanWorker>(15, TimeUnit.MINUTES).build(),
            )
        }

        /** After a media message arrives: give the chat app time to auto-download, then copy it. */
        fun scanSoon(context: Context, delaySeconds: Long = 20) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "media-scan-soon", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<MediaScanWorker>().setInitialDelay(delaySeconds, TimeUnit.SECONDS).build(),
            )
        }
    }
}
