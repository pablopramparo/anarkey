package org.anarkey.app.audio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MicrophoneOwner { TUNER, RECORDER }

/** One process-wide microphone lease. A caller keeps the lease for the full capture lifetime. */
class MicrophoneOwnership {
    private val mutex = Mutex()
    private val mutableOwner = MutableStateFlow<MicrophoneOwner?>(null)
    val owner = mutableOwner.asStateFlow()

    suspend fun acquire(owner: MicrophoneOwner): Lease {
        mutex.lock()
        mutableOwner.value = owner
        return Lease(owner)
    }

    fun tryAcquire(owner: MicrophoneOwner): Lease? {
        if (!mutex.tryLock()) return null
        mutableOwner.value = owner
        return Lease(owner)
    }

    inner class Lease internal constructor(private val owner: MicrophoneOwner) : AutoCloseable {
        private var closed = false
        override fun close() {
            if (closed) return
            closed = true
            check(mutableOwner.value == owner)
            mutableOwner.value = null
            mutex.unlock()
        }
    }
}
