package com.thewizrd.simplewear

import android.content.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.support.wearable.view.WearableDialogHelper
import android.util.Log
import android.view.View
import android.widget.ImageView
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
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.adapters.MusicPlayerListAdapter
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityMusicplaybackBinding
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.preferences.Settings.isAutoLaunchMediaCtrlsEnabled
import com.thewizrd.simplewear.preferences.Settings.setAutoLaunchMediaCtrls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

class MusicPlayerActivity : WearableListenerActivity(), OnDataChangedListener {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivityMusicplaybackBinding
    private lateinit var mMediaCtrlIcon: ImageView
    private lateinit var mMediaCtrlBtn: View
    private lateinit var mAdapter: MusicPlayerListAdapter
    private var timer: CountDownTimer? = null

    private val mMediaAppsList: MutableList<AppItemViewModel> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMusicplaybackBinding.inflate(layoutInflater)
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
                                            this@MusicPlayerActivity,
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

                                    RemoteIntent.startRemoteActivity(this@MusicPlayerActivity, intentAndroid,
                                            ConfirmationResultReceiver(this@MusicPlayerActivity))
                                }
                                else -> {
                                }
                            }
                        } else {
                            Logger.writeLine(
                                Log.INFO,
                                "%s: Unhandled action: %s",
                                "MusicPlayerActivity",
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
                        binding.bottomActionDrawer.isPeeking && binding.bottomActionDrawer.hasFocus()) {
                    binding.bottomActionDrawer.clearFocus()
                }
            }
        })

        mMediaCtrlIcon = findViewById(R.id.launch_mediacontrols_icon)
        mMediaCtrlBtn = findViewById(R.id.launch_mediacontrols_ctrl)

        val mSwitch = findViewById<Switch>(R.id.autolaunch_pref_switch)
        mSwitch.isChecked = isAutoLaunchMediaCtrlsEnabled
        findViewById<View>(R.id.autolaunch_pref).setOnClickListener {
            val state = !isAutoLaunchMediaCtrlsEnabled
            setAutoLaunchMediaCtrls(state)
            mSwitch.isChecked = state
        }

        findViewById<View>(R.id.filter_apps_btn).setOnClickListener { v ->
            val filteredApps = Settings.getMusicPlayersFilter()

            val appItems = arrayOfNulls<CharSequence>(mMediaAppsList.size)
            val checkedItems = BooleanArray(mMediaAppsList.size)
            val selectedItems = filteredApps.toMutableSet()

            mMediaAppsList.forEachIndexed { i, it ->
                appItems[i] = it.appName
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
                        mMediaAppsList.find { it.appName == appName } ?: return@setMultiChoiceItems
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
        mAdapter.setOnClickListener(object : RecyclerOnClickListenerInterface {
            override fun onClick(view: View, position: Int) {
                val vm = mAdapter.currentList[position]
                lifecycleScope.launch {
                    if (connect()) {
                        sendMessage(
                            mPhoneNodeWithApp!!.id, WearableHelper.PlayCommandPath,
                            JSONParser.serializer(
                                Pair.create(vm.packageName, vm.activityName),
                                Pair::class.java
                            ).stringToBytes()
                        )
                    }
                }
            }
        })
        binding.playerList.adapter = mAdapter

        intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS)

        // Set timer for retrieving music player data
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val buff = Wearable.getDataClient(this@MusicPlayerActivity)
                            .getDataItems(
                                WearableHelper.getWearDataUri(
                                    "*",
                                    WearableHelper.MusicPlayersPath
                                )
                            )
                                .await()

                        for (i in 0 until buff.count) {
                            val item = buff[i]
                            if (WearableHelper.MusicPlayersPath == item.uri.path) {
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
        }
    }

    private fun showProgressBar(show: Boolean) {
        lifecycleScope.launch {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            if (messageEvent.data != null && messageEvent.path == WearableHelper.PlayCommandPath) {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                when (status) {
                    ActionStatus.SUCCESS -> if (isAutoLaunchMediaCtrlsEnabled) {
                        val mediaCtrlCmpName = ComponentName("com.google.android.wearable.app",
                                "com.google.android.clockwork.home.media.MediaControlActivity")

                        try {
                            // Check if activity exists
                            packageManager.getActivityInfo(mediaCtrlCmpName, 0)

                            val mediaCtrlIntent = Intent()
                            mediaCtrlIntent
                                    .setAction(Intent.ACTION_MAIN)
                                    .addCategory(Intent.CATEGORY_LAUNCHER)
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).component = mediaCtrlCmpName
                            startActivity(mediaCtrlIntent)
                        } catch (e: Exception) {
                            // Do nothing
                        }
                    }
                    ActionStatus.PERMISSION_DENIED ->
                        lifecycleScope.launch {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(
                                    ContextCompat.getDrawable(
                                        this@MusicPlayerActivity,
                                        R.drawable.ic_full_sad
                                    )
                                )
                                .setMessage(this@MusicPlayerActivity.getString(R.string.error_permissiondenied))
                                .showOn(this@MusicPlayerActivity)
                        }
                    ActionStatus.FAILURE ->
                        lifecycleScope.launch {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(
                                    ContextCompat.getDrawable(
                                        this@MusicPlayerActivity,
                                        R.drawable.ic_full_sad
                                    )
                                )
                                .setMessage(this@MusicPlayerActivity.getString(R.string.action_failed_playmusic))
                                .showOn(this@MusicPlayerActivity)
                        }
                    else -> {
                    }
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
                    if (WearableHelper.MusicPlayersPath == item.uri.path) {
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

    private suspend fun updateMusicPlayers(dataMap: DataMap) {
        val supported_players =
            dataMap.getStringArrayList(WearableHelper.KEY_SUPPORTEDPLAYERS) ?: return

        mMediaAppsList.clear()

        for (key in supported_players) {
            val map = dataMap.getDataMap(key) ?: continue

            val model = AppItemViewModel().apply {
                appLabel = String.format(
                    "%s %s",
                    getString(R.string.prefix_playmusic),
                    map.getString(WearableHelper.KEY_LABEL)
                )
                appName = map.getString(WearableHelper.KEY_LABEL)
                packageName = map.getString(WearableHelper.KEY_PKGNAME)
                activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
                bitmapIcon = ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(this@MusicPlayerActivity),
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

            mAdapter.submitList(if (filteredApps.isNullOrEmpty()) mMediaAppsList else mMediaAppsList.filter {
                filteredApps.contains(
                    it.packageName
                )
            })
            binding.noplayersMessageview.visibility =
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
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.MusicPlayersPath, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)

        binding.playerList.requestFocus()

        val mediaCtrlCmpName = ComponentName("com.google.android.wearable.app",
                "com.google.android.clockwork.home.media.MediaControlActivity")
        try {
            mMediaCtrlIcon.setImageDrawable(packageManager.getActivityIcon(mediaCtrlCmpName))
            mMediaCtrlBtn.setOnClickListener {
                val mediaCtrlIntent = Intent()
                mediaCtrlIntent
                        .setAction(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).component = mediaCtrlCmpName
                startActivity(mediaCtrlIntent)
            }
            binding.bottomActionDrawer.visibility = View.VISIBLE
            binding.bottomActionDrawer.isPeekOnScrollDownEnabled = true
            binding.bottomActionDrawer.setIsAutoPeekEnabled(true)
            binding.bottomActionDrawer.setIsLocked(false)
        } catch (e: PackageManager.NameNotFoundException) {
            mMediaCtrlBtn.setOnClickListener(null)
            mMediaCtrlBtn.visibility = View.GONE
            binding.bottomActionDrawer.visibility = View.GONE
            binding.bottomActionDrawer.isPeekOnScrollDownEnabled = false
            binding.bottomActionDrawer.setIsAutoPeekEnabled(false)
            binding.bottomActionDrawer.setIsLocked(true)
        }

        // Update statuses
        lifecycleScope.launch {
            updateConnectionStatus()
            requestPlayersUpdate()
            // Wait for music player update
            timer!!.start()
        }
    }

    override fun onPause() {
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }
}