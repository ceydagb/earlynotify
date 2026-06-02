package com.ceydagb.earlynotify.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Accessibility servisi ile izleme servisi aynı uygulama sürecinde çalıştığı için, ekrandan
 * okunan anlık nabız ölçümlerini bu süreç-içi paylaşımlı akış üzerinden iletir.
 */
object HeartRateBus {
    private val _samples = MutableSharedFlow<HeartRateSample>(
        replay = 1,
        extraBufferCapacity = 16
    )
    val samples: SharedFlow<HeartRateSample> = _samples

    fun publish(sample: HeartRateSample) {
        _samples.tryEmit(sample)
    }
}
