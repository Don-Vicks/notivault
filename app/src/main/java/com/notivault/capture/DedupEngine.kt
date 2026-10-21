package com.notivault.capture

import java.security.MessageDigest

/**
 * Chat apps re-post the full MessagingStyle history on every new message; a stable content hash
 * lets the database's UNIQUE(hash) constraint drop the repeats.
 */
object DedupEngine {
    fun hash(m: ParsedMessage): String = sha256(
        listOf(m.packageName, m.conversationKey, m.sender, m.text, m.msgTimestamp.toString()).joinToString("\u0001"),
    )

    fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    /**
     * Some apps re-stamp their MessagingStyle history on every re-post, defeating the exact hash.
     * Given one (sender, text) group from a payload and how many look-alikes are already stored nearby,
     * returns only the newest messages beyond that count. Genuine repeats ("ok", "ok") appear twice in
     * the payload itself, so they survive.
     */
    fun unseenFromHistory(group: List<ParsedMessage>, alreadyStored: Int): List<ParsedMessage> =
        group.sortedBy { it.msgTimestamp }.drop(alreadyStored)

    const val HISTORY_WINDOW_MS = 10 * 60_000L

    /** Removes duplicates inside one notification payload, keeping first occurrence order. */
    fun distinct(messages: List<ParsedMessage>): List<ParsedMessage> = messages.distinctBy { it.hash }
}
