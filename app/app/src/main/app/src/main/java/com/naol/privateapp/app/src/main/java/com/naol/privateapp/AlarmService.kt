package com.naol.privateapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import kotlin.math.abs
import kotlin.math.sqrt

class AlarmService : Service(), SensorEventListener {

companion object {
    const val ACTION_START = "com.naol.privateapp.START"
    const val ACTION_STOP_ALARM = "com.naol.privateapp.STOP_ALARM"

    private const val CHANNEL_ID = "naol_protection"
    private const val NOTIFICATION_ID = 4101
}

private var charging = false
private var alarmActive = false
private var receiverRegistered = false
private var lastMagnitude = 0f
private var lastMovementAlarmAt = 0L
private var mediaPlayer: MediaPlayer? = null

private lateinit var sensorManager: SensorManager
private var accelerometer: Sensor? = null

private val powerReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                charging = true
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                val wasCharging = charging
                charging = false

                if (wasCharging) {
                    triggerAlarm("Charger disconnected")
                }
            }
        }

        updateNotification()
    }
}

override fun onCreate() {
    super.onCreate()

    createNotificationChannel()

    sensorManager =
        getSystemService(Context.SENSOR_SERVICE) as SensorManager

    accelerometer =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    charging = isCurrentlyCharging()

    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_POWER_CONNECTED)
        addAction(Intent.ACTION_POWER_DISCONNECTED)
    }

    if (Build.VERSION.SDK_INT >= 33) {
        registerReceiver(
            powerReceiver,
            filter,
            Context.RECEIVER_NOT_EXPORTED
        )
    } else {
        @Suppress("DEPRECATION")
        registerReceiver(powerReceiver, filter)
    }

    receiverRegistered = true
}

override fun onStartCommand(
    intent: Intent?,
    flags: Int,
    startId: Int
): Int {

    if (intent?.action == ACTION_STOP_ALARM) {
        stopAlarm()
        updateNotification()
        return START_STICKY
    }

    startForeground(
        NOTIFICATION_ID,
        buildNotification("Charging Guard is active")
    )

    charging = isCurrentlyCharging()

    accelerometer?.let {
        sensorManager.registerListener(
            this,
            it,
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    updateNotification()
    return START_STICKY
}

override fun onSensorChanged(event: SensorEvent) {
    if (!charging) return
    if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

    val magnitude = sqrt(
        event.values[0] * event.values[0] +
        event.values[1] * event.values[1] +
        event.values[2] * event.values[2]
    )

    val movement = abs(magnitude - lastMagnitude)
    lastMagnitude = magnitude

    val now = SystemClock.elapsedRealtime()

    if (movement > 4.0f &&
        now - lastMovementAlarmAt > 5000L
    ) {
        lastMovementAlarmAt = now
        triggerAlarm("Movement detected while charging")
    }
}

override fun onAccuracyChanged(
    sensor: Sensor?,
    accuracy: Int
) = Unit

private fun triggerAlarm(reason: String) {
    alarmActive = true

    try {
        if (mediaPlayer == null) {
            val uri = Settings.System.DEFAULT_ALARM_ALERT_URI
                ?: Settings.System.DEFAULT_RINGTONE_URI

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(
                            AudioAttributes.CONTENT_TYPE_SONIFICATION
                        )
                        .build()
                )

                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
            }
        }

        if (mediaPlayer?.isPlaying != true) {
            mediaPlayer?.start()
        }
    } catch (_: Exception) {
        // Vibration remains available if sound playback fails.
    }

    vibrate()
    updateNotification("ALARM: $reason")
}

private fun vibrate() {
    try {
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager

            manager.defaultVibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 700, 250),
                    0
                )
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator =
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 700, 250),
                    0
                )
            )
        }
    } catch (_: Exception) {
        // Ignore device-specific vibration errors.
    }
}

private fun stopAlarm() {
    alarmActive = false

    try {
        mediaPlayer?.stop()
    } catch (_: Exception) {
    }

    mediaPlayer?.release()
    mediaPlayer = null

    try {
        if (Build.VERSION.SDK_INT >= 31) {
            val manager = getSystemService(
                Context.VIBRATOR_MANAGER_SERVICE
            ) as VibratorManager

            manager.defaultVibrator.cancel()
        } else {
            @Suppress("DEPRECATION")
            val vibrator =
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            vibrator.cancel()
        }
    } catch (_: Exception) {
    }
}

private fun isCurrentlyCharging(): Boolean {
    val battery = registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )

    val plugged = battery?.getIntExtra(
        BatteryManager.EXTRA_PLUGGED,
        0
    ) ?: 0

    return plugged != 0
}

private fun buildNotification(message: String): Notification {
    val stopIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, AlarmService::class.java)
            .setAction(ACTION_STOP_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or
            PendingIntent.FLAG_IMMUTABLE
    )

    val openIntent = PendingIntent.getActivity(
        this,
        2,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or
            PendingIntent.FLAG_IMMUTABLE
    )

    val builder = if (Build.VERSION.SDK_INT >= 26) {
        Notification.Builder(this, CHANNEL_ID)
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
    }

    return builder
        .setSmallIcon(android.R.drawable.ic_lock_lock)
        .setContentTitle(
            if (alarmActive) {
                "Naol Private — ALARM"
            } else {
                "Naol Private"
            }
        )
        .setContentText(message)
        .setContentIntent(openIntent)
        .setOngoing(true)
        .addAction(
            android.R.drawable.ic_media_pause,
            "Stop alarm",
            stopIntent
        )
        .build()
}

private fun updateNotification(
    message: String =
        if (alarmActive) "Alarm is sounding"
        else "Charging Guard is active"
) {
    val manager =
        getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

    manager.notify(
        NOTIFICATION_ID,
        buildNotification(message)
    )
}

private fun createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Naol Private Protection",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Charging protection alarm status"
        }

        val manager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager

        manager.createNotificationChannel(channel)
    }
}

override fun onDestroy() {
    stopAlarm()
    sensorManager.unregisterListener(this)

    if (receiverRegistered) {
        try {
            unregisterReceiver(powerReceiver)
        } catch (_: Exception) {
        }
    }

    super.onDestroy()
}

override fun onBind(intent: Intent?): IBinder? = null

}
