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

    fun updateAtNextPulse(settings: MetronomeSettings) = planner.updateAtNextPulse(settings)

    fun render(destination: ShortArray) {
        destination.fill(0)
        val blockStart = frameCursor
        val blockEnd = blockStart + destination.size
        while (planner.nextPulseFrame < blockEnd) {
            val event = planner.nextPulse()
            ensureSamples(event.sound, event.volume)
            remember(event.frame, event.beatInBar)
            mixClick(destination, (event.frame - blockStart).toInt(), samplesFor(event.accent))
        }
        frameCursor = blockEnd
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

    private fun mixClick(destination: ShortArray, offset: Int, click: ShortArray) {
        val count = minOf(click.size, destination.size - offset)
        for (index in 0 until count) {
            val mixed = destination[offset + index].toInt() + click[index].toInt()
            destination[offset + index] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun makeClick(sound: ClickSound, volume: Float, accent: Accent): ShortArray {
        val frames = (sampleRate * CLICK_MILLISECONDS / 1_000.0).roundToInt()
        val accentGain = when (accent) { Accent.PRIMARY -> 0.95; Accent.SECONDARY -> 0.72; Accent.NORMAL -> 0.5 }
        val frequency = when (accent) { Accent.PRIMARY -> 1_760.0; Accent.SECONDARY -> 1_320.0; Accent.NORMAL -> 880.0 }
        val decay = when (sound) { ClickSound.WOOD -> 180.0; ClickSound.DIGITAL -> 105.0; ClickSound.BELL -> 70.0 }
        val amplitude = Short.MAX_VALUE * volume * accentGain
        return ShortArray(frames) { index ->
            val seconds = index.toDouble() / sampleRate
            val fundamental = sin(2.0 * PI * frequency * seconds)
            val overtone = when (sound) {
                ClickSound.WOOD -> 0.42 * sin(2.0 * PI * frequency * 2.7 * seconds)
                ClickSound.DIGITAL -> 0.16 * sin(2.0 * PI * frequency * 3.0 * seconds)
                ClickSound.BELL -> 0.55 * sin(2.0 * PI * frequency * 2.0 * seconds)
            }
            ((fundamental + overtone) * exp(-decay * seconds) * amplitude).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private companion object {
        const val HISTORY_CAPACITY = 64
        const val CLICK_MILLISECONDS = 24.0
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
