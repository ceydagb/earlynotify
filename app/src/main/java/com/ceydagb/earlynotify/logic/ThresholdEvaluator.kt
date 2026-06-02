package com.ceydagb.earlynotify.logic

import com.ceydagb.earlynotify.data.AppSettings
import com.ceydagb.earlynotify.data.HeartRateSample
import com.ceydagb.earlynotify.data.TriggerRule

/**
 * Nabız ölçümlerini ve ayarları girdi alıp alarmın çalıp çalmaması gerektiğine karar verir.
 *
 * Saf (yan etkisiz) bir bileşendir: aynı girdiyle her zaman aynı çıktıyı verir, bu yüzden
 * kolayca birim test edilebilir. Servis tarafı zamanlama/IO ile ilgilenir, bu sınıf yalnızca kural.
 */
class ThresholdEvaluator {

    /**
     * @param samples zamana göre ARTAN sırada (en eski önce, en yeni sonda) ölçümler.
     * @param settings güncel kullanıcı ayarları.
     * @param nowMs şu anki zaman (ms).
     * @param lastAlarmMs en son alarmın zamanı (hiç alarm yoksa 0).
     * @return alarmın şimdi çalması gerekiyorsa true.
     */
    fun shouldAlarm(
        samples: List<HeartRateSample>,
        settings: AppSettings,
        nowMs: Long,
        lastAlarmMs: Long
    ): Boolean {
        if (samples.isEmpty()) return false

        // Tekrar susturma (cooldown): son alarmdan bu yana yeterli süre geçmediyse çalma.
        if (lastAlarmMs > 0 && nowMs - lastAlarmMs < settings.cooldownMinutes * 60_000L) {
            return false
        }

        val threshold = settings.thresholdBpm
        val latest = samples.last()

        return when (settings.rule) {
            TriggerRule.INSTANT ->
                latest.bpm >= threshold

            TriggerRule.CONSECUTIVE -> {
                val n = settings.consecutiveCount.coerceAtLeast(1)
                if (samples.size < n) false
                else samples.takeLast(n).all { it.bpm >= threshold }
            }

            TriggerRule.SUSTAINED -> {
                if (latest.bpm < threshold) return false
                // En yeni ölçümde biten, hepsi eşik üstünde olan kesintisiz seriyi bul;
                // bu serinin süresi istenen saniyeyi geçiyorsa tetikle.
                var runStartMs = latest.timestampMs
                for (i in samples.indices.reversed()) {
                    if (samples[i].bpm >= threshold) {
                        runStartMs = samples[i].timestampMs
                    } else {
                        break
                    }
                }
                (latest.timestampMs - runStartMs) >= settings.sustainedSeconds * 1000L
            }
        }
    }
}
