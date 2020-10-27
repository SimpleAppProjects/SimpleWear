package com.thewizrd.simplewear.controls

import android.graphics.Bitmap
import java.util.*

class MusicPlayerViewModel {
    var bitmapIcon: Bitmap? = null
    var appLabel: String? = null
    var packageName: String? = null
    var activityName: String? = null
    val key: String?
        get() {
            if (packageName != null && activityName != null) {
                return String.format(Locale.ROOT, "%s/%s", packageName, activityName)
            } else {
                return null
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MusicPlayerViewModel

        if (bitmapIcon != other.bitmapIcon) return false
        if (appLabel != other.appLabel) return false
        if (packageName != other.packageName) return false
        if (activityName != other.activityName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bitmapIcon?.hashCode() ?: 0
        result = 31 * result + (appLabel?.hashCode() ?: 0)
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (activityName?.hashCode() ?: 0)
        return result
    }
}