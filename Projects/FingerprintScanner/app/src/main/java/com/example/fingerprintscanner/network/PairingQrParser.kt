package com.example.fingerprintscanner.network

import org.json.JSONObject
import java.net.URI
import java.io.IOException

object PairingQrParser {

    data class PairingInfo(
        val serverUrls: List<String>,
        val certificateSha256: String,
        val pairingCode: String
    )

    fun parse(rawValue: String): PairingInfo {
        val json = try {
            JSONObject(rawValue)
        } catch (exception: Exception) {
            throw IOException("The QR data is not valid JSON.", exception)
        }

        if (json.optString("type") != "FingerprintScannerPairing") {
            throw IOException("This QR code was not created by FingerprintScanner Windows Companion.")
        }
        if (json.optInt("version", -1) != 1) {
            throw IOException("This QR code uses an unsupported pairing format.")
        }

        val expiresAt = json.optLong("expiresAtUtcEpochSeconds", 0L)
        val now = System.currentTimeMillis() / 1000L
        if (expiresAt == 0L || expiresAt < now - 10L) {
            throw IOException("This pairing QR code has expired. Restart the Windows companion to generate a fresh one.")
        }

        val pin = json.optString("certificateSha256", "").uppercase()
        if (!pin.matches(Regex("^[0-9A-F]{64}$"))) {
            throw IOException("The QR code contains an invalid PC certificate fingerprint.")
        }

        val pairingCode = json.optString("pairingCode", "")
        if (!pairingCode.matches(Regex("^[0-9]{8}$"))) {
            throw IOException("The QR code contains an invalid temporary pairing code.")
        }

        val urlsJson = json.optJSONArray("serverUrls")
            ?: throw IOException("The QR code does not contain any Windows PC addresses.")
        if (urlsJson.length() !in 1..10) {
            throw IOException("The QR code contains an invalid number of PC addresses.")
        }

        val urls = buildList {
            for (index in 0 until urlsJson.length()) {
                val value = urlsJson.optString(index, "").trim()
                val uri = try {
                    URI(value)
                } catch (exception: Exception) {
                    throw IOException("A Windows PC address in the QR code is malformed.", exception)
                }
                if (!uri.scheme.equals("https", ignoreCase = true) ||
                    uri.host.isNullOrBlank() ||
                    uri.port != 47621 ||
                    uri.rawPath.orEmpty().isNotEmpty() && uri.rawPath != "/" ||
                    uri.rawQuery != null || uri.rawFragment != null
                ) {
                    throw IOException("The QR code contains an unexpected PC address. Expected HTTPS on port 47621.")
                }
                add(uri.toASCIIString().trimEnd('/'))
            }
        }.distinct()

        if (urls.isEmpty()) {
            throw IOException("The QR code contains no usable PC addresses.")
        }

        return PairingInfo(
            serverUrls = urls,
            certificateSha256 = pin,
            pairingCode = pairingCode
        )
    }
}
