# MyLauncher

A text-first launcher for Android 12+ with Android 16/API 36 as the target.

## Main behavior

- Two swipeable pages: HOME and ALL APPS.
- Large ChatGPT section on the upper half of HOME.
- Selected apps appear by name only on the lower half of HOME.
- ALL APPS lists launchable applications alphabetically by name.
- Tap an app to launch it.
- Long-press an app for Open, Add/Remove from HOME, App Info, Uninstall, and Cancel.
- HOME selections persist with Preferences DataStore.
- App Info opens Android's application-details settings page.
- Uninstall uses Android's system uninstall confirmation.
- ChatGPT opens the installed ChatGPT app, or chatgpt.com if the app is not installed.
- The app exposes itself as an Android HOME role candidate.
- Compose animations are used for launcher entry and app-row interaction.

## Toolchain

Gradle 8.13, AGP 8.12.0, Kotlin 2.3.21, JDK 17, compileSdk/targetSdk 36, minSdk 31.

The Compose/AndroidX versions are intentionally explicit and conservative. There is no Compose BOM in this project.


Version 1.1 adds an embedded ChatGPT web surface to the HOME page. It uses Android WebView with JavaScript, DOM storage, and cookies enabled for the ChatGPT web app.
