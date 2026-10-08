package org.anarkey.app

import kotlinx.coroutines.runBlocking
import org.anarkey.app.audio.MicrophoneOwner
import org.anarkey.app.audio.MicrophoneOwnership
import org.junit.Assert.*
import org.junit.Test

class MicrophoneOwnershipTest {
    @Test fun tunerAndRecorderNeverAcquireTheSameLease() = runBlocking {
        val ownership = MicrophoneOwnership()
        val tuner = ownership.acquire(MicrophoneOwner.TUNER)
        assertEquals(MicrophoneOwner.TUNER, ownership.owner.value)
        assertNull(ownership.tryAcquire(MicrophoneOwner.RECORDER))
        tuner.close()
        assertEquals(null, ownership.owner.value)
        val recorder = ownership.tryAcquire(MicrophoneOwner.RECORDER)
        assertNotNull(recorder)
        assertNull(ownership.tryAcquire(MicrophoneOwner.TUNER))
        recorder!!.close()
        recorder.close()
        assertEquals(null, ownership.owner.value)
    }
}
