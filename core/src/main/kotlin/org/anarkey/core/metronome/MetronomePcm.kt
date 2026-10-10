package org.anarkey.core.metronome

import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

enum class Meter(val numerator: Int, val denominator: Int) {
    TWO_FOUR(2, 4), THREE_FOUR(3, 4), FOUR_FOUR(4, 4), SIX_EIGHT(6, 8);

    val clicksPerBar: Int get() = numerator
    val isDottedQuarterTempo: Boolean get() = this == SIX_EIGHT

    companion object {
        fun from(numerator: Int?, denominator: Int?): Meter? = entries.firstOrNull {
            it.numerator == numerator && it.denominator == denominator
        }
    }
}

enum class Accent { NORMAL, SECONDARY, PRIMARY }
enum class ClickSound { WOOD, DIGITAL, BELL }

data class MetronomeSettings(
    val bpm: Int = 100,
    val meter: Meter = Meter.FOUR_FOUR,
    val primaryAccent: Boolean = true,
    val secondaryAccent: Boolean = true,
    val sound: ClickSound = ClickSound.WOOD,
    val volume: Float = 0.75f,
    val accentPatterns: Map<Meter, List<Accent>> = emptyMap(),
) {
    init {
        require(bpm in MIN_BPM..MAX_BPM)
        require(volume.isFinite() && volume in 0f..1f)
    }

    fun accentAt(beat: Int): Accent {
        accentPatterns[meter]?.takeIf { it.size == meter.clicksPerBar }?.let { pattern ->
            return pattern.getOrElse(beat) { Accent.NORMAL }
        }
        return when {
        beat == 0 && primaryAccent -> Accent.PRIMARY
        beat == meter.clicksPerBar / 2 && secondaryAccent -> Accent.SECONDARY
        else -> Accent.NORMAL
        }
    }

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 300
    }
}

data class PulseEvent(
    val frame: Long,
    val beatInBar: Int,
    val accent: Accent,
    val sound: ClickSound,
    val volume: Float,
)

/** Plans each click on the output sample clock and carries fractional frames without drift. */
class SamplePulsePlanner(sampleRate: Int, initial: MetronomeSettings) {
    private val sampleRate = sampleRate.also { require(it > 0) }
    private val pendingSettings = AtomicReference<MetronomeSettings?>(null)
    private var settings = initial
    private var nextFrame = 0L
    private var beatInBar = 0
    private var remainder = 0L

    val nextPulseFrame: Long get() = nextFrame

    fun updateAtNextPulse(value: MetronomeSettings) {
        pendingSettings.set(value)
    }

    fun nextPulse(): PulseEvent {
        pendingSettings.getAndSet(null)?.let { updated ->
            val meterChanged = updated.meter != settings.meter
            settings = updated
            if (meterChanged) beatInBar = 0 else beatInBar %= settings.meter.clicksPerBar
            remainder = 0L
        }

        val event = PulseEvent(nextFrame, beatInBar, settings.accentAt(beatInBar), settings.sound, settings.volume)
        val denominator = settings.bpm.toLong() * if (settings.meter.isDottedQuarterTempo) 3L else 1L
        val numerator = sampleRate.toLong() * 60L
        val wholeFrames = numerator / denominator
        val fractionalFrames = numerator % denominator
        var interval = wholeFrames
        remainder += fractionalFrames
        if (remainder >= denominator) {
            interval += remainder / denominator
            remainder %= denominator
        }
        nextFrame += interval
        beatInBar = (beatInBar + 1) % settings.meter.clicksPerBar
        return event
    }
}

/** Renders click sounds into reusable mono PCM16 blocks. */
class MetronomePcmGenerator(private val sampleRate: Int, settings: MetronomeSettings) {
    private val planner = SamplePulsePlanner(sampleRate, settings)
    private var frameCursor = 0L
    private val historyFrames = LongArray(HISTORY_CAPACITY)
    private val historyBeats = IntArray(HISTORY_CAPACITY)
    @Volatile private var latestSequence = -1L
    private var lastSound: ClickSound? = null
    private var lastVolume = Float.NaN
    private var primary = ShortArray(0)
    private var secondary = ShortArray(0)
    private var normal = ShortArray(0)
    private var accumulator = IntArray(0)
    private val voiceSamples = arrayOfNulls<ShortArray>(MAX_VOICES)
    private val voicePosition = IntArray(MAX_VOICES)

    fun updateAtNextPulse(settings: MetronomeSettings) = planner.updateAtNextPulse(settings)

