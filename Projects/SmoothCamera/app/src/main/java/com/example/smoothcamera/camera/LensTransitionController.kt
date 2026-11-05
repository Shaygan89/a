package com.example.smoothcamera.camera

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class LensTransitionController(private val scope: CoroutineScope) {
    private var job: Job? = null

    fun transition(
        startZoom: Float,
        targetZoom: Float,
        settings: TransitionSettings,
        setZoom: (Float) -> Unit,
        onProgress: (Float) -> Unit,
        onFinished: () -> Unit,
    ) {
        job?.cancel()
        if (!settings.enabled || settings.durationMs <= 0L || startZoom == targetZoom) {
            setZoom(targetZoom)
            onProgress(1f)
            onFinished()
            return
        }

        job = scope.launch {
            val started = System.nanoTime()
            val durationNs = settings.durationMs.coerceAtLeast(1L) * 1_000_000L
            var lastApplied = Float.NaN

            while (isActive) {
                val elapsed = System.nanoTime() - started
                val raw = (elapsed.toDouble() / durationNs.toDouble()).toFloat().coerceIn(0f, 1f)
                val eased = when (settings.curve) {
                    TransitionCurve.LINEAR -> raw
                    TransitionCurve.EASE_IN_OUT -> 0.5f - 0.5f * cos(Math.PI * raw).toFloat()
                    TransitionCurve.CINEMATIC -> {
                        val smooth = 0.5f - 0.5f * cos(Math.PI * raw).toFloat()
                        (smooth.pow(1.15f)).coerceIn(0f, 1f)
                    }
                }
                val zoom = startZoom + (targetZoom - startZoom) * eased
                if (zoom != lastApplied) {
                    lastApplied = zoom
                    setZoom(zoom)
                }
                onProgress(raw)
                if (raw >= 1f) break
                delay(16L)
            }
            setZoom(targetZoom)
            onProgress(1f)
            onFinished()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
