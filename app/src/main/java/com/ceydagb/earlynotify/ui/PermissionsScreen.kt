package com.ceydagb.earlynotify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsSection(
    state: PermissionsState,
    onRequestNotifications: () -> Unit,
    onRequestHealthConnect: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onRequestBattery: () -> Unit,
    onInstallHealthConnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Kurulum & İzinler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            PermissionRow(
                label = "Bildirim izni",
                ok = state.notificationsGranted,
                actionLabel = "İzin ver",
                onAction = onRequestNotifications
            )

            if (state.healthConnectAvailable) {
                PermissionRow(
                    label = "Health Connect — nabız okuma",
                    ok = state.healthConnectGranted,
                    actionLabel = "İzin ver",
                    onAction = onRequestHealthConnect
                )
            } else {
                PermissionRow(
                    label = "Health Connect yüklü değil",
                    ok = false,
                    actionLabel = "Yükle / Aç",
                    onAction = onInstallHealthConnect
                )
            }

            PermissionRow(
                label = "Accessibility (Huawei Health ekran okuma)",
                ok = state.accessibilityEnabled,
                actionLabel = "Aç",
                onAction = onOpenAccessibility
            )

            PermissionRow(
                label = "Pil optimizasyonu muafiyeti",
                ok = state.batteryOptimizationIgnored,
                actionLabel = "Ayarla",
                onAction = onRequestBattery
            )
        }
    }
}

@Composable
private fun PermissionRow(
    label: String,
    ok: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) Color(0xFF2E7D32) else Color(0xFFEF6C00)
        )
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (!ok) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
