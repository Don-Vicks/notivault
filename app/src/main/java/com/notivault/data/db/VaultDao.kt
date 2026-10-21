package com.notivault.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

private const val ROW_SELECT = """
    SELECT m.*, c.title AS conversationTitle, c.isGroup AS isGroup, a.label AS appLabel
    FROM messages m
    JOIN conversations c ON c.id = m.conversationId
    JOIN apps a ON a.packageName = m.packageName
"""

@Dao
interface VaultDao {

    // ---- apps ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApp(app: AppEntity): Long

    @Query("SELECT * FROM apps WHERE packageName = :pkg")
    suspend fun app(pkg: String): AppEntity?

    @Query("UPDATE apps SET captureEnabled = :enabled WHERE packageName = :pkg")
    suspend fun setCaptureEnabled(pkg: String, enabled: Boolean)

    @Query(
        """
        SELECT a.*,
          (SELECT COUNT(*) FROM messages m WHERE m.packageName = a.packageName) AS messageCount,
          (SELECT COUNT(*) FROM messages m WHERE m.packageName = a.packageName AND m.deletedBySender = 1) AS deletedCount
        FROM apps a ORDER BY messageCount DESC, a.label COLLATE NOCASE
        """,
    )
    fun appRows(): Flow<List<AppRow>>

    @Query("SELECT * FROM apps")
    suspend fun allApps(): List<AppEntity>

    // ---- conversations ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversation(c: ConversationEntity): Long

    @Query("SELECT * FROM conversations WHERE packageName = :pkg AND `key` = :key")
    suspend fun conversation(pkg: String, key: String): ConversationEntity?

    @Query("UPDATE conversations SET lastMessageAt = MAX(lastMessageAt, :ts), title = :title, isGroup = :isGroup WHERE id = :id")
    suspend fun touchConversation(id: Long, ts: Long, title: String, isGroup: Boolean)

    @Query(
        """
        SELECT c.id, c.packageName, c.title, c.isGroup, c.lastMessageAt, a.label AS appLabel,
          (SELECT text FROM messages WHERE conversationId = c.id ORDER BY msgTimestamp DESC LIMIT 1) AS lastText,
          (SELECT sender FROM messages WHERE conversationId = c.id ORDER BY msgTimestamp DESC LIMIT 1) AS lastSender,
          (SELECT COUNT(*) FROM messages WHERE conversationId = c.id) AS messageCount,
          (SELECT COUNT(*) FROM messages WHERE conversationId = c.id AND deletedBySender = 1) AS deletedCount
        FROM conversations c JOIN apps a ON a.packageName = c.packageName
        WHERE (:pkg IS NULL OR c.packageName = :pkg)
        ORDER BY c.lastMessageAt DESC
        """,
    )
    fun conversationRows(pkg: String?): PagingSource<Int, ConversationRow>

    @Query("SELECT c.*, a.label AS appLabel FROM conversations c JOIN apps a ON a.packageName = c.packageName WHERE c.id = :id")
    fun conversationHeader(id: Long): Flow<ConversationHeader?>

    @Query("SELECT * FROM conversations")
    suspend fun allConversations(): List<ConversationEntity>

    // ---- messages ----
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(m: MessageEntity): Long

    @Query(
        """
        SELECT id, sender, msgTimestamp, deletedBySender FROM messages
        WHERE conversationId = :conversationId AND msgTimestamp <= :upTo
        ORDER BY msgTimestamp DESC LIMIT 50
        """,
    )
    suspend fun recentForDeletion(conversationId: Long, upTo: Long): List<DeletionCandidateRow>

    @Query(
        """
        SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND sender = :sender AND text = :text
        AND msgTimestamp BETWEEN :from AND :to
        """,
    )
    suspend fun countSimilar(conversationId: Long, sender: String, text: String, from: Long, to: Long): Int

    @Query(
        """
        SELECT id, sender, msgTimestamp, deletedBySender FROM messages
        WHERE conversationId = :conversationId AND sender = :sender AND text = :text AND msgTimestamp BETWEEN :from AND :to
        ORDER BY ABS(msgTimestamp - :near) LIMIT 1
        """,
    )
    suspend fun findSimilar(conversationId: Long, sender: String, text: String, near: Long, from: Long, to: Long): DeletionCandidateRow?

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND sender = :sender AND deletedBySender = 1 AND msgTimestamp >= :from")
    suspend fun countDeleted(conversationId: Long, sender: String, from: Long): Int

    @Query("UPDATE messages SET attachmentPath = :path WHERE hash = :hash")
    suspend fun setAttachment(hash: String, path: String)

    @Query("UPDATE messages SET deletedBySender = 1, deletedDetectedAt = :at WHERE id = :id")
    suspend fun markDeleted(id: Long, at: Long)

    @Query(
        "$ROW_SELECT WHERE (:pkg IS NULL OR m.packageName = :pkg) AND (:deletedOnly = 0 OR m.deletedBySender = 1) " +
            "AND m.msgTimestamp BETWEEN :from AND :to ORDER BY m.msgTimestamp DESC",
    )
    fun timeline(pkg: String?, deletedOnly: Boolean, from: Long, to: Long): PagingSource<Int, MessageRow>

    @Query("$ROW_SELECT WHERE m.conversationId = :conversationId ORDER BY m.msgTimestamp DESC")
    fun conversationMessages(conversationId: Long): PagingSource<Int, MessageRow>

    @Query("$ROW_SELECT WHERE m.id = :id")
    fun message(id: Long): Flow<MessageRow?>

    @Query("$ROW_SELECT WHERE m.id = :id")
    suspend fun messageOnce(id: Long): MessageRow?

    @Query(
        """
        SELECT m.*, c.title AS conversationTitle, c.isGroup AS isGroup, a.label AS appLabel
        FROM messages_fts f
        JOIN messages m ON m.id = f.rowid
        JOIN conversations c ON c.id = m.conversationId
        JOIN apps a ON a.packageName = m.packageName
        WHERE messages_fts MATCH :query
        ORDER BY m.msgTimestamp DESC LIMIT 300
        """,
    )
    fun search(query: String): Flow<List<MessageRow>>

    @Query("SELECT * FROM messages ORDER BY id")
    suspend fun allMessages(): List<MessageEntity>

    @Query("SELECT attachmentPath FROM messages WHERE postedAt < :cutoff AND attachmentPath IS NOT NULL")
    suspend fun attachmentsOlderThan(cutoff: Long): List<String>

    @Query("DELETE FROM messages WHERE postedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM conversations WHERE id NOT IN (SELECT DISTINCT conversationId FROM messages)")
    suspend fun deleteEmptyConversations(): Int

    @Query("SELECT attachmentPath FROM messages WHERE attachmentPath IS NOT NULL")
    suspend fun allAttachments(): List<String>

    @Query("DELETE FROM conversations")
    suspend fun wipeConversations()

    @Query("DELETE FROM apps")
    suspend fun wipeApps()

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM messages) AS total,
               (SELECT COUNT(*) FROM messages WHERE deletedBySender = 1) AS deleted,
               (SELECT COUNT(*) FROM conversations) AS conversations,
               (SELECT COUNT(*) FROM apps) AS apps
        """,
    )
    fun stats(): Flow<VaultStats>
}

data class DeletionCandidateRow(val id: Long, val sender: String, val msgTimestamp: Long, val deletedBySender: Boolean)

data class ConversationHeader(
    val id: Long,
    val packageName: String,
    val key: String,
    val title: String,
    val isGroup: Boolean,
    val lastMessageAt: Long,
    val appLabel: String,
)
