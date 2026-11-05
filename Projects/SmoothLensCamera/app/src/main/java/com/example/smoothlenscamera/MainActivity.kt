package com.example.smoothlenscamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

class MainActivity : ComponentActivity() {
    private lateinit var cameraController: CameraController
    private var audioPermissionAsked = false
    private var pendingRecordAfterPermission = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        updatePermissions()
        if (!granted) pendingRecordAfterPermission = false
    }

    private val audioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissions()
        if (pendingRecordAfterPermission) {
            pendingRecordAfterPermission = false
            if (hasCameraPermission()) cameraController.startRecording()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        cameraController = CameraController(this)
        updatePermissions()

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color.White,
                    onPrimary = Color.Black,
                    background = Color.Black,
                    onBackground = Color.White,
                    surface = Color(0xFF151515),
                    onSurface = Color.White
                )
            ) {
                CameraScreen(
                    controller = cameraController,
                    onRequestCameraPermission = { requestCameraPermission() },
                    onRecordPressed = { handleRecordPressed() }
                )
            }
        }
        if (!hasCameraPermission()) requestCameraPermission()
    }

    override fun onResume() {
        super.onResume()
        if (::cameraController.isInitialized) {
            updatePermissions()
            cameraController.onResume()
        }
    }

    override fun onPause() {
        if (::cameraController.isInitialized) cameraController.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::cameraController.isInitialized) cameraController.release()
        super.onDestroy()
    }

    private fun handleRecordPressed() {
        if (!hasCameraPermission()) {
            requestCameraPermission()
            return
        }
        if (cameraController.uiState.isRecording) {
            cameraController.stopRecording()
            return
        }
        if (cameraController.uiState.isStartingRecording) return

        val hasAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasAudio && !audioPermissionAsked) {
            audioPermissionAsked = true
            pendingRecordAfterPermission = true
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            cameraController.startRecording()
        }
    }

    private fun requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun hasCameraPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun updatePermissions() {
        if (!::cameraController.isInitialized) return
        val cameraGranted = hasCameraPermission()
        val audioGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        cameraController.setPermissions(cameraGranted, audioGranted)
    }
}
