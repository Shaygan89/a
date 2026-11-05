package com.example.fingerprintscanner

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen QR scanner that uses the app's own camera.
 * This intentionally requests Android CAMERA permission instead of delegating
 * scanning to Google Play services.
 */
class QrScannerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_QR_VALUE = "com.example.fingerprintscanner.EXTRA_QR_VALUE"
        const val EXTRA_ERROR = "com.example.fingerprintscanner.EXTRA_QR_ERROR"
        private const val TAG = "FingerprintQrScanner"
    }

    private lateinit var previewView: PreviewView
    private lateinit var instructionView: TextView
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val resultDelivered = AtomicBoolean(false)

    private var cameraProvider: ProcessCameraProvider? = null
    private var barcodeScanner: BarcodeScanner? = null
    private var cameraStartRequested = false
    private var returningFromSettings = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            showPermissionDeniedDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScannerUi()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (returningFromSettings) {
            returningFromSettings = false
            if (hasCameraPermission()) {
                startCamera()
            } else {
                finishWithError("Camera permission was not granted. Allow Camera access in Android app settings to scan a pairing QR code.")
            }
        }
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        barcodeScanner?.close()
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    private fun buildScannerUi() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        instructionView = TextView(this).apply {
            text = "Point your camera at the FingerprintScanner QR code on Windows."
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC111111.toInt())
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        root.addView(
            instructionView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                setMargins(dp(16), dp(24), dp(16), 0)
            }
        )

        val cancelButton = Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                setResult(RESULT_CANCELED)
                finish()
            }
        }
        val footer = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), dp(20))
            addView(
                cancelButton,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        root.addView(
            footer,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(root)
    }

    private fun startCamera() {
        if (cameraStartRequested || isFinishing) return
        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        cameraStartRequested = true
        instructionView.text = "Starting camera…"

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val options = BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
                val scanner = BarcodeScanning.getClient(options)
                barcodeScanner = scanner

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    val inputImage = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    scanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            val qrValue = barcodes
                                .firstOrNull { it.format == Barcode.FORMAT_QR_CODE }
                                ?.rawValue

                            if (!qrValue.isNullOrBlank() && resultDelivered.compareAndSet(false, true)) {
                                runOnUiThread {
                                    if (!isFinishing) {
                                        instructionView.text = "QR code found. Returning to FingerprintScanner…"
                                        setResult(
                                            RESULT_OK,
                                            Intent().putExtra(EXTRA_QR_VALUE, qrValue)
                                        )
                                        finish()
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.w(TAG, "QR analysis failed", exception)
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                provider.unbindAll()
                provider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
                cameraProvider = provider
                instructionView.text = "Point your camera at the FingerprintScanner QR code on Windows."
            } catch (exception: Exception) {
                cameraStartRequested = false
                Log.e(TAG, "Unable to start camera", exception)
                finishWithError(
                    "Could not start the camera: ${exception.localizedMessage ?: exception.javaClass.simpleName}"
                )
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Camera permission required")
            .setMessage(
                "FingerprintScanner needs camera access only to scan the QR code displayed by your Windows PC. " +
                    "You can allow it in Android app settings."
            )
            .setPositiveButton("Open app settings") { _, _ ->
                returningFromSettings = true
                val settingsIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null)
                )
                startActivity(settingsIntent)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finishWithError("Camera permission was denied. Allow Camera access to scan the Windows QR code.")
            }
            .setOnCancelListener {
                finishWithError("Camera permission was denied. Allow Camera access to scan the Windows QR code.")
            }
            .show()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun finishWithError(message: String) {
        if (isFinishing) return
        setResult(RESULT_CANCELED, Intent().putExtra(EXTRA_ERROR, message))
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
