package com.example.fingerprintscanner.network

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * HTTPS client for the FingerprintScanner Windows companion.
 *
 * The Windows companion uses a self-signed certificate. We trust only the exact
 * certificate whose SHA-256 fingerprint the user entered, and we still retain
 * normal hostname verification against the certificate's Subject Alternative Name.
 */
object WindowsCompanionClient {

    data class PcHealth(
        val serverName: String,
        val paired: Boolean,
        val protocolVersion: Int
    )

    data class ServerChallenge(
        val challengeId: String,
        val challengeBytes: ByteArray
    )

    data class AuthenticationResult(
        val authenticated: Boolean,
        val phoneName: String
    )

    fun checkConnection(serverUrl: String, certificateFingerprint: String): PcHealth {
        val json = request(
            serverUrl = serverUrl,
            certificateFingerprint = certificateFingerprint,
            path = "/api/v1/health",
            method = "GET"
        )

        return PcHealth(
            serverName = json.optString("serverName", "Windows PC"),
            paired = json.optBoolean("paired", false),
            protocolVersion = json.optInt("protocolVersion", 0)
        )
    }

    fun requestPairingChallenge(
        serverUrl: String,
        certificateFingerprint: String,
        pairingCode: String,
        phoneName: String
    ): ServerChallenge {
        val body = JSONObject()
            .put("pairingCode", pairingCode)
            .put("phoneName", phoneName)

        return parseChallenge(
            request(
                serverUrl = serverUrl,
                certificateFingerprint = certificateFingerprint,
                path = "/api/v1/pair/challenge",
                method = "POST",
                body = body
            )
        )
    }

    fun completePairing(
        serverUrl: String,
        certificateFingerprint: String,
        pairingCode: String,
        phoneName: String,
        challengeId: String,
        publicKeyBase64: String,
        signatureBytes: ByteArray
    ) {
        val body = JSONObject()
            .put("pairingCode", pairingCode)
            .put("phoneName", phoneName)
            .put("challengeId", challengeId)
            .put("publicKeyBase64", publicKeyBase64)
            .put("signatureBase64", Base64.encodeToString(signatureBytes, Base64.NO_WRAP))

        request(
            serverUrl = serverUrl,
            certificateFingerprint = certificateFingerprint,
            path = "/api/v1/pair/complete",
            method = "POST",
            body = body
        )
    }

    fun requestAuthenticationChallenge(
        serverUrl: String,
        certificateFingerprint: String
    ): ServerChallenge {
        return parseChallenge(
            request(
                serverUrl = serverUrl,
                certificateFingerprint = certificateFingerprint,
                path = "/api/v1/auth/challenge",
                method = "POST",
                body = JSONObject()
            )
        )
    }

    fun verifyAuthentication(
        serverUrl: String,
        certificateFingerprint: String,
        challengeId: String,
        signatureBytes: ByteArray
    ): AuthenticationResult {
        val body = JSONObject()
            .put("challengeId", challengeId)
            .put("signatureBase64", Base64.encodeToString(signatureBytes, Base64.NO_WRAP))

        val json = request(
            serverUrl = serverUrl,
            certificateFingerprint = certificateFingerprint,
            path = "/api/v1/auth/verify",
            method = "POST",
            body = body
        )

        return AuthenticationResult(
            authenticated = json.optBoolean("authenticated", false),
            phoneName = json.optString("phoneName", "Phone")
        )
    }

    private fun parseChallenge(json: JSONObject): ServerChallenge {
        val challengeId = json.optString("challengeId")
        val challengeBase64 = json.optString("challengeBase64")

        if (challengeId.isBlank() || challengeBase64.isBlank()) {
            throw IOException("The Windows companion returned an incomplete challenge.")
        }

        val challengeBytes = try {
            Base64.decode(challengeBase64, Base64.DEFAULT)
        } catch (exception: IllegalArgumentException) {
            throw IOException("The Windows companion returned an invalid challenge.", exception)
        }

        if (challengeBytes.size != 32) {
            throw IOException("The Windows companion returned a challenge with an invalid length.")
        }

        return ServerChallenge(challengeId, challengeBytes)
    }

