package com.ceydagb.earlynotify.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/**
 * Kaynak A: Health Connect'ten nabız ölçümlerini okur.
 *
 * Huawei Health, ölçümleri Health Connect'e senkronlar; bu sınıf belirli bir andan sonraki
 * [HeartRateRecord] kayıtlarını çekip [HeartRateSample] listesine çevirir.
 */
class HealthConnectSource(private val context: Context) {

    companion object {
        val PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class)
        )
    }

    private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /** Health Connect bu cihazda kullanılabilir mi (kurulu/güncel)? */
    fun availability(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = availability() == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(PERMISSIONS)

    /**
     * [sinceMs]'den sonraki nabız ölçümlerini zamana göre artan sırada döndürür.
     * Health Connect erişilemezse boş liste döner (çağıran tarafın çökmemesi için).
     */
    suspend fun readSince(sinceMs: Long): List<HeartRateSample> {
        if (!isAvailable()) return emptyList()
        return try {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(Instant.ofEpochMilli(sinceMs))
                )
            )
            response.records
                .flatMap { record -> record.samples }
                .map { sample ->
                    HeartRateSample(
                        bpm = sample.beatsPerMinute.toInt(),
                        timestampMs = sample.time.toEpochMilli()
                    )
                }
                .sortedBy { it.timestampMs }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
