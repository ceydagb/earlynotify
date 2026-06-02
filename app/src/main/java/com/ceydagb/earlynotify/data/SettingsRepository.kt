package com.ceydagb.earlynotify.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "earlynotify_settings")

/** Ayarları DataStore üzerinde okuyup yazar. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THRESHOLD = intPreferencesKey("threshold_bpm")
        val RULE = stringPreferencesKey("rule")
        val CONSECUTIVE = intPreferencesKey("consecutive_count")
        val SUSTAINED = intPreferencesKey("sustained_seconds")
        val POLLING = intPreferencesKey("polling_seconds")
        val COOLDOWN = intPreferencesKey("cooldown_minutes")
        val ALARM_TYPE = stringPreferencesKey("alarm_type")
        val SOURCE = stringPreferencesKey("source_mode")
        val MONITORING = intPreferencesKey("monitoring_enabled") // 0/1
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(prefs.toSettings())
            prefs[Keys.THRESHOLD] = updated.thresholdBpm
            prefs[Keys.RULE] = updated.rule.name
            prefs[Keys.CONSECUTIVE] = updated.consecutiveCount
            prefs[Keys.SUSTAINED] = updated.sustainedSeconds
            prefs[Keys.POLLING] = updated.pollingSeconds
            prefs[Keys.COOLDOWN] = updated.cooldownMinutes
            prefs[Keys.ALARM_TYPE] = updated.alarmType.name
            prefs[Keys.SOURCE] = updated.sourceMode.name
            prefs[Keys.MONITORING] = if (updated.monitoringEnabled) 1 else 0
        }
    }

    private fun Preferences.toSettings(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            thresholdBpm = this[Keys.THRESHOLD] ?: defaults.thresholdBpm,
            rule = this[Keys.RULE]?.let { runCatching { TriggerRule.valueOf(it) }.getOrNull() } ?: defaults.rule,
            consecutiveCount = this[Keys.CONSECUTIVE] ?: defaults.consecutiveCount,
            sustainedSeconds = this[Keys.SUSTAINED] ?: defaults.sustainedSeconds,
            pollingSeconds = this[Keys.POLLING] ?: defaults.pollingSeconds,
            cooldownMinutes = this[Keys.COOLDOWN] ?: defaults.cooldownMinutes,
            alarmType = this[Keys.ALARM_TYPE]?.let { runCatching { AlarmType.valueOf(it) }.getOrNull() } ?: defaults.alarmType,
            sourceMode = this[Keys.SOURCE]?.let { runCatching { DataSourceMode.valueOf(it) }.getOrNull() } ?: defaults.sourceMode,
            monitoringEnabled = (this[Keys.MONITORING] ?: 0) == 1
        )
    }
}
