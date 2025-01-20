package com.thewizrd.shared_resources.data

import com.google.gson.annotations.SerializedName
import com.thewizrd.shared_resources.helpers.WearableHelper

data class AppItemData(
    @SerializedName(WearableHelper.KEY_LABEL) val label: String?,
    @SerializedName(WearableHelper.KEY_PKGNAME) val packageName: String?,
    @SerializedName(WearableHelper.KEY_ACTIVITYNAME) val activityName: String?,
    @SerializedName(WearableHelper.KEY_ICON) val iconBitmap: ByteArray?
) {
    val key = "${packageName}|${activityName}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AppItemData

        if (label != other.label) return false
        if (packageName != other.packageName) return false
        if (activityName != other.activityName) return false
        if (iconBitmap != null) {
            if (other.iconBitmap == null) return false
            if (!iconBitmap.contentEquals(other.iconBitmap)) return false
        } else if (other.iconBitmap != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = label?.hashCode() ?: 0
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (activityName?.hashCode() ?: 0)
        result = 31 * result + (iconBitmap?.contentHashCode() ?: 0)
        return result
    }
}