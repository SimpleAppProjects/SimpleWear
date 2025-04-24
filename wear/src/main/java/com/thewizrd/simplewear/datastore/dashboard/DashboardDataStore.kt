package com.thewizrd.simplewear.datastore.dashboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.stringToBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

private object DashboardDataCacheSerializer : Serializer<DashboardDataCache> {
    override val defaultValue: DashboardDataCache
        get() = DashboardDataCache()

    override suspend fun readFrom(input: InputStream): DashboardDataCache {
        return JSONParser.deserializer(input, DashboardDataCache::class.java) ?: defaultValue
    }

    override suspend fun writeTo(t: DashboardDataCache, output: OutputStream) {
        withContext(Dispatchers.IO) {
            output.write(JSONParser.serializer(t, DashboardDataCache::class.java).stringToBytes())
        }
    }
}

val Context.dashboardDataStore: DataStore<DashboardDataCache> by dataStore(
    fileName = "dashboard_cache.json",
    serializer = DashboardDataCacheSerializer
)