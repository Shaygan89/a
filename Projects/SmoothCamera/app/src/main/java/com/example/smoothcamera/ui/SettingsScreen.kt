package com.example.smoothcamera.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smoothcamera.camera.CameraSettings
import com.example.smoothcamera.camera.CameraUiState
import com.example.smoothcamera.camera.CameraViewModel
import com.example.smoothcamera.camera.TransitionCurve
import com.example.smoothcamera.camera.VideoSupport
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: CameraUiState,
    settings: CameraSettings,
    viewModel: CameraViewModel,
    onBack: () -> Unit,
) {
    val cap = state.capabilities.firstOrNull { it.id == state.selectedCameraId }
    val resolutions = cap?.videoSizes.orEmpty()
        .map { it.width to it.height }
        .filter { (w, h) -> (w == 1280 && h == 720) || (w == 1920 && h == 1080) || (w >= 3840 && h >= 2160) }
        .distinct()
        .sortedBy { it.first * it.second }
        .ifEmpty { listOf(settings.recording.width to settings.recording.height) }
    val fpsOptions = cap?.fpsRanges.orEmpty().let { ranges ->
        listOf(24, 30, 60).filter { requested -> ranges.any { requested in it.lower..it.upper } }
    }.ifEmpty { listOf(settings.recording.fps) }
    val codecOptions = remember(cap, settings.recording.width, settings.recording.height, settings.recording.fps) {
        VideoSupport.supportedVideoCodecs(settings.recording.width, settings.recording.height, settings.recording.fps)
    }.ifEmpty { listOf("video/avc") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Camera settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionTitle("Camera") }
            item {
                PreferenceSwitch("Tap to focus", "Focus and exposure meter where you tap the preview.", settings.tapToFocusEnabled, viewModel::setTapToFocus)
            }
            item { PreferenceSwitch("Rule-of-thirds grid", "Overlay a simple composition grid.", settings.gridEnabled, viewModel::setGrid) }
            item { PreferenceSwitch("Video stabilization", "Use camera stabilization when supported.", settings.stabilizationEnabled, viewModel::setStabilization) }
            item { ExposurePreference(state, settings, viewModel) }

            item { SectionTitle("Video") }
            item { OptionRow("Resolution", resolutionLabel(settings.recording.width, settings.recording.height), resolutions.map { resolutionLabel(it.first, it.second) }) { value ->
                resolutions.firstOrNull { resolutionLabel(it.first, it.second) == value }?.let { viewModel.setResolution(it.first, it.second) }
            } }
            item { OptionRow("Frame rate", "${settings.recording.fps} FPS", fpsOptions.map { "$it FPS" }) { value -> viewModel.setFps(value.removeSuffix(" FPS").toInt()) } }
            item {
                val labels = codecOptions.map { if (it == "video/hevc") "HEVC / H.265" else "H.264 / AVC" }
                OptionRow("Codec", if (settings.recording.codecMime == "video/hevc") "HEVC / H.265" else "H.264 / AVC", labels) { value ->
                    viewModel.setCodec(if (value.startsWith("HEVC")) "video/hevc" else "video/avc")
                }
            }
            item {
                val bitrates = listOf(null, 10, 20, 30, 50, 80, 100)
                OptionRow("Bitrate", settings.recording.bitrate?.let { "$it Mbps" } ?: "Auto", bitrates.map { it?.let { v -> "$v Mbps" } ?: "Auto" }) { value ->
                    viewModel.setBitrate(value.removeSuffix(" Mbps").toIntOrNull()?.times(1_000_000))
                }
            }
            item {
                PreferenceSwitch("Microphone", "Record microphone audio when permission is granted.", settings.microphoneEnabled, viewModel::setMicrophone)
            }

            item { SectionTitle("Lens transition") }
            item {
                PreferenceSwitch("Smooth lens transition", "Animate real camera zoom requests rather than a preview-only effect.", settings.transition.enabled, viewModel::setTransitionEnabled)
            }
            item {
                Column {
                    Text("Duration", style = MaterialTheme.typography.titleSmall)
                    Text("${settings.transition.durationMs} ms", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = settings.transition.durationMs.toFloat(),
                        onValueChange = { viewModel.setTransitionDuration(it.toLong()) },
                        valueRange = 300f..1200f,
                        steps = 8,
                    )
                }
            }
            item {
                OptionRow(
                    title = "Curve",
                    selected = settings.transition.curve.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    options = TransitionCurve.entries.map { it.name.replace('_', ' ').lowercase().replaceFirstChar { c -> c.uppercase() } },
                ) { value -> viewModel.setTransitionCurve(TransitionCurve.entries.first { it.name.replace('_', ' ').equals(value, ignoreCase = true) }) }
            }

            item { SectionTitle("Diagnostics") }
            item {
                PreferenceSwitch("Developer overlay", "Show active physical camera, FPS, zoom and transition diagnostics.", settings.debugOverlayEnabled, viewModel::setDebug)
            }
            item {
                val selected = cap
                if (selected != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Selected camera", style = MaterialTheme.typography.titleMedium)
                            Text("ID ${selected.id} • ${selected.hardwareLevelLabel}")
                            Text("Zoom ${selected.zoomRatioRange.lower}× – ${selected.zoomRatioRange.upper}×")
                            Text("Logical multi-camera: ${selected.isLogicalMultiCamera}")
                            Text("Physical IDs: ${selected.physicalIds.ifEmpty { listOf("none") }.joinToString()}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
}

@Composable
private fun PreferenceSwitch(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ExposurePreference(state: CameraUiState, settings: CameraSettings, viewModel: CameraViewModel) {
    val cap = state.capabilities.firstOrNull { it.id == state.selectedCameraId }
    val min = cap?.aeCompensationRange?.lower ?: 0
    val max = cap?.aeCompensationRange?.upper ?: 0
    val step = if (max > min) 1f else 1f
    Column {
        Text("Exposure compensation", style = MaterialTheme.typography.titleSmall)
        Text("${settings.exposureCompensation} steps", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = settings.exposureCompensation.toFloat().coerceIn(min.toFloat(), max.toFloat()),
            onValueChange = { viewModel.setExposure(it.toInt()) },
            valueRange = min.toFloat()..max.toFloat().coerceAtLeast(min.toFloat() + step),
            steps = (max - min).coerceAtLeast(0),
            enabled = max > min,
        )
    }
}

@Composable
private fun OptionRow(title: String, selected: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Box {
            OutlinedButton(onClick = { expanded = true }) {
                Text(selected)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}

private fun resolutionLabel(width: Int, height: Int): String = when {
    width == 1280 && height == 720 -> "720p"
    width == 1920 && height == 1080 -> "1080p"
    width >= 3840 && height >= 2160 -> "4K"
    else -> String.format(Locale.US, "%dx%d", width, height)
}
