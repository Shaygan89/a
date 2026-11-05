package com.example.smoothcamera.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlin.math.abs

class CameraSession(
    private val context: Context,
    private val cameraManager: CameraManager,
    private val onResult: (CaptureResult) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val thread = HandlerThread("smoothcamera").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile private var camera: CameraDevice? = null
    @Volatile private var session: CameraCaptureSession? = null
    @Volatile private var previewSurface: Surface? = null
    @Volatile private var recorderSurface: Surface? = null
    @Volatile private var activeRequest: CaptureRequest.Builder? = null
    @Volatile private var recording = false

    private var currentCameraId: String? = null
    private var currentZoom = 1f
    private var requestedFps = 30
    private var stabilizationEnabled = true
    private var exposureCompensation = 0
    private var whiteBalanceMode: Int? = null
    private var tapToFocusEnabled = true

    fun setPreviewSurface(surface: Surface?) {
        handler.post {
            previewSurface = surface
            if (surface != null && camera != null && session == null) {
                runCatching { reconfigureSession(recording = recording) }
                    .onFailure { onError("Unable to attach preview: ${it.message ?: it.javaClass.simpleName}") }
            }
        }
    }

    fun open(cameraId: String, config: RecordingConfig, settings: CameraSettings) {
        handler.post {
            try {
                closeCameraInternal()
                currentCameraId = cameraId
                requestedFps = config.fps
                CameraLog.d("open cameraId=$cameraId fps=${config.fps} codec=${config.codecMime}")
                stabilizationEnabled = settings.stabilizationEnabled
                exposureCompensation = settings.exposureCompensation
                whiteBalanceMode = settings.whiteBalanceMode
                tapToFocusEnabled = settings.tapToFocusEnabled

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    onError("Camera permission is not granted")
                    return@post
                }
                cameraManager.openCamera(cameraId, stateCallback, handler)
            } catch (t: Throwable) {
                onError("Unable to open camera: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    fun updateSettings(settings: CameraSettings) {
        handler.post {
            stabilizationEnabled = settings.stabilizationEnabled
            exposureCompensation = settings.exposureCompensation
            whiteBalanceMode = settings.whiteBalanceMode
            tapToFocusEnabled = settings.tapToFocusEnabled
            rebuildRepeatingRequest()
        }
    }

    fun startRecording(recorderSurface: Surface, config: RecordingConfig, settings: CameraSettings) {
        handler.post {
            if (camera == null || previewSurface == null) {
                onError("Camera preview is not ready")
                return@post
            }
            try {
                requestedFps = config.fps
                stabilizationEnabled = settings.stabilizationEnabled
                exposureCompensation = settings.exposureCompensation
                whiteBalanceMode = settings.whiteBalanceMode
                this.recorderSurface = recorderSurface
                recording = true
                CameraLog.d("configure recording surface ${config.width}x${config.height}@${config.fps}")
                reconfigureSession(recording = true)
            } catch (t: Throwable) {
                recording = false
                this.recorderSurface = null
                onError("Unable to start recording session: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    fun stopRecording() {
        handler.post {
            recording = false
            recorderSurface = null
            try {
                reconfigureSession(recording = false)
            } catch (t: Throwable) {
                onError("Unable to restore preview: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    fun setZoomRatio(value: Float) {
        handler.post {
            val builder = activeRequest ?: return@post
            val range = runCatching {
                cameraManager.getCameraCharacteristics(currentCameraId ?: return@post)
                    .get(android.hardware.camera2.CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            }.getOrNull() ?: return@post
            val zoom = value.coerceIn(range.lower, range.upper)
            currentZoom = zoom
            CameraLog.d("zoom request=$zoom recording=$recording")
            if (android.os.Build.VERSION.SDK_INT >= 36) {
                builder.set(CaptureRequest.CONTROL_ZOOM_METHOD, CaptureRequest.CONTROL_ZOOM_METHOD_ZOOM_RATIO)
            }
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, zoom)
            applyRepeating(builder)
        }
    }

    fun focusAt(xNormalized: Float, yNormalized: Float) {
        handler.post {
            val builder = activeRequest ?: return@post
            val cameraId = currentCameraId ?: return@post
            if (!tapToFocusEnabled) return@post
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val active = characteristics.get(android.hardware.camera2.CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
                ?: return@post
            val zoomedRect = cropForZoom(active, currentZoom)
            val x = (zoomedRect.left + xNormalized.coerceIn(0f, 1f) * zoomedRect.width()).toInt()
            val y = (zoomedRect.top + yNormalized.coerceIn(0f, 1f) * zoomedRect.height()).toInt()
            val size = (minOf(zoomedRect.width(), zoomedRect.height()) * 0.10f).toInt().coerceAtLeast(32)
            val rect = Rect(
                (x - size / 2).coerceIn(zoomedRect.left, zoomedRect.right - size),
                (y - size / 2).coerceIn(zoomedRect.top, zoomedRect.bottom - size),
                (x + size / 2).coerceIn(zoomedRect.left + size, zoomedRect.right),
                (y + size / 2).coerceIn(zoomedRect.top + size, zoomedRect.bottom),
            )
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL)
            applyRepeating(builder)
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(android.hardware.camera2.params.MeteringRectangle(rect, 1000)))
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(android.hardware.camera2.params.MeteringRectangle(rect, 1000)))
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
            applyRepeating(builder)
            builder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
        }
    }

    fun close() {
        handler.post { closeCameraInternal() }
        thread.quitSafely()
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            camera = device
            if (previewSurface == null) {
                onError("Preview surface is not available")
                device.close()
                camera = null
                return
            }
            reconfigureSession(recording = false)
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
            camera = null
            session = null
            onError("Camera disconnected")
        }

        override fun onError(device: CameraDevice, error: Int) {
            device.close()
            camera = null
            session = null
            onError("Camera error $error")
        }
    }

    private fun reconfigureSession(recording: Boolean) {
        val device = camera ?: return
        val preview = previewSurface ?: return
        session?.close()
        session = null

        val surfaces = buildList {
            add(preview)
            if (recording) recorderSurface?.let(::add)
        }
        if (surfaces.size < if (recording) 2 else 1) {
            onError("Required camera output surface is unavailable")
            return
        }

        device.createCaptureSession(
            surfaces,
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(newSession: CameraCaptureSession) {
                    session = newSession
                    try {
                        val template = if (recording) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW
                        val builder = device.createCaptureRequest(template).apply {
                            addTarget(preview)
                            if (recording) recorderSurface?.let(::addTarget)
                        }
                        configureCommon(builder)
                        activeRequest = builder
                        applyRepeating(builder)
                    } catch (t: Throwable) {
                        onError("Unable to configure camera request: ${t.message ?: t.javaClass.simpleName}")
                    }
                }

                override fun onConfigureFailed(failedSession: CameraCaptureSession) {
                    onError("Camera capture session configuration failed")
                }
            },
            handler
        )
    }

    private fun configureCommon(builder: CaptureRequest.Builder) {
        val cameraId = currentCameraId ?: return
        val chars = cameraManager.getCameraCharacteristics(cameraId)
        val caps = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList().orEmpty()
        val chosenFps = chooseFpsRange(caps, requestedFps)
        chosenFps?.let { builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }

        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toSet().orEmpty()
        val preferredAf = when {
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO in afModes -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE in afModes -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            CaptureRequest.CONTROL_AF_MODE_AUTO in afModes -> CaptureRequest.CONTROL_AF_MODE_AUTO
            else -> null
        }
        preferredAf?.let { builder.set(CaptureRequest.CONTROL_AF_MODE, it) }

        val aeRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        val ev = exposureCompensation.coerceIn(aeRange?.lower ?: 0, aeRange?.upper ?: 0)
        builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev)

        whiteBalanceMode?.let { mode ->
            val supported = chars.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toSet().orEmpty()
            if (mode in supported) builder.set(CaptureRequest.CONTROL_AWB_MODE, mode)
        }

        val videoStab = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.toSet().orEmpty()
        if (stabilizationEnabled && CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON in videoStab) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON)
        } else {
            val ois = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.toSet().orEmpty()
            if (stabilizationEnabled && CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON in ois) {
                builder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON)
            }
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF)
        }

        if (android.os.Build.VERSION.SDK_INT >= 36) {
            builder.set(CaptureRequest.CONTROL_ZOOM_METHOD, CaptureRequest.CONTROL_ZOOM_METHOD_ZOOM_RATIO)
        }
        val zoomRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        if (android.os.Build.VERSION.SDK_INT >= 30 && zoomRange != null) {
            currentZoom = currentZoom.coerceIn(zoomRange.lower, zoomRange.upper)
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom)
        }
    }

    private fun applyRepeating(builder: CaptureRequest.Builder) {
        val currentSession = session ?: return
        runCatching {
            currentSession.setRepeatingRequest(builder.build(), captureCallback, handler)
        }.onFailure { onError("Capture request failed: ${it.message ?: it.javaClass.simpleName}") }
    }

    private fun rebuildRepeatingRequest() {
        val device = camera ?: return
        val preview = previewSurface ?: return
        val builder = device.createCaptureRequest(if (recording) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(preview)
            if (recording) recorderSurface?.let(::addTarget)
        }
        configureCommon(builder)
        activeRequest = builder
        applyRepeating(builder)
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult,
        ) {
            onResult(result)
        }
    }

    private fun cropForZoom(active: Rect, zoom: Float): Rect {
        if (zoom <= 1f) return Rect(active)
        val width = (active.width() / zoom).toInt().coerceAtLeast(2)
        val height = (active.height() / zoom).toInt().coerceAtLeast(2)
        val left = active.centerX() - width / 2
        val top = active.centerY() - height / 2
        return Rect(left, top, left + width, top + height)
    }

    private fun chooseFpsRange(ranges: List<android.util.Range<Int>>, fps: Int): android.util.Range<Int>? =
        ranges.minByOrNull { range ->
            when {
                fps in range.lower..range.upper -> (range.upper - fps) * 10 + (fps - range.lower)
                else -> 10_000 + abs(range.upper - fps) * 100
            }
        }

    private fun closeCameraInternal() {
        runCatching { session?.stopRepeating() }
        runCatching { session?.abortCaptures() }
        session?.close()
        session = null
        activeRequest = null
        camera?.close()
        camera = null
        recorderSurface = null
        recording = false
    }
}

private fun <T> MutableList<T>.addNotNull(value: T?) {
    if (value != null) add(value)
}
