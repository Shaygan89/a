package com.example.mylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

class AppRepository(context: Context) {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager

    fun loadLaunchableApps(): List<AppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        return packageManager
            .queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .mapNotNull { resolveInfo ->
                val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                val packageName = activityInfo.packageName

                // This launcher is intentionally not listed in its own app drawer.
                if (packageName == appContext.packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(packageManager)?.toString()?.trim().orEmpty()
                if (label.isBlank()) return@mapNotNull null

                AppInfo(
                    packageName = packageName,
                    label = label,
                    activityName = activityInfo.name,
                )
            }
            .distinctBy(AppInfo::packageName)
            .sortedWith(compareBy<AppInfo, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.packageName })
            .toList()
    }
}
