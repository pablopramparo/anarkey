package org.anarkey.core

import org.anarkey.core.music.LyricWrap
import org.junit.Assert.*
import org.junit.Test

class LyricWrapTest {
    private fun split(text: String, width: Int) = LyricWrap.rows(text, width).map { range ->
        val cps = text.codePoints().toArray()
        String(cps, range.first, range.count())
    }

    @Test fun shortLinesStayInOneRow() {
        assertEquals(listOf("Amazing grace"), split("Amazing grace", 20))
        assertEquals(listOf(0 until 0), LyricWrap.rows("", 10))
    }

    @Test fun rowsBreakAfterWordsAndCoverTheWholeText() {
        val text = "I once was lost, but now am found"
        val rows = split(text, 14)
        assertEquals(text, rows.joinToString(""))
        assertTrue(rows.all { it.length <= 14 })
        assertEquals(listOf("I once was ", "lost, but now ", "am found"), rows)
    }

    @Test fun aWordLongerThanTheRowIsCut() {
        assertEquals(listOf("abcd", "efgh", "ij"), split("abcdefghij", 4))
    }

    @Test fun offsetsAreCodePointsNotUtf16Units() {
        val rows = LyricWrap.rows("🎵🎵🎵 🎵🎵", 4)
        assertEquals(listOf(0 until 4, 4 until 6), rows)
    }
}
