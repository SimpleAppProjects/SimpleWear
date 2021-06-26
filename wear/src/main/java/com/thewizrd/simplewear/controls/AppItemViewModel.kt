package com.thewizrd.simplewear.controls

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import java.util.*

class AppItemViewModel : ViewModel() {
    var bitmapIcon: Bitmap? = null
    var appLabel: String? = null
    var packageName: String? = null
    var activityName: String? = null
    val key: String?
        get() {
            return if (packageName != null && activityName != null) {
                String.format(Locale.ROOT, "%s/%s", packageName, activityName)
            } else {
                null
            }
        }
    var appType: AppType = AppType.APP

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppItemViewModel

        if (bitmapIcon != other.bitmapIcon) return false
        if (appLabel != other.appLabel) return false
        if (packageName != other.packageName) return false
        if (activityName != other.activityName) return false
        if (appType != other.appType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bitmapIcon?.hashCode() ?: 0
        result = 31 * result + (appLabel?.hashCode() ?: 0)
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (activityName?.hashCode() ?: 0)
        result = 31 * result + appType.hashCode()
        return result
    }

    enum class AppType {
        APP,
        MUSIC_PLAYER
    }
}