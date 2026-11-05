# Student Helper

A Kotlin + Jetpack Compose student task app using Material 3.

## Included features

- Add homework or exams with a title and deadline.
- Save all tasks locally using Preferences DataStore. Tasks and completion states remain after closing or restarting the app.
- The top progress card counts **only tasks due tomorrow** and animates when their completion state changes.
- Mark tasks complete with a round Material 3 button. The button color and check icon animate.
- New tasks fade into the list; task removal and reordering use Compose lazy-list item animations.
- Long-press a task or tap its three-dot menu to rename or delete it.
- Filter the list by all tasks, homework, or exams.
- Uses Android 12+ dynamic system colors when available, plus a custom light/dark fallback palette.

## Required versions

These versions are kept exactly as requested:

- Android Gradle Plugin: `8.12.0`
- Kotlin: `2.0.21`
- Gradle: `8.13`
- Core KTX: `1.10.1`
- Lifecycle Runtime KTX: `2.6.1`
- Activity Compose: `1.8.0`
- Compose BOM: `2024.09.00`
- JUnit: `4.13.2`
- AndroidX Test JUnit: `1.1.5`
- Espresso: `3.5.1`

The project uses `compileSdk = 36`, `targetSdk = 36`, and `minSdk = 31` (Android 12).

## AI handoff

Read `AI_HANDOFF.md` before changing the app. It documents the product behavior, architecture, storage format, pinned versions, and development constraints.

## Performance notes

The UI observes DataStore with lifecycle-aware collection, JSON decoding happens on a background dispatcher, list-derived values are memoized, LazyColumn rows use stable keys/content types, and date labels/formatters are cached. The existing design and animation durations are preserved.

## Open in Android Studio

1. Extract the ZIP.
2. Open the extracted `StudentHelper` folder in Android Studio.
3. Use JDK 17 for Gradle when prompted.
4. Let Android Studio sync the project and download Gradle 8.13 / the required libraries.
5. Run the `app` configuration on an Android 12 or newer device or emulator.

The Gradle wrapper distribution is configured in `gradle/wrapper/gradle-wrapper.properties`.

## Storage note

Tasks are stored on the device in the app's private DataStore file. They survive app restarts and device reboots, but this is not cloud synchronization; clearing app data or uninstalling without a device backup removes the local data.
