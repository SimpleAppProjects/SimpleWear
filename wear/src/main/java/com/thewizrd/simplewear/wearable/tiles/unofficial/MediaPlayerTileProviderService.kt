package com.thewizrd.simplewear.wearable.tiles.unofficial

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.google.android.clockwork.tiles.TileData
import com.google.android.clockwork.tiles.TileProviderService
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.ImageUtils.toBitmap
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.datastore.media.appInfoDataStore
import com.thewizrd.simplewear.datastore.media.artworkDataStore
import com.thewizrd.simplewear.datastore.media.mediaDataStore
import com.thewizrd.simplewear.media.MediaPlayerActivity
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileMessenger
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileMessenger.PlayerAction
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

class MediaPlayerTileProviderService : TileProviderService() {
    companion object {
        private const val TAG = "MediaPlayerTileProviderService"
    }

    private var mInFocus = false
    private var id = -1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var tileMessenger: MediaPlayerTileMessenger
    private lateinit var tileStateFlow: StateFlow<MediaPlayerTileState?>

    override fun onCreate() {
        super.onCreate()
        Logger.debug(TAG, "creating service...")

        tileMessenger = MediaPlayerTileMessenger(this, isLegacyTile = true)
        tileMessenger.register()

        tileStateFlow = combine(
            this.mediaDataStore.data,
            this.artworkDataStore.data,
            this.appInfoDataStore.data,
            tileMessenger.connectionState
        ) { mediaCache, artwork, appInfo, connectionStatus ->
            MediaPlayerTileState(
                connectionStatus = connectionStatus,
                title = mediaCache.mediaPlayerState?.mediaMetaData?.title,
                artist = mediaCache.mediaPlayerState?.mediaMetaData?.artist,
                artwork = artwork,
                playbackState = mediaCache.mediaPlayerState?.playbackState,
                positionState = mediaCache.mediaPlayerState?.mediaMetaData?.positionState,
                audioStreamState = mediaCache.audioStreamState,
                appIcon = appInfo.iconBitmap
            )
        }
            .stateIn(
                scope,
                started = SharingStarted.WhileSubscribed(2000),
                initialValue = null
            )

        scope.launch {
            tileStateFlow.collectLatest {
                if (mInFocus && isActive && !isIdForDummyData(id)) {
                    sendRemoteViews()
                }
            }
        }
    }

    override fun onDestroy() {
        Logger.debug(TAG, "destroying service...")
        tileMessenger.unregister()
        super.onDestroy()
        scope.cancel()
    }

    override fun onTileUpdate(tileId: Int) {
        Logger.debug(TAG, "onTileUpdate called with: tileId = $tileId")

        if (!isIdForDummyData(tileId)) {
            id = tileId

            scope.launch {
                sendRemoteViews()
            }
        }
    }

    override fun onTileFocus(tileId: Int) {
        super.onTileFocus(tileId)
        Logger.debug(TAG, "onTileFocus called with: tileId = $tileId")

        if (!isIdForDummyData(tileId)) {
            id = tileId
            mInFocus = true
            AnalyticsLogger.logEvent("on_tile_enter", Bundle().apply {
                putString("tile", TAG)
                putBoolean("isUnofficial", true)
            })

            scope.launch {
                tileMessenger.checkConnectionStatus()
                tileMessenger.requestPlayerConnect()
                tileMessenger.requestVolumeStatus()
                tileMessenger.requestUpdatePlayerState()
                tileMessenger.requestPlayerAppInfo()

                sendRemoteViews()
            }
        }
    }

    override fun onTileBlur(tileId: Int) {
        super.onTileBlur(tileId)

        Logger.debug(TAG, "onTileBlur called with: tileId = $tileId")
        if (!isIdForDummyData(tileId)) {
            mInFocus = false

            scope.launch {
                tileMessenger.requestPlayerDisconnect()
            }
        }
    }

    private suspend fun sendRemoteViews() {
        Logger.debug(TAG, "sendRemoteViews")

        val tileState = latestTileState()
        val updateViews = buildUpdate(tileState)

        val tileData = TileData.Builder()
            .setRemoteViews(updateViews)
            .build()

        sendUpdate(id, tileData)
    }

