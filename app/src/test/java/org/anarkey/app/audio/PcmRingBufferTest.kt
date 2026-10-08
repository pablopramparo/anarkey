package org.anarkey.app.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PcmRingBufferTest {
    @Test fun retainsNewestSamplesInChronologicalOrderAndWritesMonoPcmWav() {
        val buffer = PcmRingBuffer(4)
        buffer.append(shortArrayOf(1, 2, 3))
        buffer.append(shortArrayOf(4, 5, 6))
        assertArrayEquals(shortArrayOf(3, 4, 5, 6), buffer.snapshot())

        val file = File.createTempFile("anarkey-audio", ".wav")
        try {
            buffer.writeWav(file, 48_000)
            val bytes = file.readBytes()
            assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
            assertEquals(48_000, littleEndianInt(bytes, 24))
            assertEquals(8, littleEndianInt(bytes, 40))
            assertArrayEquals(shortArrayOf(3, 4, 5, 6), ShortArray(4) { i ->
                ((bytes[44 + i * 2].toInt() and 0xff) or (bytes[45 + i * 2].toInt() shl 8)).toShort()
            })
        } finally { file.delete() }
    }

    private fun littleEndianInt(bytes: ByteArray, at: Int) =
        (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8) or
            ((bytes[at + 2].toInt() and 0xff) shl 16) or (bytes[at + 3].toInt() shl 24)
}
