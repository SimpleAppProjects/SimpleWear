package com.thewizrd.shared_resources.helpers

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.thewizrd.shared_resources.BuildConfig
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.utils.Logger

object WearSettingsHelper {
    // Link to Play Store listing
    const val PACKAGE_NAME = "com.thewizrd.wearsettings"
    private const val SUPPORTED_VERSION_CODE = 1010000

    fun getPackageName(): String {
        var packageName = PACKAGE_NAME
        if (BuildConfig.DEBUG) packageName += ".debug"
        return packageName
    }

    fun isWearSettingsInstalled(): Boolean = try {
        val context = SimpleLibrary.instance.app.appContext
        context.packageManager.getApplicationInfo(getPackageName(), 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun launchWearSettings() {
        runCatching {
            val context = SimpleLibrary.instance.app.appContext

            val i = context.packageManager.getLaunchIntentForPackage(getPackageName())
            if (i != null) {
                context.startActivity(i)
            }
        }.onFailure {
            if (it !is PackageManager.NameNotFoundException) {
                Logger.writeLine(Log.ERROR, it)
            }
        }
    }

    private fun getWearSettingsVersionCode(): Int {
        val context = SimpleLibrary.instance.app.appContext
        val packageInfo = context.packageManager.getPackageInfo(getPackageName(), 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toInt()
        } else {
            packageInfo.versionCode
        }
    }

    fun isWearSettingsUpToDate(): Boolean {
        return getWearSettingsVersionCode() >= SUPPORTED_VERSION_CODE
    }

    fun getSettingsServiceComponent(): ComponentName {
        return ComponentName(getPackageName(), "$PACKAGE_NAME.SettingsService")
    }
}