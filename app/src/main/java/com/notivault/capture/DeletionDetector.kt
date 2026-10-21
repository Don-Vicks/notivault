package com.notivault.capture

/**
 * Detects the placeholders apps substitute when a sender deletes a message ("delete for everyone").
 * The placeholder itself is never stored; instead it is used to flag the original we already saved.
 */
object DeletionDetector {

    private val markers = listOf(
        "this message was deleted",
        "you deleted this message",
        "message deleted",
        "this message has been deleted",
        "message was unsent",
        "unsent a message",
        "removed a message",
        "deleted a message",
        "this message was removed",
        "message removed",
        "message has been removed",
        "this message has been withdrawn",
        "recalled a message",
        "deleted this message",
        "you unsent a message",
        "this message has been recalled",
        "este mensaje fue eliminado",
        "se eliminó este mensaje",
        "esta mensagem foi apagada",
        "mensagem apagada",
        "ce message a été supprimé",
        "message supprimé",
        "diese nachricht wurde gelöscht",
        "questo messaggio è stato eliminato",
        "dit bericht is verwijderd",
        "bu mesaj silindi",
        "это сообщение удалено",
        "تم حذف هذه الرسالة",
        "यह संदेश हटा दिया गया",
        "pesan ini telah dihapus",
        "pesan ini dihapus",
        "此消息已删除",
        "撤回了一条消息",
        "メッセージの送信を取り消しました",
        "삭제된 메시지입니다",
        "bu mesaj silindi",
    )

    fun isDeletionMarker(text: String): Boolean {
        val t = text.trim().removePrefix("🚫").trim().trimEnd('.', ' ').lowercase()
        return markers.any { t == it || t.endsWith(it) }
    }

    data class Candidate(val id: Long, val sender: String, val msgTimestamp: Long, val deleted: Boolean)

    /**
     * Picks which stored message a deletion marker refers to: exact timestamp match from the same
     * sender first (WhatsApp keeps the original timestamp), else that sender's latest message within
     * [windowMs] before the marker.
     */
    const val WINDOW_MS = 48 * 3_600_000L

    fun matchTarget(marker: ParsedMessage, candidates: List<Candidate>, windowMs: Long = WINDOW_MS): Candidate? {
        val live = candidates.filter { !it.deleted && it.sender == marker.sender }
        live.firstOrNull { it.msgTimestamp == marker.msgTimestamp }?.let { return it }
        return live
            .filter { it.msgTimestamp <= marker.msgTimestamp && marker.msgTimestamp - it.msgTimestamp <= windowMs }
            .maxByOrNull { it.msgTimestamp }
    }
}
