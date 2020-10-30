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
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.helpers.ActionStatus
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearConnectionStatus.Companion.valueOf
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.WearableHelper.getWearDataUri
import com.thewizrd.shared_resources.helpers.WearableHelper.playStoreURI
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.JSONParser.serializer
import com.thewizrd.shared_resources.utils.Logger.writeLine
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.adapters.AppsListAdapter
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityApplauncherBinding
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import java.util.concurrent.ExecutionException

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
                            val connStatus = valueOf(intent.getIntExtra(EXTRA_CONNECTIONSTATUS, 0))
                            when (connStatus) {
                                WearConnectionStatus.DISCONNECTED -> {
                                    // Navigate
                                    startActivity(Intent(this@AppLauncherActivity, PhoneSyncActivity::class.java)
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                                    finishAffinity()
                                }
                                WearConnectionStatus.APPNOTINSTALLED -> {
                                    // Open store on remote device
                                    val intentAndroid = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(playStoreURI)

                                    RemoteIntent.startRemoteActivity(this@AppLauncherActivity, intentAndroid,
                                            ConfirmationResultReceiver(this@AppLauncherActivity))
                                }
                                else -> {
                                }
                            }
                        } else {
                            writeLine(Log.INFO, "%s: Unhandled action: %s", "MusicPlayerActivity", intent.action)
                        }
                    }
                }
            }
        }

        binding.appList.setHasFixedSize(true)
        binding.appList.isEdgeItemsCenteringEnabled = true

        binding.appList.layoutManager = WearableLinearLayoutManager(this)
        mAdapter = AppsListAdapter(this)
        mAdapter.setOnClickListener(object : RecyclerOnClickListenerInterface {
            override fun onClick(view: View, position: Int) {
                val vm = mAdapter.dataset[position]
                lifecycleScope.launch {
                    if (connect()) {
                        sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.LaunchAppPath,
                                serializer(Pair.create(vm.packageName, vm.activityName), Pair::class.java).stringToBytes())
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
                                .getDataItems(getWearDataUri("*", WearableHelper.AppsPath))
                                .await()

                        for (i in 0 until buff.count) {
                            val item = buff[i]
                            if (WearableHelper.AppsPath == item.uri.path) {
                                val dataMap = DataMapItem.fromDataItem(item).dataMap
                                updateApps(dataMap)
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
    }

    private fun showProgressBar(show: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            if (messageEvent.data != null && messageEvent.path == WearableHelper.AppsPath) {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                lifecycleScope.launch(Dispatchers.Main) {
                    when (status) {
                        ActionStatus.SUCCESS ->
                            CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.SUCCESS_ANIMATION)
                                    .showOn(this@AppLauncherActivity)
                        ActionStatus.PERMISSION_DENIED ->
                            CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(ContextCompat.getDrawable(this@AppLauncherActivity, R.drawable.ic_full_sad))
                                    .setMessage(this@AppLauncherActivity.getString(R.string.error_permissiondenied))
                                    .showOn(this@AppLauncherActivity)
                        ActionStatus.FAILURE ->
                            CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(ContextCompat.getDrawable(this@AppLauncherActivity, R.drawable.ic_full_sad))
                                    .setMessage(this@AppLauncherActivity.getString(R.string.action_failed_playmusic))
                                    .showOn(this@AppLauncherActivity)
                        else -> {
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
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updateApps(dataMap)
                    }
                }
            }
        }
    }

    private suspend fun updateApps(dataMap: DataMap) {
        val availableApps: List<String> = dataMap.getStringArrayList(WearableHelper.KEY_APPS)
        val viewModels: MutableList<AppItemViewModel> = ArrayList()
        for (key in availableApps) {
            val map = dataMap.getDataMap(key)

            val model = AppItemViewModel()
            model.appType = AppItemViewModel.AppType.APP
            model.appLabel = map.getString(WearableHelper.KEY_LABEL)
            model.packageName = map.getString(WearableHelper.KEY_PKGNAME)
            model.activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
            model.bitmapIcon = ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(this@AppLauncherActivity), map.getAsset(WearableHelper.KEY_ICON))
            viewModels.add(model)
        }

        lifecycleScope.launch(Dispatchers.Main) {
            mAdapter.updateItems(viewModels)
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
        Wearable.getMessageClient(this).addListener(this)
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
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }
}