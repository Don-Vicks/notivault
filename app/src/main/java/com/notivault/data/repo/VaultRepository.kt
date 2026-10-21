package com.notivault.data.repo

import android.content.Context
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.room.withTransaction
import com.notivault.capture.DedupEngine
import com.notivault.capture.DeletionDetector
import com.notivault.capture.ParsedMessage
import com.notivault.data.crypto.KeyManager
import com.notivault.data.db.AppDatabase
import com.notivault.data.db.AppEntity
import com.notivault.data.db.ConversationEntity
import com.notivault.data.db.ConversationRow
import com.notivault.data.db.MessageEntity
import com.notivault.data.db.MessageRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.io.File

data class IngestResult(val inserted: Int, val recovered: List<MessageRow>)

class VaultRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val keys: KeyManager,
) {
    private val dao = db.dao()
    private val paging = PagingConfig(pageSize = 40, prefetchDistance = 20, enablePlaceholders = false)

    val stats = dao.stats()
    val apps = dao.appRows()

    fun timeline(pkg: String?, deletedOnly: Boolean, from: Long = 0, to: Long = Long.MAX_VALUE): Flow<PagingData<MessageRow>> =
        Pager(paging) { dao.timeline(pkg, deletedOnly, from, to) }.flow

    fun conversations(pkg: String?): Flow<PagingData<ConversationRow>> =
        Pager(paging) { dao.conversationRows(pkg) }.flow

    fun conversationMessages(id: Long): Flow<PagingData<MessageRow>> =
        Pager(paging) { dao.conversationMessages(id) }.flow

    fun conversationHeader(id: Long) = dao.conversationHeader(id)
    fun message(id: Long) = dao.message(id)

    fun search(raw: String): Flow<List<MessageRow>> {
        val q = toFtsQuery(raw) ?: return flowOf(emptyList())
        return dao.search(q)
    }

    suspend fun setCaptureEnabled(pkg: String, enabled: Boolean) = dao.setCaptureEnabled(pkg, enabled)

    suspend fun isCaptureEnabled(pkg: String): Boolean = dao.app(pkg)?.captureEnabled ?: true

    /** Stores new messages and flags originals that a deletion placeholder points at. */
    suspend fun ingest(
        appLabel: String,
        messages: List<ParsedMessage>,
        thumbnail: ByteArray?,
        removedFromHistory: List<ParsedMessage> = emptyList(),
    ): IngestResult {
        if (messages.isEmpty() && removedFromHistory.isEmpty()) return IngestResult(0, emptyList())
        val now = System.currentTimeMillis()
        val recoveredIds = mutableListOf<Long>()
        var inserted = 0
        db.withTransaction {
            dao.insertApp(AppEntity((messages.firstOrNull() ?: removedFromHistory.first()).packageName, appLabel, captureEnabled = true, firstSeen = now))
            val convIds = HashMap<String, Long>()
            suspend fun convId(m: ParsedMessage): Long = convIds.getOrPut(m.conversationKey) {
                dao.conversation(m.packageName, m.conversationKey)?.id ?: dao.insertConversation(
                    ConversationEntity(
                        packageName = m.packageName, key = m.conversationKey, title = m.conversationTitle,
                        isGroup = m.isGroup, lastMessageAt = m.msgTimestamp,
                    ),
                )
            }

            val (markers, content) = DedupEngine.distinct(messages).partition { DeletionDetector.isDeletionMarker(it.text) }

            // History payloads: keep only messages we haven't stored yet, even if the app re-stamped them.
            val fresh = content.filter { !it.fromHistory } + content.filter { it.fromHistory }
                .groupBy { Triple(it.conversationKey, it.sender, it.text) }
                .flatMap { (_, group) ->
                    val id = convId(group.first())
                    val stored = dao.countSimilar(
                        id, group.first().sender, group.first().text,
                        group.minOf { it.msgTimestamp } - DedupEngine.HISTORY_WINDOW_MS,
                        group.maxOf { it.msgTimestamp } + DedupEngine.HISTORY_WINDOW_MS,
                    )
                    DedupEngine.unseenFromHistory(group, stored)
                }

            for (m in fresh.sortedBy { it.msgTimestamp }) {
                val id = convId(m)
                dao.touchConversation(id, m.msgTimestamp, m.conversationTitle, m.isGroup)
                val rowId = dao.insertMessage(
                    MessageEntity(
                        conversationId = id, packageName = m.packageName, sender = m.sender, text = m.text,
                        postedAt = m.postedAt, msgTimestamp = m.msgTimestamp, hash = m.hash, kind = m.kind.name,
                    ),
                )
                if (rowId > 0) inserted++
            }

            // A placeholder stays in the re-posted history, so only act on placeholders beyond those
            // already accounted for by messages we previously flagged for that sender.
            markers.groupBy { it.conversationKey to it.sender }.forEach { (_, group) ->
                val id = convId(group.first())
                val alreadyFlagged = dao.countDeleted(id, group.first().sender,
                    group.minOf { it.msgTimestamp } - DeletionDetector.WINDOW_MS)
                for (m in group.sortedBy { it.msgTimestamp }.drop(alreadyFlagged)) {
                    val candidates = dao.recentForDeletion(id, m.msgTimestamp + DedupEngine.HISTORY_WINDOW_MS)
                        .map { DeletionDetector.Candidate(it.id, it.sender, it.msgTimestamp, it.deletedBySender) }
                    DeletionDetector.matchTarget(m, candidates)?.let {
                        dao.markDeleted(it.id, now)
                        recoveredIds += it.id
                    }
                }
            }

            // Apps that silently drop deleted messages from the re-posted history.
            for (m in removedFromHistory) {
                val id = convId(m)
                val w = DedupEngine.HISTORY_WINDOW_MS
                dao.findSimilar(id, m.sender, m.text, m.msgTimestamp, m.msgTimestamp - w, m.msgTimestamp + w)
                    ?.takeIf { !it.deletedBySender && it.id !in recoveredIds }
                    ?.let { dao.markDeleted(it.id, now); recoveredIds += it.id }
            }
        }
        if (thumbnail != null && inserted > 0) attachThumbnail(messages.last(), thumbnail)
        return IngestResult(inserted, recoveredIds.mapNotNull { dao.messageOnce(it) })
    }

    /** Plain-text transcript of a conversation, oldest first, with deletion annotations. */
    suspend fun transcript(conversationId: Long): Pair<String, String>? {
        val header = dao.conversationHeaderOnce(conversationId) ?: return null
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val body = buildString {
            appendLine("${header.title} · ${header.appLabel}")
            appendLine("Exported from NotiVault on ${fmt.format(java.util.Date())}. Captured from notifications.")
            appendLine()
            dao.messagesFor(conversationId).forEach { m ->
                append("[").append(fmt.format(java.util.Date(m.msgTimestamp))).append("] ").append(m.sender).append(": ").append(m.text)
                when {
                    m.deletedBySender -> append("  [deleted by sender, recovered]")
                    m.possiblyUnsentAt != null -> append("  [possibly unsent]")
                }
                appendLine()
            }
        }
        return header.title to body
    }

    suspend fun markPossiblyUnsent(m: ParsedMessage) {
        val conv = dao.conversation(m.packageName, m.conversationKey) ?: return
        val w = DedupEngine.HISTORY_WINDOW_MS
        dao.findSimilar(conv.id, m.sender, m.text, m.msgTimestamp, m.msgTimestamp - w, m.msgTimestamp + w)
            ?.let { dao.markPossiblyUnsent(it.id, System.currentTimeMillis()) }
    }

    private suspend fun attachThumbnail(m: ParsedMessage, bytes: ByteArray) {
        val file = File(context.filesDir, "attachments/${m.hash}.bin")
        keys.writeEncrypted(file, bytes)
        dao.setAttachment(m.hash, file.absolutePath)
    }

    fun readAttachment(path: String): ByteArray? = keys.readEncrypted(File(path))

    suspend fun purgeOlderThan(cutoff: Long): Int {
        dao.attachmentsOlderThan(cutoff).forEach { File(it).delete() }
        val n = dao.deleteOlderThan(cutoff)
        dao.deleteEmptyConversations()
        return n
    }

    suspend fun wipeAll() {
        dao.allAttachments().forEach { File(it).delete() }
        db.withTransaction {
            dao.wipeConversations() // cascades to messages
            dao.wipeApps()
        }
    }

    companion object {
        /** Turns free text into a safe FTS4 prefix query: `hello wor` -> `"hello"* "wor"*`. */
        fun toFtsQuery(raw: String): String? {
            val terms = raw.split(Regex("\\s+"))
                .map { it.replace("\"", "").replace("*", "") }
                .filter { it.isNotBlank() }
            if (terms.isEmpty()) return null
            return terms.joinToString(" ") { "\"$it\"*" }
        }
    }
}
