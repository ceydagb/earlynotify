package com.ceydagb.earlynotify.data

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Kaynak B: Huawei Health arayüzünde görünen anlık nabız sayısını okur.
 *
 * Heuristik bir yöntemdir: pencerede bir nabız anahtar kelimesi (bpm, nabız, kalp, heart, 心率...)
 * varsa, makul aralıktaki (30–240) sayısal düğümlerden ekranda en büyük gösterilen değeri seçer
 * (nabız genelde büyük puntoyla gösterilir). Huawei Health arayüzü değişirse bu mantık güncellenmelidir.
 */
class HuaweiHealthAccessibilityService : AccessibilityService() {

    private val keywords = listOf("bpm", "nabız", "nabiz", "kalp", "heart", "心率", "次/分", "/min")
    private val hrRange = 30..240

    @Volatile
    private var lastEmittedBpm: Int = -1
    @Volatile
    private var lastEmittedAtMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        try {
            val texts = mutableListOf<String>()
            val numericCandidates = mutableListOf<Pair<Int, Int>>() // value, bbox-area
            collect(root, texts, numericCandidates)

            val hasKeyword = texts.any { t ->
                val low = t.lowercase()
                keywords.any { low.contains(it) }
            }
            if (!hasKeyword || numericCandidates.isEmpty()) return

            // Ekranda en büyük gösterilen makul sayıyı nabız kabul et.
            val bpm = numericCandidates.maxByOrNull { it.second }?.first ?: return

            val now = System.currentTimeMillis()
            // Aynı değeri saniyede bir kereden fazla yayınlama.
            if (bpm == lastEmittedBpm && now - lastEmittedAtMs < 1000L) return
            lastEmittedBpm = bpm
            lastEmittedAtMs = now

            HeartRateBus.publish(HeartRateSample(bpm = bpm, timestampMs = now))
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        texts: MutableList<String>,
        numbers: MutableList<Pair<Int, Int>>
    ) {
        if (node == null) return
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrEmpty()) {
            texts.add(text)
            val value = text.toIntOrNull()
            if (value != null && value in hrRange) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                val area = (bounds.width().coerceAtLeast(0)) * (bounds.height().coerceAtLeast(0))
                numbers.add(value to area)
            }
        }
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), texts, numbers)
        }
    }

    override fun onInterrupt() { /* no-op */ }
}
