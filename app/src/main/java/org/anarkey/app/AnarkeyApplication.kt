package org.anarkey.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.anarkey.app.audio.MicrophoneOwnership
import org.anarkey.app.recording.data.RecordingRepository

class AnarkeyApplication : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val microphone = MicrophoneOwnership()
    val recordings by lazy { RecordingRepository(this) }
    private val reconciliation = Mutex()
    @Volatile private var reconciled = false

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { ensureReconciled() }
    }

    suspend fun ensureReconciled() = reconciliation.withLock {
        if (!reconciled) {
            recordings.reconcile()
            reconciled = true
        }
    }
}
