FingerprintScanner — Android companion starter

This is the Android/phone half of the project. It creates an EC P-256 key pair in Android Keystore and can sign a fresh test challenge only after Android's strong biometric prompt succeeds. The private key never leaves Android Keystore.

IMPORTANT: This starter does NOT unlock Windows yet. The Windows service and Windows Credential Provider are separate components that must be built and paired with this Android app. No Windows password is collected or stored by this Android app.

Pinned project versions retained exactly from your request:
- Android Gradle Plugin: 8.12.0
- Kotlin: 2.0.21
- Core KTX: 1.10.1
- JUnit: 4.13.2
- AndroidX Test JUnit: 1.1.5
- Espresso: 3.5.1
- Lifecycle Runtime KTX: 2.6.1
- Activity Compose: 1.8.0
- Compose BOM: 2024.09.00

One additional dependency is used for biometric authentication: androidx.biometric:biometric:1.1.0.

INSTALL INTO YOUR EXISTING PROJECT
1. Make a backup of your project.
2. Copy the gradle/libs.versions.toml from this package into your project's gradle folder. It contains your exact versions plus the biometric dependency and Compose plugin alias.
3. Replace the root build.gradle.kts with this package's build.gradle.kts.
4. Replace app/build.gradle.kts with this package's app/build.gradle.kts.
5. Copy the app/src/main/AndroidManifest.xml and the Kotlin source tree into the matching locations.
6. In Android Studio, set Gradle JDK to JDK 17. AGP 8.12.0 requires Gradle 8.13 and JDK 17. The Gradle wrapper distribution should therefore be gradle-8.13-bin.zip.
7. Ensure Android SDK Platform 36 is installed. This module uses compileSdk 36 / targetSdk 36 / minSdk 29.
8. Sync Gradle, then run on your phone.

PACKAGE NAME
The source package and Gradle namespace are com.example.fingerprintscanner. If your Android Studio project was created with a different package, either use this package consistently or tell me the package name and I will adapt the files.

FIRST RUN
1. Make sure at least one fingerprint is enrolled in Android Settings.
2. Open FingerprintScanner.
3. Tap Create phone key.
4. Tap Test fingerprint signing and authenticate with your fingerprint.
5. The app will show a challenge and a signature. This proves the phone can produce a cryptographic proof after biometric authentication.
6. Copy the public key and keep it private to your own pairing flow. It is not a secret, but do not share it publicly unless you intend to.

SECURITY NOTES
- The app asks Android to perform strong biometric authentication. Android controls which strong biometric modality is used; the app does not receive fingerprint images or templates.
- The private key is generated in Android Keystore and is not exportable by this app.
- Adding/removing enrolled biometrics may invalidate the key. If authentication signing stops working after changing fingerprints, use Delete phone key and create a new one, then pair again.
- Keep your normal Windows password/PIN sign-in available as a recovery method.
