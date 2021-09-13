package com.thewizrd.simplewear

import android.content.*
import android.os.Bundle
import android.os.CountDownTimer
import android.support.wearable.view.WearableDialogHelper
import android.util.Log
import android.view.View
import android.widget.Switch
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.drawer.WearableDrawerLayout
import androidx.wear.widget.drawer.WearableDrawerView
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.*
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.adapters.MusicPlayerListAdapter
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityMusicplayerlistBinding
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver
import com.thewizrd.simplewear.media.MediaPlayerActivity
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.Dispatchers
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

    private val mMediaAppsList: MutableList<AppItemViewModel> = ArrayList()

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
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    )
                                    finishAffinity()
                                }
                                WearConnectionStatus.APPNOTINSTALLED -> {
                                    // Open store on remote device
                                    val intentAndroid = Intent(Intent.ACTION_VIEW)
                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                        .setData(WearableHelper.getPlayStoreURI())

                                    RemoteIntent.startRemoteActivity(
                                        this@MediaPlayerListActivity, intentAndroid,
                                        ConfirmationResultReceiver(this@MediaPlayerListActivity)
                                    )

                                    // Navigate
                                    startActivity(
                                        Intent(
                                            this@MediaPlayerListActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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

        val mSwitch = findViewById<Switch>(R.id.autolaunch_pref_switch)
        mSwitch.isChecked = Settings.isAutoLaunchMediaCtrlsEnabled
        findViewById<View>(R.id.autolaunch_pref).setOnClickListener {
            val state = !Settings.isAutoLaunchMediaCtrlsEnabled
            Settings.setAutoLaunchMediaCtrls(state)
            mSwitch.isChecked = state
        }

        findViewById<View>(R.id.filter_apps_btn).setOnClickListener { v ->
            val filteredApps = Settings.getMusicPlayersFilter()

            val appItems = arrayOfNulls<CharSequence>(mMediaAppsList.size)
            val checkedItems = BooleanArray(mMediaAppsList.size)
            val selectedItems = filteredApps.toMutableSet()

            mMediaAppsList.forEachIndexed { i, it ->
                appItems[i] = it.appLabel
                checkedItems[i] = filteredApps.contains(it.packageName)
            }

            val dialog = WearableDialogHelper.DialogBuilder(v.context, R.style.WearDialogTheme)
                .setPositiveIcon(R.drawable.round_button_ok)
                .setNegativeIcon(R.drawable.round_button_cancel)
                .setTitle(R.string.title_filter_apps)
                .setMultiChoiceItems(appItems, checkedItems) { d, which, isChecked ->
                    val appName = appItems[which]

                    // Insert/Remove from collection
                    val model =
                        mMediaAppsList.find { it.appLabel == appName } ?: return@setMultiChoiceItems
                    if (isChecked) {
                        selectedItems.add(model.packageName!!)
                    } else {
                        selectedItems.remove(model.packageName)
                    }
                }
                .setCancelable(true)
                .setOnDismissListener {
                    // Update filtered apps
                    Settings.setMusicPlayersFilter(selectedItems)

                    // Update list
                    updateAppsList()
                }
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null)
                .show()

            dialog.listView?.requestFocus()
        }

        binding.playerList.setHasFixedSize(true)
        binding.playerList.isEdgeItemsCenteringEnabled = true

        binding.playerList.layoutManager = WearableLinearLayoutManager(this)
        mAdapter = MusicPlayerListAdapter()
        mAdapter.setOnClickListener(object : ListAdapterOnClickInterface<AppItemViewModel> {
            override fun onClick(view: View, position: Int, item: AppItemViewModel) {
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
        binding.playerList.adapter = mAdapter

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

    private fun showProgressBar(show: Boolean) {
        lifecycleScope.launch {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
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
                        .setCustomDrawable(ContextCompat.getDrawable(this, R.drawable.ic_full_sad))
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
                bitmapIcon = ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(this@MediaPlayerListActivity),
                    map.getAsset(WearableHelper.KEY_ICON)
                )
            }
            mMediaAppsList.add(model)
        }

        updateAppsList()
    }

    private fun updateAppsList() {
        lifecycleScope.launch {
            val filteredApps = Settings.getMusicPlayersFilter()

            mAdapter.submitList(
                if (filteredApps.isNullOrEmpty()) {
                    mMediaAppsList.toList()
                } else {
                    mMediaAppsList.filter { filteredApps.contains(it.packageName) }
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