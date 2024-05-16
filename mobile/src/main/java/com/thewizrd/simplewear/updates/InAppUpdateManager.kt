package com.thewizrd.simplewear.updates

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.gson.reflect.TypeToken
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.updates.UpdateInfo
import com.thewizrd.shared_resources.utils.AnalyticsLogger
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

    private val appUpdateManager = AppUpdateManagerFactory.create(context.applicationContext)
    private var appUpdateInfo: AppUpdateInfo? = null
    private var configUpdateinfo: UpdateInfo? = null

    suspend fun checkIfUpdateAvailable(): Boolean {
        try {
            // Returns an intent object that you use to check for an update.
            appUpdateInfo = appUpdateManager.requestAppUpdateInfo()

            // Checks that the platform will allow the specified type of update.
            if (appUpdateInfo?.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                // Check priority of update
                val remoteUpdateInfo = getRemoteUpdateInfo()
                configUpdateinfo = remoteUpdateInfo?.find { input ->
                    // NOTE: RemoteConfig contains wear app version code
                    // Wear app versionCode is always +1 to phone version
                    input.versionCode.minus(1) == appUpdateInfo?.availableVersionCode()?.toLong()
                }
                if (configUpdateinfo != null) {
                    return true
                }
            }
        } catch (e: InstallException) {
            if (e.errorCode == InstallErrorCode.ERROR_APP_NOT_OWNED || e.errorCode == InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND) {
                Logger.writeLine(Log.INFO, "Checking for update using fallback")
                return checkIfUpdateAvailableFallback()
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            if (e.cause is InstallException) {
                val errorCode = (e.cause as InstallException).errorCode
                if (errorCode == InstallErrorCode.ERROR_APP_NOT_OWNED || errorCode == InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND) {
                    Logger.writeLine(Log.INFO, "Checking for update using fallback")
                    return checkIfUpdateAvailableFallback()
                }
            }
        }
        return false
    }

    // TODO: until this is implemented in Play Console, use Firebase RemoteConfig
    private suspend fun getRemoteUpdateInfo(): List<UpdateInfo>? {
        return withContext(Dispatchers.IO) {
            val mConfig = FirebaseRemoteConfig.getInstance()
            mConfig.fetchAndActivate().await()

            val json = mConfig.getString("android_updates")

            val updateTypeToken = object : TypeToken<List<UpdateInfo>>() {}.type
            return@withContext JSONParser.deserializer(json, updateTypeToken)
        }
    }

    private suspend fun checkIfUpdateAvailableFallback(): Boolean {
        try {
            val remoteUpdateInfo = getRemoteUpdateInfo()
            val lastUpdate = remoteUpdateInfo?.lastOrNull()

            if (lastUpdate != null) {
                return WearableHelper.getAppVersionCode() < lastUpdate.versionCode
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
        return false
    }

    /**
     * Must call [InAppUpdateManager.checkIfUpdateAvailable] before this.
     *
     * @return If update available return priority (1 -> 5, with 5 as high priority); Returns -1 if update not available
     */
    val updatePriority: Int
        get() = configUpdateinfo?.updatePriority ?: -1

    fun shouldStartImmediateUpdate(): Boolean {
        if (appUpdateInfo != null && configUpdateinfo != null) {
            return appUpdateInfo!!.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE) && configUpdateinfo!!.updatePriority > 3
        } else {
            return false
        }
    }

    suspend fun shouldStartImmediateUpdateFlow(): Boolean {
        return withContext(Dispatchers.IO) {
            checkIfUpdateAvailable() && shouldStartImmediateUpdate()
        }
    }

    fun startImmediateUpdateFlow(activity: Activity, requestCode: Int) {
        AnalyticsLogger.logEvent("$TAG: startImmedUpdateFlow")

        try {
            appUpdateManager.startUpdateFlowForResult( // Pass the intent that is returned by 'getAppUpdateInfo()'.
                appUpdateInfo!!,  // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
                AppUpdateType.IMMEDIATE,  // The current activity making the update request.
                activity,  // Include a request code to later monitor this update request.
                requestCode
            )
        } catch (e: IntentSender.SendIntentException) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    fun resumeUpdateIfStarted(activity: Activity, requestCode: Int) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            appUpdateInfo = info

            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                // If an in-app update is already running, resume the update.
                try {
                    AnalyticsLogger.logEvent("$TAG: resuming update flow")

                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        AppUpdateType.IMMEDIATE,
                        activity,
                        requestCode
                    )
                } catch (e: IntentSender.SendIntentException) {
                    Logger.writeLine(Log.ERROR, e)
                }
            }
        }
    }
}