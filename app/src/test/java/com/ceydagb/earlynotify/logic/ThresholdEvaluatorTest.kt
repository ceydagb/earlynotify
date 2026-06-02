package com.ceydagb.earlynotify.logic

import com.ceydagb.earlynotify.data.AppSettings
import com.ceydagb.earlynotify.data.HeartRateSample
import com.ceydagb.earlynotify.data.TriggerRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThresholdEvaluatorTest {

    private val evaluator = ThresholdEvaluator()

    private fun samples(vararg pairs: Pair<Int, Long>): List<HeartRateSample> =
        pairs.map { HeartRateSample(bpm = it.first, timestampMs = it.second) }

    // --- INSTANT ---

    @Test
    fun instant_fires_when_latest_at_or_above_threshold() {
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.INSTANT)
        val data = samples(70 to 1000L, 81 to 2000L)
        assertTrue(evaluator.shouldAlarm(data, s, nowMs = 2000L, lastAlarmMs = 0L))
    }

    @Test
    fun instant_does_not_fire_below_threshold() {
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.INSTANT)
        val data = samples(79 to 2000L)
        assertFalse(evaluator.shouldAlarm(data, s, nowMs = 2000L, lastAlarmMs = 0L))
    }

    @Test
    fun instant_fires_exactly_at_threshold() {
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.INSTANT)
        val data = samples(80 to 2000L)
        assertTrue(evaluator.shouldAlarm(data, s, nowMs = 2000L, lastAlarmMs = 0L))
    }

    // --- CONSECUTIVE ---

    @Test
    fun consecutive_fires_when_last_n_all_above() {
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.CONSECUTIVE, consecutiveCount = 3)
        val data = samples(60 to 1000L, 85 to 2000L, 90 to 3000L, 82 to 4000L)
        assertTrue(evaluator.shouldAlarm(data, s, nowMs = 4000L, lastAlarmMs = 0L))
    }

    @Test
    fun consecutive_does_not_fire_when_one_below() {
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.CONSECUTIVE, consecutiveCount = 3)
        val data = samples(85 to 1000L, 70 to 2000L, 90 to 3000L)
        assertFalse(evaluator.shouldAlarm(data, s, nowMs = 3000L, lastAlarmMs = 0L))
    }

    @Test
    fun consecutive_does_not_fire_with_too_few_samples() {
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.CONSECUTIVE, consecutiveCount = 3)
        val data = samples(85 to 1000L, 90 to 2000L)
        assertFalse(evaluator.shouldAlarm(data, s, nowMs = 2000L, lastAlarmMs = 0L))
    }

    // --- SUSTAINED ---

    @Test
    fun sustained_fires_after_continuous_duration() {
        // Eşik üstünde 30 sn kesintisiz kalmış: 100s..135s = 35 sn
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.SUSTAINED, sustainedSeconds = 30)
        val data = samples(70 to 90_000L, 85 to 100_000L, 90 to 120_000L, 82 to 135_000L)
        assertTrue(evaluator.shouldAlarm(data, s, nowMs = 135_000L, lastAlarmMs = 0L))
    }

    @Test
    fun sustained_does_not_fire_before_duration() {
        // Eşik üstünde yalnızca 100s..115s = 15 sn (30 sn gerekli)
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.SUSTAINED, sustainedSeconds = 30)
        val data = samples(70 to 90_000L, 85 to 100_000L, 90 to 115_000L)
        assertFalse(evaluator.shouldAlarm(data, s, nowMs = 115_000L, lastAlarmMs = 0L))
    }

    @Test
    fun sustained_run_resets_after_dip_below_threshold() {
        // 100s'de yükseldi, 120s'de düştü, 125s'de tekrar yükseldi: seri yalnızca 125s'den
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.SUSTAINED, sustainedSeconds = 30)
        val data = samples(85 to 100_000L, 70 to 120_000L, 90 to 125_000L, 88 to 130_000L)
        assertFalse(evaluator.shouldAlarm(data, s, nowMs = 130_000L, lastAlarmMs = 0L))
    }

    // --- COOLDOWN ---

    @Test
    fun cooldown_blocks_alarm_within_window() {
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.INSTANT, cooldownMinutes = 5)
        val data = samples(95 to 200_000L)
        // Son alarm 2 dk önce -> 5 dk dolmadı -> susturulmalı
        val lastAlarm = 200_000L - 2 * 60_000L
        assertFalse(evaluator.shouldAlarm(data, s, nowMs = 200_000L, lastAlarmMs = lastAlarm))
    }

    @Test
    fun cooldown_allows_alarm_after_window() {
        val s = AppSettings(thresholdBpm = 80, rule = TriggerRule.INSTANT, cooldownMinutes = 5)
        val data = samples(95 to 600_000L)
        val lastAlarm = 600_000L - 6 * 60_000L
        assertTrue(evaluator.shouldAlarm(data, s, nowMs = 600_000L, lastAlarmMs = lastAlarm))
    }

    // --- Empty ---

    @Test
    fun empty_samples_never_fire() {
        val s = AppSettings()
        assertFalse(evaluator.shouldAlarm(emptyList(), s, nowMs = 1000L, lastAlarmMs = 0L))
    }
}
