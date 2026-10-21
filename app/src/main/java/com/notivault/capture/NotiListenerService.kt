package com.notivault.capture

import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.notivault.capture.apps.AppProfiles
import com.notivault.capture.apps.DeletionSignal
import com.notivault.data.prefs.SettingsStore
import com.notivault.data.repo.VaultRepository
import com.notivault.work.MediaScanWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotiListenerService : NotificationListenerService() {

    @Inject lateinit var repo: VaultRepository
    @Inject lateinit var settings: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Single writer: notifications are persisted strictly in arrival order. */
    private val queue = Channel<Pair<RawNotification, ByteArray?>>(Channel.UNLIMITED)

    override fun onCreate() {
        super.onCreate()
        scope.launch { for ((raw, thumb) in queue) process(raw, thumb) }
    }

    override fun onListenerConnected() {
        scope.launch { settings.setListenerConnected(true) }
        // Capture whatever is currently in the shade (e.g. after a reboot or reinstall).
        runCatching { activeNotifications }.getOrNull()?.forEach { enqueue(it) }
    }

    override fun onListenerDisconnected() {
        scope.launch { settings.setListenerConnected(false) }
        requestRebind(ComponentName(this, NotiListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = enqueue(sbn)

    private val recentRemovals = ArrayDeque<RemovalHeuristic.Removal>()

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?, reason: Int) {
        val profile = AppProfiles.forPackage(sbn.packageName) ?: return
        val removal = RemovalHeuristic.Removal(sbn.packageName, sbn.key, System.currentTimeMillis(), reason, sbn.postTime)
        synchronized(recentRemovals) {
            recentRemovals.addLast(removal)
            while (recentRemovals.size > 50) recentRemovals.removeFirst()
        }
        val payload = synchronized(lastHistory) { lastHistory["${sbn.packageName}|${sbn.key}"] }.orEmpty()
        scope.launch {
            delay(RemovalHeuristic.BULK_WINDOW_MS + 500) // wait to see whether sibling chats were cleared too
            val recent = synchronized(recentRemovals) { recentRemovals.toList() }
            val likely = RemovalHeuristic.isLikelyUnsend(removal, recent, payload.size, DeletionSignal.HistoryRemoval in profile.deletion)
            if (likely) runCatching { repo.markPossiblyUnsent(payload.single()) }.onFailure { Log.w(TAG, "unsend mark failed", it) }
        }
    }

    private fun enqueue(sbn: StatusBarNotification) {
        runCatching {
            queue.trySend(NotificationExtractor.extract(sbn) to NotificationExtractor.thumbnail(this, sbn))
        }.onFailure { Log.w(TAG, "extract failed for ${sbn.packageName}", it) }
    }

    private suspend fun process(raw: RawNotification, thumb: ByteArray?) {
        runCatching {
            if (settings.current().capturePaused) return
            if (!repo.isCaptureEnabled(raw.packageName)) return
            if (settings.current().messagingOnly && !AppProfiles.isSocial(raw.packageName)) return
            val label = appLabel(raw.packageName, raw.appInfo)
            val parsed = NotificationParser.parse(raw, packageName, label)
            val removed = historyRemovals(raw, parsed)
            if (parsed.isEmpty() && removed.isEmpty()) return
            val result = repo.ingest(label, parsed, thumb, removed)
            if (parsed.any { it.kind in MEDIA_KINDS } || result.recovered.isNotEmpty()) MediaScanWorker.scanSoon(this)
            if (result.recovered.isNotEmpty() && settings.current().recoveredAlerts) {
                RecoveredNotifier.notify(this, result.recovered)
            }
            settings.heartbeat()
        }.onFailure { Log.e(TAG, "ingest failed", it) }
    }

    /** Last MessagingStyle payload per notification, for apps that delete by dropping history lines. */
    private val lastHistory = object : LinkedHashMap<String, List<ParsedMessage>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ParsedMessage>>?) = size > 200
    }

    private fun historyRemovals(raw: RawNotification, parsed: List<ParsedMessage>): List<ParsedMessage> {
        val profile = AppProfiles.forPackage(raw.packageName) ?: return emptyList()
        if (DeletionSignal.HistoryRemoval !in profile.deletion || raw.messages.isEmpty()) return emptyList()
        val key = "${raw.packageName}|${raw.key}"
        val previous = synchronized(lastHistory) { lastHistory.put(key, parsed) }.orEmpty()
        return HistoryDiff.removed(previous, parsed)
    }

    private fun appLabel(pkg: String, fromNotification: ApplicationInfo?): String =
        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            .recoverCatching { packageManager.getApplicationLabel(fromNotification!!).toString() }
            .getOrNull() ?: AppProfiles.forPackage(pkg)?.name ?: pkg

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "NotiVault"
        private val MEDIA_KINDS = setOf(MessageKind.Photo, MessageKind.Video, MessageKind.Voice, MessageKind.Audio,
            MessageKind.Gif, MessageKind.Sticker, MessageKind.Document)

        fun isEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return flat?.contains(ComponentName(context, NotiListenerService::class.java).flattenToString()) == true
        }

        fun rebind(context: Context) {
            if (!isEnabled(context)) return
            // Toggling the component nudges the system to re-bind a listener it has dropped.
            val cn = ComponentName(context, NotiListenerService::class.java)
            val pm = context.packageManager
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            requestRebind(cn)
        }
    }
}
