package com.notivault.data

import com.notivault.data.media.MediaRules
import com.notivault.data.media.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRulesTest {
    @Test fun `classifies chat media by extension or mime`() {
        assertEquals(MediaType.Image, MediaRules.type("IMG-20261021-WA0003.jpg", null))
        assertEquals(MediaType.Video, MediaRules.type("VID-20261021-WA0001.mp4", null))
        assertEquals(MediaType.Audio, MediaRules.type("PTT-20261021-WA0002.opus", null))
        assertEquals(MediaType.Document, MediaRules.type("invoice.pdf", null))
        assertEquals(MediaType.Image, MediaRules.type("noext", "image/webp"))
        assertNull(MediaRules.type("msgstore.db.crypt14", null))
    }

    @Test fun `skips hidden, sent, statuses and backup folders`() {
        listOf(".Statuses", ".nomedia", "Sent", "Private", "Databases", "Backups").forEach { assertTrue(it, MediaRules.skip(it)) }
        listOf("WhatsApp Images", "WhatsApp Voice Notes", "Telegram Video").forEach { assertFalse(it, MediaRules.skip(it)) }
    }
}
