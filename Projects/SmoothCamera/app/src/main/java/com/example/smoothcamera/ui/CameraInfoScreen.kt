package com.example.smoothcamera.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smoothcamera.camera.CameraCapability
import com.example.smoothcamera.camera.CameraUiState
import com.example.smoothcamera.camera.PhysicalLensInfo
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraInfoScreen(state: CameraUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera capabilities") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Runtime inspection", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "These values come from Camera2 at runtime. Camera IDs are intentionally not hard-coded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(state.capabilities) { capability -> CameraCapabilityCard(capability) }
        }
    }
}

@Composable
private fun CameraCapabilityCard(cap: CameraCapability) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Camera ${cap.id}", style = MaterialTheme.typography.titleLarge)
            Text("Facing: ${cap.lensFacing}")
            Text("Hardware: ${cap.hardwareLevelLabel}")
            Text("Logical multi-camera: ${cap.isLogicalMultiCamera}")
            Text("Physical IDs: ${cap.physicalIds.ifEmpty { listOf("none") }.joinToString()}")
            Text("Focal lengths: ${cap.focalLengthsMm.joinToString { String.format(Locale.US, "%.2f mm", it) }.ifBlank { "unknown" }}")
            Text("Sensor: ${cap.sensorWidthMm?.let { "%.2f".format(Locale.US, it) } ?: "?"} × ${cap.sensorHeightMm?.let { "%.2f".format(Locale.US, it) } ?: "?"} mm")
            Text("Sensor orientation: ${cap.sensorOrientation}°")
            Text("Preview sizes: ${cap.outputSizes.take(5).joinToString()}")
            Text("Video sizes: ${cap.videoSizes.take(8).joinToString()}")
            Text("FPS ranges: ${cap.fpsRanges.joinToString()}")
            Text("Zoom ratio: ${cap.zoomRatioRange.lower}× – ${cap.zoomRatioRange.upper}×")
            Text("Max digital zoom: ${cap.maxDigitalZoom}×")
            Text("RAW: ${cap.supportsRaw} • 10-bit/HDR capability: ${cap.supportsHdr10Bit}")
            Text("Video stabilization modes: ${cap.stabilizationModes.joinToString()}")
            Text("OIS: ${cap.oisAvailable}")
            Text("AE compensation: ${cap.aeCompensationRange}")
            if (cap.physicalLenses.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Physical lenses", style = MaterialTheme.typography.titleMedium)
                cap.physicalLenses.forEach { lens -> PhysicalLensRow(lens) }
            }
        }
    }
}

@Composable
private fun PhysicalLensRow(lens: PhysicalLensInfo) {
    Column(Modifier.padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("${lens.id} • ${lens.kind}", style = MaterialTheme.typography.bodyLarge)
        Text("Focal: ${lens.focalLengthsMm.joinToString { String.format(Locale.US, "%.2f mm", it) }}")
        Text("Sensor: ${lens.sensorWidthMm ?: "?"} × ${lens.sensorHeightMm ?: "?"} mm")
    }
}
