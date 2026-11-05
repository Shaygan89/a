package com.example.smoothcamera.camera

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import java.io.Closeable
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

class VideoRecorder(private val context: Context) : Closeable {
    private var mediaRecorder: MediaRecorder? = null
    private var outputUri: Uri? = null
    private var outputPfd: android.os.ParcelFileDescriptor? = null

    fun prepare(
        config: RecordingConfig,
        orientationDegrees: Int,
        sensorOrientation: Int,
        facing: LensFacing,
    ): Uri {
        check(mediaRecorder == null) { "Recorder is already prepared" }

        val resolver = context.contentResolver
        val name = "SmoothLens_${timestamp()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/SmoothCamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore refused to create a video destination")
        outputUri = uri

        try {
            val pfd = resolver.openFileDescriptor(uri, "w")
                ?: error("Could not open output file")
            outputPfd = pfd

            val recorder = MediaRecorder(context)
            mediaRecorder = recorder

            if (config.recordAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            if (config.recordAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(48_000)
            }
            recorder.setVideoEncoder(
                if (config.codecMime == "video/hevc") MediaRecorder.VideoEncoder.HEVC
                else MediaRecorder.VideoEncoder.H264
            )
            recorder.setVideoSize(config.width, config.height)
            recorder.setVideoFrameRate(config.fps)
            recorder.setVideoEncodingBitRate(config.bitrate ?: defaultBitrate(config.width, config.height, config.fps))
            recorder.setOrientationHint(computeOrientation(sensorOrientation, orientationDegrees, facing))
            recorder.setOutputFile(pfd.fileDescriptor)
            recorder.prepare()

            return uri
        } catch (t: Throwable) {
            cleanupMediaStore(resolver, uri)
            close()
            throw t
        }
    }

    fun surface(): Surface = mediaRecorder?.surface
        ?: error("Recorder is not prepared")

    fun start() {
        mediaRecorder?.start() ?: error("Recorder is not prepared")
    }

    fun stop() {
        val recorder = mediaRecorder ?: return
        try {
            recorder.stop()
            markReady(context.contentResolver, outputUri)
        } catch (t: RuntimeException) {
            cleanupMediaStore(context.contentResolver, outputUri)
            throw t
        } finally {
            closeRecorderOnly()
            outputPfd?.close()
            outputPfd = null
        }
    }

    fun uri(): Uri? = outputUri

    override fun close() {
        closeRecorderOnly()
        outputPfd?.close()
        outputPfd = null
    }

    private fun closeRecorderOnly() {
        mediaRecorder?.reset()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun markReady(resolver: ContentResolver, uri: Uri?) {
        if (uri == null || Build.VERSION.SDK_INT < 29) return
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
    }

    private fun cleanupMediaStore(resolver: ContentResolver, uri: Uri?) {
        if (uri != null) resolver.delete(uri, null, null)
        outputUri = null
    }

    private fun timestamp(): String =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))

    private fun defaultBitrate(width: Int, height: Int, fps: Int): Int {
        val pixels = width.toLong() * height
        val base = when {
            pixels >= 3840L * 2160L -> 45_000_000
            pixels >= 1920L * 1080L -> 16_000_000
            else -> 8_000_000
        }
        return max(4_000_000, (base * (fps.coerceIn(24, 60) / 30f)).toInt())
    }

    private fun computeOrientation(sensorOrientation: Int, displayRotationDegrees: Int, facing: LensFacing): Int {
        return if (facing == LensFacing.FRONT) {
            (sensorOrientation + displayRotationDegrees) % 360
        } else {
            (sensorOrientation - displayRotationDegrees + 360) % 360
        }
    }
}
