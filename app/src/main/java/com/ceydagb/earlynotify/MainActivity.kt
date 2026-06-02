package com.ceydagb.earlynotify

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.ceydagb.earlynotify.service.MonitorService
import com.ceydagb.earlynotify.ui.MainViewModel
import com.ceydagb.earlynotify.ui.PermissionsSection
import com.ceydagb.earlynotify.ui.SettingsSection

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppContent(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }
}

@Composable
private fun AppContent(viewModel: MainViewModel) {
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    val settings by viewModel.settings.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val accessibilityDiag by viewModel.accessibilityDiag.collectAsState()
    val healthConnectDiag by viewModel.healthConnectDiag.collectAsState()

    // Health Connect izin akışı
    val healthLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { viewModel.refreshPermissions() }

    // Bildirim izni (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions() }

    // İzleme açıldığında servisi başlat / kapandığında durdur
    androidx.compose.runtime.LaunchedEffect(settings.monitoringEnabled) {
        if (settings.monitoringEnabled) MonitorService.start(activity)
        else MonitorService.stop(activity)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "EarlyNotify",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (settings.monitoringEnabled) "İzleme açık — eşik ${settings.thresholdBpm} bpm"
            else "İzleme kapalı",
            style = MaterialTheme.typography.bodyMedium
        )

        PermissionsSection(
            state = permissions,
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onRequestHealthConnect = { healthLauncher.launch(viewModel.healthConnectPermissions) },
            onOpenAccessibility = {
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onRequestBattery = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${activity.packageName}"))
                runCatching { activity.startActivity(intent) }
                    .onFailure { activity.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            },
            onInstallHealthConnect = {
                val market = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.google.android.apps.healthdata")
                )
                runCatching { activity.startActivity(market) }.onFailure {
                    activity.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                        )
                    )
                }
            }
        )

        SettingsSection(
            settings = settings,
            onChange = { transform -> viewModel.update(transform) },
            onTestAlarm = { viewModel.fireTestAlarm() }
        )

        com.ceydagb.earlynotify.ui.DiagnosticsSection(
            accessibility = accessibilityDiag,
            healthConnect = healthConnectDiag,
            onTestHealthConnect = { viewModel.runHealthConnectDiagnostic() }
        )

        Text(
            "Not: Tam anlık uyarı için Huawei Health'in nabız ekranını açık tutmak (Accessibility) " +
                "en hızlı sonucu verir. Health Connect senkronu birkaç dakika gecikebilir.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
