package com.example.smoothcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.smoothcamera.camera.CameraViewModel
import com.example.smoothcamera.ui.CameraInfoScreen
import com.example.smoothcamera.ui.CameraScreen
import com.example.smoothcamera.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<CameraViewModel> {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CameraViewModel(applicationContext) as T
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmoothLensTheme {
                CameraApp(viewModel)
            }
        }
    }
}

@Composable
private fun CameraApp(viewModel: CameraViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var screen by remember { mutableStateOf(AppScreen.CAMERA) }
    var pendingMicrophoneRequest by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        viewModel.initialize(granted, micGranted)
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        viewModel.updatePermissions(cameraGranted, granted)
        if (pendingMicrophoneRequest) {
            pendingMicrophoneRequest = false
            if (granted) viewModel.startRecording(displayRotationDegrees(context))
            else viewModel.startRecording(displayRotationDegrees(context))
        }
    }

    LaunchedEffect(Unit) {
        val cameraGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (!cameraGranted) cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        else viewModel.initialize(true, micGranted)
    }

    MaterialTheme {
        when (screen) {
            AppScreen.CAMERA -> CameraScreen(
                viewModel = viewModel,
                state = state,
                settings = settings,
                onOpenSettings = { screen = AppScreen.SETTINGS },
                onOpenInfo = { screen = AppScreen.INFO },
                onRequestMicrophone = {
                    pendingMicrophoneRequest = true
                    microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            )
            AppScreen.SETTINGS -> SettingsScreen(
                state = state,
                settings = settings,
                viewModel = viewModel,
                onBack = { screen = AppScreen.CAMERA },
            )
            AppScreen.INFO -> CameraInfoScreen(
                state = state,
                onBack = { screen = AppScreen.CAMERA },
            )
        }
    }
}

private enum class AppScreen { CAMERA, SETTINGS, INFO }

private fun displayRotationDegrees(context: android.content.Context): Int = when (context.display?.rotation ?: Surface.ROTATION_0) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}

@Composable
private fun SmoothLensTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val dynamic = android.os.Build.VERSION.SDK_INT >= 31
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
