package org.anarkey.core.music

/** Splits a lyric into display rows without breaking words, so long lines fit a narrow screen. */
object LyricWrap {
    /** Row bounds as `[start, end)` code-point offsets; they cover the whole text, in order, with no gaps. */
    fun rows(text: String, width: Int): List<IntRange> {
        val units = text.codePoints().toArray()
        val size = units.size
        if (size <= width || width < 1) return listOf(0 until size)
        val rows = mutableListOf<IntRange>()
        var start = 0
        while (start < size) {
            var end = minOf(start + width, size)
            if (end < size) {
                // Prefer to end the row right after a space; a word longer than the row is cut where it must be.
                val cut = (end downTo start + 1).firstOrNull { units[it - 1] == ' '.code }
                if (cut != null) end = cut
            }
            rows += start until end
            start = end
        }
        return rows
    }
}