    private suspend fun buildUpdate(tileState: MediaPlayerTileState): RemoteViews {
        val views: RemoteViews

        if (tileState.connectionStatus != WearConnectionStatus.CONNECTED) {
            views = RemoteViews(packageName, R.layout.tile_disconnected)
            when (tileState.connectionStatus) {
                WearConnectionStatus.APPNOTINSTALLED -> {
                    views.setTextViewText(R.id.message, getString(R.string.error_notinstalled))
                    views.setImageViewResource(
                        R.id.imageButton,
                        R.drawable.common_full_open_on_phone
                    )
                }
                else -> {
                    views.setTextViewText(R.id.message, getString(R.string.status_disconnected))
                    views.setImageViewResource(
                        R.id.imageButton,
                        R.drawable.ic_phonelink_erase_white_24dp
                    )
                }
            }
            views.setOnClickPendingIntent(R.id.tile, getTapIntent(this))
            return views
        }

        views = RemoteViews(packageName, R.layout.tile_mediaplayer)
        views.setOnClickPendingIntent(R.id.tile, getTapIntent(this))

        if (tileState.playbackState == null || tileState.playbackState == PlaybackState.NONE) {
            views.setViewVisibility(R.id.player_controls, View.GONE)
            views.setViewVisibility(R.id.nomedia_view, View.VISIBLE)
            views.setViewVisibility(R.id.album_art_imageview, View.GONE)
            views.setViewVisibility(R.id.app_icon, View.VISIBLE)
            views.setOnClickPendingIntent(
                R.id.playrandom_button,
                getActionClickIntent(this, MediaHelper.MediaPlayPath)
            )
        } else {
            views.setViewVisibility(R.id.player_controls, View.VISIBLE)
            views.setViewVisibility(R.id.nomedia_view, View.GONE)
            views.setViewVisibility(R.id.album_art_imageview, View.VISIBLE)
            views.setViewVisibility(R.id.app_icon, View.VISIBLE)

            views.setTextViewText(R.id.title_view, tileState.title)
            views.setTextViewText(R.id.subtitle_view, tileState.artist)
            views.setViewVisibility(
                R.id.subtitle_view,
                if (tileState.artist.isNullOrBlank()) View.GONE else View.VISIBLE
            )
            views.setViewVisibility(
                R.id.play_button,
                if (tileState.playbackState != PlaybackState.PLAYING) View.VISIBLE else View.GONE
            )
            views.setViewVisibility(
                R.id.pause_button,
                if (tileState.playbackState != PlaybackState.PLAYING) View.GONE else View.VISIBLE
            )
            views.setImageViewBitmap(R.id.album_art_imageview, tileState.artwork?.toBitmap())

            if (tileState.appIcon != null) {
                views.setImageViewBitmap(R.id.app_icon, tileState.appIcon.toBitmap())
            } else {
                views.setImageViewResource(R.id.app_icon, R.drawable.ic_play_circle_simpleblue)
            }

            views.setProgressBar(
                R.id.volume_progressBar,
                tileState.audioStreamState?.maxVolume ?: 100,
                tileState.audioStreamState?.currentVolume ?: 0,
                false
            )

            views.setOnClickPendingIntent(
                R.id.prev_button,
                getActionClickIntent(this, MediaHelper.MediaPreviousPath)
            )
            views.setOnClickPendingIntent(
                R.id.play_button,
                getActionClickIntent(this, MediaHelper.MediaPlayPath)
            )
            views.setOnClickPendingIntent(
                R.id.pause_button,
                getActionClickIntent(this, MediaHelper.MediaPausePath)
            )
            views.setOnClickPendingIntent(
                R.id.next_button,
                getActionClickIntent(this, MediaHelper.MediaNextPath)
            )

            views.setOnClickPendingIntent(
                R.id.vol_down_button,
                getActionClickIntent(this, MediaHelper.MediaVolumeDownPath)
            )
            views.setOnClickPendingIntent(
                R.id.vol_up_button,
                getActionClickIntent(this, MediaHelper.MediaVolumeUpPath)
            )
        }

        return views
    }

    private fun getTapIntent(context: Context): PendingIntent {
        val onClickIntent = Intent(context.applicationContext, MediaPlayerActivity::class.java)
        return PendingIntent.getActivity(context, 0, onClickIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getActionClickIntent(context: Context, action: String): PendingIntent {
        val onClickIntent =
            Intent(context.applicationContext, MediaPlayerTileProviderService::class.java)
                .setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            onClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MediaHelper.MediaPlayPath -> requestPlayerAction(PlayerAction.PLAY)
            MediaHelper.MediaPausePath -> requestPlayerAction(PlayerAction.PAUSE)
            MediaHelper.MediaPreviousPath -> requestPlayerAction(PlayerAction.PREVIOUS)
            MediaHelper.MediaNextPath -> requestPlayerAction(PlayerAction.NEXT)
            MediaHelper.MediaVolumeUpPath -> requestPlayerAction(PlayerAction.VOL_UP)
            MediaHelper.MediaVolumeDownPath -> requestPlayerAction(PlayerAction.VOL_DOWN)
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun requestPlayerAction(action: PlayerAction) {
        scope.launch {
            tileMessenger.requestPlayerAction(action)
        }
    }

    private suspend fun latestTileState(): MediaPlayerTileState {
        var tileState = tileStateFlow.filterNotNull().first()

        if (tileState.isEmpty) {
            Logger.debug(TAG, "No tile state available. loading from remote...")
            tileMessenger.updatePlayerStateFromRemote()

            // Try to await for full metadata change
            runCatching {
                withTimeoutOrNull(5000) {
                    supervisorScope {
                        var songChanged = false

                        tileStateFlow.filterNotNull().collectLatest { newState ->
                            if (!songChanged && newState.title != tileState.title && newState.artist != tileState.artist) {
                                // new song; wait for artwork
                                tileState = newState
                                songChanged = true
                            } else if (songChanged && !newState.artwork.contentEquals(tileState.artwork)) {
                                tileState = newState
                                coroutineContext.cancel()
                            }
                        }
                    }
                }
            }
        }

        return tileState
    }
}