package com.thewizrd.simplewear.media

import android.graphics.Bitmap

data class MediaItemModel(val id: String) {
    var icon: Bitmap? = null
    var title: String? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaItemModel

        if (id != other.id) return false
        if (icon != other.icon) return false
        if (title != other.title) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (icon?.hashCode() ?: 0)
        result = 31 * result + (title?.hashCode() ?: 0)
        return result
    }
}