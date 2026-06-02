package com.ceydagb.earlynotify.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Accessibility servisinin son ekran taramasının ham içeriği (tanılama için). */
data class AccessibilitySnapshot(
    val timestampMs: Long,
    val packageName: String,
    val hasUnitToken: Boolean,
    val unitTokensFound: List<String>,
    val candidates: List<NumberCandidate>,
    val chosen: Int?,
    val allTexts: List<String>
)

data class NumberCandidate(
    val value: Int,
    val area: Int,
    val nearbyText: String
)

/** Health Connect okumasının son durumu (tanılama için). */
data class HealthConnectSnapshot(
    val timestampMs: Long,
    val available: Boolean,
    val availabilityCode: Int,
    val granted: Boolean,
    val recordCount: Int,
    val latestBpm: Int?,
    val latestAtMs: Long?,
    val error: String?
)

/**
 * Tanılama verilerini UI'ya taşıyan süreç-içi paylaşımlı durum. Accessibility servisi ve
 * Health Connect okumaları buraya son gözlemlerini yazar; tanılama ekranı bunları gösterir.
 */
object DiagnosticsBus {
    private val _accessibility = MutableStateFlow<AccessibilitySnapshot?>(null)
    val accessibility: StateFlow<AccessibilitySnapshot?> = _accessibility

    private val _healthConnect = MutableStateFlow<HealthConnectSnapshot?>(null)
    val healthConnect: StateFlow<HealthConnectSnapshot?> = _healthConnect

    fun publishAccessibility(snapshot: AccessibilitySnapshot) {
        _accessibility.value = snapshot
    }

    fun publishHealthConnect(snapshot: HealthConnectSnapshot) {
        _healthConnect.value = snapshot
    }
}
