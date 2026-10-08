package org.anarkey.app.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.anarkey.app.AnarkeyApplication
import org.anarkey.app.AppLanguage
import org.anarkey.app.MainActivity
import org.anarkey.app.R
import org.anarkey.app.audio.MicrophoneOwner
import org.anarkey.app.recording.data.inspectM4a
import org.anarkey.core.recording.RecordingStatus
import org.anarkey.core.recording.RecordingTimeline
import java.io.File

enum class RecorderPhase { IDLE, STARTING, RECORDING, PAUSED, FINALIZING, ERROR }
data class RecorderRuntime(val phase: RecorderPhase = RecorderPhase.IDLE, val recordingId: String? = null,
    val durationMs: Long = 0, val message: String? = null)

class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val app get() = application as AnarkeyApplication
    private var recorder: MediaRecorder? = null
    private var recordingId: String? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }
    private var lease: org.anarkey.app.audio.MicrophoneOwnership.Lease? = null
    private val timeline = RecordingTimeline()
    private var ticker: kotlinx.coroutines.Job? = null
    private val commandLock = Mutex()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfoType)
        when (intent?.action) {
            ACTION_START -> { val songId = intent?.getStringExtra(EXTRA_SONG_ID); dispatch { begin(songId) } }
            ACTION_PAUSE -> dispatch { pause() }
            ACTION_RESUME -> dispatch { resume() }
            ACTION_MARK -> dispatch { mark() }
            ACTION_STOP -> dispatch { finish() }
            ACTION_CANCEL -> dispatch { cancelRecording() }
        }
        return START_NOT_STICKY
    }

    private suspend fun begin(songId: String? = null) {
        if (RecordingRuntimeStore.value.value.phase != RecorderPhase.IDLE) return
        RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.STARTING)
        try {
            app.ensureReconciled()
            val held = app.microphone.acquire(MicrophoneOwner.RECORDER)
            lease = held
            val row = app.recordings.createRecording(songId)
            recordingId = row.id
            val output = app.recordings.files.temporary(row.id)
            check(output.createNewFile()) { "The temporary audio file already exists" }
            val created = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder = created
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(44_100)
            created.setAudioEncodingBitRate(96_000)
            created.setOutputFile(output.absolutePath)
            created.prepare()
            created.start()
            timeline.start(SystemClock.elapsedRealtime())
            RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.RECORDING, row.id)
            ticker = scope.launch {
                while (true) {
                    val current = RecordingRuntimeStore.value.value
                    if (current.phase == RecorderPhase.RECORDING || current.phase == RecorderPhase.PAUSED)
                        RecordingRuntimeStore.value.value = current.copy(durationMs = timeline.positionMs(SystemClock.elapsedRealtime()))
                    delay(250)
                }
            }
            updateNotification()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            runCatching { recorder?.release() }; recorder = null
            try { recordingId?.let { app.recordings.markRecovery(it) } } catch (_: Exception) { }
            releaseLease()
            RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.ERROR, recordingId, message = e.message ?: "No se pudo iniciar la grabación")
            updateNotification()
        }
    }

    private suspend fun pause() {
        if (RecordingRuntimeStore.value.value.phase != RecorderPhase.RECORDING) return
        try {
            recorder?.pause()
            timeline.pause(SystemClock.elapsedRealtime())
            recordingId?.let { app.recordings.transition(it, RecordingStatus.PAUSED) }
            RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.PAUSED, recordingId,
                timeline.positionMs(SystemClock.elapsedRealtime()))
            updateNotification()
        } catch (e: Throwable) { failCapture(e) }
    }

    private suspend fun resume() {
        if (RecordingRuntimeStore.value.value.phase != RecorderPhase.PAUSED) return
        try {
            recorder?.resume()
            timeline.resume(SystemClock.elapsedRealtime())
            recordingId?.let { app.recordings.transition(it, RecordingStatus.RECORDING) }
            RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.RECORDING, recordingId,
                timeline.positionMs(SystemClock.elapsedRealtime()))
            updateNotification()
        } catch (e: Throwable) { failCapture(e) }
    }

    private suspend fun mark() {
        val current = RecordingRuntimeStore.value.value
        if (current.phase != RecorderPhase.RECORDING && current.phase != RecorderPhase.PAUSED) return
        recordingId?.let { app.recordings.addMarker(it, timeline.positionMs(SystemClock.elapsedRealtime())) }
    }

    private suspend fun finish() {
        val id = recordingId ?: run { stopSelf(); return }
        val phase = RecordingRuntimeStore.value.value.phase
        if (phase == RecorderPhase.ERROR) {
            RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.IDLE)
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        if (phase != RecorderPhase.RECORDING && phase != RecorderPhase.PAUSED) return
        RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.FINALIZING, id,
            timeline.positionMs(SystemClock.elapsedRealtime()))
        ticker?.cancel()
        try {
            if (phase == RecorderPhase.RECORDING) timeline.finish(SystemClock.elapsedRealtime())
            else timeline.finish(SystemClock.elapsedRealtime())
            val stopSucceeded = runCatching { recorder?.stop() }.isSuccess
            runCatching { recorder?.release() }; recorder = null
            releaseLease()
            val partial = app.recordings.files.temporary(id)
            if (!stopSucceeded || !partial.isFile || partial.length() < 256) throw IllegalStateException("El archivo no terminó correctamente; se conservó para recuperación")
            val info = withContext(Dispatchers.IO) { inspectM4a(partial) }
            val wave = withContext(Dispatchers.IO) { WaveformGenerator.generate(partial) }
            app.recordings.markFinalizing(id, info.durationMs, partial.length(), info.sampleRateHz, wave)
            app.recordings.files.promote(id)
            app.recordings.markReady(id)
            RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.IDLE)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            runCatching { recorder?.release() }; recorder = null
            releaseLease()
            try { app.recordings.markRecovery(id) } catch (_: Exception) { }
            RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.ERROR, id,
                timeline.positionMs(SystemClock.elapsedRealtime()), e.message ?: "El audio necesita recuperación")
            updateNotification()
        }
    }

    private suspend fun cancelRecording() {
        val id = recordingId ?: run { stopSelf(); return }
        ticker?.cancel()
        runCatching { recorder?.stop() }; runCatching { recorder?.release() }; recorder = null
        releaseLease()
        // Cancel is explicit: preserve the closed file as recoverable instead of deleting audio silently.
        try { app.recordings.markRecovery(id) } catch (_: Exception) { }
        RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }

    private suspend fun failCapture(error: Throwable) {
        val id = recordingId
        runCatching { recorder?.release() }; recorder = null
        releaseLease()
        if (id != null) try { app.recordings.markRecovery(id) } catch (_: Exception) { }
        RecordingRuntimeStore.value.value = RecorderRuntime(RecorderPhase.ERROR, id,
            timeline.positionMs(SystemClock.elapsedRealtime()), error.message ?: "Se interrumpió la captura")
        updateNotification()
    }

    private fun releaseLease() { lease?.close(); lease = null }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL_ID, getString(R.string.recording_channel), NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val state = RecordingRuntimeStore.value.value.phase
        val title = if (state == RecorderPhase.PAUSED) getString(R.string.recording_paused) else getString(R.string.recording_active)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.brand_notification)
            .setContentTitle(title).setContentText(getString(R.string.recording_notification_text))
            .setContentIntent(open).setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(state != RecorderPhase.ERROR)
            .addAction(0, getString(R.string.stop_recording), stop).build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    override fun onDestroy() {
        ticker?.cancel()
        // An unexpected service death preserves the file and metadata for startup reconciliation.
        runCatching { recorder?.release() }; recorder = null
        lease?.close(); lease = null
        scope.cancel()
        super.onDestroy()
    }

    private fun dispatch(block: suspend () -> Unit) { scope.launch { commandLock.withLock { block() } } }

    companion object {
        const val ACTION_START = "org.anarkey.action.START_RECORDING"
        const val ACTION_PAUSE = "org.anarkey.action.PAUSE_RECORDING"
        const val ACTION_RESUME = "org.anarkey.action.RESUME_RECORDING"
        const val ACTION_MARK = "org.anarkey.action.MARK_RECORDING"
        const val ACTION_STOP = "org.anarkey.action.STOP_RECORDING"
        const val ACTION_CANCEL = "org.anarkey.action.CANCEL_RECORDING"
        const val EXTRA_SONG_ID = "org.anarkey.extra.SONG_ID"
        const val CHANNEL_ID = "recording"
        const val NOTIFICATION_ID = 47
        // This constant was added in API 30; legacy platforms must receive type 0. Lint does not
        // trace the SDK guard through a static companion property, so the suppression is scoped here.
        @SuppressLint("InlinedApi")
        val ServiceInfoType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
    }
}

object RecordingRuntimeStore {
    val value = kotlinx.coroutines.flow.MutableStateFlow(RecorderRuntime())
}
