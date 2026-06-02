package com.ceydagb.earlynotify.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ceydagb.earlynotify.data.AlarmType
import com.ceydagb.earlynotify.data.AppSettings
import com.ceydagb.earlynotify.data.DataSourceMode
import com.ceydagb.earlynotify.data.TriggerRule

@Composable
fun SettingsSection(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onTestAlarm: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Ayarlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // İzleme aç/kapa
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("İzleme aktif", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.monitoringEnabled,
                    onCheckedChange = { checked -> onChange { it.copy(monitoringEnabled = checked) } }
                )
            }

            // Eşik
            NumberStepper(
                label = "Eşik nabız (bpm)",
                value = settings.thresholdBpm,
                min = 40, max = 200, step = 1,
                onValue = { v -> onChange { it.copy(thresholdBpm = v) } }
            )

            // Tetik kuralı
            Text("Tetik kuralı", fontWeight = FontWeight.SemiBold)
            ChipRow(
                options = listOf(
                    TriggerRule.INSTANT to "Anında",
                    TriggerRule.CONSECUTIVE to "N ardışık",
                    TriggerRule.SUSTAINED to "Süre boyunca"
                ),
                selected = settings.rule,
                onSelect = { r -> onChange { it.copy(rule = r) } }
            )

            when (settings.rule) {
                TriggerRule.CONSECUTIVE -> NumberStepper(
                    label = "Ardışık ölçüm sayısı (N)",
                    value = settings.consecutiveCount,
                    min = 2, max = 10, step = 1,
                    onValue = { v -> onChange { it.copy(consecutiveCount = v) } }
                )
                TriggerRule.SUSTAINED -> NumberStepper(
                    label = "Kesintisiz süre (saniye)",
                    value = settings.sustainedSeconds,
                    min = 5, max = 600, step = 5,
                    onValue = { v -> onChange { it.copy(sustainedSeconds = v) } }
                )
                TriggerRule.INSTANT -> {}
            }

            // Cooldown
            NumberStepper(
                label = "Tekrar susturma (dakika)",
                value = settings.cooldownMinutes,
                min = 0, max = 60, step = 1,
                onValue = { v -> onChange { it.copy(cooldownMinutes = v) } }
            )

            // Polling (yalnız Health Connect)
            NumberStepper(
                label = "Health Connect kontrol sıklığı (saniye)",
                value = settings.pollingSeconds,
                min = 5, max = 300, step = 5,
                onValue = { v -> onChange { it.copy(pollingSeconds = v) } }
            )

            // Alarm tipi
            Text("Alarm tipi", fontWeight = FontWeight.SemiBold)
            ChipRow(
                options = listOf(
                    AlarmType.NOTIFICATION_ONLY to "Sadece bildirim",
                    AlarmType.NOTIFICATION_VIBRATE to "Titreşim",
                    AlarmType.NOTIFICATION_SOUND_VIBRATE to "Ses + titreşim"
                ),
                selected = settings.alarmType,
                onSelect = { a -> onChange { it.copy(alarmType = a) } }
            )

            // Veri kaynağı
            Text("Veri kaynağı", fontWeight = FontWeight.SemiBold)
            ChipRow(
                options = listOf(
                    DataSourceMode.BOTH to "İkisi de",
                    DataSourceMode.HEALTH_CONNECT to "Health Connect",
                    DataSourceMode.ACCESSIBILITY to "Ekran okuma"
                ),
                selected = settings.sourceMode,
                onSelect = { m -> onChange { it.copy(sourceMode = m) } }
            )

            OutlinedButton(onClick = onTestAlarm, modifier = Modifier.fillMaxWidth()) {
                Text("Test alarmı çal")
            }
        }
    }
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onValue: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(
            onClick = { onValue((value - step).coerceAtLeast(min)) },
            modifier = Modifier.size(44.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) { Text("−") }
        Text(
            value.toString(),
            modifier = Modifier.padding(horizontal = 12.dp),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        FilledTonalButton(
            onClick = { onValue((value + step).coerceAtMost(max)) },
            modifier = Modifier.size(44.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
        ) { Text("+") }
    }
}

@Composable
private fun <T> ChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}
