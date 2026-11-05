package com.example.mylauncher

/**
 * Minimal information needed by the launcher.
 *
 * We intentionally do not load app icons. The UI is text-only by design.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val activityName: String,
)
