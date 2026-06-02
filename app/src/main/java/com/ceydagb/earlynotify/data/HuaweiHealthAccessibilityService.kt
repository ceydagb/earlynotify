package com.ceydagb.earlynotify.data

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Kaynak B: Huawei Health arayüzünde görünen anlık nabız sayısını okur.
 *
 * Heuristik bir yöntemdir. Yanlış pozitifi azaltmak için: ekranda gerçek bir nabız BİRİM etiketi
 * ("bpm", "atış/dk", "次/分", "/min" vb.) bulunmadıkça hiçbir değer YAYINLANMAZ. Birim varsa,
 * tercihen birim etiketine en yakın sayıyı, yoksa ekranda en büyük gösterilen makul sayıyı seçer.
 *
 * Her tarama, [DiagnosticsBus] üzerine ham içeriğiyle (tüm metinler, aday sayılar, seçilen değer)
 * yazılır; böylece tanılama ekranından gerçek Huawei Health arayüzü görülüp kural ayarlanabilir.
 */
class HuaweiHealthAccessibilityService : AccessibilityService() {

    // Sadece GERÇEK nabız birimi sayılan, güçlü belirteçler (kart başlıkları "kalp/heart" gibi
    // gevşek kelimeler KASITLI olarak dışarıda; onlar ana ekranda yanlış pozitife yol açıyordu).
    private val unitTokens = listOf(
        "bpm", "/min", "次/分", "atış/dk", "atim/dk", "atım/dk", "at/dk", "/dk", "vuru/dk", "min⁻¹"
    )
    private val hrRange = 30..240

    @Volatile private var lastEmittedBpm: Int = -1
    @Volatile private var lastEmittedAtMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        try {
            val texts = mutableListOf<String>()
            val candidates = mutableListOf<NumberCandidate>()
            collect(root, parentText = "", texts = texts, candidates = candidates)

            val lowerJoined = texts.joinToString(" ") { it.lowercase() }
            val unitsFound = unitTokens.filter { lowerJoined.contains(it.lowercase()) }
            val hasUnit = unitsFound.isNotEmpty()

            // Seçim: birim etiketine en yakın (nearbyText'inde birim geçen) aday; yoksa en büyük alan.
            val chosen: Int? = when {
                candidates.isEmpty() -> null
                !hasUnit -> null
                else -> {
                    val unitAdjacent = candidates.firstOrNull { c ->
                        val low = c.nearbyText.lowercase()
                        unitTokens.any { low.contains(it.lowercase()) }
                    }
                    (unitAdjacent ?: candidates.maxByOrNull { it.area })?.value
                }
            }

            val now = System.currentTimeMillis()
            DiagnosticsBus.publishAccessibility(
                AccessibilitySnapshot(
                    timestampMs = now,
                    packageName = root.packageName?.toString() ?: "?",
                    hasUnitToken = hasUnit,
                    unitTokensFound = unitsFound,
                    candidates = candidates.sortedByDescending { it.area }.take(12),
                    chosen = chosen,
                    allTexts = texts.take(60)
                )
            )

            if (chosen != null) {
                // Aynı değeri saniyede bir kereden fazla yayınlama.
                if (chosen == lastEmittedBpm && now - lastEmittedAtMs < 1000L) return
                lastEmittedBpm = chosen
                lastEmittedAtMs = now
                HeartRateBus.publish(HeartRateSample(bpm = chosen, timestampMs = now))
            }
        } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun collect(
        node: AccessibilityNodeInfo?,
        parentText: String,
        texts: MutableList<String>,
        candidates: MutableList<NumberCandidate>
    ) {
        if (node == null) return
        val own = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val selfText = listOf(own, desc).filter { it.isNotEmpty() }.joinToString(" ")

        if (own.isNotEmpty()) texts.add(own)
        if (desc.isNotEmpty() && desc != own) texts.add(desc)

        // Sayısal aday: düğüm metni ya da içerik açıklaması tek bir makul sayıysa.
        val numeric = own.toIntOrNull() ?: desc.toIntOrNull()
        if (numeric != null && numeric in hrRange) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val area = bounds.width().coerceAtLeast(0) * bounds.height().coerceAtLeast(0)
            // Yakın metin: ebeveyn metni + bu düğümün kendi metinleri (birim etiketi yakalamak için).
            val nearby = listOf(parentText, selfText).filter { it.isNotEmpty() }.joinToString(" ")
            candidates.add(NumberCandidate(value = numeric, area = area, nearbyText = nearby))
        }

        // Çocuklara, bu düğümün metnini "ebeveyn metni" olarak geçir.
        val childParentText = listOf(parentText, selfText).filter { it.isNotEmpty() }.joinToString(" ")
        for (i in 0 until node.childCount) {
            collect(node.getChild(i), childParentText, texts, candidates)
        }
    }

    override fun onInterrupt() { /* no-op */ }
}
