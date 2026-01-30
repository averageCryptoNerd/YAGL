package dev.basicallyavg.yagl

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null
)

fun getInstalledApps(context: Context): List<AppInfo> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)
    
    val apps = packageManager.queryIntentActivities(intent, 0)
    return apps.map { resolveInfo ->
        val appName = resolveInfo.loadLabel(packageManager).toString()
        if (appName.isNotBlank()) {
            AppInfo(
                packageName = resolveInfo.activityInfo.packageName,
                name = appName,
                icon = resolveInfo.loadIcon(packageManager)
            )
        } else {
            null
        }
    }.filterNotNull()
        .distinctBy { it.packageName }
        .sortedBy { it.name.lowercase() }
}

fun launchApp(context: Context, packageName: String) {
    val packageManager = context.packageManager
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    intent?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}
