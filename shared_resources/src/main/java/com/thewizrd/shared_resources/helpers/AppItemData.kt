package com.thewizrd.shared_resources.helpers

import android.util.Base64
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

data class AppItemData(
    @SerializedName(WearableHelper.KEY_LABEL) val label: String?,
    @SerializedName(WearableHelper.KEY_PKGNAME) val packageName: String?,
    @SerializedName(WearableHelper.KEY_ACTIVITYNAME) val activityName: String?,
    @SerializedName(WearableHelper.KEY_ICON) val iconBitmap: ByteArray?
) {

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

object AppItemSerializer {
    fun AppItemData.serialize(writer: JsonWriter) {
        writer.beginObject()

        writer.name(WearableHelper.KEY_LABEL)
        writer.value(label)

        writer.name(WearableHelper.KEY_PKGNAME)
        writer.value(packageName)

        writer.name(WearableHelper.KEY_ACTIVITYNAME)
        writer.value(activityName)

        writer.name(WearableHelper.KEY_ICON)
        writer.value(iconBitmap?.let {
            Base64.encodeToString(it, Base64.DEFAULT)
        })

        writer.endObject()
    }

    fun deserializeItem(reader: JsonReader): AppItemData {
        var label: String? = null
        var packageName: String? = null
        var activityName: String? = null
        var iconBitmap: ByteArray? = null

        reader.beginObject()

        while (reader.hasNext() && reader.peek() != JsonToken.END_OBJECT) {
            val property = reader.nextName()

            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull()
                continue
            }

            when (property) {
                WearableHelper.KEY_LABEL -> {
                    label = reader.nextString()
                }
                WearableHelper.KEY_PKGNAME -> {
                    packageName = reader.nextString()
                }
                WearableHelper.KEY_ACTIVITYNAME -> {
                    activityName = reader.nextString()
                }
                WearableHelper.KEY_ICON -> {
                    iconBitmap = Base64.decode(reader.nextString(), Base64.DEFAULT)
                }
                else -> {
                    reader.skipValue()
                }
            }
        }

        if (reader.peek() == JsonToken.END_OBJECT)
            reader.endObject()

        return AppItemData(label, packageName, activityName, iconBitmap)
    }

    fun Iterable<AppItemData>.serialize(writer: JsonWriter) {
        writer.beginArray()

        this.forEach {
            it.serialize(writer)
        }

        writer.endArray()
    }

    fun deserialize(reader: JsonReader): List<AppItemData> {
        val items = ArrayList<AppItemData>()

        reader.beginArray()

        while (reader.hasNext() && reader.peek() != JsonToken.END_ARRAY) {
            val item = deserializeItem(reader)
            items.add(item)
        }

        reader.endArray()

        return items
    }
}