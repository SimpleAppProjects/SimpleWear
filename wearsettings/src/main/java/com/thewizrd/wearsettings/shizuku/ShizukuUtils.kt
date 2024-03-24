package com.thewizrd.wearsettings.shizuku

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

object ShizukuUtils {
    private const val PACKAGE_NAME = "moe.shizuku.privileged.api"

    // Link to Play Store listing
    private const val PLAY_STORE_APP_URI = "market://details?id=${PACKAGE_NAME}"
    private const val PLAY_STORE_APP_WEBURI =
        "https://play.google.com/store/apps/details?id=${PACKAGE_NAME}"

    fun getShizukuState(context: Context): ShizukuState {
        return if (Shizuku.pingBinder()) {
            if (isPermissionGranted(context)) {
                ShizukuState.RUNNING
            } else {
                ShizukuState.PERMISSION_DENIED
            }
        } else if (!isShizukuInstalled(context)) {
            ShizukuState.NOT_INSTALLED
        } else {
            ShizukuState.NOT_RUNNING
        }
    }

    fun isShizukuInstalled(context: Context): Boolean = try {
        context.packageManager.getApplicationInfo(PACKAGE_NAME, 0).enabled
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun isPermissionGranted(context: Context): Boolean {
        return if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            ContextCompat.checkSelfPermission(
                context,
                ShizukuProvider.PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermission(context: Activity, requestCode: Int) {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(ShizukuProvider.PERMISSION),
                requestCode
            )
        } else {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun requestPermission(context: Fragment, requestCode: Int) {
        if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) {
            context.requestPermissions(arrayOf(ShizukuProvider.PERMISSION), requestCode)
        } else {
            Shizuku.requestPermission(requestCode)
        }
    }

    fun openPlayStoreListing(context: Context) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .addCategory(Intent.CATEGORY_BROWSABLE)
                    .setData(getPlayStoreURI())
            )
        } catch (e: ActivityNotFoundException) {
            val i = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(getPlayStoreWebURI())

            if (i.resolveActivity(context.packageManager) != null) {
                context.startActivity(i)
            }
        }
    }

    fun startShizukuActivity(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(
                        PACKAGE_NAME, "moe.shizuku.manager.MainActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    private fun getPlayStoreURI(): Uri {
        return Uri.parse(PLAY_STORE_APP_URI)
    }

    private fun getPlayStoreWebURI(): Uri {
        return Uri.parse(PLAY_STORE_APP_WEBURI)
    }

    fun getUserId(): Int {
        val isRoot = Shizuku.getUid() == 0

        return if (isRoot) Process.myUserHandle().hashCode() else 0
    }
}

enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING,
    PERMISSION_DENIED,
    RUNNING
}