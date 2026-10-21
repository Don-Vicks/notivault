package com.notivault.capture

import kotlin.math.abs

/**
 * When a message is deleted and it was the only one in its notification, apps like Telegram cancel the
 * notification instead of re-posting it, which looks the same as the user reading it elsewhere. We
 * can't prove an unsend, so this only yields a "possibly unsent" flag, never a Recovered one, and only
 * when the circumstances point that way.
 */
object RemovalHeuristic {
    /** NotificationListenerService.REASON_APP_CANCEL */
    const val REASON_APP_CANCEL = 8
    const val BULK_WINDOW_MS = 2_000L
    const val MAX_AGE_MS = 30 * 60_000L

    data class Removal(val pkg: String, val key: String, val at: Long, val reason: Int, val postTime: Long)

    fun isLikelyUnsend(
        target: Removal,
        recent: List<Removal>,
        lastPayloadSize: Int,
        appDeletesByRemoval: Boolean,
    ): Boolean =
        appDeletesByRemoval &&
            target.reason == REASON_APP_CANCEL &&
            lastPayloadSize == 1 &&
            target.at - target.postTime in 0..MAX_AGE_MS &&
            // Opening the app or reading on desktop clears several chats at once.
            recent.none { it.pkg == target.pkg && it.key != target.key && abs(it.at - target.at) <= BULK_WINDOW_MS }
}
