package com.ceydagb.earlynotify.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ceydagb.earlynotify.alarm.AlarmNotifier
import com.ceydagb.earlynotify.data.AccessibilitySnapshot
import com.ceydagb.earlynotify.data.AppSettings
import com.ceydagb.earlynotify.data.DiagnosticsBus
import com.ceydagb.earlynotify.data.HealthConnectSnapshot
import com.ceydagb.earlynotify.data.HealthConnectSource
import com.ceydagb.earlynotify.data.HuaweiHealthAccessibilityService
import com.ceydagb.earlynotify.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** İzinlerin ve sistem ayarlarının anlık durumu. */
data class PermissionsState(
    val healthConnectAvailable: Boolean = false,
    val healthConnectGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val healthConnect = HealthConnectSource(app)
    private val notifier = AlarmNotifier(app)

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _permissions = MutableStateFlow(PermissionsState())
    val permissions: StateFlow<PermissionsState> = _permissions

    init {
        notifier.ensureChannels()
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(transform) }
    }

    fun setMonitoring(enabled: Boolean) = update { it.copy(monitoringEnabled = enabled) }

    fun fireTestAlarm() {
        notifier.fireTest(settings.value.alarmType)
    }

    val healthConnectPermissions: Set<String> = HealthConnectSource.PERMISSIONS

    // Tanılama akışları
    val accessibilityDiag: StateFlow<AccessibilitySnapshot?> = DiagnosticsBus.accessibility
    val healthConnectDiag: StateFlow<HealthConnectSnapshot?> = DiagnosticsBus.healthConnect

    /** Health Connect'ten son 30 dakikayı okuyup tanılama özetini yayınlar. */
    fun runHealthConnectDiagnostic() {
        viewModelScope.launch {
            val snapshot = healthConnect.readDiagnostic(30 * 60 * 1000L)
            DiagnosticsBus.publishHealthConnect(snapshot)
        }
    }

    fun refreshPermissions() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val hcAvailable = healthConnect.isAvailable()
            val hcGranted = if (hcAvailable) runCatching { healthConnect.hasPermissions() }.getOrDefault(false) else false
            _permissions.value = PermissionsState(
                healthConnectAvailable = hcAvailable,
                healthConnectGranted = hcGranted,
                accessibilityEnabled = isAccessibilityEnabled(ctx),
                notificationsGranted = areNotificationsGranted(ctx),
                batteryOptimizationIgnored = isBatteryOptimizationIgnored(ctx)
            )
        }
    }

    private fun isAccessibilityEnabled(ctx: Context): Boolean {
        val expected = ComponentName(ctx, HuaweiHealthAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            ctx.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    private fun areNotificationsGranted(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    private fun isBatteryOptimizationIgnored(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }
}
