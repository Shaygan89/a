package com.example.smoothcamera.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.DynamicRangeProfiles
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

class CameraRepository(context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    suspend fun inspect(): List<CameraCapability> = withContext(Dispatchers.Default) {
        cameraManager.cameraIdList.mapNotNull { id ->
            runCatching { inspect(id) }.getOrNull()
        }
    }

    fun characteristics(cameraId: String): CameraCharacteristics =
        cameraManager.getCameraCharacteristics(cameraId)

    fun manager(): CameraManager = cameraManager

    private fun inspect(id: String): CameraCapability {
        val c = cameraManager.getCameraCharacteristics(id)
        val facing = lensFacingOf(c.get(CameraCharacteristics.LENS_FACING))
        val level = c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            ?: CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
        val capabilities = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet().orEmpty()
        val isLogical = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA in capabilities
        val physicalIds = if (Build.VERSION.SDK_INT >= 28) c.physicalCameraIds.toList().sorted() else emptyList()

        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val previewSizes = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList().orEmpty()
            .sortedWith(compareByDescending<Size> { it.width.toLong() * it.height }.thenByDescending { it.width })
        val videoSizes = map?.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
            .sortedWith(compareByDescending<Size> { it.width.toLong() * it.height }.thenByDescending { it.width })
        val fpsRanges = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)?.toList().orEmpty()
            .sortedWith(compareBy({ it.upper }, { it.lower }))

        val focalLengths = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
        val sensorSize = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val maxDigitalZoom = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
        val zoomRange = if (Build.VERSION.SDK_INT >= 30) {
            c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
        } else null
        val finalZoomRange = zoomRange ?: Range(1f, maxDigitalZoom.coerceAtLeast(1f))

        val supportsRaw = CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW in capabilities
        val supportsHdr10Bit = if (Build.VERSION.SDK_INT >= 33) {
            CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT in capabilities &&
                (runCatching {
                    val profiles = c.get(CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES)?.supportedProfiles
                    profiles?.any { it != DynamicRangeProfiles.STANDARD } == true
                }.getOrDefault(false))
        } else false

        val stabilizationModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)?.toList().orEmpty()
        val oisModes = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.toList().orEmpty()
        val afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList().orEmpty()
        val evRange = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(0, 0)
        val awbModes = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList().orEmpty()

        val physicalLenses = if (isLogical && Build.VERSION.SDK_INT >= 28) {
            val reference = focalLengths.minOrNull() ?: 1f
            physicalIds.mapNotNull { physicalId ->
                runCatching {
                    val pc = cameraManager.getCameraCharacteristics(physicalId)
                    val f = pc.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty()
                    val ps = pc.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                    val pm = pc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    PhysicalLensInfo(
                        id = physicalId,
                        focalLengthsMm = f,
                        sensorWidthMm = ps?.width,
                        sensorHeightMm = ps?.height,
                        outputSizes = pm?.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList().orEmpty(),
                        kind = f.firstOrNull()?.let { lensKindFor(it, reference) } ?: LensKind.UNKNOWN,
                    )
                }.getOrNull()
            }.sortedBy { it.focalLengthsMm.firstOrNull() ?: Float.MAX_VALUE }
        } else emptyList()

        return CameraCapability(
            id = id,
            lensFacing = facing,
            hardwareLevel = level,
            hardwareLevelLabel = hardwareLevelName(level),
            isLogicalMultiCamera = isLogical,
            physicalIds = physicalIds,
            focalLengthsMm = focalLengths,
            sensorWidthMm = sensorSize?.width,
            sensorHeightMm = sensorSize?.height,
            outputSizes = previewSizes,
            videoSizes = videoSizes,
            fpsRanges = fpsRanges,
            maxDigitalZoom = maxDigitalZoom,
            zoomRatioRange = finalZoomRange,
            supportsRaw = supportsRaw,
            supportsHdr10Bit = supportsHdr10Bit,
            stabilizationModes = stabilizationModes,
            oisAvailable = oisModes.any { it == CameraCharacteristics.LENS_OPTICAL_STABILIZATION_MODE_ON },
            afModes = afModes,
            aeCompensationRange = evRange,
            awbModes = awbModes,
            physicalLenses = physicalLenses,
            activeArrayWidth = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.width(),
            activeArrayHeight = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)?.height(),
            sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
        )
    }

    fun choosePreferredBack(capabilities: List<CameraCapability>): CameraCapability? =
        capabilities.asSequence()
            .filter { it.lensFacing == LensFacing.BACK }
            .sortedWith(
                compareByDescending<CameraCapability> { it.isLogicalMultiCamera }
                    .thenByDescending { it.physicalLenses.size }
                    .thenByDescending { it.zoomRatioRange.lower <= 0.6f }
                    .thenBy { it.id.length }
            )
            .firstOrNull()

    fun chooseFront(capabilities: List<CameraCapability>): CameraCapability? =
        capabilities.firstOrNull { it.lensFacing == LensFacing.FRONT }
}
