package com.example.fingerprintscanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.fingerprintscanner.network.PairingQrParser
import com.example.fingerprintscanner.network.WindowsCompanionClient
import com.example.fingerprintscanner.security.FingerprintKeyStore
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.Executors

private data class FingerprintScannerUiState(
    val canAuthenticate: Boolean = false,
    val biometricMessage: String = "Checking biometric support…",
    val hasKey: Boolean = false,
    val publicKeyFingerprint: String = "",
    val status: String = "Create a phone key to begin.",
    val challengeBase64: String = "",
    val signatureBase64: String = "",
    val serverUrl: String = "",
    val certificateFingerprint: String = "",
    val pairingCode: String = "",
    val isConnected: Boolean = false,
    val isPaired: Boolean = false,
    val pcStatus: String = "Not connected yet.",
    val networkBusy: Boolean = false
)

private sealed class PendingOperation {
    object LocalTest : PendingOperation()

    data class PairPhone(
        val serverUrl: String,
        val certificateFingerprint: String,
        val pairingCode: String,
        val phoneName: String,
        val challengeId: String
    ) : PendingOperation()

    data class AuthenticateWithPc(
        val serverUrl: String,
        val certificateFingerprint: String,
        val challengeId: String
    ) : PendingOperation()
}

class MainActivity : FragmentActivity() {

    private val phoneKeyStore = FingerprintKeyStore()
    private val networkExecutor = Executors.newSingleThreadExecutor()

    private var uiState by mutableStateOf(FingerprintScannerUiState())
    private var pendingChallenge: ByteArray? = null
    private var pendingOperation: PendingOperation? = null
    private var scannedServerUrls: List<String> = emptyList()

