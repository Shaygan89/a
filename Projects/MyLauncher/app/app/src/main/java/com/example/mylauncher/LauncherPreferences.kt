package com.example.mylauncher

import android.content.Context
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private const val DATASTORE_NAME = "tool_launcher_preferences"

val Context.launcherDataStore by preferencesDataStore(name = DATASTORE_NAME)

object LauncherPreferenceKeys {
    val homePackages = stringSetPreferencesKey("home_packages")
}
