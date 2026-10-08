package org.anarkey.app

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.anarkey.app.audio.*
import org.anarkey.app.diagnostics.DetectionHistory
import org.anarkey.app.diagnostics.TunerEventLogger
import org.anarkey.core.music.TunerConfiguration

enum class CaptureStatus { STARTING, RUNNING, PAUSED, PERMISSION, UNSUPPORTED, UNAVAILABLE, BUSY }
data class DebugState(val status: CaptureStatus = CaptureStatus.STARTING, val frame: AudioFrame? = null)

class TunerViewModel(application: Application) : AndroidViewModel(application) {
    private val capture = AudioCapture(application)
    // Compose cancels an old effect before launching its replacement, but cancellation
    // still needs to finish releasing audio and diagnostics before the next owner starts.
    private val foregroundOwnership = Mutex()
    val exportedAudio = capture.exportedAudio
    val hasRecentAudio = capture.hasRecentAudio
    val captureRunning = capture.captureRunning
    private val mutableState = MutableStateFlow(DebugState())
    val state = mutableState.asStateFlow()
    private val detectionHistory = DetectionHistory()
    private val targetFrequencyHz = MutableStateFlow<Double?>(null)
    val history = detectionHistory.entries
    fun clearHistory() = detectionHistory.clear()
    fun saveRecentAudio(customName: String) = capture.saveRecentAudio(customName, eventLogger::recentLog)
    fun setTargetFrequency(frequencyHz: Double?) { targetFrequencyHz.value = frequencyHz }
    private val eventLogger = TunerEventLogger()
    private val enabled = MutableStateFlow(true)
    private val attempt = MutableStateFlow(0)
    private var lastConfiguration: TunerConfiguration? = null
    fun toggle() { enabled.value = !enabled.value }
    fun retry() { enabled.value = true; attempt.value++ }
    fun permissionMissing() { mutableState.value = DebugState(CaptureStatus.PERMISSION) }

    override fun onCleared() { capture.close() }

    /** Owned by the RESUMED tuner navigation entry, never by the entire Activity. */
    suspend fun foreground(configuration: TunerConfiguration) = foregroundOwnership.withLock {
        if (lastConfiguration != configuration) {
            detectionHistory.clear()
            lastConfiguration = configuration
        }
        try {
            combine(enabled, attempt, getApplication<AnarkeyApplication>().microphone.owner) { active, _, owner ->
                active && owner != MicrophoneOwner.RECORDER
            }.distinctUntilChanged().collectLatest { active ->
                if (!active) {
                    mutableState.value = DebugState(CaptureStatus.PAUSED)
                    return@collectLatest
                }
                mutableState.value = DebugState(CaptureStatus.STARTING)
                eventLogger.captureStarting(configuration.a4Hz)
                val lease = getApplication<AnarkeyApplication>().microphone.tryAcquire(MicrophoneOwner.TUNER)
                if (lease == null) {
                    mutableState.value = DebugState(CaptureStatus.BUSY)
                    return@collectLatest
                }
                try {
                    capture.run(a4Hz = configuration.a4Hz,
                        highPassHz = if (configuration.selection.instrumentId == "bass") AudioCapture.BASS_HIGH_PASS_HZ else AudioCapture.GUITAR_HIGH_PASS_HZ,
                        targetFrequencyHz = { targetFrequencyHz.value }) {
                        detectionHistory.record(it.tuner, SystemClock.elapsedRealtime(), System.currentTimeMillis())
                        eventLogger.onFrame(it.tuner, SystemClock.elapsedRealtime(), it.comparisonPitch, configuration.a4Hz)
                        mutableState.value = DebugState(CaptureStatus.RUNNING, it)
                    }
                } catch (cancelled: CancellationException) { eventLogger.captureStopped(); throw cancelled }
                  catch (_: SecurityException) { eventLogger.captureFailed("PERMISSION"); permissionMissing() }
                  catch (_: UnsupportedAudioException) { eventLogger.captureFailed("UNSUPPORTED_CONFIGURATION"); mutableState.value = DebugState(CaptureStatus.UNSUPPORTED) }
                  catch (_: MicrophoneUnavailableException) { eventLogger.captureFailed("MICROPHONE_UNAVAILABLE"); mutableState.value = DebugState(CaptureStatus.UNAVAILABLE) }
                  catch (_: IllegalStateException) { eventLogger.captureFailed("ILLEGAL_AUDIO_STATE"); mutableState.value = DebugState(CaptureStatus.UNAVAILABLE) }
                finally { lease.close() }
            }
        } finally {
            mutableState.value = DebugState(CaptureStatus.PAUSED)
        }
    }
}