    private lateinit var biometricPrompt: BiometricPrompt

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            handleScannedPairingQr(
                result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE)
            )
        } else {
            val scannerError = result.data?.getStringExtra(QrScannerActivity.EXTRA_ERROR)
            uiState = uiState.copy(
                networkBusy = false,
                status = scannerError ?: "QR scanning cancelled."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSavedConnectionSettings()
        configureBiometricPrompt()
        refreshUiState()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FingerprintScannerScreen(
                        state = uiState,
                        onScanPairingQr = ::scanPairingQr,
                        onCheckConnection = ::checkWindowsConnection,
                        onPairPhone = ::pairPhoneWithWindows,
                        onAuthenticateWithPc = ::authenticateWithWindows,
                        onCreateKey = ::createPhoneKey,
                        onTestSigning = ::testFingerprintSigning,
                        onCopyPublicKey = ::copyPublicKey,
                        onDeleteKey = ::deletePhoneKey
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        networkExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun loadSavedConnectionSettings() {
        val preferences = getSharedPreferences("windows_connection", Context.MODE_PRIVATE)
        uiState = uiState.copy(
            serverUrl = preferences.getString("server_url", "") ?: "",
            certificateFingerprint = preferences.getString("certificate_fingerprint", "") ?: ""
        )
    }

    private fun saveConnectionSettings() {
        getSharedPreferences("windows_connection", Context.MODE_PRIVATE)
            .edit()
            .putString("server_url", uiState.serverUrl)
            .putString("certificate_fingerprint", uiState.certificateFingerprint)
            .apply()
    }

    private fun scanPairingQr() {
        uiState = uiState.copy(status = "Opening the camera for QR scanning…")
        qrScannerLauncher.launch(Intent(this, QrScannerActivity::class.java))
    }

    private fun handleScannedPairingQr(rawValue: String?) {
        if (rawValue.isNullOrBlank()) {
            uiState = uiState.copy(status = "The scanned QR code was empty.")
            return
        }

        try {
            val pairingInfo = PairingQrParser.parse(rawValue)
            scannedServerUrls = pairingInfo.serverUrls
            uiState = uiState.copy(
                serverUrl = pairingInfo.serverUrls.first(),
                certificateFingerprint = pairingInfo.certificateSha256,
                pairingCode = pairingInfo.pairingCode,
                isConnected = false,
                isPaired = false,
                networkBusy = false,
                pcStatus = "QR code imported. Checking the Windows PC…",
                status = "QR code scanned successfully. Testing the available PC addresses."
            )
            saveConnectionSettings()
            checkWindowsConnection()
        } catch (exception: Exception) {
            uiState = uiState.copy(
                networkBusy = false,
                status = "This is not a valid FingerprintScanner pairing QR code: ${exception.message ?: "invalid data"}"
            )
        }
    }

    private fun configureBiometricPrompt() {
        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {

                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    super.onAuthenticationSucceeded(result)
                    completeBiometricSigning(result)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    super.onAuthenticationError(errorCode, errString)
                    pendingChallenge = null
                    pendingOperation = null
                    uiState = uiState.copy(
                        networkBusy = false,
                        status = "Authentication cancelled or failed: $errString"
                    )
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    uiState = uiState.copy(status = "Fingerprint not recognized. Try again.")
                }
            }
        )
    }

    private fun refreshUiState(status: String = uiState.status) {
        val biometricResult = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)

        val (canAuthenticate, biometricMessage) = when (biometricResult) {
            BiometricManager.BIOMETRIC_SUCCESS -> true to "Strong biometric authentication is available."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false to "This device has no supported biometric hardware."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> false to "Biometric hardware is temporarily unavailable."
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> false to "Enroll a fingerprint in Android Settings first."
            else -> false to "Strong biometric authentication is not currently available (code $biometricResult)."
        }

        val hasKey = runCatching { phoneKeyStore.hasKey() }.getOrDefault(false)
        val fingerprint = if (hasKey) {
            runCatching { phoneKeyStore.getPublicKeyFingerprint() }.getOrDefault("")
        } else {
            ""
        }

        uiState = uiState.copy(
            canAuthenticate = canAuthenticate,
            biometricMessage = biometricMessage,
            hasKey = hasKey,
            publicKeyFingerprint = fingerprint,
            status = status
        )
    }

    private fun createPhoneKey() {
        if (!uiState.canAuthenticate) {
            uiState = uiState.copy(status = uiState.biometricMessage)
            return
        }

        try {
            phoneKeyStore.createKey()
            uiState = uiState.copy(
                status = "Phone key created. Its private key is protected by Android Keystore.",
                challengeBase64 = "",
                signatureBase64 = ""
            )
            refreshUiState(uiState.status)
        } catch (exception: Exception) {
            uiState = uiState.copy(
                status = "Could not create the phone key: ${exception.message ?: exception.javaClass.simpleName}"
            )
        }
    }

    private fun testFingerprintSigning() {
        if (!uiState.hasKey) {
            uiState = uiState.copy(status = "Create the phone key before testing fingerprint signing.")
            return
        }
        if (!uiState.canAuthenticate) {
            uiState = uiState.copy(status = uiState.biometricMessage)
            return
        }

        val challenge = ByteArray(32)
        SecureRandom().nextBytes(challenge)
        startBiometricSigning(
            challenge = challenge,
            operation = PendingOperation.LocalTest,
            title = "Verify your fingerprint",
            subtitle = "Authorize this FingerprintScanner test"
        )
    }

    private fun checkWindowsConnection() {
        val certificateFingerprint = uiState.certificateFingerprint.trim()
        val candidates = (scannedServerUrls.ifEmpty { listOf(uiState.serverUrl.trim()) })
            .filter { it.isNotBlank() }
            .distinct()

        if (candidates.isEmpty() || certificateFingerprint.isBlank()) {
            uiState = uiState.copy(status = "Scan the QR code displayed by the Windows companion first.")
            return
        }

        uiState = uiState.copy(networkBusy = true, status = "Trying the Windows PC addresses from the QR code…")

        networkExecutor.execute {
            var selectedUrl: String? = null
            var selectedHealth: WindowsCompanionClient.PcHealth? = null
            val failures = mutableListOf<String>()

            for (candidate in candidates) {
                try {
                    val health = WindowsCompanionClient.checkConnection(candidate, certificateFingerprint)
                    selectedUrl = candidate
                    selectedHealth = health
                    break
                } catch (exception: Exception) {
                    failures += "$candidate → ${exception.message ?: exception.javaClass.simpleName}"
                }
            }

            runOnUiThread {
                val connectedUrl = selectedUrl
                val health = selectedHealth
                if (connectedUrl != null && health != null) {
                    scannedServerUrls = listOf(connectedUrl)
                    uiState = uiState.copy(
                        serverUrl = connectedUrl,
                        networkBusy = false,
                        isConnected = true,
                        isPaired = health.paired,
                        pcStatus = "Connected to ${health.serverName} at $connectedUrl.",
                        status = if (health.paired) {
                            "Connection verified. This PC already has a paired phone."
                        } else {
                            "Connection verified. Pair this phone to continue."
                        }
                    )
                    saveConnectionSettings()
                } else {
                    uiState = uiState.copy(
                        networkBusy = false,
                        isConnected = false,
                        isPaired = false,
                        pcStatus = "Could not reach the Windows companion.",
                        status = buildString {
                            append("Every address in the QR code failed. ")
                            append(failures.joinToString(" | ").take(1400))
                            append(" Check that the companion is running, Windows Firewall allows TCP 47621 on Private networks, and the Wi-Fi router does not isolate wireless clients.")
                        }
                    )
                }
            }
        }
    }

    private fun pairPhoneWithWindows() {
        if (!uiState.hasKey) {
            uiState = uiState.copy(status = "Create the phone key before pairing.")
            return
        }
        if (!uiState.canAuthenticate) {
            uiState = uiState.copy(status = uiState.biometricMessage)
            return
        }

        if (!uiState.isConnected) {
            uiState = uiState.copy(status = "Scan the Windows QR code and wait until the PC connection is verified.")
            return
        }

        val serverUrl = uiState.serverUrl.trim()
        val certificateFingerprint = uiState.certificateFingerprint.trim()
        val pairingCode = uiState.pairingCode
        val phoneName = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80)

        if (serverUrl.isBlank() || certificateFingerprint.isBlank()) {
            uiState = uiState.copy(status = "Enter the PC HTTPS address and certificate fingerprint first.")
            return
        }
        if (pairingCode.length != 8) {
            uiState = uiState.copy(status = "Enter the eight-digit pairing code shown on Windows.")
            return
        }

        uiState = uiState.copy(networkBusy = true, status = "Requesting a one-time pairing challenge…")

        networkExecutor.execute {
            try {
                val challenge = WindowsCompanionClient.requestPairingChallenge(
                    serverUrl = serverUrl,
                    certificateFingerprint = certificateFingerprint,
                    pairingCode = pairingCode,
                    phoneName = phoneName
                )

                runOnUiThread {
                    startBiometricSigning(
                        challenge = challenge.challengeBytes,
                        operation = PendingOperation.PairPhone(
                            serverUrl = serverUrl,
                            certificateFingerprint = certificateFingerprint,
                            pairingCode = pairingCode,
                            phoneName = phoneName,
                            challengeId = challenge.challengeId
                        ),
                        title = "Pair with your Windows PC",
                        subtitle = "Use your fingerprint to approve this phone"
                    )
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        networkBusy = false,
                        isConnected = false,
                        status = friendlyNetworkError(exception)
                    )
                }
            }
        }
    }

    private fun authenticateWithWindows() {
        if (!uiState.hasKey) {
            uiState = uiState.copy(status = "Create the phone key before authenticating.")
            return
        }
        if (!uiState.canAuthenticate) {
            uiState = uiState.copy(status = uiState.biometricMessage)
            return
        }
        if (!uiState.isPaired) {
            uiState = uiState.copy(status = "Connect and pair this phone with Windows first.")
            return
        }

        val serverUrl = uiState.serverUrl.trim()
        val certificateFingerprint = uiState.certificateFingerprint.trim()

        uiState = uiState.copy(networkBusy = true, status = "Requesting a fresh Windows authentication challenge…")

        networkExecutor.execute {
            try {
                val challenge = WindowsCompanionClient.requestAuthenticationChallenge(
                    serverUrl = serverUrl,
                    certificateFingerprint = certificateFingerprint
                )

                runOnUiThread {
                    startBiometricSigning(
                        challenge = challenge.challengeBytes,
                        operation = PendingOperation.AuthenticateWithPc(
                            serverUrl = serverUrl,
                            certificateFingerprint = certificateFingerprint,
                            challengeId = challenge.challengeId
                        ),
                        title = "Authenticate to Windows",
                        subtitle = "Your fingerprint authorizes this sign-in test"
                    )
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        networkBusy = false,
                        isConnected = false,
                        isPaired = false,
                        status = friendlyNetworkError(exception)
                    )
                }
            }
        }
    }

    private fun startBiometricSigning(
        challenge: ByteArray,
        operation: PendingOperation,
        title: String,
        subtitle: String
    ) {
        try {
            val signature = phoneKeyStore.createSignatureForSigning()
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText("Cancel")
                .build()

            pendingChallenge = challenge
            pendingOperation = operation
            uiState = uiState.copy(
                networkBusy = false,
                status = "Waiting for fingerprint approval…",
                challengeBase64 = "",
                signatureBase64 = ""
            )

            biometricPrompt.authenticate(
                promptInfo,
                BiometricPrompt.CryptoObject(signature)
            )
        } catch (exception: Exception) {
            pendingChallenge = null
            pendingOperation = null
            uiState = uiState.copy(
                networkBusy = false,
                status = "Could not start fingerprint signing: ${exception.message ?: exception.javaClass.simpleName}"
            )
        }
    }

    private fun completeBiometricSigning(result: BiometricPrompt.AuthenticationResult) {
        val challenge = pendingChallenge
        val operation = pendingOperation
        pendingChallenge = null
        pendingOperation = null

        if (challenge == null || operation == null) {
            uiState = uiState.copy(status = "The authentication request expired. Please try again.")
            return
        }

        try {
            val signature = result.cryptoObject?.signature
                ?: throw IllegalStateException("Android did not return the signing operation.")

            signature.update(challenge)
            val signatureBytes = signature.sign()

            when (operation) {
                is PendingOperation.LocalTest -> {
                    uiState = uiState.copy(
                        status = "Fingerprint accepted. The test challenge was signed successfully.",
                        challengeBase64 = Base64.encodeToString(challenge, Base64.NO_WRAP),
                        signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
                    )
                }

                is PendingOperation.PairPhone -> completePairing(operation, signatureBytes)
                is PendingOperation.AuthenticateWithPc -> completePcAuthentication(operation, signatureBytes)
            }
        } catch (exception: Exception) {
            uiState = uiState.copy(
                networkBusy = false,
                status = "Could not sign the challenge: ${exception.message ?: exception.javaClass.simpleName}"
            )
        }
    }

    private fun completePairing(
        operation: PendingOperation.PairPhone,
        signatureBytes: ByteArray
    ) {
        uiState = uiState.copy(networkBusy = true, status = "Verifying the signed pairing challenge on Windows…")

        networkExecutor.execute {
            try {
                WindowsCompanionClient.completePairing(
                    serverUrl = operation.serverUrl,
                    certificateFingerprint = operation.certificateFingerprint,
                    pairingCode = operation.pairingCode,
                    phoneName = operation.phoneName,
                    challengeId = operation.challengeId,
                    publicKeyBase64 = phoneKeyStore.getPublicKeyBase64(),
                    signatureBytes = signatureBytes
                )

                runOnUiThread {
                    uiState = uiState.copy(
                        networkBusy = false,
                        isConnected = true,
                        isPaired = true,
                        pairingCode = "",
                        pcStatus = "Phone paired successfully.",
                        status = "Pairing successful. Windows has stored your phone's public key."
                    )
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        networkBusy = false,
                        isPaired = false,
                        status = friendlyNetworkError(exception)
                    )
                }
            }
        }
    }

    private fun completePcAuthentication(
        operation: PendingOperation.AuthenticateWithPc,
        signatureBytes: ByteArray
    ) {
        uiState = uiState.copy(networkBusy = true, status = "Windows is verifying your signed challenge…")

        networkExecutor.execute {
            try {
                val result = WindowsCompanionClient.verifyAuthentication(
                    serverUrl = operation.serverUrl,
                    certificateFingerprint = operation.certificateFingerprint,
                    challengeId = operation.challengeId,
                    signatureBytes = signatureBytes
                )

                runOnUiThread {
                    uiState = uiState.copy(
                        networkBusy = false,
                        isConnected = true,
                        isPaired = result.authenticated,
                        pcStatus = if (result.authenticated) "Phone authentication verified." else "Authentication rejected.",
                        status = if (result.authenticated) {
                            "Success! Windows verified the phone's fingerprint-authorized signature."
                        } else {
                            "Windows rejected the authentication proof. Pair the phone again if necessary."
                        }
                    )
                }
            } catch (exception: Exception) {
                runOnUiThread {
                    uiState = uiState.copy(
                        networkBusy = false,
                        isConnected = false,
                        status = friendlyNetworkError(exception)
                    )
                }
            }
        }
    }

    private fun friendlyNetworkError(exception: Exception): String {
        val message = exception.message?.takeIf { it.isNotBlank() }
            ?: exception.javaClass.simpleName
        return when (exception) {
            is IOException -> message
            else -> "Operation failed: $message"
        }
    }

    private fun copyPublicKey() {
        try {
            val publicKey = phoneKeyStore.getPublicKeyBase64()
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FingerprintScanner public key", publicKey))
            uiState = uiState.copy(status = "Public key copied to clipboard.")
        } catch (exception: Exception) {
            uiState = uiState.copy(
                status = "Could not copy the public key: ${exception.message ?: exception.javaClass.simpleName}"
            )
        }
    }

    private fun deletePhoneKey() {
        try {
            phoneKeyStore.deleteKey()
            pendingChallenge = null
            pendingOperation = null
            uiState = uiState.copy(
                isPaired = false,
                status = "Phone key deleted. Create a new key and pair again to restore Windows authentication.",
                challengeBase64 = "",
                signatureBase64 = ""
            )
            refreshUiState(uiState.status)
        } catch (exception: Exception) {
            uiState = uiState.copy(
                status = "Could not delete the phone key: ${exception.message ?: exception.javaClass.simpleName}"
            )
        }
    }
}

