package org.anarkey.app.metronome

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.anarkey.app.AppLanguage
import org.anarkey.app.MainActivity
import org.anarkey.app.R

/**
 * Keeps the process in the foreground while the metronome plays so audio survives the screen turning off.
 * The audio itself stays in [MetronomeViewModel]; this service only holds the notification and a wake lock.
 */
class MetronomeService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguage.wrap(newBase))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("WakelockTimeout")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), ServiceInfoType)
        if (intent?.action == ACTION_STOP) {
            val stop = onStopRequested
            if (stop != null) stop() else stopSelf()
            return START_NOT_STICKY
        }
        if (wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anarkey:metronome").apply { acquire() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        super.onDestroy()
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.metronome_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(this, 3, Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 4, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.brand_notification)
            .setContentTitle(getString(R.string.metronome_notification_title))
            .setContentText(getString(R.string.metronome_notification_text))
            .setContentIntent(open).setOngoing(true).setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, getString(R.string.metronome_stop), stop).build()
    }

    companion object {
        private const val ACTION_STOP = "org.anarkey.action.STOP_METRONOME"
        private const val CHANNEL_ID = "metronome"
        private const val NOTIFICATION_ID = 48
        private val ServiceInfoType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

        /** Set by the view model that owns playback; the notification's Stop button calls it. */
        @Volatile var onStopRequested: (() -> Unit)? = null

        fun start(context: Context) {
            // Starting can be refused if the app is already in the background; the audio then just plays on.
            runCatching { ContextCompat.startForegroundService(context, Intent(context, MetronomeService::class.java)) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MetronomeService::class.java))
        }
    }
}
