# SmoothCamera

## Build toolchain used for this project

- Android Gradle Plugin: **8.12.0**
- Gradle wrapper: **8.13**
- Kotlin: **2.2.0**
- Compose Compiler Gradle plugin: **2.2.0**
- compileSdk / targetSdk: **36**
- JDK: **17**

The project intentionally uses Compose BOM **2025.05.01** and AndroidX Activity/Lifecycle versions from the same pre-Compose-1.12 era. Compose 1.12.x requires API 37/AGP 9.x, which cannot be used with the Android Studio/AGP ceiling of this project.

The camera implementation remains Camera2-based and dynamically inspects camera capabilities.
