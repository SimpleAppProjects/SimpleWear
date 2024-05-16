package com.thewizrd.wearsettings.shizuku

import android.Manifest
import android.content.Context
import android.content.pm.IPackageManager
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

fun Context.grantPermissionThroughShizuku(permission: String): Boolean {
    return try {
        val packageMgr = SystemServiceHelper.getSystemService("package")
            .let(::ShizukuBinderWrapper)
            .let(IPackageManager.Stub::asInterface)

        val userId = ShizukuUtils.getUserId()

        packageMgr.grantRuntimePermission(packageName, permission, userId)

        true
    } catch (e: IllegalStateException) {
        false
    }
}

fun Context.grantSecureSettingsPermission(): Boolean {
    return grantPermissionThroughShizuku(Manifest.permission.WRITE_SECURE_SETTINGS)
}