package com.notivault

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import com.notivault.capture.RecoveredNotifier
import com.notivault.security.LockManager
import com.notivault.work.RetentionWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NotiVaultApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var lockManager: LockManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        RecoveredNotifier.createChannels(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lockManager)
        RetentionWorker.schedule(this)
    }
}
