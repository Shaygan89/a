package com.example.smoothcamera.ui

import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.smoothcamera.camera.CameraSettings
import com.example.smoothcamera.camera.CameraUiState
import com.example.smoothcamera.camera.CameraViewModel
import com.example.smoothcamera.camera.LensFacing
import java.util.Locale

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    state: CameraUiState,
    settings: CameraSettings,
    onOpenSettings: () -> Unit,
    onOpenInfo: () -> Unit,
    onRequestMicrophone: () -> Unit,
) {
    val view = LocalView.current
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val selectedCapability = state.capabilities.firstOrNull { it.id == state.selectedCameraId }
    val zoomStops = remember(selectedCapability) {
        val range = selectedCapability?.zoomRatioRange
        buildList {
            if (range != null && range.lower <= 0.55f) add(0.5f)
            if (range != null && 1f in range.lower..range.upper) add(1f)
            if (range != null && range.upper >= 2f) add(2f)
            if (range != null && range.upper >= 4f) add(4f)
            if (range != null && range.upper >= 8f) add(8f)
        }.distinct()
    }

    Box(Modifier.fillMaxSize()) {
        CameraPreview(
            modifier = Modifier.fillMaxSize(),
            capability = selectedCapability,
            onSurfaceReady = viewModel::attachPreviewSurface,
            onSurfaceDestroyed = viewModel::detachPreviewSurface,
        )

        if (settings.gridEnabled) GridOverlay(Modifier.fillMaxSize())

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        val w = size.width.coerceAtLeast(1)
                        val h = size.height.coerceAtLeast(1)
                        viewModel.focusAt(offset.x / w, offset.y / h)
                    }
                }
                .transformable(
                    state = rememberTransformableState { zoomChange, _, _ ->
                        if (!state.transitionActive) viewModel.zoomBy(zoomChange)
                    }
                )
        )

        focusPoint?.let { point ->
            FocusIndicator(point = point, modifier = Modifier.fillMaxSize())
            LaunchedEffect(point) {
                kotlinx.coroutines.delay(900L)
                focusPoint = null
            }
        }

        if (settings.debugOverlayEnabled) {
            DebugOverlay(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 18.dp, end = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledIconButton(
                onClick = onOpenInfo,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.42f)),
            ) { Icon(Icons.Default.Info, contentDescription = "Camera information") }
            FilledIconButton(
                onClick = onOpenSettings,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.42f)),
            ) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContentLabel(
                text = state.currentLensLabel,
                zoom = state.currentZoom,
                transition = state.transitionActive,
            )

            Spacer(Modifier.height(10.dp))

            if (zoomStops.isNotEmpty()) {
                Surface(
                    color = Color.Black.copy(alpha = 0.44f),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        zoomStops.forEach { zoom ->
                            val selected = kotlin.math.abs(state.currentZoom - zoom) < 0.06f
                            TextButton(
                                onClick = { viewModel.requestZoom(zoom) },
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.White,
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 3.dp),
                            ) {
                                Text(if (zoom == zoom.toInt().toFloat()) "${zoom.toInt()}×" else String.format(Locale.US, "%.1f×", zoom))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (!state.isRecording) {
                        val next = if (selectedCapability?.lensFacing == LensFacing.FRONT) {
                            state.capabilities.firstOrNull { it.lensFacing == LensFacing.BACK }
                        } else {
                            state.capabilities.firstOrNull { it.lensFacing == LensFacing.FRONT }
                        }
                        next?.let { viewModel.selectCamera(it.id) }
                    }
                }) {
                    Icon(Icons.Default.Cameraswitch, contentDescription = "Switch camera", tint = Color.White)
                }

                FilledIconButton(
                    onClick = {
                        if (state.isRecording) {
                            viewModel.stopRecording()
                        } else {
                            if (settings.microphoneEnabled && !state.hasMicrophonePermission) onRequestMicrophone()
                            else viewModel.startRecording(displayRotationDegrees(view))
                        }
                    },
                    modifier = Modifier.size(76.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (state.isRecording) MaterialTheme.colorScheme.error else Color.White,
                        contentColor = if (state.isRecording) Color.White else Color.Black,
                    ),
                ) {
                    Icon(
                        if (state.isRecording) Icons.Default.StopCircle else Icons.Default.Videocam,
                        contentDescription = if (state.isRecording) "Stop recording" else "Start recording",
                        modifier = Modifier.size(34.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isRecording) {
                        Text(
                            "● ${formatElapsed(state.recordingElapsedMs)}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    } else {
                        Icon(Icons.Default.CameraFront, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun AnimatedContentLabel(text: String, zoom: Float, transition: Boolean) {
    Surface(
        color = Color.Black.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(String.format(Locale.US, "%.2f×", zoom), color = Color.White, fontSize = 13.sp)
            Text(text, color = Color.White, fontSize = 12.sp)
            if (transition) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun FocusIndicator(point: Offset, modifier: Modifier) {
    Canvas(modifier) {
        drawCircle(
            color = Color.White,
            radius = 28.dp.toPx(),
            center = point,
            style = Stroke(width = 2.dp.toPx()),
        )
        drawLine(
            color = Color.White,
            start = Offset(point.x - 7.dp.toPx(), point.y),
            end = Offset(point.x + 7.dp.toPx(), point.y),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(point.x, point.y - 7.dp.toPx()),
            end = Offset(point.x, point.y + 7.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun GridOverlay(modifier: Modifier) {
    Canvas(modifier) {
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        repeat(2) { i ->
            drawLine(Color.White.copy(alpha = 0.18f), Offset(thirdW * (i + 1), 0f), Offset(thirdW * (i + 1), size.height), strokeWidth = 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, thirdH * (i + 1)), Offset(size.width, thirdH * (i + 1)), strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
private fun DebugOverlay(state: CameraUiState, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.72f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("CAMERA DEBUG", color = Color.White, style = MaterialTheme.typography.labelMedium)
            Text("Camera: ${state.selectedCameraId ?: "—"}", color = Color.White, fontSize = 11.sp)
            Text("Physical: ${state.activePhysicalId ?: "unknown"}", color = Color.White, fontSize = 11.sp)
            Text("Lens: ${state.currentLensLabel}", color = Color.White, fontSize = 11.sp)
            Text(String.format(Locale.US, "Zoom: %.2f×", state.currentZoom), color = Color.White, fontSize = 11.sp)
            Text(String.format(Locale.US, "FPS: %.1f  Frame: %.1f ms", state.debugFps, state.debugFrameTimeMs), color = Color.White, fontSize = 11.sp)
            if (state.transitionActive) Text(String.format(Locale.US, "Transition: %.0f%%", state.transitionProgress * 100f), color = Color.White, fontSize = 11.sp)
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private fun displayRotationDegrees(view: android.view.View): Int = when (view.display?.rotation ?: Surface.ROTATION_0) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}
