package com.example.smoothcamera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureResult
import android.net.Uri
import android.os.SystemClock
import android.util.Size
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smoothcamera.data.CameraSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class CameraViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val repository = CameraRepository(appContext)
    private val settingsRepository = CameraSettingsRepository(appContext)
    private val transitionController = LensTransitionController(viewModelScope)

    private val _uiState = MutableStateFlow(
        CameraUiState(hasMicrophonePermission = false)
    )
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _settings = MutableStateFlow(settingsRepository.load())
    val settings: StateFlow<CameraSettings> = _settings.asStateFlow()

    private var cameraSession: CameraSession? = null
    private var previewSurface: android.view.Surface? = null
    private var recorder: VideoRecorder? = null
    private var selectedCapability: CameraCapability? = null
    private var lastCaptureTimestampNs = 0L
    private var fpsSamples = 0
    private var fpsWindowStartNs = 0L
    private var recordingStartedElapsed = 0L
    private var initialized = false

    fun initialize(hasCameraPermission: Boolean, hasMicrophonePermission: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasCameraPermission = hasCameraPermission,
            hasMicrophonePermission = hasMicrophonePermission,
        )
        if (!hasCameraPermission || initialized) return
        initialized = true
        viewModelScope.launch {
            runCatching {
                val caps = repository.inspect()
                val preferred = repository.choosePreferredBack(caps)
                    ?: repository.chooseFront(caps)
                    ?: error("No usable camera was found")
                selectedCapability = preferred
                _uiState.value = _uiState.value.copy(
                    isInitialized = true,
                    capabilities = caps,
                    selectedCameraId = preferred.id,
                    currentZoom = preferred.zoomRatioRange.lower,
                    currentLensLabel = lensLabel(preferred, preferred.zoomRatioRange.lower, null),
                    message = null,
                )
                if (previewSurface != null) openSelectedInternal(preferred)
            }.onFailure {
                initialized = false
                _uiState.value = _uiState.value.copy(
                    isInitialized = false,
                    message = it.message ?: "Camera inspection failed",
                )
            }
        }
    }

    fun updatePermissions(camera: Boolean, microphone: Boolean) {
        _uiState.value = _uiState.value.copy(hasCameraPermission = camera, hasMicrophonePermission = microphone)
    }

    fun attachPreviewSurface(surface: android.view.Surface) {
        previewSurface = surface
        cameraSession?.setPreviewSurface(surface)
        if (selectedCapability != null && _uiState.value.hasCameraPermission && cameraSession == null) {
            openSelectedInternal(selectedCapability!!)
        }
    }

    fun detachPreviewSurface() {
        previewSurface = null
        cameraSession?.setPreviewSurface(null)
    }

    fun selectCamera(cameraId: String) {
        val capability = _uiState.value.capabilities.firstOrNull { it.id == cameraId } ?: return
        if (_uiState.value.isRecording) return
        selectedCapability = capability
        _uiState.value = _uiState.value.copy(
            selectedCameraId = cameraId,
            currentZoom = capability.zoomRatioRange.lower,
            activePhysicalId = null,
            currentLensLabel = lensLabel(capability, capability.zoomRatioRange.lower, null),
        )
        openSelectedInternal(capability)
    }

    fun requestZoom(target: Float) {
        val cap = selectedCapability ?: return
        val clamped = target.coerceIn(cap.zoomRatioRange.lower, cap.zoomRatioRange.upper)
        val start = _uiState.value.currentZoom
        if (abs(start - clamped) < 0.001f) return

        if (!_settings.value.transition.enabled) {
            setZoom(clamped)
            return
        }

        _uiState.value = _uiState.value.copy(
            transitionActive = true,
            transitionProgress = 0f,
            targetZoom = clamped,
        )
        transitionController.transition(
            startZoom = start,
            targetZoom = clamped,
            settings = _settings.value.transition,
            setZoom = ::setZoom,
            onProgress = { progress ->
                _uiState.value = _uiState.value.copy(
                    transitionActive = progress < 1f,
                    transitionProgress = progress,
                    targetZoom = clamped,
                )
            },
            onFinished = {
                _uiState.value = _uiState.value.copy(
                    transitionActive = false,
                    transitionProgress = 1f,
                    targetZoom = null,
                )
            },
        )
    }

    fun zoomBy(factor: Float) {
        val current = _uiState.value.currentZoom
        setZoom(current * factor)
    }

    fun setZoom(value: Float) {
        val cap = selectedCapability ?: return
        val zoom = value.coerceIn(cap.zoomRatioRange.lower, cap.zoomRatioRange.upper)
        _uiState.value = _uiState.value.copy(
            currentZoom = zoom,
            currentLensLabel = lensLabel(cap, zoom, _uiState.value.activePhysicalId),
        )
        cameraSession?.setZoomRatio(zoom)
    }

    fun focusAt(x: Float, y: Float) {
        cameraSession?.focusAt(x, y)
    }

    fun startRecording(displayRotationDegrees: Int) {
        if (_uiState.value.isRecording) return
        val cap = selectedCapability ?: return
        val s = _settings.value
        val mic = s.microphoneEnabled && _uiState.value.hasMicrophonePermission
        val config = sanitizeRecordingConfig(cap, s.recording.copy(recordAudio = mic))
        val sensorOrientation = runCatching {
            repository.characteristics(cap.id).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        }.getOrDefault(0)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val videoRecorder = VideoRecorder(appContext)
                val uri = videoRecorder.prepare(
                    config = config,
                    orientationDegrees = displayRotationDegrees,
                    sensorOrientation = sensorOrientation,
                    facing = cap.lensFacing,
                )
                recorder = videoRecorder
                withContext(Dispatchers.Main) {
                    cameraSession?.startRecording(videoRecorder.surface(), config, s)
                    videoRecorder.start()
                    recordingStartedElapsed = SystemClock.elapsedRealtime()
                    lastCaptureTimestampNs = 0L
                    fpsSamples = 0
                    fpsWindowStartNs = 0L
                    CameraLog.d("recording started uri=$uri")
                    _uiState.value = _uiState.value.copy(
                        isRecording = true,
                        recordingElapsedMs = 0L,
                        recordingUri = uri.toString(),
                        message = null,
                    )
                    startRecordingTicker()
                }
            } catch (t: Throwable) {
                cameraSession?.stopRecording()
                runCatching { recorder?.close() }
                recorder = null
                _uiState.value = _uiState.value.copy(message = "Recording failed: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    fun stopRecording() {
        if (!_uiState.value.isRecording) return
        _uiState.value = _uiState.value.copy(isRecording = false)
        viewModelScope.launch(Dispatchers.IO) {
            val currentRecorder = recorder
            try {
                currentRecorder?.stop()
                cameraSession?.stopRecording()
                CameraLog.d("recording stopped uri=${currentRecorder?.uri()}")
                _uiState.value = _uiState.value.copy(message = "Video saved")
            } catch (t: Throwable) {
                cameraSession?.stopRecording()
                _uiState.value = _uiState.value.copy(message = "Could not finalize video: ${t.message ?: t.javaClass.simpleName}")
                runCatching { currentRecorder?.close() }
            } finally {
                recorder = null
            }
        }
    }

    fun setStabilization(enabled: Boolean) = updateSettings { it.copy(stabilizationEnabled = enabled) }
    fun setMicrophone(enabled: Boolean) = updateSettings { it.copy(microphoneEnabled = enabled) }
    fun setTapToFocus(enabled: Boolean) = updateSettings { it.copy(tapToFocusEnabled = enabled) }
    fun setGrid(enabled: Boolean) = updateSettings { it.copy(gridEnabled = enabled) }
    fun setDebug(enabled: Boolean) = updateSettings { it.copy(debugOverlayEnabled = enabled) }
    fun setExposure(value: Int) = updateSettings { it.copy(exposureCompensation = value) }
    fun setTransitionDuration(value: Long) = updateSettings { it.copy(transition = it.transition.copy(durationMs = value)) }
    fun setTransitionCurve(value: TransitionCurve) = updateSettings { it.copy(transition = it.transition.copy(curve = value)) }
    fun setTransitionEnabled(enabled: Boolean) = updateSettings { it.copy(transition = it.transition.copy(enabled = enabled)) }

    fun setResolution(width: Int, height: Int) = updateSettings {
        it.copy(recording = it.recording.copy(width = width, height = height))
    }

    fun setFps(fps: Int) = updateSettings {
        it.copy(recording = it.recording.copy(fps = fps))
    }

    fun setCodec(mime: String) = updateSettings {
        it.copy(recording = it.recording.copy(codecMime = mime))
    }

    fun setBitrate(bitrate: Int?) = updateSettings {
        it.copy(recording = it.recording.copy(bitrate = bitrate))
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private fun updateSettings(transform: (CameraSettings) -> CameraSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        settingsRepository.save(updated)
        cameraSession?.updateSettings(updated)
    }

    private fun openSelectedInternal(capability: CameraCapability) {
        cameraSession?.close()
        cameraSession = CameraSession(
            context = appContext,
            cameraManager = repository.manager(),
            onResult = ::onCaptureResult,
            onError = { message ->
                _uiState.value = _uiState.value.copy(message = message, isRecording = false)
            },
        ).also { it.setPreviewSurface(previewSurface) }
        cameraSession?.open(capability.id, _settings.value.recording, _settings.value)
    }

    private fun onCaptureResult(result: CaptureResult) {
        val active = if (android.os.Build.VERSION.SDK_INT >= 29) {
            result.get(CaptureResult.LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID)
        } else null
        if (active != _uiState.value.activePhysicalId) {
            val cap = selectedCapability
            CameraLog.d("active physical camera changed: ${_uiState.value.activePhysicalId} -> $active")
            _uiState.value = _uiState.value.copy(
                activePhysicalId = active,
                currentLensLabel = cap?.let { lensLabel(it, _uiState.value.currentZoom, active) } ?: _uiState.value.currentLensLabel,
            )
        }

        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        if (lastCaptureTimestampNs != 0L) {
            fpsSamples++
        }
        lastCaptureTimestampNs = timestamp
        if (fpsWindowStartNs == 0L) fpsWindowStartNs = timestamp
        val elapsed = timestamp - fpsWindowStartNs
        if (elapsed >= 1_000_000_000L && fpsSamples > 0) {
            val fps = fpsSamples * 1_000_000_000f / elapsed.toFloat()
            val frameTime = 1000f / fps.coerceAtLeast(0.1f)
            _uiState.value = _uiState.value.copy(debugFps = fps, debugFrameTimeMs = frameTime)
            fpsSamples = 0
            fpsWindowStartNs = timestamp
        }
    }

    private fun startRecordingTicker() {
        viewModelScope.launch {
            while (_uiState.value.isRecording) {
                val elapsed = SystemClock.elapsedRealtime() - recordingStartedElapsed
                _uiState.value = _uiState.value.copy(recordingElapsedMs = elapsed)
                delay(250L)
            }
        }
    }

    private fun sanitizeRecordingConfig(cap: CameraCapability, config: RecordingConfig): RecordingConfig {
        val requested = Size(config.width, config.height)
        val actual = chooseSize(cap.videoSizes, requested) ?: chooseSize(cap.outputSizes, requested)
        val width = actual?.width ?: 1920
        val height = actual?.height ?: 1080
        val fps = chooseFps(cap.fpsRanges, config.fps)
        val codecs = VideoSupport.supportedVideoCodecs(width, height, fps)
        val mime = if (config.codecMime in codecs) config.codecMime else codecs.firstOrNull() ?: "video/avc"
        return config.copy(width = width, height = height, fps = fps, codecMime = mime)
    }

    private fun chooseSize(sizes: List<Size>, requested: Size): Size? {
        if (sizes.isEmpty()) return null
        return sizes.minWithOrNull(
            compareBy<Size> {
                if (it == requested) 0 else 1
            }.thenBy {
                val arDiff = abs(it.width.toFloat() / it.height - requested.width.toFloat() / requested.height)
                arDiff
            }.thenBy {
                abs((it.width * it.height).toLong() - (requested.width * requested.height).toLong())
            }
        )
    }

    private fun chooseFps(ranges: List<android.util.Range<Int>>, requested: Int): Int {
        val exact = ranges.firstOrNull { it.lower == requested && it.upper == requested }
        if (exact != null) return requested
        val containing = ranges.firstOrNull { requested in it.lower..it.upper }
        return containing?.upper?.coerceAtLeast(requested) ?: ranges.maxOfOrNull { it.upper } ?: 30
    }

    private fun lensLabel(cap: CameraCapability, zoom: Float, activePhysicalId: String?): String {
        activePhysicalId?.let { id ->
            cap.physicalLenses.firstOrNull { it.id == id }?.let { lens ->
                return when (lens.kind) {
                    LensKind.ULTRA_WIDE -> "Ultra-wide"
                    LensKind.WIDE -> "Main"
                    LensKind.TELEPHOTO -> "Telephoto"
                    LensKind.MACRO -> "Macro"
                    LensKind.FRONT -> "Front"
                    LensKind.UNKNOWN -> "Physical $id"
                }
            }
        }
        return when {
            zoom <= 0.65f -> "Ultra-wide"
            zoom < 1.8f -> "Main"
            else -> "Telephoto / digital"
        }
    }

    override fun onCleared() {
        transitionController.cancel()
        cameraSession?.close()
        cameraSession = null
        recorder?.close()
        recorder = null
        super.onCleared()
    }
}
