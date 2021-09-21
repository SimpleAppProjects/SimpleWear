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
import androidx.wear.widget.WearableLinearLayoutManager
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.ListAdapterOnClickInterface
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.adapters.AppsListAdapter
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityApplauncherBinding
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
        binding.appList.isEdgeItemsCenteringEnabled = true

        binding.appList.layoutManager = WearableLinearLayoutManager(this)
        mAdapter = AppsListAdapter()
        mAdapter.setOnClickListener(object : ListAdapterOnClickInterface<AppItemViewModel> {
            override fun onClick(view: View, position: Int, item: AppItemViewModel) {
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
        binding.appList.adapter = mAdapter

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
                                try {
                                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                                    updateApps(dataMap)
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
            showProgressBar(false)

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (WearableHelper.AppsPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateApps(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateApps(dataMap: DataMap) {
        val availableApps = dataMap.getStringArrayList(WearableHelper.KEY_APPS) ?: return
        val viewModels = ArrayList<AppItemViewModel>()
        for (key in availableApps) {
            val map = dataMap.getDataMap(key) ?: continue

            val model = AppItemViewModel().apply {
                appType = AppItemViewModel.AppType.APP
                appLabel = map.getString(WearableHelper.KEY_LABEL)
                packageName = map.getString(WearableHelper.KEY_PKGNAME)
                activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
                bitmapIcon = try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(this@AppLauncherActivity),
                        map.getAsset(WearableHelper.KEY_ICON)
                    )
                } catch (e: Exception) {
                    null
                }
            }
            viewModels.add(model)
        }

        lifecycleScope.launch {
            mAdapter.submitList(viewModels)
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
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }
}