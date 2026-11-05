package com.example.smoothcamera.camera

import android.media.MediaCodecList

object VideoSupport {
    fun supportsVideo(mime: String, width: Int, height: Int, fps: Int): Boolean {
        val codecs = runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos }
            .getOrDefault(emptyArray())
        return codecs.any { info ->
            if (!info.isEncoder || info.supportedTypes.none { it.equals(mime, ignoreCase = true) }) return@any false
            val type = info.supportedTypes.first { it.equals(mime, ignoreCase = true) }
            val capabilities = runCatching { info.getCapabilitiesForType(type).videoCapabilities }.getOrNull()
                ?: return@any false
            runCatching {
                capabilities.isSizeSupported(width, height) &&
                    capabilities.areSizeAndRateSupported(width, height, fps.toDouble())
            }.getOrDefault(false)
        }
    }

    fun supportedVideoCodecs(width: Int, height: Int, fps: Int): List<String> =
        listOf("video/avc", "video/hevc").filter { supportsVideo(it, width, height, fps) }
}
