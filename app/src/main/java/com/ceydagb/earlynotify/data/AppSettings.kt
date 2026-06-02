package com.ceydagb.earlynotify.data

/** Hangi tetikleme kuralı kullanılacak. */
enum class TriggerRule {
    /** İlk eşik-aşan ölçümde anında alarm. */
    INSTANT,

    /** Son N ölçümün hepsi eşik üstündeyse alarm. */
    CONSECUTIVE,

    /** Eşik üstünde kesintisiz X saniye kalınca alarm. */
    SUSTAINED
}

/** Alarm verme şekli. */
enum class AlarmType {
    NOTIFICATION_ONLY,
    NOTIFICATION_VIBRATE,
    NOTIFICATION_SOUND_VIBRATE
}

/** Hangi veri kaynağı kullanılacak. */
enum class DataSourceMode {
    HEALTH_CONNECT,
    ACCESSIBILITY,
    BOTH
}

/** Kullanıcının ayarlardan belirlediği tüm seçenekler. */
data class AppSettings(
    val thresholdBpm: Int = 80,
    val rule: TriggerRule = TriggerRule.INSTANT,
    val consecutiveCount: Int = 3,
    val sustainedSeconds: Int = 30,
    val pollingSeconds: Int = 30,
    val cooldownMinutes: Int = 5,
    val alarmType: AlarmType = AlarmType.NOTIFICATION_SOUND_VIBRATE,
    val sourceMode: DataSourceMode = DataSourceMode.BOTH,
    val monitoringEnabled: Boolean = false
)

/** Tek bir nabız ölçümü. */
data class HeartRateSample(
    val bpm: Int,
    val timestampMs: Long
)
