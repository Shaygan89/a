package com.example.smoothcamera.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.smoothcamera.camera.CameraCapability
import kotlin.math.max

@Composable
fun CameraPreview(
    modifier: Modifier,
    capability: CameraCapability?,
    onSurfaceReady: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = {
            TextureView(context).apply {
                var previewSurface: Surface? = null
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        surface.setDefaultBufferSize(1920, 1080)
                        previewSurface = Surface(surface)
                        onSurfaceReady(previewSurface!!)
                        updateTransform(this@apply, capability)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        updateTransform(this@apply, capability)
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        onSurfaceDestroyed()
                        previewSurface?.release()
                        previewSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                }
            }
        },
        update = { updateTransform(it, capability) },
    )

    DisposableEffect(Unit) {
        onDispose { onSurfaceDestroyed() }
    }
}

private fun updateTransform(view: TextureView, capability: CameraCapability?) {
    val matrix = Matrix()
    val displayDegrees = when (view.display?.rotation) {
        android.view.Surface.ROTATION_90 -> 90
        android.view.Surface.ROTATION_180 -> 180
        android.view.Surface.ROTATION_270 -> 270
        else -> 0
    }
    val sensor = capability?.sensorOrientation ?: 0
    val rotation = if (capability?.lensFacing == com.example.smoothcamera.camera.LensFacing.FRONT) {
        (sensor + displayDegrees) % 360
    } else {
        (sensor - displayDegrees + 360) % 360
    }
    if (rotation != 0) matrix.postRotate(rotation.toFloat(), view.width / 2f, view.height / 2f)

    val bufferWidth = 1920f
    val bufferHeight = 1080f
    val rotatedWidth = if (rotation % 180 == 0) bufferWidth else bufferHeight
    val rotatedHeight = if (rotation % 180 == 0) bufferHeight else bufferWidth
    val scale = max(view.width / rotatedWidth, view.height / rotatedHeight)
    matrix.postScale(scale, scale, view.width / 2f, view.height / 2f)
    view.setTransform(matrix)
}