    private fun request(
        serverUrl: String,
        certificateFingerprint: String,
        path: String,
        method: String,
        body: JSONObject? = null
    ): JSONObject {
        val baseUrl = serverUrl.trim().trimEnd('/')
        if (!baseUrl.startsWith("https://", ignoreCase = true)) {
            throw IOException("Use an HTTPS address, for example https://192.168.1.20:47621.")
        }

        val normalizedFingerprint = certificateFingerprint
            .filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
            .uppercase()

        if (normalizedFingerprint.length != 64) {
            throw IOException("The certificate fingerprint must contain 64 hexadecimal characters (SHA-256).")
        }

        val expectedCertificateHash = try {
            normalizedFingerprint.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (exception: NumberFormatException) {
            throw IOException("The certificate fingerprint is not valid hexadecimal.", exception)
        }

        val url = URL("$baseUrl$path")
        val connection = (url.openConnection() as? HttpsURLConnection)
            ?: throw IOException("Could not create an HTTPS connection.")

        try {
            connection.requestMethod = method
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")

            val trustManager = PinnedCertificateTrustManager(expectedCertificateHash)
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), null)
            }
            connection.sslSocketFactory = sslContext.socketFactory
            // This is a self-signed, locally paired server. The exact certificate is
            // pinned in PinnedCertificateTrustManager. Do not additionally require the
            // certificate's old IP Subject Alternative Name to match: home routers can
            // assign a new LAN IP after the certificate was first created.
            connection.hostnameVerifier = HostnameVerifier { _, session ->
                try {
                    val peerCertificate = session.peerCertificates.firstOrNull() as? X509Certificate
                        ?: return@HostnameVerifier false
                    val actualHash = MessageDigest.getInstance("SHA-256")
                        .digest(peerCertificate.encoded)
                    MessageDigest.isEqual(expectedCertificateHash, actualHash)
                } catch (_: Exception) {
                    false
                }
            }

            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }

            val responseCode = connection.responseCode
            val responseStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

            if (responseCode !in 200..299) {
                val serverMessage = runCatching {
                    JSONObject(responseText).optString("error")
                }.getOrDefault("")

                val message = if (serverMessage.isNotBlank()) {
                    serverMessage
                } else {
                    "Windows companion returned HTTP $responseCode."
                }
                throw IOException(message)
            }

            return if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } catch (exception: javax.net.ssl.SSLException) {
            throw IOException(
                "TLS certificate verification failed. Scan the QR code from the currently running Windows companion.",
                exception
            )
        } catch (exception: ConnectException) {
            throw IOException(
                "The PC refused the connection. Check that the companion is running and Windows Firewall allows TCP port 47621 on Private networks.",
                exception
            )
        } catch (exception: SocketTimeoutException) {
            throw IOException(
                "The PC did not respond before the timeout. Check Windows Firewall and make sure the Wi-Fi router does not isolate connected devices.",
                exception
            )
        } catch (exception: UnknownHostException) {
            throw IOException("The PC address from the QR code could not be resolved.", exception)
        } finally {
            connection.disconnect()
        }
    }

    private class PinnedCertificateTrustManager(
        private val expectedSha256: ByteArray
    ) : X509TrustManager {

        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            throw CertificateException("Client certificates are not accepted by this client.")
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            if (chain.isNullOrEmpty()) {
                throw CertificateException("The Windows companion did not present a certificate.")
            }

            val certificate = chain[0]
            certificate.checkValidity()
            val actualSha256 = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)

            if (!MessageDigest.isEqual(expectedSha256, actualSha256)) {
                throw CertificateException("The Windows PC certificate does not match the saved fingerprint.")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
}
