package com.ceydagb.earlynotify.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.ceydagb.earlynotify.MainActivity
import com.ceydagb.earlynotify.R
import com.ceydagb.earlynotify.data.AlarmType

/**
 * Bildirim kanallarını yönetir, foreground servis bildirimini ve nabız alarmını üretir.
 *
 * Bildirim kanalı sesi/titreşimi oluşturulduktan sonra değiştirilemediği için her alarm tipi
 * ayrı bir kanala sahiptir; ayarlara göre uygun kanal seçilir.
 */
class AlarmNotifier(private val context: Context) {

    companion object {
        const val FOREGROUND_CHANNEL_ID = "earlynotify_monitor"
        const val ALARM_CHANNEL_SILENT = "earlynotify_alarm_silent"
        const val ALARM_CHANNEL_VIBRATE = "earlynotify_alarm_vibrate"
        const val ALARM_CHANNEL_SOUND = "earlynotify_alarm_sound"

        const val FOREGROUND_NOTIF_ID = 1001
        const val ALARM_NOTIF_ID = 1002
    }

    private val manager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // Foreground servis bildirimi: sessiz, düşük öncelik.
        manager.createNotificationChannel(
            NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Nabız izleme (arka plan)",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "İzleme servisi çalışırken görünen kalıcı bildirim." }
        )

        val alarmAudio = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val vibratePattern = longArrayOf(0, 400, 200, 400, 200, 600)

        manager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_SILENT, "Alarm — sadece bildirim", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(false)
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_VIBRATE, "Alarm — titreşim", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = vibratePattern
                setSound(null, null)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(ALARM_CHANNEL_SOUND, "Alarm — ses + titreşim", NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                vibrationPattern = vibratePattern
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    alarmAudio
                )
            }
        )
    }

    /** İzleme servisinin kalıcı (foreground) bildirimi. */
    fun buildForegroundNotification(contentText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, FOREGROUND_CHANNEL_ID)
            .setContentTitle("EarlyNotify çalışıyor")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Eşik aşıldığında alarmı çalar. */
    fun fireAlarm(bpm: Int, alarmType: AlarmType) {
        ensureChannels()
        val channel = channelFor(alarmType)

        val openIntent = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, channel)
            .setContentTitle("⚠️ Nabız yüksek: $bpm bpm")
            .setContentText("Eşik aşıldı — nabzınızı kontrol edin.")
            .setSmallIcon(R.drawable.ic_stat_heart)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(openIntent)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Eski sürümlerde kanal yok; sesi/titreşimi bildirimde belirt.
            when (alarmType) {
                AlarmType.NOTIFICATION_ONLY -> {}
                AlarmType.NOTIFICATION_VIBRATE -> builder.setVibrate(longArrayOf(0, 400, 200, 400))
                AlarmType.NOTIFICATION_SOUND_VIBRATE -> {
                    builder.setVibrate(longArrayOf(0, 400, 200, 400))
                    builder.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                }
            }
        }

        manager.notify(ALARM_NOTIF_ID, builder.build())

        // Kanal sesine ek olarak doğrudan titreşim (heads-up garantisi için).
        if (alarmType != AlarmType.NOTIFICATION_ONLY) {
            vibrate()
        }
    }

    /** Ayarlardan "test alarmı" butonu için. */
    fun fireTest(alarmType: AlarmType) = fireAlarm(bpm = 0, alarmType = alarmType)

    private fun channelFor(alarmType: AlarmType): String = when (alarmType) {
        AlarmType.NOTIFICATION_ONLY -> ALARM_CHANNEL_SILENT
        AlarmType.NOTIFICATION_VIBRATE -> ALARM_CHANNEL_VIBRATE
        AlarmType.NOTIFICATION_SOUND_VIBRATE -> ALARM_CHANNEL_SOUND
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        val pattern = longArrayOf(0, 400, 200, 400, 200, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            vibrator?.vibrate(pattern, -1)
        }
    }
}
