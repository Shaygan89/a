package com.example.smoothlenscamera

import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale
import kotlin.math.abs

@Composable
fun CameraScreen(
    controller: CameraController,
    onRequestCameraPermission: () -> Unit,
    onRecordPressed: () -> Unit
) {
    val state = controller.uiState

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context -> TextureView(context).also(controller::attach) },
            modifier = Modifier.fillMaxSize()
        )

        if (!state.hasCameraPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Camera permission needed",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    text = "Allow camera access to use the live preview and record video.",
                    color = Color.LightGray
                )
                Button(onClick = onRequestCameraPermission) {
                    Text("Allow camera")
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            state.message?.let { message ->
                Surface(
                    color = Color(0xCC202020),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        fontSize = 13.sp
                    )
                }
            }

            if (state.isRecording || state.isStartingRecording) {
                Surface(
                    color = Color(0xCC111111),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = if (state.isStartingRecording) "STARTING…" else formatDuration(state.elapsedMillis),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        fontSize = 14.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.supportedZooms.forEach { ratio ->
                    val selected = abs(state.effectiveZoom - ratio) < 0.08f
                    val label = when (ratio) {
                        0.5f -> "0.5×"
                        1.0f -> "1×"
                        2.0f -> "2×"
                        else -> String.format(Locale.US, "%.1f×", ratio)
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Color.White else Color(0x990C0C0C))
                            .border(
                                BorderStroke(1.dp, if (selected) Color.White else Color(0x88FFFFFF)),
                                CircleShape
                            )
                            .clickable(
                                enabled = state.isCameraReady && !state.isStartingRecording
                            ) { controller.zoomTo(ratio) }
                            .padding(horizontal = 18.dp, vertical = 11.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (selected) Color.Black else Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(82.dp)
                    .border(3.dp, Color.White, CircleShape)
                    .padding(7.dp)
                    .clip(CircleShape)
                    .background(Color(0x33000000))
                    .clickable(
                        enabled = state.isCameraReady && !state.isStartingRecording ||
                            !state.hasCameraPermission
                    ) { onRecordPressed() }
                    .semantics {
                        contentDescription = if (state.isRecording) "Stop recording" else "Start recording"
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(if (state.isRecording) 27.dp else 58.dp)
                        .clip(if (state.isRecording) RoundedCornerShape(6.dp) else CircleShape)
                        .background(Color(0xFFFF3038))
                )
            }
            Spacer(modifier = Modifier.size(2.dp))
        }
    }
}

internal fun formatDuration(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%02d:%02d", totalSeconds / 60L, totalSeconds % 60L)
}
