package com.notivault.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DedupEngineTest {
    private val own = "com.notivault"

    @Test fun `re-posted messaging history hashes identically`() {
        val first = NotificationParser.parse(raw(messages = listOf(RawMessage("Bob", "one", 1))), own)
        val second = NotificationParser.parse(raw(messages = listOf(RawMessage("Bob", "one", 1), RawMessage("Bob", "two", 2))), own)
        assertEquals(first[0].hash, second[0].hash)
        val stored = first.map { it.hash }.toSet()
        assertEquals(listOf("two"), second.filter { it.hash !in stored }.map { it.text })
    }

    @Test fun `same text at different times is distinct`() {
        val a = NotificationParser.parse(raw(messages = listOf(RawMessage("Bob", "ok", 1), RawMessage("Bob", "ok", 2))), own)
        assertNotEquals(a[0].hash, a[1].hash)
        assertEquals(2, DedupEngine.distinct(a).size)
    }

    @Test fun `distinct collapses duplicates within a payload`() {
        val a = NotificationParser.parse(raw(messages = listOf(RawMessage("Bob", "x", 1), RawMessage("Bob", "x", 1))), own)
        assertEquals(1, DedupEngine.distinct(a).size)
    }

    @Test fun `sha256 is hex and stable`() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", DedupEngine.sha256("hello"))
    }
}

class HistoryDedupTest {
    private fun m(text: String, ts: Long) = ParsedMessage("p", "c", "C", true, "Bob", text, ts, ts, fromHistory = true)

    @Test fun `re-stamped history adds nothing once stored`() {
        assertEquals(0, DedupEngine.unseenFromHistory(listOf(m("hi", 5_000)), alreadyStored = 1).size)
    }

    @Test fun `genuine repeats in one payload keep the newest unseen ones`() {
        val out = DedupEngine.unseenFromHistory(listOf(m("ok", 2), m("ok", 1), m("ok", 3)), alreadyStored = 1)
        assertEquals(listOf(2L, 3L), out.map { it.msgTimestamp })
    }
}
