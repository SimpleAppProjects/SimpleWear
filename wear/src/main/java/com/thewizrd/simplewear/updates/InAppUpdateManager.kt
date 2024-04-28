package com.thewizrd.simplewear.updates

import android.content.Context
import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.reflect.TypeToken
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.updates.UpdateInfo
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class InAppUpdateManager private constructor(context: Context) {
    companion object {
        private const val TAG = "InAppUpdateManager"

        @JvmStatic
        fun create(context: Context): InAppUpdateManager {
            return InAppUpdateManager(context)
        }
    }

    private var configUpdateinfo: UpdateInfo? = null

    suspend fun checkIfUpdateAvailable(): Boolean {
        try {
            val remoteUpdateInfo = getRemoteUpdateInfo()
            val lastUpdate = remoteUpdateInfo?.lastOrNull()

            if (lastUpdate != null) {
                configUpdateinfo = lastUpdate
                return WearableHelper.getAppVersionCode() < lastUpdate.versionCode
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
        return false
    }

    private suspend fun getRemoteUpdateInfo(): List<UpdateInfo>? {
        return withContext(Dispatchers.IO) {
            val mConfig = FirebaseRemoteConfig.getInstance()
            mConfig.fetchAndActivate().await()

            val json = mConfig.getString("android_updates")

            val updateTypeToken = object : TypeToken<List<UpdateInfo>>() {}.type
            return@withContext JSONParser.deserializer(json, updateTypeToken)
        }
    }

    /**
     * Must call [InAppUpdateManager.checkIfUpdateAvailable] before this.
     *
     * @return If update available return priority (1 -> 5, with 5 as high priority); Returns -1 if update not available
     */
    val updatePriority: Int
        get() = configUpdateinfo?.updatePriority ?: -1
}