package com.thewizrd.simplewear.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.ambient.AmbientModeSupport
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.WearableListenerActivity
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.MediaPlayerControlsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

class MediaPlayerControlsFragment : Fragment(), MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {
    private lateinit var binding: MediaPlayerControlsBinding

    private var deleteJob: Job? = null

    private lateinit var mAmbientReceiver: BroadcastReceiver
    private var isAmbientMode: Boolean = false
    private var isLowBitAmbient = false
    private var doBurnInProtection = false
    private var showLoading = false
    private var showPlaybackLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isAmbientMode = requireArguments().getBoolean(AmbientModeSupport.FRAGMENT_TAG, false)
        isLowBitAmbient =
            requireArguments().getBoolean(AmbientModeSupport.EXTRA_LOWBIT_AMBIENT, false)
        doBurnInProtection =
            requireArguments().getBoolean(AmbientModeSupport.EXTRA_BURN_IN_PROTECTION, false)

        if (isAmbientMode) {
            viewLifecycleOwnerLiveData.observe(
                this@MediaPlayerControlsFragment,
                object : Observer<LifecycleOwner> {
                    override fun onChanged(it: LifecycleOwner?) {
                        if (it != null) {
                            viewLifecycleOwnerLiveData.removeObserver(this)
                            it.lifecycleScope.launchWhenCreated {
                                enterAmbientMode()
                            }
                        }
                    }
                })
        }

        mAmbientReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    MediaPlayerActivity.ACTION_ENTERAMBIENTMODE -> {
                        isLowBitAmbient =
                            intent.getBooleanExtra(AmbientModeSupport.EXTRA_LOWBIT_AMBIENT, false)
                        doBurnInProtection = intent.getBooleanExtra(
                            AmbientModeSupport.EXTRA_BURN_IN_PROTECTION,
                            false
                        )

                        viewLifecycleOwnerLiveData.observe(
                            this@MediaPlayerControlsFragment,
                            object : Observer<LifecycleOwner> {
                                override fun onChanged(it: LifecycleOwner?) {
                                    if (it != null) {
                                        viewLifecycleOwnerLiveData.removeObserver(this)
                                        it.lifecycleScope.launchWhenCreated {
                                            enterAmbientMode()
                                        }
                                    }
                                }
                            })
                    }
                    MediaPlayerActivity.ACTION_EXITAMBIENTMODE -> {
                        viewLifecycleOwnerLiveData.observe(
                            this@MediaPlayerControlsFragment,
                            object : Observer<LifecycleOwner> {
                                override fun onChanged(it: LifecycleOwner?) {
                                    if (it != null) {
                                        viewLifecycleOwnerLiveData.removeObserver(this)
                                        it.lifecycleScope.launchWhenCreated {
                                            exitAmbientMode()
                                        }
                                    }
                                }
                            })
                    }
                    MediaPlayerActivity.ACTION_UPDATEAMBIENTMODE -> {
                        isAmbientMode = true

                        if (doBurnInProtection && view != null) {
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
            val actionData = VolumeAction(ValueDirection.UP, AudioStreamType.MUSIC)

            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(
                    Intent(WearableListenerActivity.ACTION_CHANGED)
                        .putExtra(
                            WearableListenerActivity.EXTRA_ACTIONDATA,
                            JSONParser.serializer(actionData, Action::class.java)
                        )
                )
        }

        binding.volDownButton.setOnClickListener {
            val actionData = VolumeAction(ValueDirection.DOWN, AudioStreamType.MUSIC)

            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(
                    Intent(WearableListenerActivity.ACTION_CHANGED)
                        .putExtra(
                            WearableListenerActivity.EXTRA_ACTIONDATA,
                            JSONParser.serializer(actionData, Action::class.java)
                        )
                )
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

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private fun enterAmbientMode() {
        showPlaybackLoading(false)
        showLoading(false)

        isAmbientMode = true

        binding.albumArtImageview.visibility = View.GONE
        binding.prevButton.visibility = View.INVISIBLE
        binding.playbackLoadingbar.visibility = View.GONE
        binding.playpauseButton.visibility = View.VISIBLE
        binding.nextButton.visibility = View.INVISIBLE
        binding.volumeControls.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.GONE

        binding.playpauseButton.setImageResource(R.drawable.playpause_button_ambient)

        if (isLowBitAmbient) {
            binding.timeClock.paint.isAntiAlias = false
            binding.titleView.paint.isAntiAlias = false
            binding.subtitleView.paint.isAntiAlias = false
        }
    }

    private fun exitAmbientMode() {
        isAmbientMode = false

        binding.albumArtImageview.visibility = View.VISIBLE
        binding.prevButton.visibility = View.VISIBLE
        binding.playbackLoadingbar.visibility = View.GONE
        binding.playpauseButton.visibility = View.VISIBLE
        binding.nextButton.visibility = View.VISIBLE
        binding.volumeControls.visibility = View.VISIBLE

        binding.playpauseButton.setImageResource(R.drawable.playpause_button)

        showPlaybackLoading(showPlaybackLoading)
        showLoading(showLoading)

        if (isLowBitAmbient) {
            binding.timeClock.paint.isAntiAlias = true
            binding.titleView.paint.isAntiAlias = true
            binding.subtitleView.paint.isAntiAlias = true
        }

        if (doBurnInProtection) {
            binding.root.translationX = 0f
            binding.root.translationY = 0f
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(requireContext()).addListener(this)
        Wearable.getDataClient(requireContext()).addListener(this)

        // Request connect to media player
        requestAudioStreamState()
        updatePlayerState()
    }

    override fun onPause() {
        Wearable.getDataClient(requireContext()).removeListener(this)
        Wearable.getMessageClient(requireContext()).removeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(requireContext())
            .unregisterReceiver(mAmbientReceiver)
        super.onDestroy()
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (WearableHelper.MediaPlayerStatePath == item.uri.path) {
                        deleteJob?.cancel()
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updatePlayerState(dataMap)
                    }
                } else if (event.type == DataEvent.TYPE_DELETED) {
                    val item = event.dataItem
                    if (WearableHelper.MediaPlayerStatePath == item.uri.path) {
                        deleteJob?.cancel()
                        deleteJob = lifecycleScope.launch {
                            delay(3000)
                            updatePlayerState(DataMap())
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        lifecycleScope.launch {
            if (messageEvent.path == WearableHelper.AudioStatusPath) {
                val status = messageEvent.data?.let {
                    JSONParser.deserializer(it.bytesToString(), AudioStreamState::class.java)
                }

                lifecycleScope.launch {
                    if (status == null) {
                        binding.volumeProgressBar.progress = 0
                    } else {
                        binding.volumeProgressBar.max = status.maxVolume
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            binding.volumeProgressBar.min = status.minVolume
                        }
                        binding.volumeProgressBar.progress = status.currentVolume
                    }
                }
            } else if (messageEvent.path == WearableHelper.MediaPlayPath) {
                val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                if (actionStatus == ActionStatus.TIMEOUT) {
                    CustomConfirmationOverlay()
                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                        .setCustomDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_full_sad
                            )
                        )
                        .setMessage(getString(R.string.error_playback_failed))
                        .showAbove(binding.root)
                }
            }
        }
    }

    private fun updatePlayerState(dataMap: DataMap) {
        viewLifecycleOwner.lifecycleScope.launch {
            val stateName = dataMap.getString(WearableHelper.KEY_MEDIA_PLAYBACKSTATE)
            val playbackState = stateName?.let { PlaybackState.valueOf(it) } ?: PlaybackState.NONE
            val title = dataMap.getString(WearableHelper.KEY_MEDIA_METADATA_TITLE)
            val artist = dataMap.getString(WearableHelper.KEY_MEDIA_METADATA_ARTIST)
            val artBitmap = try {
                ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(requireContext()),
                    dataMap.getAsset(WearableHelper.KEY_MEDIA_METADATA_ART)
                )
            } catch (e: Exception) {
                null
            }

            if (playbackState != PlaybackState.NONE) {
                binding.titleView.text = title
                binding.subtitleView.text = artist
                binding.albumArtImageview.setImageBitmap(artBitmap)

                binding.subtitleView.visibility =
                    if (artist.isNullOrBlank()) View.GONE else View.VISIBLE
            } else {
                binding.titleView.text = getString(R.string.message_playback_stopped)
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

    private fun requestAudioStreamState() {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(WearableHelper.AudioStatusPath))
        }
    }

    private fun updatePlayerState() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(requireContext())
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            WearableHelper.MediaPlayerStatePath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (WearableHelper.MediaPlayerStatePath == item.uri.path) {
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
                .sendBroadcast(Intent(if (checked) WearableHelper.MediaPlayPath else WearableHelper.MediaPausePath))
        }
    }

    private fun requestSkipToPreviousAction() {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(WearableHelper.MediaPreviousPath))
        }
    }

    private fun requestSkipToNextAction() {
        lifecycleScope.launch {
            LocalBroadcastManager.getInstance(requireContext())
                .sendBroadcast(Intent(WearableHelper.MediaNextPath))
        }
    }

    private fun showLoading(show: Boolean) {
        showLoading = show
        if (isAmbientMode) return
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.albumArtImageview.visibility = if (show) View.INVISIBLE else View.VISIBLE
        binding.playerControls.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private fun showPlaybackLoading(show: Boolean) {
        showPlaybackLoading = show
        if (isAmbientMode) return
        binding.playpauseButton.visibility = if (show) View.GONE else View.VISIBLE
        binding.playbackLoadingbar.visibility = if (show) View.VISIBLE else View.GONE
    }
}