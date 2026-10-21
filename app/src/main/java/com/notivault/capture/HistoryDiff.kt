package com.notivault.capture

/**
 * Apps like Telegram and Signal don't show a placeholder when a message is deleted; they re-post the
 * notification without it. Comparing consecutive MessagingStyle payloads for the same notification
 * reveals the removal. A message only counts as removed if something *older* than it survived, so the
 * normal trimming of old history off the front of the window is never mistaken for a deletion.
 */
object HistoryDiff {
    private fun key(m: ParsedMessage) = m.sender to m.text

    fun removed(previous: List<ParsedMessage>, next: List<ParsedMessage>): List<ParsedMessage> {
        if (previous.isEmpty() || next.isEmpty()) return emptyList()
        val remaining = next.groupingBy(::key).eachCount().toMutableMap()
        val prev = previous.sortedBy { it.msgTimestamp }
        val survived = BooleanArray(prev.size)
        prev.forEachIndexed { i, m ->
            val n = remaining[key(m)] ?: 0
            if (n > 0) { remaining[key(m)] = n - 1; survived[i] = true }
        }
        val firstSurvivor = survived.indexOfFirst { it }
        if (firstSurvivor < 0) return emptyList() // whole history replaced: ambiguous, don't guess
        // Same sender + same timestamp with new text is an edit, not a deletion.
        val edited = next.map { it.sender to it.msgTimestamp }.toSet()
        return prev.filterIndexed { i, m -> i > firstSurvivor && !survived[i] && (m.sender to m.msgTimestamp) !in edited }
    }
}
