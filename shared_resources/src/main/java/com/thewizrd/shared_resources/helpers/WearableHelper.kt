package com.thewizrd.shared_resources.helpers

import android.net.Uri
import android.util.Log
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
    const val MusicPlayersPath = "/music-players"
    const val PlayCommandPath = "/music/play"
    const val OpenMusicPlayerPath = "/music/start-activity"
    const val BtDiscoverPath = "/bluetooth/discoverable"
    const val PingPath = "/ping"
    const val AppsPath = "/apps"
    const val LaunchAppPath = "/apps/start-activity"

    // For Music Player DataMap
    const val KEY_SUPPORTEDPLAYERS = "key_supported_players"
    const val KEY_APPS = "key_apps"
    const val KEY_LABEL = "key_label"
    const val KEY_ICON = "key_icon"
    const val KEY_PKGNAME = "key_package_name"
    const val KEY_ACTIVITYNAME = "key_activity_name"

    // For MediaController
    const val MediaPlayerConnectPath = "/media/connect"
    const val MediaPlayerStatePath = "/media/playback_state"
    const val MediaPlayerStateStoppedPath = "/media/playback_state/stopped"
    const val MediaPlayPath = "/media/action/play"
    const val MediaPausePath = "/media/action/pause"
    const val MediaPreviousPath = "/media/action/previous"
    const val MediaNextPath = "/media/action/skip"
    const val MediaActionsPath = "/media/actions"
    const val MediaBrowserItemsPath = "/media/browseritems"
    const val MediaBrowserItemsExtraSuggestedPath = "/media/browseritems_extrasuggested"
    const val MediaQueueItemsPath = "/media/queueitems"
    const val MediaPlayFromSearchPath = "/media/searchplay"
    const val MediaPlayerDisconnectPath = "/media/disconnect"

    const val MediaBrowserItemsClickPath = "/media/browseritems/click"
    const val MediaBrowserItemsExtraSuggestedClickPath = "/media/browseritems_extrasuggested/click"
    const val MediaActionsClickPath = "/media/actions/click"
    const val MediaQueueItemsClickPath = "/media/queueitems/click"

    const val MediaBrowserItemsBackPath = "/media/mediaitems/back"
    const val MediaBrowserItemsExtraSuggestedBackPath = "/media/browseritems_extrasuggested/back"
    const val ACTIONITEM_BACK = "back"
    const val ACTIONITEM_PLAY = "play_random"

    const val KEY_MEDIAITEMS = "key_mediaitems"
    const val KEY_MEDIAITEM_ISROOT = "key_mediaitem_isroot"
    const val KEY_MEDIAITEM_ID = "key_mediaitem_id"
    const val KEY_MEDIAITEM_ICON = "key_mediaitem_icon"
    const val KEY_MEDIAITEM_TITLE = "key_mediaitem_title"

    const val KEY_MEDIA_ACTIONITEM_ACTION = "key_media_actionitem_action"
    const val KEY_MEDIA_ACTIONITEM_TITLE = "key_media_actionitem_title"
    const val KEY_MEDIA_ACTIONITEM_ICON = "key_media_actionitem_icon"

    const val KEY_MEDIA_METADATA_TITLE = "key_media_metadata_title"
    const val KEY_MEDIA_METADATA_ARTIST = "key_media_metadata_artist"
    const val KEY_MEDIA_METADATA_ART = "key_media_metadata_art"
    const val KEY_MEDIA_PLAYBACKSTATE = "key_media_playbackstate"

    const val KEY_MEDIA_SUPPORTS_PLAYFROMSEARCH = "key_media_supports_playfromsearch"

    const val KEY_MEDIA_ACTIVEQUEUEITEM_ID = "key_media_activequeueitem_id"

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
}