    /**
     * Fills the next block. A click is usually longer than a block (blocks are a few milliseconds), so whatever
     * does not fit keeps sounding in the following blocks as a "voice"; dropping it would cut every sound short.
     */
    fun render(destination: ShortArray) {
        if (accumulator.size < destination.size) accumulator = IntArray(destination.size)
        accumulator.fill(0, 0, destination.size)
        val blockStart = frameCursor
        val blockEnd = blockStart + destination.size
        for (voice in 0 until MAX_VOICES) {
            val samples = voiceSamples[voice] ?: continue
            val position = voicePosition[voice]
            val written = mix(destination.size, 0, samples, position)
            if (position + written >= samples.size) voiceSamples[voice] = null else voicePosition[voice] = position + written
        }
        while (planner.nextPulseFrame < blockEnd) {
            val event = planner.nextPulse()
            ensureSamples(event.sound, event.volume)
            remember(event.frame, event.beatInBar)
            val click = samplesFor(event.accent)
            val written = mix(destination.size, (event.frame - blockStart).toInt(), click, 0)
            if (written < click.size) startVoice(click, written)
        }
        for (index in destination.indices) {
            val mixed = accumulator[index]
            // Above the knee a sum is compressed rather than clipped, so overlapping rings stay smooth.
            val magnitude = kotlin.math.abs(mixed)
            val limited = if (magnitude > KNEE) (KNEE + (magnitude - KNEE) * 2 / 5) * (if (mixed < 0) -1 else 1) else mixed
            destination[index] = limited.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
        frameCursor = blockEnd
    }

    /** Adds [click] from [from] into the block starting at [offset]; returns how many samples fitted. */
    private fun mix(blockSize: Int, offset: Int, click: ShortArray, from: Int): Int {
        val count = minOf(click.size - from, blockSize - offset)
        for (index in 0 until count) accumulator[offset + index] += click[from + index].toInt()
        return count.coerceAtLeast(0)
    }

    /** Keeps a click ringing into later blocks; with no free slot the one closest to its end gives way. */
    private fun startVoice(click: ShortArray, position: Int) {
        var slot = -1
        var mostPlayed = -1.0
        for (voice in 0 until MAX_VOICES) {
            val samples = voiceSamples[voice]
            if (samples == null) { slot = voice; break }
            val played = voicePosition[voice].toDouble() / samples.size
            if (played > mostPlayed) { mostPlayed = played; slot = voice }
        }
        voiceSamples[slot] = click
        voicePosition[slot] = position
    }

    /** Returns the beat whose scheduled output frame is at or before the played frame, or -1. */
    fun beatAtPlayedFrame(playedFrame: Long): Int {
        val latest = latestSequence
        val earliest = maxOf(0L, latest - HISTORY_CAPACITY + 1)
        for (sequence in latest downTo earliest) {
            val index = (sequence % HISTORY_CAPACITY).toInt()
            if (historyFrames[index] <= playedFrame) return historyBeats[index]
        }
        return -1
    }

    private fun remember(frame: Long, beat: Int) {
        val sequence = latestSequence + 1
        val index = (sequence % HISTORY_CAPACITY).toInt()
        historyFrames[index] = frame
        historyBeats[index] = beat
        latestSequence = sequence
    }

    private fun ensureSamples(sound: ClickSound, volume: Float) {
        if (lastSound == sound && lastVolume == volume) return
        lastSound = sound
        lastVolume = volume
        primary = makeClick(sound, volume, Accent.PRIMARY)
        secondary = makeClick(sound, volume, Accent.SECONDARY)
        normal = makeClick(sound, volume, Accent.NORMAL)
    }

    private fun samplesFor(accent: Accent) = when (accent) {
        Accent.PRIMARY -> primary
        Accent.SECONDARY -> secondary
        Accent.NORMAL -> normal
    }

    /**
     * Each sound has its own length and character, not just a different overtone on the same tick:
     * wood is a short, low "tok" with a noise attack; digital is a gated beep (odd harmonics, flat level);
     * bell is a hand bell on the accented beats only, with the wooden tick on the others.
     * All are scaled to the same peak, so changing the sound changes the timbre and not the loudness.
     */
    private fun makeClick(sound: ClickSound, volume: Float, accent: Accent): ShortArray {
        val accentGain = when (accent) { Accent.PRIMARY -> 0.95; Accent.SECONDARY -> 0.72; Accent.NORMAL -> 0.5 }
        val base = when (accent) { Accent.PRIMARY -> 1_760.0; Accent.SECONDARY -> 1_320.0; Accent.NORMAL -> 880.0 }
        val raw = when (sound) {
            ClickSound.WOOD -> woodClick(base)
            ClickSound.DIGITAL -> digitalClick(base)
            // As on a mechanical metronome, the bell marks the accented beats and the rest are the wooden tick.
            ClickSound.BELL -> if (accent == Accent.NORMAL) woodClick(base) else bellClick(if (accent == Accent.PRIMARY) 1_175.0 else 1_046.0)
        }
        val loudest = raw.maxOf { kotlin.math.abs(it) }.takeIf { it > 0.0 } ?: 1.0
        val scale = Short.MAX_VALUE * volume * accentGain / loudest
        return ShortArray(raw.size) { (raw[it] * scale).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort() }
    }

    private fun woodClick(base: Double): DoubleArray {
        val f = base * 0.55
        var noise = 0x2545F491
        return DoubleArray((sampleRate * 0.045).roundToInt()) { index ->
            val t = index.toDouble() / sampleRate
            noise = noise xor (noise shl 13); noise = noise xor (noise ushr 17); noise = noise xor (noise shl 5)
            val hiss = (noise.toDouble() / Int.MAX_VALUE) * exp(-900.0 * t) * 0.8
            sin(2.0 * PI * f * t) * exp(-110.0 * t) +
                0.55 * sin(2.0 * PI * f * 2.32 * t) * exp(-160.0 * t) +
                0.3 * sin(2.0 * PI * f * 3.9 * t) * exp(-220.0 * t) + hiss
        }
    }

    private fun digitalClick(base: Double): DoubleArray {
        val seconds = 0.060
        return DoubleArray((sampleRate * seconds).roundToInt()) { index ->
            val t = index.toDouble() / sampleRate
            // Around 1 kHz on the accent, like a quartz metronome; higher than that it turns shrill.
            val w = 2.0 * PI * base * 0.57 * t
            val body = sin(w) + 0.25 * sin(3.0 * w) + 0.08 * sin(5.0 * w)
            body * minOf(1.0, t / 0.001) * minOf(1.0, (seconds - t) / 0.008)
        }
    }

    /**
     * A hand bell, chosen by ear from several candidates: a clear strike tone with a slightly detuned twin that
     * makes it shimmer, an octave, and a few higher partials that die first. It is a short "ting" of under half a second.
     * It is not saturated or compressed, and is neither very high nor very low: a thin, short tone sounds like a
     * triangle, a low one like a dull thump.
     * Oscillators are rotated step by step (no sine per sample) because this is rendered on the audio thread.
     */
    private fun bellClick(strikeHz: Double): DoubleArray {
        //                         strike twin octave
        val ratio = doubleArrayOf(1.0, 1.0, 2.0, 2.4, 3.0, 4.2, 5.9)
        val offsetHz = doubleArrayOf(0.0, 1.2, 0.0, 0.0, 0.0, 0.0, 0.0)
        val strength = doubleArrayOf(1.0, 0.6, 0.7, 0.35, 0.3, 0.2, 0.1)
        // A short "ting", like the bell of a mechanical metronome: it marks the beat and gets out of the way.
        val decaySeconds = doubleArrayOf(0.2, 0.2, 0.14, 0.1, 0.08, 0.05, 0.03) // time constant of each partial
        val seconds = 0.45
        val frames = (sampleRate * seconds).roundToInt()
        val out = DoubleArray(frames)
        for (partial in ratio.indices) {
            val step = 2.0 * PI * (strikeHz * ratio[partial] + offsetHz[partial]) / sampleRate
            val cos = kotlin.math.cos(step)
            val sin = sin(step)
            val decay = exp(-1.0 / (sampleRate * decaySeconds[partial]))
            var x = 0.0 // sin(theta)
            var y = 1.0 // cos(theta)
            var envelope = strength[partial]
            for (index in 0 until frames) {
                out[index] += x * envelope
                val nextX = x * cos + y * sin
                y = y * cos - x * sin
                x = nextX
                envelope *= decay
            }
        }
        // A very short bright tick at the start is the strike of the clapper.
        val strike = (sampleRate * 0.001).roundToInt()
        var noise = 0x1F123BB5
        for (index in 0 until strike) {
            noise = noise xor (noise shl 13); noise = noise xor (noise ushr 17); noise = noise xor (noise shl 5)
            out[index] += (noise.toDouble() / Int.MAX_VALUE) * 0.5 * (1.0 - index.toDouble() / strike)
        }
        // Fade the last milliseconds so the buffer never ends on a step.
        val fade = (sampleRate * 0.04).roundToInt()
        for (index in 0 until fade) out[frames - 1 - index] *= index.toDouble() / fade
        return out
    }

    private companion object {
        const val HISTORY_CAPACITY = 64
        const val KNEE = 24_000
        const val MAX_VOICES = 16
    }
}

/** Median of recent taps; long pauses and implausible intervals restart the sequence. */
class TapTempoCalculator(private val maxIntervals: Int = 5) {
    private var previousTapMs: Long? = null
    private val intervals = ArrayDeque<Long>()

    init { require(maxIntervals >= 1) }

    fun tap(timeMs: Long): Int? {
        val previous = previousTapMs
        if (previous == null) { previousTapMs = timeMs; return null }
        val interval = timeMs - previous
        if (interval > MAX_INTERVAL_MS || interval <= 0L) {
            intervals.clear()
            previousTapMs = timeMs
            return null
        }
        if (interval < MIN_INTERVAL_MS) return null
        if (intervals.size >= 2) {
            val center = median(intervals)
            if (interval < center * 0.5 || interval > center * 1.5) return null
        }
        previousTapMs = timeMs
        if (intervals.size == maxIntervals) intervals.removeFirst()
        intervals.addLast(interval)
        return (60_000.0 / median(intervals)).roundToInt().coerceIn(MetronomeSettings.MIN_BPM, MetronomeSettings.MAX_BPM)
    }

    fun reset() { previousTapMs = null; intervals.clear() }

    private fun median(values: Collection<Long>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
    }

    private companion object {
        const val MIN_INTERVAL_MS = 200L
        const val MAX_INTERVAL_MS = 2_000L
    }
}
