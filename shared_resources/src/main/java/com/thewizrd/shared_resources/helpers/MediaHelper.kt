package com.thewizrd.shared_resources.helpers

object MediaHelper {
    const val MusicPlayersPath = "/music-players"
    const val PlayCommandPath = "/music/play"
    const val OpenMusicPlayerPath = "/music/start-activity"

    // For MediaController
    const val MediaPlayerAutoLaunchPath = "/media/autolaunch"
    const val MediaPlayerConnectPath = "/media/connect"
    const val MediaPlayerStatePath = "/media/playback_state"
    const val MediaPlayerStateBridgePath = "/media/playback_state/bridge"
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

    const val MediaVolumeUpPath = "/media/volume/up"
    const val MediaVolumeDownPath = "/media/volume/down"
    const val MediaVolumeStatusPath = "/media/volume/status"
    const val MediaSetVolumePath = "/media/volume/set"

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

    // For Music Player DataMap
    const val KEY_SUPPORTEDPLAYERS = "key_supported_players"

    const val KEY_VOLUME = "key_volume"
}