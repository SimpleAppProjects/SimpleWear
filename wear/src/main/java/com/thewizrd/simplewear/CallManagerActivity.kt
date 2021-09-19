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
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.*
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityCallmanagerBinding
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class CallManagerActivity : WearableListenerActivity(), DataClient.OnDataChangedListener {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivityCallmanagerBinding
    private lateinit var timer: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCallmanagerBinding.inflate(layoutInflater)
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
                                            this@CallManagerActivity,
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
                                        this@CallManagerActivity, intentAndroid,
                                        ConfirmationResultReceiver(this@CallManagerActivity)
                                    )

                                    // Navigate
                                    startActivity(
                                        Intent(
                                            this@CallManagerActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
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
                                "CallManagerActivity",
                                intent.action
                            )
                        }
                    }
                }
            }
        }

        intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS)

        // Set timer for retrieving call status
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                refreshCallUI()
            }
        }

        binding.endcallButton.setOnClickListener {
            lifecycleScope.launch {
                if (connect()) {
                    sendMessage(mPhoneNodeWithApp!!.id, InCallUIHelper.EndCallPath, null)
                }
            }
        }

        binding.muteButton.setOnClickListener {
            val isChecked = binding.muteButton.isChecked
            lifecycleScope.launch {
                if (connect()) {
                    sendMessage(
                        mPhoneNodeWithApp!!.id,
                        InCallUIHelper.MuteMicPath,
                        isChecked.booleanToBytes()
                    )
                }
            }
        }

        binding.volumeButton.setOnClickListener {
            val intent: Intent = Intent(this, ValueActionActivity::class.java)
                .putExtra(EXTRA_ACTION, Actions.VOLUME)
                .putExtra(ValueActionActivity.EXTRA_STREAMTYPE, AudioStreamType.VOICE_CALL)
            this.startActivityForResult(intent, -1)
        }

        binding.speakerphoneButton.setOnClickListener {
            val isChecked = binding.speakerphoneButton.isChecked
            lifecycleScope.launch {
                if (connect()) {
                    sendMessage(
                        mPhoneNodeWithApp!!.id,
                        InCallUIHelper.SpeakerphonePath,
                        isChecked.booleanToBytes()
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
            InCallUIHelper.CallStatePath -> {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                if (status == ActionStatus.PERMISSION_DENIED) {
                    timer.cancel()

                    CustomConfirmationOverlay()
                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                        .setCustomDrawable(ContextCompat.getDrawable(this, R.drawable.ic_full_sad))
                        .setMessage(getString(R.string.error_permissiondenied))
                        .showOn(this)

                    openAppOnPhone(false)

                    showProgressBar(false)
                    showInCallUI(false)
                } else if (status == ActionStatus.SUCCESS) {
                    refreshCallUI()
                }
            }
            InCallUIHelper.MuteMicStatusPath -> {
                val toggle = messageEvent.data.bytesToBool()
                binding.muteButton.isChecked = toggle
            }
            InCallUIHelper.SpeakerphoneStatusPath -> {
                val toggle = messageEvent.data.bytesToBool()
                binding.speakerphoneButton.isChecked = toggle
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            showProgressBar(false)

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (InCallUIHelper.CallStatePath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateCallUI(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateCallUI(dataMap: DataMap) {
        val callActive = dataMap.getBoolean(InCallUIHelper.KEY_CALLACTIVE, false)
        val callerName = dataMap.getString(InCallUIHelper.KEY_CALLERNAME)
        val callerBmp = try {
            ImageUtils.bitmapFromAssetStream(
                Wearable.getDataClient(this),
                dataMap.getAsset(InCallUIHelper.KEY_CALLERBMP)
            )
        } catch (e: Exception) {
            null
        }

        lifecycleScope.launch {
            if (callActive) {
                if (!callerName.isNullOrBlank()) {
                    binding.incallCallerName.text = callerName
                } else {
                    binding.incallCallerName.setText(R.string.message_callactive)
                }
                binding.callBackground.setImageBitmap(callerBmp)
                showInCallUI()
            } else {
                showInCallUI(false)
            }
        }
    }

    private fun showInCallUI(show: Boolean = true) {
        binding.incallUi.visibility = if (show) View.VISIBLE else View.GONE
        binding.nocallPrompt.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun refreshCallUI() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(this@CallManagerActivity)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            InCallUIHelper.CallStatePath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (InCallUIHelper.CallStatePath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateCallUI(dataMap)
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

    private fun requestCallState() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, InCallUIHelper.CallStatePath, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)

        binding.incallCallerName.requestFocus()

        // Update statuses
        lifecycleScope.launch {
            showProgressBar(true)
            binding.nocallPrompt.visibility = View.GONE

            updateConnectionStatus()
            requestCallState()
            // Wait for call state update
            timer.start()
        }
    }

    override fun onPause() {
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}