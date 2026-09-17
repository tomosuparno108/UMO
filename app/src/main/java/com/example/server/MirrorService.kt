package com.example.server

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioCapture
import com.example.encoder.ScreenEncoder
import com.example.model.StreamSettings
import com.example.model.StreamStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MirrorService : Service() {
    private val TAG = "MirrorService"
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaProjection: MediaProjection? = null
    private var screenEncoder: ScreenEncoder? = null
    private var audioCapture: AudioCapture? = null
    private var mirrorServer: MirrorServer? = null

    companion object {
        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        private const val NOTIFICATION_CHANNEL_ID = "usb_mirror_service_channel"
        private const val NOTIFICATION_ID = 1001

        // Global state observable by ViewModel
        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _statsFlow = MutableStateFlow(StreamStats())
        val statsFlow: StateFlow<StreamStats> = _statsFlow.asStateFlow()

        var currentSettings: StreamSettings = StreamSettings()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "USBMirror::StreamingWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopMirroring()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultCode != 0 && resultData != null) {
                startForegroundWithNotification()
                startMirroring(resultCode, resultData)
            } else {
                Log.e(TAG, "Missing projection result code or data intent.")
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundWithNotification() {
        val stopIntent = Intent(this, MirrorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("USB Mirror OBS is Active")
            .setContentText("Streaming on localhost:${currentSettings.port} | Anti-Lag Mode")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Stream", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startMirroring(resultCode: Int, resultData: Intent) {
        try {
            wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24h safety timeout

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData) ?: run {
                Log.e(TAG, "MediaProjection was null")
                stopSelf()
                return
            }
            mediaProjection = projection

            // Get display density
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val densityDpi = metrics.densityDpi

            // Start Mirror Server
            val server = MirrorServer(
                port = currentSettings.port,
                onRequestKeyframe = {
                    screenEncoder?.requestKeyframe()
                }
            )
            mirrorServer = server
            server.start()

            // Observe stats from server
            serviceScope.launch {
                server.statsFlow.collect { stats ->
                    _statsFlow.value = stats
                }
            }

            // Start Screen Encoder
            val encoder = ScreenEncoder(
                mediaProjection = projection,
                settings = currentSettings,
                densityDpi = densityDpi,
                onFrameAvailable = { frameBytes ->
                    server.broadcastVideo(frameBytes)
                }
            )
            screenEncoder = encoder
            encoder.start()

            // Start Audio Capture
            val audio = AudioCapture(
                mediaProjection = projection,
                settings = currentSettings,
                onAudioAvailable = { audioBytes ->
                    server.broadcastAudio(audioBytes)
                }
            )
            audioCapture = audio
            audio.start()

            _isRunningFlow.value = true
            Log.i(TAG, "USB Mirror Service successfully started.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start mirroring: ${e.message}", e)
            stopMirroring()
        }
    }

    private fun stopMirroring() {
        try {
            audioCapture?.stop()
            audioCapture = null
        } catch (ignored: Exception) {}

        try {
            screenEncoder?.stop()
            screenEncoder = null
        } catch (ignored: Exception) {}

        try {
            mirrorServer?.stop()
            mirrorServer = null
        } catch (ignored: Exception) {}

        try {
            mediaProjection?.stop()
            mediaProjection = null
        } catch (ignored: Exception) {}

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (ignored: Exception) {}

        _isRunningFlow.value = false
        _statsFlow.value = _statsFlow.value.copy(isStreaming = false, activeClients = 0)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "USB Mirror Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active notification while USB screen mirroring is running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopMirroring()
        super.onDestroy()
    }
}
