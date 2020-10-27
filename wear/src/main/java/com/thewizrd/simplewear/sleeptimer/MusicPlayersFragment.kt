package com.thewizrd.simplewear.sleeptimer

import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.CountDownTimer
import android.support.wearable.input.RotaryEncoder
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.OnGenericMotionListener
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.widget.WearableLinearLayoutManager
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.WearableHelper.getWearDataUri
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.Logger.writeLine
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.MusicPlayerViewModel
import com.thewizrd.simplewear.databinding.FragmentMusicplayersSleepBinding
import com.thewizrd.simplewear.fragments.SwipeDismissFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.concurrent.ExecutionException

class MusicPlayersFragment : SwipeDismissFragment(), OnDataChangedListener {
    private lateinit var binding: FragmentMusicplayersSleepBinding
    private lateinit var mAdapter: PlayerListAdapter
    private var timer: CountDownTimer? = null
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    private lateinit var selectedPlayer: SelectedPlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set timer for retrieving music player data
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val buff = Wearable.getDataClient(requireActivity())
                                .getDataItems(getWearDataUri("*", WearableHelper.MusicPlayersPath))
                                .await()

                        for (i in 0 until buff.count) {
                            val item = buff[i]
                            if (WearableHelper.MusicPlayersPath == item.uri.path) {
                                val dataMap = DataMapItem.fromDataItem(item).dataMap
                                updateMusicPlayers(dataMap)
                                showProgressBar(false)
                            }
                        }
                        buff.release()
                    } catch (e: ExecutionException) {
                        writeLine(Log.ERROR, e)
                    } catch (e: InterruptedException) {
                        writeLine(Log.ERROR, e)
                    }
                }
            }
        }

        selectedPlayer = ViewModelProvider(requireActivity(), ViewModelProvider.NewInstanceFactory())
                .get(SelectedPlayerViewModel::class.java)
        selectedPlayer.key.observe(this, { s ->
            val mapRequest = PutDataMapRequest.create(SleepTimerHelper.SleepTimerAudioPlayerPath)
            mapRequest.dataMap.putString(SleepTimerHelper.KEY_SELECTEDPLAYER, s)
            Wearable.getDataClient(requireActivity()).putDataItem(
                    mapRequest.asPutDataRequest()).addOnFailureListener { e -> writeLine(Log.ERROR, e) }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val outerView = super.onCreateView(inflater, container, savedInstanceState)
        binding = FragmentMusicplayersSleepBinding.inflate(inflater, outerView as ViewGroup?, true)

        binding.playerList.setHasFixedSize(true)
        binding.playerList.isEdgeItemsCenteringEnabled = false
        binding.playerList.layoutManager = WearableLinearLayoutManager(requireActivity(), null)
        binding.playerList.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            /* BoxInsetLayout impl */
            private val FACTOR = 0.146447f //(1 - sqrt(2)/2)/2
            private val mIsRound = resources.configuration.isScreenRound
            private val paddingTop = binding.playerList.paddingTop
            private val paddingBottom = binding.playerList.paddingBottom
            private val paddingStart = ViewCompat.getPaddingStart(binding.playerList)
            private val paddingEnd = ViewCompat.getPaddingEnd(binding.playerList)

            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                        oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                binding.playerList.removeOnLayoutChangeListener(this)

                val verticalPadding = resources.getDimensionPixelSize(R.dimen.inner_frame_layout_padding)

                val mScreenHeight = Resources.getSystem().displayMetrics.heightPixels
                val mScreenWidth = Resources.getSystem().displayMetrics.widthPixels

                val rightEdge = Math.min(binding.playerList.measuredWidth, mScreenWidth)
                val bottomEdge = Math.min(binding.playerList.measuredHeight, mScreenHeight)
                val verticalInset = (FACTOR * Math.max(rightEdge, bottomEdge)).toInt()

                binding.playerList.setPaddingRelative(
                        paddingStart,
                        if (mIsRound) verticalInset else verticalPadding,
                        paddingEnd,
                        paddingBottom + if (mIsRound) verticalInset else verticalPadding
                )
            }
        })
        binding.playerList.setOnGenericMotionListener(OnGenericMotionListener { v, event ->
            if (event.action == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {

                // Don't forget the negation here
                val delta = -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(requireActivity())

                // Swap these axes if you want to do horizontal scrolling instead
                v.scrollBy(0, Math.round(delta))

                return@OnGenericMotionListener true
            }
            false
        })
        binding.playerList.requestFocus()

        mAdapter = PlayerListAdapter(requireActivity())
        mAdapter.setOnClickListener(object : RecyclerOnClickListenerInterface {
            override fun onClick(view: View, position: Int) {
                onClickListener?.onClick(view, position)
            }
        })
        binding.playerList.adapter = mAdapter

        binding.playerGroup.visibility = View.GONE

        return outerView
    }

    fun setOnClickListener(listener: RecyclerOnClickListenerInterface?) {
        onClickListener = listener
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun showProgressBar(show: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(requireActivity()).addListener(this)

        binding.playerList.requestFocus()

        LocalBroadcastManager.getInstance(requireActivity())
                .sendBroadcast(Intent(WearableHelper.MusicPlayersPath))
        timer!!.start()
        getSelectedPlayerData()
    }

    private fun getSelectedPlayerData() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            var prefKey: String? = null
            try {
                val buff = Wearable.getDataClient(requireActivity())
                        .getDataItems(getWearDataUri("*", SleepTimerHelper.SleepTimerAudioPlayerPath))
                        .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (SleepTimerHelper.SleepTimerAudioPlayerPath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        prefKey = dataMap.getString(SleepTimerHelper.KEY_SELECTEDPLAYER, null)
                        break
                    }
                }
                buff.release()
            } catch (e: ExecutionException) {
                writeLine(Log.ERROR, e)
                prefKey = null
            } catch (e: InterruptedException) {
                writeLine(Log.ERROR, e)
                prefKey = null
            }
            selectedPlayer.setKey(prefKey)
        }
    }

    override fun onPause() {
        super.onPause()
        timer?.cancel()
        Wearable.getDataClient(requireActivity()).removeListener(this)
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        // Cancel timer
        timer?.cancel()
        showProgressBar(false)

        for (event in dataEventBuffer) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (WearableHelper.MusicPlayersPath == item.uri.path) {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    viewLifecycleOwner.lifecycleScope.launch {
                        updateMusicPlayers(dataMap)
                    }
                }
            }
        }
    }

    private suspend fun updateMusicPlayers(dataMap: DataMap) {
        val supported_players: List<String> = dataMap.getStringArrayList(WearableHelper.KEY_SUPPORTEDPLAYERS)
        val viewModels: MutableList<MusicPlayerViewModel> = ArrayList()
        val playerPref = selectedPlayer.key.value
        var selectedPlayerModel: MusicPlayerViewModel? = null
        for (key in supported_players) {
            val map = dataMap.getDataMap(key)

            val model = MusicPlayerViewModel()
            model.appLabel = map.getString(WearableHelper.KEY_LABEL)
            model.packageName = map.getString(WearableHelper.KEY_PKGNAME)
            model.activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
            model.bitmapIcon = ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(requireActivity()), map.getAsset(WearableHelper.KEY_ICON))

            viewModels.add(model)

            if (playerPref != null && model.key == playerPref) {
                selectedPlayerModel = model
            }
        }

        if (selectedPlayerModel == null) {
            selectedPlayer.setKey(null)
        } else {
            selectedPlayer.setKey(selectedPlayerModel.key)
        }

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
            mAdapter.updateItems(viewModels)

            binding.noplayersMessageview.visibility = if (viewModels.size > 0) View.GONE else View.VISIBLE
            binding.playerGroup.visibility = if (viewModels.size > 0) View.VISIBLE else View.GONE
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                if (binding.playerList.visibility == View.VISIBLE && !binding.playerList.hasFocus()) {
                    binding.playerList.requestFocus()
                }
            }
        }
    }
}