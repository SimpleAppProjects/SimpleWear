package com.thewizrd.simplewear.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.lifecycle.LifecycleAwareFragment
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.BuildConfig
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AmbientModeViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.MediaPlayerControlsBinding
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class MediaPlayerControlsFragment : LifecycleAwareFragment(), MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {
    private lateinit var binding: MediaPlayerControlsBinding

    private var deleteJob: Job? = null

    private lateinit var mAmbientReceiver: BroadcastReceiver
    private val mAmbientMode: AmbientModeViewModel by activityViewModels()

    private var showLoading = false
    private var showPlaybackLoading = false

    private var mAudioStreamState: AudioStreamState? = null

    private val volumeScope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var progressBarJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mAmbientReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    MediaPlayerActivity.ACTION_UPDATEAMBIENTMODE -> {
                        if (mAmbientMode.ambientModeEnabled.value == true &&
                            mAmbientMode.doBurnInProtection.value == true &&
                            view != null
                        ) {
                            binding.root.translationX =
                                Random.nextInt(-10, 10 + 1).toFloat()
                            binding.root.translationY =
                                Random.nextInt(-10, 10 + 1).toFloat()
                        }
                    }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(MediaPlayerActivity.ACTION_ENTERAMBIENTMODE)
            addAction(MediaPlayerActivity.ACTION_EXITAMBIENTMODE)
            addAction(MediaPlayerActivity.ACTION_UPDATEAMBIENTMODE)
        }

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(mAmbientReceiver, intentFilter)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MediaPlayerControlsBinding.inflate(inflater, container, false)

        binding.volUpButton.setOnClickListener {
            requestVolumeUp()
        }

        binding.volDownButton.setOnClickListener {
            requestVolumeDown()
        }

        binding.playpauseButton.isCheckable = true
        binding.playpauseButton.isChecked = false
        binding.playpauseButton.setOnClickListener {
            // not checked -> checked (paused -> playing)
            // checked -> not checked (playing -> paused)
            requestPlayPauseAction(!binding.playpauseButton.isChecked)
        }

        binding.prevButton.setOnClickListener {
            requestSkipToPreviousAction()
        }
        binding.nextButton.setOnClickListener {
            requestSkipToNextAction()
        }

        binding.volumeProgressBar.setOnGenericMotionListener(object : View.OnGenericMotionListener {
            private var mRemoteVolume: Float? = null

            override fun onGenericMotion(v: View, event: MotionEvent): Boolean {
                if (event.action == MotionEvent.ACTION_SCROLL &&
                    event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER) &&
                    mAmbientMode.ambientModeEnabled.value != true &&
                    mAudioStreamState != null
                ) {
                    val scaleFactor = ViewConfigurationCompat.getScaledVerticalScrollFactor(
                        ViewConfiguration.get(v.context), v.context
                    )
                    // Don't forget the negation here
                    val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) * scaleFactor

                    // Scaling to (25 * scaleFactor) seems to be good
                    // On emulator = ~2400
                    val scaleMax = 25 * scaleFactor
                    val audioState = mAudioStreamState!!

                    val maxVolume = scaleValueFromState(
                        audioState.maxVolume.toFloat(),
                        audioState,
                        0f,
                        scaleMax
                    )
                    val currVolume = mRemoteVolume ?: scaleValueFromState(
                        audioState.currentVolume.toFloat(),
                        audioState,
                        0f,
                        scaleMax
                    )
                    val minVolume = scaleValueFromState(
                        audioState.minVolume.toFloat(),
                        audioState,
                        0f,
                        scaleMax
                    )

                    val scaledVolume = currVolume + delta
                    mRemoteVolume = normalize(scaledVolume, 0f, scaleMax)

                    val scaledDownValue =
                        scaleValueToState(scaledVolume, 0f, scaleMax, audioState).run {
                            if (!this.isNaN()) this else 0f
                        }
                    val volume = normalizeToState(scaledDownValue.roundToInt(), audioState)

                    if (BuildConfig.DEBUG) {
                        Log.d(
                            "MediaVolScroller",
                            "currVol = ${(currVolume).roundToInt()}, " +
                                    "maxVol = ${(maxVolume).roundToInt()}, " +
                                    "minVol = ${(minVolume).roundToInt()}, " +
                                    "delta = $delta, " +
                                    "scaleFactor = $scaleFactor, " +
                                    "scaledVol = $scaledVolume, " +
                                    "setVol = $volume"
                        )
                    }

                    requestSetVolume(volume)

                    return true
                } else {
                    return false
                }
            }
        })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mAmbientMode.ambientModeEnabled.observe(viewLifecycleOwner) { enabled ->
            if (enabled) {
                enterAmbientMode()
            } else {
                exitAmbientMode()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(mAmbientReceiver)
        volumeScope.cancel()
    }

    private fun enterAmbientMode() {
        showPlaybackLoading(false)
        showLoading(false)

        binding.albumArtImageview.visibility = View.GONE
        binding.prevButton.visibility = View.INVISIBLE
        binding.playbackLoadingbar.visibility = View.GONE
        binding.playpauseButton.visibility = View.VISIBLE
        binding.nextButton.visibility = View.INVISIBLE
        binding.volumeControls.visibility = View.INVISIBLE
        binding.progressBar.hide()

        binding.playpauseButton.setImageResource(R.drawable.playpause_button_ambient)

        binding.titleView.isSelected = false

        if (mAmbientMode.isLowBitAmbient.value == true) {
            binding.timeText.enterLowBitAmbientMode()
            binding.titleView.paint.isAntiAlias = false
            binding.subtitleView.paint.isAntiAlias = false
        }

        binding.volumeProgressBar.clearFocus()
    }

    private fun exitAmbientMode() {
        binding.albumArtImageview.visibility = View.VISIBLE
        binding.prevButton.visibility = View.VISIBLE
        binding.playbackLoadingbar.visibility = View.GONE
        binding.playpauseButton.visibility = View.VISIBLE
        binding.nextButton.visibility = View.VISIBLE
        binding.volumeControls.visibility = View.VISIBLE

        binding.playpauseButton.setImageResource(R.drawable.playpause_button)

        showPlaybackLoading(showPlaybackLoading)
        showLoading(showLoading)

        binding.titleView.isSelected = true

        if (mAmbientMode.isLowBitAmbient.value == true) {
            binding.timeText.exitLowBitAmbientMode()
            binding.titleView.paint.isAntiAlias = true
            binding.subtitleView.paint.isAntiAlias = true
        }

        if (mAmbientMode.doBurnInProtection.value == true) {
            binding.root.translationX = 0f
            binding.root.translationY = 0f
        }

        binding.volumeProgressBar.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(requireContext()).addListener(this)
        Wearable.getDataClient(requireContext()).addListener(this)

        // Request connect to media player
        requestVolumeStatus()
        updatePlayerState()

        if (mAmbientMode.ambientModeEnabled.value != true) {
            binding.titleView.isSelected = true
        }

        binding.volumeProgressBar.requestFocus()
    }

    override fun onPause() {
        Wearable.getDataClient(requireContext()).removeListener(this)
        Wearable.getMessageClient(requireContext()).removeListener(this)
        super.onPause()
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (MediaHelper.MediaPlayerStatePath == item.uri.path) {
                        deleteJob?.cancel()
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updatePlayerState(dataMap)
                    }
                } else if (event.type == DataEvent.TYPE_DELETED) {
                    val item = event.dataItem
                    if (MediaHelper.MediaPlayerStatePath == item.uri.path) {
                        deleteJob?.cancel()
                        deleteJob = lifecycleScope.launch delete@{
                            delay(1000)

                            if (!isActive) return@delete

                            updatePlayerState(DataMap())
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        lifecycleScope.launch {
            when (messageEvent.path) {
                WearableHelper.AudioStatusPath,
                MediaHelper.MediaVolumeStatusPath -> {
                    progressBarJob?.cancel()
                    progressBarJob = async {
                        if (!isActive) return@async

                        val status = messageEvent.data?.let {
                            JSONParser.deserializer(
                                it.bytesToString(),
                                AudioStreamState::class.java
                            )
                        }
                        mAudioStreamState = status

                        if (!isActive) return@async

                        updateProgressBar(status)
                    }
                }
                MediaHelper.MediaPlayPath -> {
                    val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                    if (actionStatus == ActionStatus.TIMEOUT) {
                        CustomConfirmationOverlay()
                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                            .setCustomDrawable(R.drawable.ws_full_sad)
                            .setMessage(R.string.error_playback_failed)
                            .showAbove(binding.root)
                    }
                }
            }
        }
    }

    private fun updateProgressBar(state: ValueActionState?) {
        if (state == null) {
            binding.volumeProgressBar.progress = 0
        } else {
            binding.volumeProgressBar.max = state.maxValue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.volumeProgressBar.min = state.minValue
            }
            binding.volumeProgressBar.progress = state.currentValue
        }
    }

    private fun updatePlayerState(dataMap: DataMap) {
        runWithView {
            val stateName = dataMap.getString(MediaHelper.KEY_MEDIA_PLAYBACKSTATE)
            val playbackState = stateName?.let { PlaybackState.valueOf(it) } ?: PlaybackState.NONE
            val title = dataMap.getString(MediaHelper.KEY_MEDIA_METADATA_TITLE)
            val artist = dataMap.getString(MediaHelper.KEY_MEDIA_METADATA_ARTIST)
            val artBitmap = dataMap.getAsset(MediaHelper.KEY_MEDIA_METADATA_ART)?.let {
                try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(binding.albumArtImageview.context),
                        it
                    )
                } catch (e: Exception) {
                    null
                }
            }

            if (playbackState != PlaybackState.NONE) {
                binding.titleView.text = title
                binding.subtitleView.text = artist
                binding.albumArtImageview.setImageBitmap(artBitmap)

                binding.subtitleView.visibility =
                    if (artist.isNullOrBlank()) View.GONE else View.VISIBLE
            } else {
                binding.titleView.text =
                    binding.titleView.context.getString(R.string.message_playback_stopped)
                binding.subtitleView.text = ""
                binding.albumArtImageview.setImageBitmap(null)

                binding.subtitleView.visibility = View.GONE
            }

            when (playbackState) {
                PlaybackState.NONE -> {
                    showLoading(false)
                    showPlaybackLoading(false)
                    binding.playpauseButton.setChecked(false, false)
                }
                PlaybackState.LOADING -> {
                    showLoading(false)
                    showPlaybackLoading(true)
                }
                PlaybackState.PLAYING -> {
                    showLoading(false)
                    showPlaybackLoading(false)
                    binding.playpauseButton.setChecked(true, false)
                }
                PlaybackState.PAUSED -> {
                    showLoading(false)
                    showPlaybackLoading(false)
                    binding.playpauseButton.setChecked(false, false)
                }
            }
        }
    }

    private fun updatePlayerState() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(requireContext())
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaPlayerStatePath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaPlayerStatePath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updatePlayerState(dataMap)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private fun requestPlayPauseAction(checked: Boolean) {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(if (checked) MediaHelper.MediaPlayPath else MediaHelper.MediaPausePath))
        }
    }

    private fun requestSkipToPreviousAction() {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(MediaHelper.MediaPreviousPath))
        }
    }

    private fun requestSkipToNextAction() {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(MediaHelper.MediaNextPath))
        }
    }

    private fun requestVolumeUp() {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(MediaHelper.MediaVolumeUpPath))
        }
    }

    private fun requestVolumeDown() {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(MediaHelper.MediaVolumeDownPath))
        }
    }

    private fun requestVolumeStatus() {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(MediaHelper.MediaVolumeStatusPath))
        }
    }

    private fun requestSetVolume(value: Int) {
        volumeScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcastSync(Intent(MediaHelper.MediaSetVolumePath).apply {
                    putExtra(MediaHelper.KEY_VOLUME, value)
                })
        }
    }

    private fun showLoading(show: Boolean) {
        showLoading = show
        if (mAmbientMode.ambientModeEnabled.value == true) return
        if (show) {
            binding.progressBar.show()
        } else {
            binding.progressBar.hide()
        }
        binding.albumArtImageview.visibility = if (show) View.INVISIBLE else View.VISIBLE
        binding.playerControls.visibility = if (show) View.INVISIBLE else View.VISIBLE
        if (!show) {
            binding.volumeProgressBar.requestFocus()
        }
    }

    private fun showPlaybackLoading(show: Boolean) {
        showPlaybackLoading = show
        if (mAmbientMode.ambientModeEnabled.value == true) return
        binding.playpauseButton.visibility = if (show) View.GONE else View.VISIBLE
        binding.playbackLoadingbar.visibility = if (show) View.VISIBLE else View.GONE
    }

    /* Value scaling */
    private fun scaleValue(
        value: Float,
        minValue: Float,
        maxValue: Float,
        scaleMin: Float,
        scaleMax: Float
    ): Float {
        return ((value - minValue) / (maxValue - minValue)) * (scaleMax - scaleMin) + scaleMin
    }

    private fun scaleValueFromState(
        value: Float,
        state: ValueActionState,
        scaleMin: Float,
        scaleMax: Float
    ): Float {
        return ((value - state.minValue) / (state.maxValue - state.minValue)) * (scaleMax - scaleMin) + scaleMin
    }

    private fun scaleValueToState(
        value: Float,
        minValue: Float,
        maxValue: Float,
        state: ValueActionState
    ): Float {
        return ((value - minValue) / (maxValue - minValue)) * (state.maxValue - state.minValue) + state.minValue
    }

    private fun normalize(value: Int, minValue: Int, maxValue: Int): Int {
        return min(maxValue, max(value, minValue))
    }

    private fun normalize(value: Float, minValue: Float, maxValue: Float): Float {
        return min(maxValue, max(value, minValue))
    }

    private fun normalizeToState(value: Int, state: ValueActionState): Int {
        return normalize(value, state.minValue, state.maxValue)
    }
}