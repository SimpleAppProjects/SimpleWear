package com.thewizrd.shared_resources.helpers

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.Node
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

    private const val VERSION_CODE: Long = 341915000

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
    const val VersionPath = "/version"
    const val TimedActionsStatusPath = "/status/timedActions"
    const val TimedActionAddPath = "/timedActions/add"
    const val TimedActionDeletePath = "/timedActions/delete"
    const val TimedActionUpdatePath = "/timedActions/update"

    // For Apps DataMap
    const val KEY_APPS = "key_apps"
    const val KEY_LABEL = "key_label"
    const val KEY_ICON = "key_icon"
    const val KEY_PKGNAME = "key_package_name"
    const val KEY_ACTIVITYNAME = "key_activity_name"

    // For Activity Launcher
    private const val SCHEME_APP = "simplewear"
    private const val PATH_REMOTE_LAUNCH = "launch-activity"
    const val URI_PARAM_PKGNAME = "package"
    const val URI_PARAM_ACTIVITYNAME = "activity"

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

    fun getLaunchActivityUri(packageName: String, activityName: String): Uri {
        return Uri.Builder()
            .scheme(SCHEME_APP)
            .authority(PATH_REMOTE_LAUNCH)
            .appendQueryParameter(URI_PARAM_PKGNAME, packageName)
            .appendQueryParameter(URI_PARAM_ACTIVITYNAME, activityName)
            .build()
    }

    fun createRemoteActivityIntent(packageName: String, activityName: String): Intent {
        return Intent(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_DEFAULT)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(getLaunchActivityUri(packageName, activityName))
    }

    fun isRemoteLaunchUri(uri: Uri): Boolean {
        return uri.scheme == SCHEME_APP && uri.host == PATH_REMOTE_LAUNCH &&
                !uri.getQueryParameter(URI_PARAM_PKGNAME).isNullOrEmpty() &&
                !uri.getQueryParameter(URI_PARAM_ACTIVITYNAME).isNullOrEmpty()
    }

    fun Uri.toLaunchIntent(): Intent {
        return Intent(Intent.ACTION_MAIN)
            .apply {
                if (getQueryParameter(MediaHelper.URI_PARAM_MEDIAPLAYER) == "true") {
                    addCategory(Intent.CATEGORY_APP_MUSIC)
                } else {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
            }
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setComponent(
                ComponentName(
                    this.getQueryParameter(URI_PARAM_PKGNAME)!!,
                    this.getQueryParameter(URI_PARAM_ACTIVITYNAME)!!
                )
            )
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
    */
    fun pickBestNodeId(nodes: Collection<Node>): Node? {
        var bestNode: Node? = null

        // Find a nearby node/phone or pick one arbitrarily. Realistically, there is only one phone.
        for (node in nodes) {
            if (node.isNearby) {
                return node
            }
            bestNode = node
        }
        return bestNode
    }

    fun getBLEServiceUUID(): ParcelUuid =
        ParcelUuid.fromString("0000DA28-0000-1000-8000-00805F9B34FB")

    fun isAppUpToDate(versionCode: Long): Boolean {
        return versionCode >= VERSION_CODE
    }

    fun getAppVersionCode(): Long = try {
        val context = SimpleLibrary.instance.app.appContext
        val packageInfo = context.run {
            packageManager.getPackageInfo(packageName, 0)
        }

        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

        versionCode
    } catch (e: PackageManager.NameNotFoundException) {
        0
    }
}