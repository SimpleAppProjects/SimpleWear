package com.thewizrd.simplewear.datastore.media

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.thewizrd.shared_resources.data.AppItemData
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.stringToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

private object MediaDataCacheStateSerializer : Serializer<MediaDataCache> {
    override val defaultValue: MediaDataCache
        get() = MediaDataCache()

    override suspend fun readFrom(input: InputStream): MediaDataCache {
        return JSONParser.deserializer(input, MediaDataCache::class.java) ?: defaultValue
    }

    override suspend fun writeTo(t: MediaDataCache, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(JSONParser.serializer(t, MediaDataCache::class.java).stringToBytes())
        }
    }
}

private object ArtworkCacheSerializer : Serializer<ByteArray> {
    override val defaultValue: ByteArray
        get() = byteArrayOf()

    override suspend fun readFrom(input: InputStream): ByteArray {
        return input.readBytes()
    }

    override suspend fun writeTo(t: ByteArray, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(t)
        }
    }
}

private object AppItemCacheSerializer : Serializer<AppItemData> {
    override val defaultValue: AppItemData
        get() = AppItemData(null, null, null, null)

    override suspend fun readFrom(input: InputStream): AppItemData {
        return JSONParser.deserializer(input, AppItemData::class.java) ?: defaultValue
    }

    override suspend fun writeTo(t: AppItemData, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(JSONParser.serializer(t, AppItemData::class.java).stringToBytes())
        }
    }
}

val Context.mediaDataStore: DataStore<MediaDataCache> by dataStore(
    fileName = "media_cache.json",
    serializer = MediaDataCacheStateSerializer
)

val Context.artworkDataStore: DataStore<ByteArray> by dataStore(
    fileName = "artwork_cache.bin",
    serializer = ArtworkCacheSerializer
)

val Context.appInfoDataStore: DataStore<AppItemData> by dataStore(
    fileName = "app_info_cache.json",
    serializer = AppItemCacheSerializer
)