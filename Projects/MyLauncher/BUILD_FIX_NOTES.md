# MyLauncher compatibility notes

This project intentionally uses a conservative, explicit dependency set rather than chasing the newest AndroidX versions.

- Gradle: 8.13
- Android Gradle Plugin: 8.13.2
- Kotlin: 2.3.21
- Java/JDK: 17
- compileSdk: 36
- targetSdk: 36
- minSdk: 31 (Android 12)
- Compose Runtime/UI/Foundation/Animation: 1.11.4
- Material 3: 1.4.0
- AndroidX Core KTX: 1.17.0
- Activity Compose: 1.12.4
- Lifecycle: 2.9.4
- DataStore Preferences: 1.2.1

The Compose BOM is deliberately not used. This prevents an unrelated future BOM update from selecting a Compose generation that requires a newer compile SDK or Android Gradle Plugin.

The Android window theme uses `Theme.Material.NoActionBar` only as a host for the Compose content. The launcher itself is rendered entirely with Jetpack Compose Material 3.
