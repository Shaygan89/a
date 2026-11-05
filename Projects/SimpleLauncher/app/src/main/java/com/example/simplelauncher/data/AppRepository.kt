package com.example.simplelauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppInfo(
    val packageName: String,
    val label: String,
) {
    companion object {
        fun from(pm: PackageManager, info: android.content.pm.ApplicationInfo): AppInfo {
            return AppInfo(
                packageName = info.packageName,
                label = pm.getApplicationLabel(info).toString(),
            )
        }
    }
}

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy {
        context.getSharedPreferences("home_launcher_prefs", Context.MODE_PRIVATE)
    }

    /** All launchable apps installed on the device. */
    suspend fun getLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        // Apps that have a launcher entry (MAIN + LAUNCHER).
        val intent = Intent(Intent.ACTION_MAIN, null)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val seen = HashSet<String>()
        val result = ArrayList<AppInfo>()

        for (ri in resolveInfos) {
            val pkg = ri.activityInfo.packageName
            if (!seen.add(pkg)) continue
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                // Skip hidden / disabled apps.
                result.add(AppInfo.from(pm, appInfo))
            } catch (_: PackageManager.NameNotFoundException) {
                // ignore
            }
        }

        return@withContext result.sortedBy { it.label.lowercase() }
    }

    /** Apps the user has pinned to the home screen. */
    suspend fun getHomeApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val pkgs = prefs.getStringSet(KEY_HOME_APPS, emptySet()) ?: emptySet()
        return@withContext pkgs.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                AppInfo.from(pm, appInfo)
            } catch (_: PackageManager.NameNotFoundException) {
                null // app was uninstalled; drop it silently
            }
        }.sortedBy { it.label.lowercase() }
    }

    fun isHomeApp(packageName: String): Boolean =
        prefs.getStringSet(KEY_HOME_APPS, emptySet())?.contains(packageName) == true

    fun addHomeApp(packageName: String) {
        val current = HashSet(prefs.getStringSet(KEY_HOME_APPS, emptySet()) ?: emptySet())
        current.add(packageName)
        prefs.edit().putStringSet(KEY_HOME_APPS, current).apply()
    }

    fun removeHomeApp(packageName: String) {
        val current = HashSet(prefs.getStringSet(KEY_HOME_APPS, emptySet()) ?: emptySet())
        current.remove(packageName)
        prefs.edit().putStringSet(KEY_HOME_APPS, current).apply()
    }

    companion object {
        private const val KEY_HOME_APPS = "home_apps"
    }
}
