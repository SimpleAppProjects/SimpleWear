package com.thewizrd.simplewear

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.drawer.WearableDrawerLayout
import androidx.wear.widget.drawer.WearableDrawerView
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.*
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.simplewear.adapters.ListHeaderAdapter
import com.thewizrd.simplewear.adapters.MusicPlayerListAdapter
import com.thewizrd.simplewear.adapters.SpacerAdapter
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.controls.WearChipButton
import com.thewizrd.simplewear.databinding.ActivityMusicplayerlistBinding
import com.thewizrd.simplewear.helpers.AppItemComparator
import com.thewizrd.simplewear.helpers.CustomScrollingLayoutCallback
import com.thewizrd.simplewear.helpers.SpacerItemDecoration
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import com.thewizrd.simplewear.media.MediaPlayerActivity
import com.thewizrd.simplewear.media.MediaPlayerFilterFragment
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.viewmodels.MediaPlayerListViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class MediaPlayerListActivity : WearableListenerActivity(), MessageClient.OnMessageReceivedListener,
    OnDataChangedListener {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivityMusicplayerlistBinding
    private lateinit var mAdapter: MusicPlayerListAdapter
    private var timer: CountDownTimer? = null

    private val mMediaAppsList: MutableSet<AppItemViewModel> = TreeSet(AppItemComparator())
    private val viewModel: MediaPlayerListViewModel by viewModels()

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMusicplayerlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lifecycleScope.launch {
                    if (intent.action != null) {
                        if (ACTION_UPDATECONNECTIONSTATUS == intent.action) {
                            when (WearConnectionStatus.valueOf(
                                intent.getIntExtra(
                                    EXTRA_CONNECTIONSTATUS,
                                    0
                                )
                            )) {
                                WearConnectionStatus.DISCONNECTED -> {
                                    // Navigate
                                    startActivity(
                                        Intent(
                                            this@MediaPlayerListActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    finishAffinity()
                                }
                                WearConnectionStatus.APPNOTINSTALLED -> {
                                    // Open store on remote device
                                    val intentAndroid = Intent(Intent.ACTION_VIEW)
                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                        .setData(WearableHelper.getPlayStoreURI())

                                    runCatching {
                                        remoteActivityHelper.startRemoteActivity(intentAndroid)
                                            .await()

                                        showConfirmationOverlay(true)
                                    }.onFailure {
                                        if (it !is CancellationException) {
                                            showConfirmationOverlay(false)
                                        }
                                    }

                                    // Navigate
                                    startActivity(
                                        Intent(
                                            this@MediaPlayerListActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    finishAffinity()
                                }
                            }
                        } else {
                            Logger.writeLine(
                                Log.INFO,
                                "%s: Unhandled action: %s",
                                "MediaPlayerListActivity",
                                intent.action
                            )
                        }
                    }
                }
            }
        }

        binding.drawerLayout.setDrawerStateCallback(object : WearableDrawerLayout.DrawerStateCallback() {
            override fun onDrawerOpened(layout: WearableDrawerLayout, drawerView: WearableDrawerView) {
                super.onDrawerOpened(layout, drawerView)
                drawerView.requestFocus()
            }

            override fun onDrawerClosed(layout: WearableDrawerLayout, drawerView: WearableDrawerView) {
                super.onDrawerClosed(layout, drawerView)
                drawerView.clearFocus()
                binding.playerList.requestFocus()
            }

            override fun onDrawerStateChanged(layout: WearableDrawerLayout, newState: Int) {
                super.onDrawerStateChanged(layout, newState)
                if (newState == WearableDrawerView.STATE_IDLE &&
                    binding.bottomActionDrawer.isPeeking && binding.bottomActionDrawer.hasFocus()
                ) {
                    binding.bottomActionDrawer.clearFocus()
                }
            }
        })

        binding.bottomActionDrawer.visibility = View.VISIBLE
        binding.bottomActionDrawer.isPeekOnScrollDownEnabled = true
        binding.bottomActionDrawer.setIsAutoPeekEnabled(true)
        binding.bottomActionDrawer.setIsLocked(false)

        val mSwitch = findViewById<WearChipButton>(R.id.autolaunch_pref)
        mSwitch.isChecked = Settings.isAutoLaunchMediaCtrlsEnabled
        mSwitch.setOnClickListener {
            val state = !Settings.isAutoLaunchMediaCtrlsEnabled
            Settings.setAutoLaunchMediaCtrls(state)
            mSwitch.isChecked = state
        }

        findViewById<View>(R.id.filter_apps_btn).setOnClickListener { v ->
            val fragment = MediaPlayerFilterFragment()

            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit()
        }

        binding.playerList.setHasFixedSize(true)
        //binding.playerList.isEdgeItemsCenteringEnabled = true
        binding.playerList.addItemDecoration(
            SpacerItemDecoration(
                dpToPx(16f).toInt(),
                dpToPx(4f).toInt()
            )
        )

        binding.playerList.layoutManager =
            WearableLinearLayoutManager(this, CustomScrollingLayoutCallback())
        mAdapter = MusicPlayerListAdapter()
        mAdapter.setOnClickListener(object : ListAdapterOnClickInterface<AppItemViewModel> {
            override fun onClick(view: View, item: AppItemViewModel) {
                lifecycleScope.launch {
                    if (connect()) {
                        val nodeID = mPhoneNodeWithApp!!.id
                        sendMessage(
                            nodeID,
                            MediaHelper.OpenMusicPlayerPath,
                            JSONParser.serializer(
                                Pair.create(item.packageName, item.activityName),
                                Pair::class.java
                            ).stringToBytes()
                        )
                    }
                    startActivity(
                        MediaPlayerActivity.buildIntent(
                            this@MediaPlayerListActivity,
                            item
                        )
                    )
                }
            }
        })
        binding.playerList.adapter = ConcatAdapter(
            ListHeaderAdapter(getString(R.string.action_apps)),
            mAdapter,
            SpacerAdapter(dpToPx(48f).toInt())
        )

        binding.retryFab.setOnClickListener {
            lifecycleScope.launch {
                updateConnectionStatus()
                requestPlayersUpdate()
            }
        }

        intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS)

        // Set timer for retrieving music player data
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                refreshMusicPlayers()
            }
        }

        lifecycleScope.launchWhenResumed {
            if (Settings.isAutoLaunchMediaCtrlsEnabled) {
                if (connect()) {
                    sendMessage(
                        mPhoneNodeWithApp!!.id,
                        MediaHelper.MediaPlayerAutoLaunchPath,
                        null
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.filteredAppsList.value = Settings.getMusicPlayersFilter()

        viewModel.filteredAppsList.observe(this, {
            if (mMediaAppsList.isNotEmpty()) {
                updateAppsList()
            }
        })
    }

    private fun showProgressBar(show: Boolean) {
        lifecycleScope.launch {
            if (show) {
                binding.progressBar.show()
            } else {
                binding.progressBar.hide()
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        when (messageEvent.path) {
            MediaHelper.MusicPlayersPath -> {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                if (status == ActionStatus.PERMISSION_DENIED) {
                    timer?.cancel()

                    CustomConfirmationOverlay()
                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                        .setCustomDrawable(ContextCompat.getDrawable(this, R.drawable.ws_full_sad))
                        .setMessage(getString(R.string.error_permissiondenied))
                        .showOn(this)

                    openAppOnPhone(false)

                    mMediaAppsList.clear()
                    updateAppsList()
                } else if (status == ActionStatus.SUCCESS) {
                    refreshMusicPlayers()
                }
            }
            MediaHelper.MediaPlayerAutoLaunchPath -> {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                if (status == ActionStatus.SUCCESS) {
                    startActivity(MediaPlayerActivity.buildAutoLaunchIntent(this))
                }
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            // Cancel timer
            timer?.cancel()
            showProgressBar(false)

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (MediaHelper.MusicPlayersPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateMusicPlayers(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            }
        }
    }

    private fun refreshMusicPlayers() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(this@MediaPlayerListActivity)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MusicPlayersPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MusicPlayersPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateMusicPlayers(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                        showProgressBar(false)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    @Synchronized
    private suspend fun updateMusicPlayers(dataMap: DataMap) {
        val supported_players =
            dataMap.getStringArrayList(MediaHelper.KEY_SUPPORTEDPLAYERS) ?: return

        mMediaAppsList.clear()

        for (key in supported_players) {
            val map = dataMap.getDataMap(key) ?: continue

            val model = AppItemViewModel().apply {
                appLabel = map.getString(WearableHelper.KEY_LABEL)
                packageName = map.getString(WearableHelper.KEY_PKGNAME)
                activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
                bitmapIcon = map.getAsset(WearableHelper.KEY_ICON)?.let {
                    try {
                        ImageUtils.bitmapFromAssetStream(
                            Wearable.getDataClient(this@MediaPlayerListActivity),
                            it
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            mMediaAppsList.add(model)
        }

        viewModel.mediaAppsList.postValue(mMediaAppsList.toList())
        updateAppsList()
    }

    private fun updateAppsList() {
        lifecycleScope.launch {
            val filteredApps = Settings.getMusicPlayersFilter()

            mAdapter.submitList(
                if (filteredApps.isNullOrEmpty()) {
                    mMediaAppsList.toList()
                } else {
                    mMediaAppsList.toMutableList().apply {
                        removeIf { !filteredApps.contains(it.packageName) }
                    }
                }
            )
            showProgressBar(false)
            binding.noplayersView.visibility =
                if (mMediaAppsList.size > 0) View.GONE else View.VISIBLE
            binding.playerList.visibility = if (mMediaAppsList.size > 0) View.VISIBLE else View.GONE
            lifecycleScope.launch {
                if (binding.playerList.visibility == View.VISIBLE && !binding.playerList.hasFocus()) {
                    binding.playerList.requestFocus()
                }
            }
        }
    }

    private fun requestPlayersUpdate() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MusicPlayersPath, null)
            }
        }
    }

    private fun requestPlayerDisconnect() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayerDisconnectPath, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)

        binding.playerList.requestFocus()

        // Update statuses
        lifecycleScope.launch {
            updateConnectionStatus()
            requestPlayersUpdate()
            // Wait for music player update
            timer!!.start()
        }
    }

    override fun onPause() {
        requestPlayerDisconnect()
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }
}