package com.example.smoothlenscamera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.MediaRecorder
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val hasAudioPermission: Boolean = false,
    val isCameraReady: Boolean = false,
    val isRecording: Boolean = false,
    val isStartingRecording: Boolean = false,
    val elapsedMillis: Long = 0L,
    val supportedZooms: List<Float> = listOf(1.0f),
    val effectiveZoom: Float = 1.0f,
    val message: String? = null
)

private data class CameraConfig(
    val id: String,
    val characteristics: CameraCharacteristics,
    val previewSize: Size,
    val recordingSizes: List<Size>,
    val minZoom: Float,
    val maxZoom: Float,
    val usesZoomRatio: Boolean,
    val activeArray: Rect,
    val sensorOrientation: Int,
    val isLogicalMultiCamera: Boolean,
    val physicalCameraIds: Set<String>,
    val focalLengths: FloatArray
) {
    fun zoomStops(): List<Float> = buildList {
        if (usesZoomRatio && minZoom <= 0.5001f) add(0.5f)
        add(1.0f)
        if (maxZoom >= 2.0f) add(2.0f)
    }
}

class CameraController(context: Context) {
    companion object {
        private const val TAG = "SmoothLensCamera"
        private const val ZOOM_ANIMATION_MS = 320L
        private const val ZOOM_FRAME_DELAY_MS = 24L
    }

