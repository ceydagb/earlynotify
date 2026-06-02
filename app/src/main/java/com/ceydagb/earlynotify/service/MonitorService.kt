package com.ceydagb.earlynotify.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.ceydagb.earlynotify.alarm.AlarmNotifier
import com.ceydagb.earlynotify.data.AppSettings
import com.ceydagb.earlynotify.data.DataSourceMode
import com.ceydagb.earlynotify.data.HealthConnectSource
import com.ceydagb.earlynotify.data.HeartRateBus
import com.ceydagb.earlynotify.data.HeartRateSample
import com.ceydagb.earlynotify.data.SettingsRepository
import com.ceydagb.earlynotify.logic.ThresholdEvaluator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Arka planda çalışan foreground servis. İki kaynaktan (Health Connect + Accessibility) gelen
 * nabız ölçümlerini kayan bir pencerede tutar, her yeni veride [ThresholdEvaluator] ile kuralı
 * değerlendirir ve gerekiyorsa [AlarmNotifier] üzerinden alarm üretir.
 */
class MonitorService : Service() {

    companion object {
        const val ACTION_START = "com.ceydagb.earlynotify.START"
        const val ACTION_STOP = "com.ceydagb.earlynotify.STOP"

        /** Pencerede tutulacak en uzun geçmiş (sürekli/sustained kuralı için yeterli). */
        private const val WINDOW_MS = 15 * 60 * 1000L

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MonitorService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val window = ArrayDeque<HeartRateSample>()

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var notifier: AlarmNotifier
    private lateinit var healthConnect: HealthConnectSource
    private val evaluator = ThresholdEvaluator()

    @Volatile
    private var settings: AppSettings = AppSettings()
    @Volatile
    private var lastAlarmMs: Long = 0L
    @Volatile
    private var lastBpm: Int = -1

    override fun onCreate() {
        super.onCreate()
        settingsRepo = SettingsRepository(applicationContext)
        notifier = AlarmNotifier(applicationContext)
        healthConnect = HealthConnectSource(applicationContext)
        notifier.ensureChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopMonitoring()
                return START_NOT_STICKY
            }
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        startForegroundCompat(notifier.buildForegroundNotification("Nabız izleniyor…"))

        scope.launch {
            settingsRepo.settings.collect { s ->
                settings = s
                if (!s.monitoringEnabled) {
                    stopMonitoring()
                }
            }
        }

        // Kaynak B — Accessibility (ekrandan okunan anlık değer)
        scope.launch {
            HeartRateBus.samples.collect { sample ->
                if (settings.sourceMode != DataSourceMode.HEALTH_CONNECT) {
                    onNewSample(sample)
                }
            }
        }

        // Kaynak A — Health Connect (periyodik okuma)
        scope.launch {
            var lastReadMs = System.currentTimeMillis() - 60_000L
            while (true) {
                if (settings.sourceMode != DataSourceMode.ACCESSIBILITY && healthConnect.isAvailable()) {
                    val granted = runCatching { healthConnect.hasPermissions() }.getOrDefault(false)
                    if (granted) {
                        val samples = healthConnect.readSince(lastReadMs)
                        if (samples.isNotEmpty()) {
                            lastReadMs = samples.last().timestampMs
                            for (s in samples) onNewSample(s)
                        }
                    }
                }
                delay(settings.pollingSeconds.coerceAtLeast(5) * 1000L)
            }
        }
    }

    private suspend fun onNewSample(sample: HeartRateSample) {
        mutex.withLock {
            window.addLast(sample)
            val cutoff = System.currentTimeMillis() - WINDOW_MS
            while (window.isNotEmpty() && window.first().timestampMs < cutoff) {
                window.removeFirst()
            }
            lastBpm = sample.bpm

            val now = System.currentTimeMillis()
            val fire = evaluator.shouldAlarm(window.toList(), settings, now, lastAlarmMs)
            if (fire) {
                lastAlarmMs = now
                notifier.fireAlarm(sample.bpm, settings.alarmType)
            }
        }
        updateForegroundText()
    }

    private fun updateForegroundText() {
        val text = if (lastBpm > 0) "Son nabız: $lastBpm bpm (eşik ${settings.thresholdBpm})"
        else "Nabız izleniyor… (eşik ${settings.thresholdBpm})"
        startForegroundCompat(notifier.buildForegroundNotification(text))
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                AlarmNotifier.FOREGROUND_NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                AlarmNotifier.FOREGROUND_NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(AlarmNotifier.FOREGROUND_NOTIF_ID, notification)
        }
    }

    private fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
