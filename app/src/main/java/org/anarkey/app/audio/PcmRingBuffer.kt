package org.anarkey.app.audio

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/** Debug-only rolling PCM buffer. Audio stays in memory until explicitly exported. */
internal class PcmRingBuffer(private val capacity: Int) {
    init { require(capacity > 0) }
    private val data = ShortArray(capacity)
    private var next = 0
    private var size = 0

    @Synchronized fun append(samples: ShortArray, offset: Int = 0, count: Int = samples.size) {
        require(offset >= 0 && count >= 0 && offset + count <= samples.size)
        for (i in offset until offset + count) {
            data[next] = samples[i]
            next = (next + 1) % capacity
            if (size < capacity) size++
        }
    }

    @Synchronized fun snapshot(): ShortArray = ShortArray(size) { i -> data[(next - size + i + capacity) % capacity] }

    @Synchronized fun writeWav(file: File, sampleRate: Int) {
        writeWav(file, sampleRate, snapshot())
    }

    fun writeWav(file: File, sampleRate: Int, samples: ShortArray) {
        FileOutputStream(file).use { out ->
            writeHeader(out, samples.size * 2, sampleRate)
            for (sample in samples) {
                out.write(sample.toInt() and 0xff)
                out.write((sample.toInt() ushr 8) and 0xff)
            }
        }
    }

    private fun writeHeader(out: OutputStream, pcmBytes: Int, sampleRate: Int) {
        fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
        fun le(value: Int) { out.write(value and 0xff); out.write(value ushr 8 and 0xff); out.write(value ushr 16 and 0xff); out.write(value ushr 24 and 0xff) }
        ascii("RIFF"); le(36 + pcmBytes); ascii("WAVEfmt "); le(16)
        out.write(1); out.write(0); out.write(1); out.write(0)
        le(sampleRate); le(sampleRate * 2); out.write(2); out.write(0); out.write(16); out.write(0)
        ascii("data"); le(pcmBytes)
    }
}