@Composable
private fun FingerprintScannerScreen(
    state: FingerprintScannerUiState,
    onScanPairingQr: () -> Unit,
    onCheckConnection: () -> Unit,
    onPairPhone: () -> Unit,
    onAuthenticateWithPc: () -> Unit,
    onCreateKey: () -> Unit,
    onTestSigning: () -> Unit,
    onCopyPublicKey: () -> Unit,
    onDeleteKey: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "FingerprintScanner",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Use your phone's biometric sensor to approve secure authentication requests from your Windows PC.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Biometric readiness", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.canAuthenticate) "Available" else "Not ready",
                    color = if (state.canAuthenticate) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
                Text(state.biometricMessage, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Windows PC connection", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Scan the QR code shown by the Windows companion. The address, certificate pin, and temporary pairing code will be imported automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onScanPairingQr,
                    enabled = !state.networkBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan Windows QR code")
                }

                if (state.serverUrl.isNotBlank()) {
                    Text("Windows PC address", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(state.serverUrl, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (state.certificateFingerprint.isNotBlank()) "Certificate pin imported from QR code"
                        else "Certificate pin not loaded",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (state.pairingCode.length == 8) "Temporary pairing code imported"
                        else "Pairing code not loaded. Scan a fresh QR code to pair.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onCheckConnection,
                    enabled = !state.networkBusy && state.serverUrl.isNotBlank() && state.certificateFingerprint.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Retry PC connection")
                }

                Button(
                    onClick = onPairPhone,
                    enabled = !state.networkBusy && state.isConnected && state.canAuthenticate && state.hasKey && state.pairingCode.length == 8,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Pair this phone")
                }

                Button(
                    onClick = onAuthenticateWithPc,
                    enabled = !state.networkBusy && state.isConnected && state.canAuthenticate && state.hasKey && state.isPaired,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test fingerprint authentication")
                }

                if (state.networkBusy) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator()
                        Text("Communicating with Windows…")
                    }
                }

                Text(
                    text = state.pcStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Phone signing key", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.hasKey) "Created on this device" else "Not created yet",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (state.publicKeyFingerprint.isNotBlank()) {
                    Text("Public-key SHA-256 fingerprint", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(
                            state.publicKeyFingerprint.chunked(2).joinToString(" "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        Button(
            onClick = onCreateKey,
            enabled = state.canAuthenticate && !state.hasKey && !state.networkBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create phone key")
        }

        OutlinedButton(
            onClick = onTestSigning,
            enabled = state.canAuthenticate && state.hasKey && !state.networkBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Test local fingerprint signing")
        }

        OutlinedButton(
            onClick = onCopyPublicKey,
            enabled = state.hasKey && !state.networkBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Copy public key")
        }

        OutlinedButton(
            onClick = onDeleteKey,
            enabled = state.hasKey && !state.networkBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Delete phone key")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Text(state.status, style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (state.challengeBase64.isNotBlank() && state.signatureBase64.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Local signing test result", style = MaterialTheme.typography.titleMedium)
                    Text("Challenge (Base64)", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(state.challengeBase64, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("ECDSA signature (Base64)", style = MaterialTheme.typography.labelMedium)
                    SelectionContainer {
                        Text(state.signatureBase64, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text(
            text = "This milestone pairs the phone with a Windows companion and tests challenge-response authentication. It does not yet unlock the Windows lock screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
