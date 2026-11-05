package com.example.smoothcamera.camera

import android.hardware.camera2.CameraCharacteristics
import android.util.Range
import android.util.Size

@JvmInline
value class CameraId(val value: String)

enum class LensFacing {
    FRONT, BACK, EXTERNAL, UNKNOWN
}

enum class LensKind {
    ULTRA_WIDE, WIDE, TELEPHOTO, FRONT, MACRO, UNKNOWN
}

data class PhysicalLensInfo(
    val id: String,
    val focalLengthsMm: List<Float>,
    val sensorWidthMm: Float?,
    val sensorHeightMm: Float?,
    val outputSizes: List<Size>,
    val kind: LensKind,
)

data class CameraCapability(
    val id: String,
    val lensFacing: LensFacing,
    val hardwareLevel: Int,
    val hardwareLevelLabel: String,
    val isLogicalMultiCamera: Boolean,
    val physicalIds: List<String>,
    val focalLengthsMm: List<Float>,
    val sensorWidthMm: Float?,
    val sensorHeightMm: Float?,
    val outputSizes: List<Size>,
    val videoSizes: List<Size>,
    val fpsRanges: List<Range<Int>>,
    val maxDigitalZoom: Float,
    val zoomRatioRange: Range<Float>,
    val supportsRaw: Boolean,
    val supportsHdr10Bit: Boolean,
    val stabilizationModes: List<Int>,
    val oisAvailable: Boolean,
    val afModes: List<Int>,
    val aeCompensationRange: Range<Int>,
    val awbModes: List<Int>,
    val physicalLenses: List<PhysicalLensInfo>,
    val activeArrayWidth: Int?,
    val activeArrayHeight: Int?,
    val sensorOrientation: Int,
)

data class RecordingConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val codecMime: String = "video/avc",
    val bitrate: Int? = null,
    val recordAudio: Boolean = true,
)

data class TransitionSettings(
    val enabled: Boolean = true,
    val durationMs: Long = 500L,
    val curve: TransitionCurve = TransitionCurve.EASE_IN_OUT,
)

enum class TransitionCurve {
    LINEAR,
    EASE_IN_OUT,
    CINEMATIC,
}

data class CameraSettings(
    val recording: RecordingConfig = RecordingConfig(),
    val transition: TransitionSettings = TransitionSettings(),
    val stabilizationEnabled: Boolean = true,
    val tapToFocusEnabled: Boolean = true,
    val microphoneEnabled: Boolean = true,
    val gridEnabled: Boolean = false,
    val exposureCompensation: Int = 0,
    val whiteBalanceMode: Int? = null,
    val debugOverlayEnabled: Boolean = false,
)

data class CameraUiState(
    val hasCameraPermission: Boolean = false,
    val hasMicrophonePermission: Boolean = false,
    val isInitialized: Boolean = false,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val currentZoom: Float = 1f,
    val activePhysicalId: String? = null,
    val currentLensLabel: String = "Starting…",
    val capabilities: List<CameraCapability> = emptyList(),
    val selectedCameraId: String? = null,
    val message: String? = null,
    val debugFps: Float = 0f,
    val debugFrameTimeMs: Float = 0f,
    val transitionActive: Boolean = false,
    val transitionProgress: Float = 0f,
    val targetZoom: Float? = null,
    val recordingUri: String? = null,
)

fun lensFacingOf(value: Int?): LensFacing = when (value) {
    CameraCharacteristics.LENS_FACING_BACK -> LensFacing.BACK
    CameraCharacteristics.LENS_FACING_FRONT -> LensFacing.FRONT
    CameraCharacteristics.LENS_FACING_EXTERNAL -> LensFacing.EXTERNAL
    else -> LensFacing.UNKNOWN
}

fun hardwareLevelName(value: Int): String = when (value) {
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
    else -> "UNKNOWN"
}

fun lensKindFor(focalLengthMm: Float, focalReferenceMm: Float): LensKind {
    if (focalReferenceMm <= 0f) return LensKind.UNKNOWN
    val ratio = focalLengthMm / focalReferenceMm
    return when {
        ratio <= 0.72f -> LensKind.ULTRA_WIDE
        ratio <= 1.35f -> LensKind.WIDE
        ratio >= 1.8f -> LensKind.TELEPHOTO
        else -> LensKind.UNKNOWN
    }
}
