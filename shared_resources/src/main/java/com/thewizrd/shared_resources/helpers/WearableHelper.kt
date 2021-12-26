package com.thewizrd.shared_resources.helpers

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.PutDataRequest
import com.thewizrd.shared_resources.SimpleLibrary
import com.thewizrd.shared_resources.utils.Logger

object WearableHelper {
    // Name of capability listed in Phone app's wear.xml
    const val CAPABILITY_PHONE_APP = "com.thewizrd.simplewear_phone_app"

    // Name of capability listed in Wear app's wear.xml
    const val CAPABILITY_WEAR_APP = "com.thewizrd.simplewear_wear_app"

    // Link to Play Store listing
    private const val PLAY_STORE_APP_URI = "market://details?id=com.thewizrd.simplewear"

    fun getPlayStoreURI(): Uri = Uri.parse(PLAY_STORE_APP_URI)

    // For WearableListenerService
    const val StartActivityPath = "/start-activity"
    const val AppStatePath = "/app_state"
    const val ActionsPath = "/actions"
    const val StatusPath = "/status"
    const val BatteryPath = "/status/battery"
    const val BluetoothPath = "/status/bt"
    const val WifiPath = "/status/wifi"
    const val UpdatePath = "/update/all"
    const val AudioStatusPath = "/status/audio"
    const val AudioVolumePath = "/status/audio/volume"
    const val BtDiscoverPath = "/bluetooth/discoverable"
    const val PingPath = "/ping"
    const val AppsPath = "/apps"
    const val AppsIconSettingsPath = "/apps/settings/icon"
    const val LaunchAppPath = "/apps/start-activity"
    const val ValueStatusPath = "/status/valueaction"
    const val ValueStatusSetPath = "/status/valueaction/setvalue"
    const val BrightnessModePath = "/status/brightness/mode"

    // For Apps DataMap
    const val KEY_APPS = "key_apps"
    const val KEY_LABEL = "key_label"
    const val KEY_ICON = "key_icon"
    const val KEY_PKGNAME = "key_package_name"
    const val KEY_ACTIVITYNAME = "key_activity_name"

    fun isGooglePlayServicesInstalled(): Boolean {
        val queryResult = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(SimpleLibrary.instance.app.appContext)
        if (queryResult == ConnectionResult.SUCCESS) {
            Logger.writeLine(Log.INFO, "App: Google Play Services is installed on this device.")
            return true
        }
        if (GoogleApiAvailability.getInstance().isUserResolvableError(queryResult)) {
            val errorString = GoogleApiAvailability.getInstance().getErrorString(queryResult)
            Logger.writeLine(
                Log.INFO,
                "App: There is a problem with Google Play Services on this device: %s - %s",
                queryResult, errorString
            )
        }
        return false
    }

    fun getWearDataUri(NodeId: String?, Path: String?): Uri {
        return Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .authority(NodeId)
            .path(Path)
            .build()
    }

    fun getWearDataUri(Path: String?): Uri {
        return Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .path(Path)
            .build()
    }

    fun getRemoteIntentForPackage(packageName: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            data = "android-app://${packageName}".toUri()
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    }
}