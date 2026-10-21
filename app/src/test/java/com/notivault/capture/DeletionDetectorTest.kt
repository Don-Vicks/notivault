package com.notivault.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionDetectorTest {
    @Test fun `recognises placeholders across apps and languages`() {
        listOf(
            "This message was deleted", "🚫 This message was deleted.", "this message was deleted",
            "Este mensaje fue eliminado", "Diese Nachricht wurde gelöscht", "Ce message a été supprimé",
            "Alice unsent a message", "Message deleted",
        ).forEach { assertTrue(it, DeletionDetector.isDeletionMarker(it)) }
    }

    @Test fun `normal text is not a marker`() {
        listOf("I deleted the file", "was this message deleted?", "hello").forEach {
            assertFalse(it, DeletionDetector.isDeletionMarker(it))
        }
    }

    private fun marker(sender: String, ts: Long) = ParsedMessage("p", "c", "C", false, sender, "This message was deleted", ts, ts)

    @Test fun `prefers exact timestamp match`() {
        val c = listOf(
            DeletionDetector.Candidate(1, "Bob", 100, false),
            DeletionDetector.Candidate(2, "Bob", 200, false),
        )
        assertEquals(1L, DeletionDetector.matchTarget(marker("Bob", 100), c)?.id)
    }

    @Test fun `falls back to latest earlier message from same sender`() {
        val c = listOf(
            DeletionDetector.Candidate(1, "Bob", 100, false),
            DeletionDetector.Candidate(2, "Bob", 200, false),
            DeletionDetector.Candidate(3, "Amy", 250, false),
        )
        assertEquals(2L, DeletionDetector.matchTarget(marker("Bob", 300), c)?.id)
    }

    @Test fun `skips already deleted, other senders and out of window`() {
        val c = listOf(
            DeletionDetector.Candidate(1, "Bob", 100, true),
            DeletionDetector.Candidate(2, "Amy", 100, false),
        )
        assertNull(DeletionDetector.matchTarget(marker("Bob", 100), c))
        val old = listOf(DeletionDetector.Candidate(1, "Bob", 0, false))
        assertNull(DeletionDetector.matchTarget(marker("Bob", 49 * 3_600_000L), old))
    }
}
