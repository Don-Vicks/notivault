package com.notivault.capture

import com.notivault.capture.apps.AppProfiles
import com.notivault.capture.apps.DeletionSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialAppsTest {
    private val own = "com.notivault"

    @Test fun `profiles cover the major chat apps with unique packages`() {
        listOf("com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
            "com.facebook.orca", "com.instagram.android", "com.snapchat.android", "com.discord", "com.Slack",
            "com.google.android.apps.messaging", "com.viber.voip", "com.tencent.mm", "jp.naver.line.android",
        ).forEach { assertNotNull(it, AppProfiles.forPackage(it)) }
        val pkgs = AppProfiles.all.flatMap { it.packages }
        assertEquals(pkgs.size, pkgs.toSet().size)
    }

    @Test fun `whatsapp legacy sender at group title`() {
        val m = NotificationParser.parse(raw(pkg = "com.whatsapp", title = "Ada @ Squad", text = "see you"), own).single()
        assertEquals("Ada", m.sender); assertEquals("Squad", m.conversationTitle); assertTrue(m.isGroup)
    }

    @Test fun `whatsapp group colon sender title with unread counter`() {
        val m = NotificationParser.parse(raw(pkg = "com.whatsapp", title = "Squad (3 messages): Ken", text = "ok"), own).single()
        assertEquals("Ken", m.sender); assertEquals("Squad", m.conversationTitle)
    }

    @Test fun `telegram group body prefix`() {
        val m = NotificationParser.parse(raw(pkg = "org.telegram.messenger", title = "Dev Chat", text = "Bola: shipped it"), own).single()
        assertEquals("Bola", m.sender); assertEquals("Dev Chat", m.conversationTitle); assertEquals("shipped it", m.text)
    }

    @Test fun `instagram app-titled body prefix becomes the conversation`() {
        val m = NotificationParser.parse(raw(pkg = "com.instagram.android", title = "Instagram", text = "zara.k: lol that reel"), own, "Instagram").single()
        assertEquals("zara.k", m.sender); assertEquals("zara.k", m.conversationTitle); assertEquals("lol that reel", m.text)
    }

    @Test fun `1-to-1 body with colon from the same person is not split`() {
        val m = NotificationParser.parse(raw(pkg = "org.telegram.messenger", title = "Bola", text = "Bola: note to self"), own).single()
        assertEquals("Bola", m.sender); assertEquals("Bola", m.conversationTitle)
    }

    @Test fun `typing indicators and app status banners are noise`() {
        listOf("typing…", "Ada is typing...", "Ken is recording audio", "Checking for new messages", "Connecting").forEach {
            assertTrue(it, NotificationParser.parse(raw(pkg = "org.telegram.messenger", title = "X", text = it), own).isEmpty())
        }
        assertTrue(NotificationParser.parse(raw(pkg = "com.whatsapp", title = "WhatsApp", text = "4 messages from 2 chats"), own).isEmpty())
    }

    @Test fun `media kinds are classified`() {
        mapOf(
            "📷 Photo" to MessageKind.Photo, "🎤 Voice message (0:12)" to MessageKind.Voice, "🎥 Video" to MessageKind.Video,
            "Sticker" to MessageKind.Sticker, "GIF" to MessageKind.Gif, "📄 invoice.pdf" to MessageKind.Document,
            "📍 Location" to MessageKind.Location, "Missed voice call" to MessageKind.Call,
            "Reacted 👍 to \"see you\"" to MessageKind.Reaction, "zara sent you a photo" to MessageKind.Photo,
            "hello there" to MessageKind.Text, "I'll photo-copy it" to MessageKind.Text,
        ).forEach { (t, k) -> assertEquals(t, k, MessageKind.classify(t)) }
    }

    @Test fun `deletion placeholders from more apps`() {
        listOf("Zara unsent a message", "Ken removed a message", "张三撤回了一条消息", "This message has been recalled").forEach {
            assertTrue(it, DeletionDetector.isDeletionMarker(it))
        }
    }

    @Test fun `telegram and signal advertise history removal`() {
        assertTrue(DeletionSignal.HistoryRemoval in AppProfiles.forPackage("org.telegram.messenger")!!.deletion)
        assertTrue(DeletionSignal.HistoryRemoval in AppProfiles.forPackage("org.thoughtcrime.securesms")!!.deletion)
    }
}

class HistoryDiffTest {
    private fun m(sender: String, text: String, ts: Long) = ParsedMessage("p", "c", "C", true, sender, text, ts, ts, fromHistory = true)

    @Test fun `message dropped from the middle is a deletion`() {
        val prev = listOf(m("A", "1", 1), m("B", "secret", 2), m("A", "3", 3))
        val next = listOf(m("A", "1", 1), m("A", "3", 3), m("A", "4", 4))
        assertEquals(listOf("secret"), HistoryDiff.removed(prev, next).map { it.text })
    }

    @Test fun `history trimmed from the front is not a deletion`() {
        val prev = listOf(m("A", "1", 1), m("B", "2", 2), m("A", "3", 3))
        val next = listOf(m("B", "2", 2), m("A", "3", 3), m("A", "4", 4))
        assertTrue(HistoryDiff.removed(prev, next).isEmpty())
    }

    @Test fun `edited message is not a deletion`() {
        val prev = listOf(m("A", "1", 1), m("B", "teh plan", 2), m("A", "3", 3))
        val next = listOf(m("A", "1", 1), m("B", "the plan", 2), m("A", "3", 3))
        assertTrue(HistoryDiff.removed(prev, next).isEmpty())
    }

    @Test fun `fully replaced history is ambiguous and ignored`() {
        assertTrue(HistoryDiff.removed(listOf(m("A", "x", 1)), listOf(m("A", "y", 5))).isEmpty())
    }

    @Test fun `re-stamped history still matches by content`() {
        val prev = listOf(m("A", "1", 1), m("B", "gone", 2), m("A", "3", 3))
        val next = listOf(m("A", "1", 101), m("A", "3", 103))
        assertEquals(listOf("gone"), HistoryDiff.removed(prev, next).map { it.text })
    }
}

class RemovalHeuristicTest {
    private fun r(key: String, at: Long, reason: Int = RemovalHeuristic.REASON_APP_CANCEL, post: Long = at - 60_000) =
        RemovalHeuristic.Removal("org.telegram.messenger", key, at, reason, post)

    @Test fun `lone app-cancelled single message is possibly unsent`() {
        val t = r("a", 1_000_000)
        assertTrue(RemovalHeuristic.isLikelyUnsend(t, listOf(t), lastPayloadSize = 1, appDeletesByRemoval = true))
    }

    @Test fun `bulk clear means read, not unsent`() {
        val t = r("a", 1_000_000)
        assertFalse(RemovalHeuristic.isLikelyUnsend(t, listOf(t, r("b", 1_000_800)), 1, true))
    }

    @Test fun `user swipe, multi-message payloads, old posts and placeholder-only apps are ignored`() {
        val swipe = r("a", 1_000_000, reason = 2)
        assertFalse(RemovalHeuristic.isLikelyUnsend(swipe, listOf(swipe), 1, true))
        val t = r("a", 1_000_000)
        assertFalse(RemovalHeuristic.isLikelyUnsend(t, listOf(t), 3, true))
        assertFalse(RemovalHeuristic.isLikelyUnsend(t, listOf(t), 1, false))
        val old = r("a", 10_000_000, post = 0)
        assertFalse(RemovalHeuristic.isLikelyUnsend(old, listOf(old), 1, true))
    }
}
