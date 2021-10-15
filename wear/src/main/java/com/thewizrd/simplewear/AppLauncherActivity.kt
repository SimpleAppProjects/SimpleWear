package com.thewizrd.simplewear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.wear.widget.WearableLinearLayoutManager
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.google.gson.stream.JsonReader
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.*
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.ImageUtils.toBitmap
import com.thewizrd.simplewear.adapters.AppsListAdapter
import com.thewizrd.simplewear.adapters.ListHeaderAdapter
import com.thewizrd.simplewear.adapters.SpacerAdapter
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.controls.WearChipButton
import com.thewizrd.simplewear.databinding.ActivityApplauncherBinding
import com.thewizrd.simplewear.helpers.CustomScrollingLayoutCallback
import com.thewizrd.simplewear.helpers.SpacerItemDecoration
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.InputStreamReader
import java.util.*

class AppLauncherActivity : WearableListenerActivity(), OnDataChangedListener {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivityApplauncherBinding
    private lateinit var mAdapter: AppsListAdapter
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityApplauncherBinding.inflate(layoutInflater)
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
                                            this@AppLauncherActivity,
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
                                            this@AppLauncherActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    finishAffinity()
                                }
                                else -> {
                                }
                            }
                        } else {
                            Logger.writeLine(
                                Log.INFO,
                                "%s: Unhandled action: %s",
                                "AppLauncherActivity",
                                intent.action
                            )
                        }
                    }
                }
            }
        }

        binding.appList.setHasFixedSize(true)
        //binding.appList.isEdgeItemsCenteringEnabled = true
        binding.appList.addItemDecoration(
            SpacerItemDecoration(
                dpToPx(16f).toInt(),
                dpToPx(4f).toInt()
            )
        )

        binding.appList.layoutManager =
            WearableLinearLayoutManager(this, CustomScrollingLayoutCallback())
        mAdapter = AppsListAdapter()
        mAdapter.setOnClickListener(object : ListAdapterOnClickInterface<AppItemViewModel> {
            override fun onClick(view: View, item: AppItemViewModel) {
                lifecycleScope.launch {
                    if (connect()) {
                        val nodeID = mPhoneNodeWithApp!!.id
                        sendMessage(
                            nodeID, WearableHelper.LaunchAppPath,
                            JSONParser.serializer(
                                Pair.create(item.packageName, item.activityName),
                                Pair::class.java
                            ).stringToBytes()
                        )
                    }
                }
            }
        })
        binding.appList.adapter = ConcatAdapter(
            ListHeaderAdapter(getString(R.string.action_apps)),
            mAdapter,
            SpacerAdapter(dpToPx(48f).toInt())
        )

        binding.retryFab.setOnClickListener {
            lifecycleScope.launch {
                updateConnectionStatus()
                requestAppsUpdate()
            }
        }

        findViewById<WearChipButton>(R.id.icons_pref).also { iconsPref ->
            iconsPref.setOnClickListener {
                iconsPref.toggle()
                Settings.setLoadAppIcons(iconsPref.isChecked)
                lifecycleScope.launch(Dispatchers.IO) {
                    val dataRequest = PutDataMapRequest.create(WearableHelper.AppsIconSettingsPath)
                    dataRequest.dataMap.putBoolean(WearableHelper.KEY_ICON, iconsPref.isChecked)
                    dataRequest.setUrgent()
                    runCatching {
                        Wearable
                            .getDataClient(this@AppLauncherActivity)
                            .putDataItem(dataRequest.asPutDataRequest())
                            .await()
                    }.onFailure {
                        Logger.writeLine(Log.ERROR, it)
                    }
                }
            }
            iconsPref.isChecked = Settings.isLoadAppIcons()
        }

        intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS)

        // Set timer for retrieving music player data
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val buff = Wearable.getDataClient(this@AppLauncherActivity)
                            .getDataItems(
                                WearableHelper.getWearDataUri(
                                    "*",
                                    WearableHelper.AppsPath
                                )
                            )
                            .await()

                        for (i in 0 until buff.count) {
                            val item = buff[i]
                            if (WearableHelper.AppsPath == item.uri.path) {
                                val appsList = try {
                                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                                    createAppsList(dataMap)
                                } catch (e: Exception) {
                                    Logger.writeLine(Log.ERROR, e)
                                    null
                                }
                                updateAppsList(appsList ?: emptyList())
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
            if (show) {
                binding.progressBar.show()
            } else {
                binding.progressBar.hide()
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            if (messageEvent.data != null && messageEvent.path == WearableHelper.LaunchAppPath) {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                lifecycleScope.launch {
                    when (status) {
                        ActionStatus.SUCCESS -> {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.SUCCESS_ANIMATION)
                                .showOn(this@AppLauncherActivity)
                        }
                        ActionStatus.PERMISSION_DENIED -> {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(
                                    ContextCompat.getDrawable(
                                        this@AppLauncherActivity,
                                        R.drawable.ws_full_sad
                                    )
                                )
                                .setMessage(this@AppLauncherActivity.getString(R.string.error_permissiondenied))
                                .showOn(this@AppLauncherActivity)

                            openAppOnPhone(false)
                        }
                        ActionStatus.FAILURE -> {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(
                                    ContextCompat.getDrawable(
                                        this@AppLauncherActivity,
                                        R.drawable.ws_full_sad
                                    )
                                )
                                .setMessage(this@AppLauncherActivity.getString(R.string.error_actionfailed))
                                .showOn(this@AppLauncherActivity)
                        }
                    }
                }
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            // Cancel timer
            timer?.cancel()

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (WearableHelper.AppsPath == item.uri.path) {
                        val appsList = try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            createAppsList(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                            null
                        }
                        updateAppsList(appsList ?: emptyList())
                        showProgressBar(false)
                    }
                }
            }
        }
    }

    private fun createAppsList(dataMap: DataMap): List<AppItemViewModel> {
        val availableApps =
            dataMap.getStringArrayList(WearableHelper.KEY_APPS) ?: return emptyList()
        val viewModels = ArrayList<AppItemViewModel>()
        for (key in availableApps) {
            val map = dataMap.getDataMap(key) ?: continue

            val model = AppItemViewModel().apply {
                appType = AppItemViewModel.AppType.APP
                appLabel = map.getString(WearableHelper.KEY_LABEL)
                packageName = map.getString(WearableHelper.KEY_PKGNAME)
                activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
            }
            viewModels.add(model)
        }

        return viewModels
    }

    private suspend fun createAppsList(items: List<AppItemData>): List<AppItemViewModel> {
        val viewModels = ArrayList<AppItemViewModel>(items.size)

        items.forEach { item ->
            val model = AppItemViewModel().apply {
                appType = AppItemViewModel.AppType.APP
                appLabel = item.label
                packageName = item.packageName
                activityName = item.activityName
                bitmapIcon = item.iconBitmap?.toBitmap()
            }
            viewModels.add(model)
        }

        return viewModels
    }

    private fun updateAppsList(viewModels: List<AppItemViewModel>) {
        lifecycleScope.launch {
            mAdapter.submitList(viewModels)
            showProgressBar(false)
            binding.noappsView.visibility = if (viewModels.isNotEmpty()) View.GONE else View.VISIBLE
            binding.appList.visibility = if (viewModels.isNotEmpty()) View.VISIBLE else View.GONE
            lifecycleScope.launch {
                if (binding.appList.visibility == View.VISIBLE && !binding.appList.hasFocus()) {
                    binding.appList.requestFocus()
                }
            }
        }
    }

    private fun requestAppsUpdate() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.AppsPath, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getChannelClient(this).registerChannelCallback(mChannelCallback)

        binding.appList.requestFocus()

        // Update statuses
        lifecycleScope.launch {
            updateConnectionStatus()
            requestAppsUpdate()
            // Wait for music player update
            timer!!.start()
        }
    }

    override fun onPause() {
        Wearable.getChannelClient(this).unregisterChannelCallback(mChannelCallback)
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    private val mChannelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            super.onChannelOpened(channel)
            // Check if we can load the data
            if (channel.path == WearableHelper.AppsPath) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val channelClient = Wearable.getChannelClient(this@AppLauncherActivity)
                    runCatching {
                        val inputStream = channelClient.getInputStream(channel).await()
                        inputStream.use {
                            val reader = JsonReader(InputStreamReader(it))
                            val items = AppItemSerializer.deserialize(reader)
                            updateAppsList(createAppsList(items ?: emptyList()))
                        }
                    }
                }
            }
        }
    }
}