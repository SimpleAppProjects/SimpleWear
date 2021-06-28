package com.thewizrd.shared_resources.sleeptimer

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.thewizrd.shared_resources.BuildConfig
import com.thewizrd.shared_resources.SimpleLibrary

object SleepTimerHelper {
    // Link to Play Store listing
    const val PACKAGE_NAME = "com.thewizrd.simplesleeptimer"
    private const val PLAY_STORE_APP_URI = "market://details?id=$PACKAGE_NAME"

    fun getPlayStoreURI(): Uri = Uri.parse(PLAY_STORE_APP_URI)

    fun getPackageName(): String {
        var packageName = PACKAGE_NAME
        if (BuildConfig.DEBUG) packageName += ".debug"
        return packageName
    }

    fun isSleepTimerInstalled(): Boolean = try {
        val context = SimpleLibrary.instance.app.appContext
        context.packageManager.getApplicationInfo(getPackageName(), 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun launchSleepTimer() {
        try {
            val context = SimpleLibrary.instance.app.appContext

            val directIntent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setComponent(ComponentName(getPackageName(), "$PACKAGE_NAME.SleepTimerActivity"))

            if (directIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(directIntent)
            } else {
                val i = context.packageManager.getLaunchIntentForPackage(getPackageName())
                if (i != null) {
                    context.startActivity(i)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
        }
    }
}