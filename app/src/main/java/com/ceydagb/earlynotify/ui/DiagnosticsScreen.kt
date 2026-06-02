package com.ceydagb.earlynotify.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ceydagb.earlynotify.data.AccessibilitySnapshot
import com.ceydagb.earlynotify.data.HealthConnectSnapshot

@Composable
fun DiagnosticsSection(
    accessibility: AccessibilitySnapshot?,
    healthConnect: HealthConnectSnapshot?,
    onTestHealthConnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tanılama", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Sorun gidermek için. Huawei Health'in CANLI NABIZ ekranını aç, sonra buraya dön.",
                style = MaterialTheme.typography.bodySmall
            )

            Divider()

            // --- Health Connect ---
            Text("Health Connect", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = onTestHealthConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Health Connect'i test et (son 30 dk)")
            }
            if (healthConnect == null) {
                Mono("Henüz test edilmedi.")
            } else {
                Mono(buildString {
                    appendLine("kullanılabilir: ${healthConnect.available} (kod ${healthConnect.availabilityCode})")
                    appendLine("izin: ${healthConnect.granted}")
                    appendLine("son 30 dk kayıt sayısı: ${healthConnect.recordCount}")
                    if (healthConnect.latestBpm != null) {
                        appendLine("en yeni: ${healthConnect.latestBpm} bpm (${ageText(healthConnect.latestAtMs)})")
                    } else {
                        appendLine("en yeni: yok")
                    }
                    if (healthConnect.error != null) appendLine("hata: ${healthConnect.error}")
                })
            }

            Divider()

            // --- Accessibility ---
            Text("Ekran okuma (Accessibility)", fontWeight = FontWeight.SemiBold)
            if (accessibility == null) {
                Mono("Henüz tarama yok. Huawei Health'i aç ve nabız ekranına git.")
            } else {
                Mono(buildString {
                    appendLine("paket: ${accessibility.packageName}")
                    appendLine("son tarama: ${ageText(accessibility.timestampMs)}")
                    appendLine("birim etiketi bulundu: ${accessibility.hasUnitToken} ${accessibility.unitTokensFound}")
                    appendLine("SEÇİLEN nabız: ${accessibility.chosen ?: "yok"}")
                    appendLine("")
                    appendLine("aday sayılar (değer @alan | yakın metin):")
                    if (accessibility.candidates.isEmpty()) appendLine("  (yok)")
                    accessibility.candidates.forEach { c ->
                        appendLine("  ${c.value} @${c.area} | ${c.nearbyText.take(40)}")
                    }
                    appendLine("")
                    appendLine("ekrandaki metinler:")
                    accessibility.allTexts.forEach { appendLine("  $it") }
                })
            }
        }
    }
}

@Composable
private fun Mono(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace
    )
}

private fun ageText(atMs: Long?): String {
    if (atMs == null) return "?"
    val secs = ((System.currentTimeMillis() - atMs) / 1000L).coerceAtLeast(0)
    return when {
        secs < 60 -> "$secs sn önce"
        secs < 3600 -> "${secs / 60} dk önce"
        else -> "${secs / 3600} sa önce"
    }
}
