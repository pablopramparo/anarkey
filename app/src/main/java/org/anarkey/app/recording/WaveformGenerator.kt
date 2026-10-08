package org.anarkey.app.recording

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder
import org.anarkey.core.recording.WaveformCodec

/** Decodes the AAC stream incrementally; only 256 peak values are retained. */
object WaveformGenerator {
    fun generate(file: File, bins: Int = WaveformCodec.DEFAULT_BINS): ByteArray? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    extractor.selectTrack(i); audioFormat = format; break
                }
            }
            val format = audioFormat ?: return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            val expectedFrames = ((durationUs * rate) / 1_000_000L).coerceAtLeast(1L)
            val peaks = FloatArray(bins)
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var frames = 0L
            var pcmFloat = false
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val input = decoder.getInputBuffer(inputIndex)!!
                        val pts = extractor.sampleTime.coerceAtLeast(0)
                        val sampleSize = extractor.readSampleData(input, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        pcmFloat = outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == android.media.AudioFormat.ENCODING_PCM_FLOAT
                    }
                    else -> if (outputIndex >= 0) {
                        val output = decoder.getOutputBuffer(outputIndex)
                        if (output != null && info.size > 0) {
                            output.position(info.offset); output.limit(info.offset + info.size)
                            output.order(ByteOrder.LITTLE_ENDIAN)
                            val bytesPerSample = if (pcmFloat) 4 else 2
                            val frameCount = info.size / (bytesPerSample * channels)
                            repeat(frameCount) { frame ->
                                var peak = 0f
                                repeat(channels) { channel ->
                                    val absolute = if (pcmFloat) kotlin.math.abs(output.float) else kotlin.math.abs(output.short.toInt() / 32768f)
                                    if (absolute > peak) peak = absolute
                                }
                                val bin = ((frames + frame) * bins / expectedFrames).toInt().coerceIn(0, bins - 1)
                                peaks[bin] = maxOf(peaks[bin], peak)
                            }
                            frames += frameCount
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
            return ByteArray(bins) { (peaks[it] * 255f).toInt().coerceIn(0, 255).toByte() }
        } catch (_: Exception) { return null }
        finally {
            runCatching { decoder?.stop() }
            decoder?.release()
            extractor.release()
        }
    }
}
