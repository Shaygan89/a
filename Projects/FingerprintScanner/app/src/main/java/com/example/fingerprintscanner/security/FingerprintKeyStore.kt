package com.example.fingerprintscanner.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Owns the phone's non-exportable signing key.
 *
 * The private key is generated inside Android Keystore. A successful strong
 * biometric authentication is required for each signing operation.
 */
class FingerprintKeyStore {

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "com.example.fingerprintscanner.windows-auth.v1"
    }

    private fun loadKeyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEY_STORE).apply {
            load(null)
        }
    }

    fun hasKey(): Boolean {
        return loadKeyStore().containsAlias(KEY_ALIAS)
    }

    /** Creates an EC P-256 key if it does not already exist. */
    fun createKey(): String {
        if (!hasKey()) {
            val parameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Zero seconds means authentication is required for every use.
                parameterSpec.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG
                )
            } else {
                // API 29 equivalent: -1 means authentication for every use.
                @Suppress("DEPRECATION")
                parameterSpec.setUserAuthenticationValidityDurationSeconds(-1)
            }

            val generator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEY_STORE
            )
            generator.initialize(parameterSpec.build())
            generator.generateKeyPair()
        }

        return getPublicKeyBase64()
    }

    /**
     * Prepares a signature operation. Pass this Signature to BiometricPrompt
     * as a CryptoObject; do not call sign() until authentication succeeds.
     */
    fun createSignatureForSigning(): Signature {
        val privateKey = loadKeyStore().getKey(KEY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("The phone key does not exist. Create it first.")

        return Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
        }
    }

    /** Returns the X.509 SubjectPublicKeyInfo encoding, Base64 without wrapping. */
    fun getPublicKeyBase64(): String {
        val publicKey = loadKeyStore().getCertificate(KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("The public key is unavailable. Create the key again.")

        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /** Returns SHA-256(publicKey), formatted as uppercase hexadecimal. */
    fun getPublicKeyFingerprint(): String {
        val publicKey = loadKeyStore().getCertificate(KEY_ALIAS)?.publicKey
            ?: throw IllegalStateException("The public key is unavailable. Create the key again.")

        val digest = MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
        return digest.joinToString(separator = "") { byte -> "%02X".format(byte) }
    }

    /** Deletes only this app's key pair; it does not remove phone fingerprints. */
    fun deleteKey() {
        val keyStore = loadKeyStore()
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }
}
