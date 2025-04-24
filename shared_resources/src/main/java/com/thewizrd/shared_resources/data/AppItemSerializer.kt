package com.thewizrd.shared_resources.data
// TODO: move to data

import android.util.Base64
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.thewizrd.shared_resources.helpers.WearableHelper

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