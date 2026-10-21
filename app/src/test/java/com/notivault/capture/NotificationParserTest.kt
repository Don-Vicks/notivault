package com.notivault.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationParserTest {
    private val own = "com.notivault"

    @Test fun `plain notification uses big text over text and when timestamp`() {
        val out = NotificationParser.parse(raw(text = "short", bigText = "the full long message"), own)
        assertEquals(1, out.size)
        assertEquals("Alice", out[0].sender)
        assertEquals("the full long message", out[0].text)
        assertEquals(900L, out[0].msgTimestamp)
        assertEquals("alice", out[0].conversationKey)
    }

    @Test fun `messaging style yields one message per entry with senders`() {
        val out = NotificationParser.parse(
            raw(conversationTitle = "Family", group = true, messages = listOf(
                RawMessage("Mum", "Dinner at 7", 10), RawMessage("Dad", "ok", 20), RawMessage(null, "coming", 30),
            )), own,
        )
        assertEquals(listOf("Mum", "Dad", "You"), out.map { it.sender })
        assertTrue(out.all { it.isGroup && it.conversationTitle == "Family" })
        assertEquals(listOf(10L, 20L, 30L), out.map { it.msgTimestamp })
    }

    @Test fun `ignores own app, ongoing, summaries, system categories and blank text`() {
        assertTrue(NotificationParser.parse(raw(pkg = own), own).isEmpty())
        assertTrue(NotificationParser.parse(raw(ongoing = true), own).isEmpty())
        assertTrue(NotificationParser.parse(raw(summary = true), own).isEmpty())
        assertTrue(NotificationParser.parse(raw(category = "progress"), own).isEmpty())
        assertTrue(NotificationParser.parse(raw(text = "   ", bigText = null), own).isEmpty())
        assertTrue(NotificationParser.parse(raw(pkg = "com.android.systemui"), own).isEmpty())
    }

    @Test fun `drops summary noise like N new messages`() {
        assertTrue(NotificationParser.parse(raw(text = "5 new messages"), own).isEmpty())
        assertTrue(NotificationParser.parse(raw(text = "12 messages from 3 chats"), own).isEmpty())
        assertEquals(1, NotificationParser.parse(raw(text = "I have 5 new messages for you"), own).size)
    }
}