    private val appContext = context.applicationContext
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val contentResolver = context.contentResolver
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cameraThread = HandlerThread("SmoothLensCameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    var uiState by mutableStateOf(CameraUiState())
        private set

    @Volatile private var textureView: TextureView? = null
    @Volatile private var hasCameraPermission = false
    @Volatile private var hasAudioPermission = false
    @Volatile private var resumed = false

    private var cameraConfig: CameraConfig? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var recordingSurface: Surface? = null
    private var repeatingBuilder: CaptureRequest.Builder? = null
    private var currentRequestedZoom = 1.0f
    private var lastActivePhysicalId: String? = null
    private var lastPostedEffectiveZoom = 1.0f
    private var zoomAnimation: Runnable? = null
    private var recordingTimer: Runnable? = null
    private var recordingStartElapsed = 0L

    private var mediaRecorder: MediaRecorder? = null
    private var outputDescriptor: ParcelFileDescriptor? = null
    private var pendingOutputUri: Uri? = null
    private var recordingHasStarted = false
    private var currentRecordingSizeIndex = 0
    private var recordingAudioEnabled = false

    fun attach(view: TextureView) {
        if (textureView === view) return
        textureView = view
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: android.graphics.SurfaceTexture,
                width: Int,
                height: Int
            ) {
                cameraHandler.post { openCameraIfReady() }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: android.graphics.SurfaceTexture,
                width: Int,
                height: Int
            ) {
                updatePreviewTransform()
            }

            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                cameraHandler.post { closeCameraResources() }
                return true
            }

            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) = Unit
        }
        if (view.isAvailable) cameraHandler.post { openCameraIfReady() }
    }

    fun setPermissions(cameraGranted: Boolean, audioGranted: Boolean) {
        hasCameraPermission = cameraGranted
        hasAudioPermission = audioGranted
        uiState = uiState.copy(
            hasCameraPermission = cameraGranted,
            hasAudioPermission = audioGranted,
            message = if (cameraGranted) uiState.message else null
        )
        cameraHandler.post {
            if (cameraGranted) {
                openCameraIfReady()
            } else {
                closeCameraResources()
            }
        }
    }

    fun onResume() {
        cameraHandler.post {
            resumed = true
            openCameraIfReady()
        }
    }

    fun onPause() {
        cameraHandler.post {
            resumed = false
            closeCameraResources()
        }
    }

    fun release() {
        cameraHandler.post {
            resumed = false
            closeCameraResources()
            cameraThread.quitSafely()
        }
    }

    fun zoomTo(target: Float) {
        cameraHandler.post {
            val config = cameraConfig ?: return@post
            if (captureSession == null || repeatingBuilder == null) return@post
            val clamped = target.coerceIn(config.minZoom, config.maxZoom)
            animateZoom(clamped)
        }
    }

    fun startRecording() {
        cameraHandler.post {
            if (!resumed || !hasCameraPermission || cameraDevice == null || captureSession == null) {
                postUi { uiState = uiState.copy(message = "Camera is not ready yet") }
                return@post
            }
            if (recordingHasStarted || mediaRecorder != null || uiState.isStartingRecording) return@post
            recordingAudioEnabled = hasAudioPermission && ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            postUi {
                uiState = uiState.copy(isStartingRecording = true, message = null, elapsedMillis = 0L)
            }
            currentRecordingSizeIndex = 0
            prepareRecorderAndSession(currentRecordingSizeIndex)
        }
    }

    fun stopRecording() {
        cameraHandler.post {
            if (mediaRecorder == null && !recordingHasStarted) return@post
            stopRecordingInternal(reopenPreview = true)
        }
    }

    private fun openCameraIfReady() {
        if (!resumed || !hasCameraPermission || cameraDevice != null) return
        val view = textureView ?: return
        if (!view.isAvailable || view.surfaceTexture == null) return
        try {
            val config = selectCameraConfig() ?: run {
                postUi {
                    uiState = uiState.copy(isCameraReady = false, message = "No usable rear camera was found")
                }
                return
            }
            cameraConfig = config
            currentRequestedZoom = currentRequestedZoom.coerceIn(config.minZoom, config.maxZoom)
            postUi {
                uiState = uiState.copy(
                    supportedZooms = config.zoomStops(),
                    effectiveZoom = currentRequestedZoom,
                    message = null
                )
            }
            Log.i(TAG, "Opening rear camera id=${config.id}; logical=${config.isLogicalMultiCamera}; " +
                "physicalIds=${config.physicalCameraIds}; focalLengths=${config.focalLengths.contentToString()}; " +
                "zoomRange=${config.minZoom}..${config.maxZoom}; zoomRatioApi=${config.usesZoomRatio}; " +
                "recordSizes=${config.recordingSizes}")
            cameraManager.openCamera(config.id, cameraStateCallback, cameraHandler)
        } catch (e: SecurityException) {
            postUi { uiState = uiState.copy(isCameraReady = false, message = "Camera permission is required") }
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Unable to open camera", e)
            postUi { uiState = uiState.copy(isCameraReady = false, message = "Unable to open camera") }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unexpected camera setup error", e)
            postUi { uiState = uiState.copy(isCameraReady = false, message = "Camera setup failed") }
        }
    }

    private fun selectCameraConfig(): CameraConfig? {
        data class Candidate(
            val id: String,
            val characteristics: CameraCharacteristics,
            val logical: Boolean,
            val minZoom: Float,
            val maxZoom: Float,
            val ratioSupported: Boolean,
            val physicalIds: Set<String>,
            val focalLengths: FloatArray,
            val score: Float
        )

        val candidates = cameraManager.cameraIdList.mapNotNull { id ->
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                if (chars.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) {
                    return@mapNotNull null
                }
                val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return@mapNotNull null
                val previewSizes = map.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList().orEmpty()
                val recordingSizes = map.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
                if (previewSizes.isEmpty() || recordingSizes.isEmpty()) return@mapNotNull null

                val capabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
                val logical = capabilities.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA)
                val physicalIds = chars.physicalCameraIds
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS) ?: floatArrayOf()
                val ratioRange = chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
                val ratioSupported = ratioRange != null
                val minZoom = ratioRange?.lower ?: 1.0f
                val maxZoom = ratioRange?.upper
                    ?: (chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1.0f).coerceAtLeast(1.0f)
                val score = (if (logical) 1000.0f else 0.0f) +
                    (if (ratioSupported && minZoom <= 0.51f) 150.0f else 0.0f) +
                    (if (maxZoom >= 2.0f) 20.0f else 0.0f) +
                    (if (physicalIds.isNotEmpty()) 10.0f else 0.0f)

                Log.i(TAG, "Rear candidate id=$id logical=$logical physicalIds=$physicalIds " +
                    "focalLengths=${focalLengths.contentToString()} zoomRange=${ratioRange ?: "crop fallback"} " +
                    "maxDigital=${chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)} " +
                    "previewSizes=${previewSizes.size} recorderSizes=${recordingSizes.size}")

                Candidate(id, chars, logical, minZoom, maxZoom, ratioSupported, physicalIds, focalLengths, score)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping camera id=$id", e)
                null
            }
        }

        val chosen = candidates.maxByOrNull { it.score } ?: return null
        val chars = chosen.characteristics
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return null
        val recordingSizes = map.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
        val previewSizes = map.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList().orEmpty()
        val preferredRecordingSizes = orderRecordingSizes(recordingSizes)
        val preferredRecording = preferredRecordingSizes.firstOrNull() ?: return null
        val previewSize = choosePreviewSize(previewSizes, preferredRecording)
        val activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 1, 1)
        val sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90

        return CameraConfig(
            id = chosen.id,
            characteristics = chars,
            previewSize = previewSize,
            recordingSizes = preferredRecordingSizes,
            minZoom = chosen.minZoom,
            maxZoom = chosen.maxZoom,
            usesZoomRatio = chosen.ratioSupported,
            activeArray = activeArray,
            sensorOrientation = sensorOrientation,
            isLogicalMultiCamera = chosen.logical,
            physicalCameraIds = chosen.physicalIds,
            focalLengths = chosen.focalLengths
        )
    }

    private fun orderRecordingSizes(sizes: List<Size>): List<Size> {
        fun find(width: Int, height: Int): Size? = sizes.firstOrNull {
            (it.width == width && it.height == height) || (it.width == height && it.height == width)
        }
        val preferred = mutableListOf<Size>()
        find(1920, 1080)?.let(preferred::add)
        find(1280, 720)?.let { if (it !in preferred) preferred.add(it) }
        val atMost1080 = sizes.filter { it.width <= 1920 && it.height <= 1080 }
            .sortedByDescending { it.width.toLong() * it.height.toLong() }
        atMost1080.forEach { if (it !in preferred) preferred.add(it) }
        sizes.sortedBy { it.width.toLong() * it.height.toLong() }
            .forEach { if (it !in preferred) preferred.add(it) }
        return preferred
    }

    private fun choosePreviewSize(sizes: List<Size>, recordingSize: Size): Size {
        val targetRatio = recordingSize.width.toFloat() / recordingSize.height.toFloat()
        val similarRatio = sizes.filter {
            abs(it.width.toFloat() / it.height.toFloat() - targetRatio) < 0.03f
        }
        val candidates = if (similarRatio.isNotEmpty()) similarRatio else sizes
        val idealArea = 1280L * 720L
        return candidates.minByOrNull { size ->
            abs(size.width.toLong() * size.height.toLong() - idealArea).toDouble() +
                abs(size.width.toFloat() / size.height.toFloat() - targetRatio) * 500_000.0
        } ?: sizes.first()
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (!resumed || !hasCameraPermission) {
                camera.close()
                return
            }
            cameraDevice = camera
            createPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.w(TAG, "Camera disconnected")
            camera.close()
            if (cameraDevice === camera) cameraDevice = null
            closeSessionAndSurfaces()
            postUi {
                uiState = uiState.copy(isCameraReady = false, isRecording = false, message = "Camera disconnected")
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error=$error")
            camera.close()
            if (cameraDevice === camera) cameraDevice = null
            closeSessionAndSurfaces()
            postUi {
                uiState = uiState.copy(isCameraReady = false, isRecording = false, message = "Camera error ($error)")
            }
        }
    }

    private fun createPreviewSession() {
        val device = cameraDevice ?: return
        val surface = ensurePreviewSurface() ?: return
        if (captureSession != null) {
            try { captureSession?.close() } catch (_: Exception) { }
            captureSession = null
        }
        try {
            device.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!resumed || cameraDevice !== device) {
                            session.close()
                            return
                        }
                        captureSession = session
                        try {
                            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            builder.addTarget(surface)
                            configureAutomaticControls(builder)
                            applyZoomToBuilder(builder, currentRequestedZoom)
                            repeatingBuilder = builder
                            session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
                            postUi {
                                uiState = uiState.copy(
                                    isCameraReady = true,
                                    isRecording = false,
                                    isStartingRecording = false,
                                    supportedZooms = cameraConfig?.zoomStops() ?: listOf(1.0f),
                                    message = null
                                )
                            }
                            updatePreviewTransform()
                        } catch (e: Exception) {
                            Log.e(TAG, "Could not start preview request", e)
                            session.close()
                            captureSession = null
                            postUi { uiState = uiState.copy(isCameraReady = false, message = "Preview could not start") }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        session.close()
                        postUi { uiState = uiState.copy(isCameraReady = false, message = "Camera preview configuration failed") }
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Unable to configure preview", e)
            postUi { uiState = uiState.copy(isCameraReady = false, message = "Unable to configure camera preview") }
        }
    }

    private fun ensurePreviewSurface(): Surface? {
        previewSurface?.let { return it }
        val view = textureView ?: return null
        if (!view.isAvailable) return null
        val texture = view.surfaceTexture ?: return null
        val config = cameraConfig ?: return null
        texture.setDefaultBufferSize(config.previewSize.width, config.previewSize.height)
        return Surface(texture).also { previewSurface = it }
    }

    private fun configureAutomaticControls(builder: CaptureRequest.Builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
        val chars = cameraConfig?.characteristics ?: return
        val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toSet().orEmpty()
        val afMode = when {
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            afModes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) -> CaptureRequest.CONTROL_AF_MODE_AUTO
            else -> CaptureRequest.CONTROL_AF_MODE_OFF
        }
        builder.set(CaptureRequest.CONTROL_AF_MODE, afMode)

        val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES).orEmpty()
        val fps30 = fpsRanges.firstOrNull { it.lower == 30 && it.upper == 30 }
            ?: fpsRanges.firstOrNull { it.lower <= 30 && it.upper >= 30 }
        if (fps30 != null) builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fps30)
    }

    private fun applyZoomToBuilder(builder: CaptureRequest.Builder, ratio: Float) {
        val config = cameraConfig ?: return
        val safeRatio = ratio.coerceIn(config.minZoom, config.maxZoom)
        if (config.usesZoomRatio) {
            builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, safeRatio)
        } else {
            val array = config.activeArray
            val cropWidth = (array.width() / safeRatio).toInt().coerceAtLeast(1)
            val cropHeight = (array.height() / safeRatio).toInt().coerceAtLeast(1)
            val left = array.centerX() - cropWidth / 2
            val top = array.centerY() - cropHeight / 2
            builder.set(
                CaptureRequest.SCALER_CROP_REGION,
                Rect(left, top, left + cropWidth, top + cropHeight)
            )
        }
    }

    private fun animateZoom(target: Float) {
        zoomAnimation?.let(cameraHandler::removeCallbacks)
        val from = currentRequestedZoom.coerceAtLeast(0.01f)
        val startedAt = SystemClock.elapsedRealtime()
        val animation = object : Runnable {
            override fun run() {
                val config = cameraConfig ?: return
                val session = captureSession ?: return
                val builder = repeatingBuilder ?: return
                val raw = ((SystemClock.elapsedRealtime() - startedAt).toFloat() / ZOOM_ANIMATION_MS).coerceIn(0f, 1f)
                val eased = raw * raw * (3f - 2f * raw)
                val value = exp(ln(from) + (ln(target) - ln(from)) * eased)
                    .coerceIn(config.minZoom, config.maxZoom)
                try {
                    applyZoomToBuilder(builder, value)
                    session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
                    currentRequestedZoom = value
                    if (raw < 1f) {
                        cameraHandler.postDelayed(this, ZOOM_FRAME_DELAY_MS)
                    } else {
                        currentRequestedZoom = target
                        applyZoomToBuilder(builder, target)
                        session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
                        zoomAnimation = null
                    }
                } catch (e: CameraAccessException) {
                    Log.e(TAG, "Zoom update failed", e)
                    postUi { uiState = uiState.copy(message = "Zoom update failed") }
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "Zoom request ignored because session is no longer active", e)
                }
            }
        }
        zoomAnimation = animation
        cameraHandler.post(animation)
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            val config = cameraConfig ?: return
            val reportedRatio = result.get(CaptureResult.CONTROL_ZOOM_RATIO)
            val crop = result.get(CaptureResult.SCALER_CROP_REGION)
            val effective = when {
                reportedRatio != null -> reportedRatio
                crop != null && crop.width() > 0 -> config.activeArray.width().toFloat() / crop.width().toFloat()
                else -> currentRequestedZoom
            }
            val activePhysicalId = if (config.isLogicalMultiCamera) {
                result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
            } else null
            if (activePhysicalId != null && activePhysicalId != lastActivePhysicalId) {
                Log.i(TAG, "Active physical camera changed: $lastActivePhysicalId -> $activePhysicalId")
                lastActivePhysicalId = activePhysicalId
            }
            if (abs(effective - lastPostedEffectiveZoom) > 0.015f) {
                lastPostedEffectiveZoom = effective
                postUi { uiState = uiState.copy(effectiveZoom = effective) }
            }
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            Log.w(TAG, "Capture request failed reason=${failure.reason}")
        }
    }

    private fun prepareRecorderAndSession(sizeIndex: Int) {
        val device = cameraDevice ?: run {
            failRecordingSetup("Camera is not available")
            return
        }
        val config = cameraConfig ?: run {
            failRecordingSetup("Camera configuration is unavailable")
            return
        }
        if (!resumed || previewSurface == null) {
            failRecordingSetup("Camera preview is not ready")
            return
        }
        if (sizeIndex >= config.recordingSizes.size) {
            failRecordingSetup("This camera could not configure a supported recording stream")
            return
        }
        currentRecordingSizeIndex = sizeIndex
        cleanupRecorderAttempt(deletePendingOutput = true)
        val size = config.recordingSizes[sizeIndex]

        try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "SmoothLens_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SmoothLensCamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Could not create a video entry")
            pendingOutputUri = uri
            outputDescriptor = contentResolver.openFileDescriptor(uri, "w")
                ?: throw IOException("Could not open the video output")

            val recorder = MediaRecorder()
            mediaRecorder = recorder
            if (recordingAudioEnabled) recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(outputDescriptor!!.fileDescriptor)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (recordingAudioEnabled) recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setVideoSize(size.width, size.height)
            recorder.setVideoFrameRate(30)
            val pixels = size.width.toLong() * size.height.toLong()
            val bitrate = when {
                pixels >= 1920L * 1080L -> 8_000_000
                pixels >= 1280L * 720L -> 5_000_000
                else -> (pixels * 5L).coerceIn(1_500_000L, 5_000_000L).toInt()
            }
            recorder.setVideoEncodingBitRate(bitrate)
            if (recordingAudioEnabled) {
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44_100)
            }
            val displayDegrees = when (textureView?.display?.rotation ?: Surface.ROTATION_0) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            recorder.setOrientationHint((config.sensorOrientation - displayDegrees + 360) % 360)
            recorder.prepare()
            recordingSurface = recorder.surface

            captureSession?.close()
            captureSession = null
            device.createCaptureSession(
                listOfNotNull(previewSurface, recordingSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        if (!resumed || cameraDevice !== device) {
                            session.close()
                            cleanupRecorderAttempt(deletePendingOutput = true)
                            return
                        }
                        captureSession = session
                        try {
                            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                            builder.addTarget(previewSurface!!)
                            builder.addTarget(recordingSurface!!)
                            configureAutomaticControls(builder)
                            applyZoomToBuilder(builder, currentRequestedZoom)
                            repeatingBuilder = builder
                            session.setRepeatingRequest(builder.build(), captureCallback, cameraHandler)
                            mediaRecorder?.start()
                            recordingHasStarted = true
                            recordingStartElapsed = SystemClock.elapsedRealtime()
                            postUi {
                                uiState = uiState.copy(
                                    isCameraReady = true,
                                    isRecording = true,
                                    isStartingRecording = false,
                                    elapsedMillis = 0L,
                                    message = null
                                )
                            }
                            startRecordingTimer()
                        } catch (e: Exception) {
                            Log.e(TAG, "Recorder could not start", e)
                            stopRecordingInternal(reopenPreview = true, failureMessage = "Could not start recording")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.w(TAG, "Recording session rejected size=${size.width}x${size.height}")
                        session.close()
                        captureSession = null
                        cleanupRecorderAttempt(deletePendingOutput = true)
                        if (resumed && sizeIndex + 1 < config.recordingSizes.size) {
                            prepareRecorderAndSession(sizeIndex + 1)
                        } else {
                            failRecordingSetup("Camera rejected the available recording configuration")
                        }
                    }
                },
                cameraHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare recording at ${size.width}x${size.height}", e)
            cleanupRecorderAttempt(deletePendingOutput = true)
            if (sizeIndex + 1 < config.recordingSizes.size) {
                prepareRecorderAndSession(sizeIndex + 1)
            } else {
                failRecordingSetup("Unable to prepare video recording")
            }
        }
    }

    private fun startRecordingTimer() {
        recordingTimer?.let(cameraHandler::removeCallbacks)
        val timer = object : Runnable {
            override fun run() {
                if (!recordingHasStarted) return
                val elapsed = SystemClock.elapsedRealtime() - recordingStartElapsed
                postUi { uiState = uiState.copy(elapsedMillis = elapsed) }
                cameraHandler.postDelayed(this, 250L)
            }
        }
        recordingTimer = timer
        cameraHandler.post(timer)
    }

    private fun stopRecordingInternal(reopenPreview: Boolean, failureMessage: String? = null) {
        recordingTimer?.let(cameraHandler::removeCallbacks)
        recordingTimer = null
        zoomAnimation?.let(cameraHandler::removeCallbacks)
        zoomAnimation = null

        var savedSuccessfully = false
        val recorder = mediaRecorder
        if (recorder != null && recordingHasStarted) {
            try {
                recorder.stop()
                savedSuccessfully = true
            } catch (e: RuntimeException) {
                Log.w(TAG, "Recorder stopped before enough data was written", e)
            }
        }

        try {
            captureSession?.stopRepeating()
        } catch (_: Exception) {
        }
        try {
            captureSession?.close()
        } catch (_: Exception) {
        }
        captureSession = null
        repeatingBuilder = null

        recorder?.let {
            try { it.reset() } catch (_: Exception) { }
            try { it.release() } catch (_: Exception) { }
        }
        mediaRecorder = null
        recordingHasStarted = false
        recordingSurface?.release()
        recordingSurface = null
        try { outputDescriptor?.close() } catch (_: Exception) { }
        outputDescriptor = null

        val output = pendingOutputUri
        if (output != null) {
            if (savedSuccessfully) {
                try {
                    val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                    contentResolver.update(output, values, null, null)
                } catch (e: Exception) {
                    Log.e(TAG, "Could not publish recorded video", e)
                    try { contentResolver.delete(output, null, null) } catch (_: Exception) { }
                    savedSuccessfully = false
                }
            } else {
                try { contentResolver.delete(output, null, null) } catch (_: Exception) { }
            }
        }
        pendingOutputUri = null

        val finalSaved = savedSuccessfully
        postUi {
            uiState = uiState.copy(
                isRecording = false,
                isStartingRecording = false,
                elapsedMillis = if (finalSaved) uiState.elapsedMillis else 0L,
                isCameraReady = false,
                message = failureMessage ?: if (finalSaved) "Saved to Movies/SmoothLensCamera" else null
            )
        }
        if (reopenPreview && resumed && cameraDevice != null) createPreviewSession()
    }

    private fun failRecordingSetup(message: String) {
        cleanupRecorderAttempt(deletePendingOutput = true)
        val previewStillActive = captureSession != null
        postUi {
            uiState = uiState.copy(
                isRecording = false,
                isStartingRecording = false,
                isCameraReady = previewStillActive,
                message = message
            )
        }
        if (!previewStillActive && resumed && cameraDevice != null) createPreviewSession()
    }

    private fun cleanupRecorderAttempt(deletePendingOutput: Boolean) {
        try { mediaRecorder?.reset() } catch (_: Exception) { }
        try { mediaRecorder?.release() } catch (_: Exception) { }
        mediaRecorder = null
        try { recordingSurface?.release() } catch (_: Exception) { }
        recordingSurface = null
        try { outputDescriptor?.close() } catch (_: Exception) { }
        outputDescriptor = null
        val uri = pendingOutputUri
        if (deletePendingOutput && uri != null) {
            try { contentResolver.delete(uri, null, null) } catch (_: Exception) { }
        }
        pendingOutputUri = null
        recordingHasStarted = false
    }

    private fun closeCameraResources() {
        if (mediaRecorder != null || recordingHasStarted) {
            stopRecordingInternal(reopenPreview = false)
        }
        try { captureSession?.stopRepeating() } catch (_: Exception) { }
        try { captureSession?.close() } catch (_: Exception) { }
        captureSession = null
        repeatingBuilder = null
        try { previewSurface?.release() } catch (_: Exception) { }
        previewSurface = null
        try { cameraDevice?.close() } catch (_: Exception) { }
        cameraDevice = null
        cameraConfig = null
        zoomAnimation?.let(cameraHandler::removeCallbacks)
        zoomAnimation = null
        postUi {
            uiState = uiState.copy(
                isCameraReady = false,
                isRecording = false,
                isStartingRecording = false,
                elapsedMillis = 0L
            )
        }
    }

    private fun closeSessionAndSurfaces() {
        try { captureSession?.close() } catch (_: Exception) { }
        captureSession = null
        repeatingBuilder = null
        try { previewSurface?.release() } catch (_: Exception) { }
        previewSurface = null
        cleanupRecorderAttempt(deletePendingOutput = true)
    }

    private fun updatePreviewTransform() {
        val view = textureView ?: return
        val size = cameraConfig?.previewSize ?: return
        if (view.width == 0 || view.height == 0) return
        mainHandler.post {
            if (textureView !== view || view.width == 0 || view.height == 0) return@post
            val config = cameraConfig ?: return@post
            val rotationDegrees = when (view.display?.rotation ?: Surface.ROTATION_0) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
            val relativeRotation = (config.sensorOrientation - rotationDegrees + 360) % 360
            val viewRect = RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
            val bufferRect = if (relativeRotation == 90 || relativeRotation == 270) {
                RectF(0f, 0f, size.height.toFloat(), size.width.toFloat())
            } else {
                RectF(0f, 0f, size.width.toFloat(), size.height.toFloat())
            }
            val centerX = viewRect.centerX()
            val centerY = viewRect.centerY()
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            val matrix = Matrix()
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = max(
                view.height.toFloat() / bufferRect.height(),
                view.width.toFloat() / bufferRect.width()
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(relativeRotation.toFloat(), centerX, centerY)
            view.setTransform(matrix)
        }
    }

    private fun postUi(action: () -> Unit) {
        mainHandler.post { action() }
    }
}
