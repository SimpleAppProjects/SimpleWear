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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.helpers.*
import com.thewizrd.shared_resources.helpers.WearConnectionStatus.Companion.valueOf
import com.thewizrd.shared_resources.helpers.WearableHelper.playStoreURI
import com.thewizrd.shared_resources.tasks.AsyncTask
import com.thewizrd.shared_resources.utils.JSONParser.deserializer
import com.thewizrd.shared_resources.utils.JSONParser.serializer
import com.thewizrd.shared_resources.utils.Logger.writeLine
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityValueactionBinding
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver

class ValueActionActivity : WearableListenerActivity() {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivityValueactionBinding

    private var mAction: Actions? = null

    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityValueactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intent = intent
        if (intent.hasExtra(EXTRA_ACTION)) {
            mAction = intent.getSerializableExtra(EXTRA_ACTION) as Actions
            if (mAction !== Actions.VOLUME) {
                // Not a ValueAction
                setResult(RESULT_CANCELED)
                finish()
            }
        } else {
            // No action given
            setResult(RESULT_CANCELED)
            finish()
        }

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                AsyncTask.run {
                    if (intent.action != null) {
                        if (ACTION_UPDATECONNECTIONSTATUS == intent.action) {
                            val connStatus = valueOf(intent.getIntExtra(EXTRA_CONNECTIONSTATUS, 0))
                            when (connStatus) {
                                WearConnectionStatus.DISCONNECTED -> {
                                    // Navigate
                                    startActivity(Intent(this@ValueActionActivity, PhoneSyncActivity::class.java)
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                                    finishAffinity()
                                }
                                WearConnectionStatus.APPNOTINSTALLED -> {
                                    // Open store on remote device
                                    val intentAndroid = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(playStoreURI)

                                    RemoteIntent.startRemoteActivity(this@ValueActionActivity, intentAndroid,
                                            ConfirmationResultReceiver(this@ValueActionActivity))
                                }
                                else -> {
                                }
                            }
                        } else if (WearableHelper.ActionsPath == intent.action) {
                            timer?.cancel()

                            val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                            val action = deserializer(jsonData, Action::class.java)

                            if (!action!!.isActionSuccessful) {
                                runOnUiThread {
                                    when (action.actionStatus) {
                                        ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                            CustomConfirmationOverlay()
                                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                    .setCustomDrawable(ContextCompat.getDrawable(this@ValueActionActivity, R.drawable.ic_full_sad))
                                                    .setMessage(getString(R.string.error_actionfailed))
                                                    .showOn(this@ValueActionActivity)
                                        }
                                        ActionStatus.PERMISSION_DENIED -> {
                                            CustomConfirmationOverlay()
                                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                    .setCustomDrawable(ContextCompat.getDrawable(this@ValueActionActivity, R.drawable.ic_full_sad))
                                                    .setMessage(getString(R.string.error_permissiondenied))
                                                    .showOn(this@ValueActionActivity)

                                            openAppOnPhone(false)
                                        }
                                        ActionStatus.TIMEOUT -> {
                                            CustomConfirmationOverlay()
                                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                    .setCustomDrawable(ContextCompat.getDrawable(this@ValueActionActivity, R.drawable.ic_full_sad))
                                                    .setMessage(getString(R.string.error_sendmessage))
                                                    .showOn(this@ValueActionActivity)
                                        }
                                        ActionStatus.SUCCESS -> {
                                        }
                                    }
                                }
                            }
                        } else if (ACTION_CHANGED == intent.action) {
                            val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                            val action = deserializer(jsonData, Action::class.java)
                            requestAction(jsonData)

                            runOnUiThread {
                                timer?.cancel()
                                timer = object : CountDownTimer(3000, 500) {
                                    override fun onTick(millisUntilFinished: Long) {}
                                    override fun onFinish() {
                                        action!!.setActionSuccessful(ActionStatus.TIMEOUT)
                                        LocalBroadcastManager.getInstance(this@ValueActionActivity)
                                                .sendBroadcast(Intent(WearableHelper.ActionsPath)
                                                        .putExtra(EXTRA_ACTIONDATA, serializer(action, Action::class.java)))
                                    }
                                }
                                timer!!.start()
                            }
                        } else {
                            writeLine(Log.INFO, "%s: Unhandled action: %s", "ValueActionActivity", intent.action)
                        }
                    }
                }
            }
        }

        binding.increaseBtn.setOnClickListener(View.OnClickListener {
            val actionData = ValueAction(mAction!!, ValueDirection.UP)
            LocalBroadcastManager.getInstance(this@ValueActionActivity)
                    .sendBroadcast(Intent(ACTION_CHANGED)
                            .putExtra(EXTRA_ACTIONDATA,
                                    serializer(actionData, Action::class.java)))
        })
        binding.decreaseBtn.setOnClickListener(View.OnClickListener {
            val actionData = ValueAction(mAction!!, ValueDirection.DOWN)
            LocalBroadcastManager.getInstance(this@ValueActionActivity)
                    .sendBroadcast(Intent(ACTION_CHANGED)
                            .putExtra(EXTRA_ACTIONDATA,
                                    serializer(actionData, Action::class.java)))
        })
        when (mAction) {
            Actions.VOLUME -> {
                binding.actionTitle.setText(R.string.action_volume)
                binding.actionIcon.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_volume_up_white_24dp))
            }
        }

        intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS)
        intentFilter.addAction(ACTION_CHANGED)
        intentFilter.addAction(WearableHelper.ActionsPath)
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        AsyncTask.run {
            updateConnectionStatus()
        }
    }

    override fun onPause() {
        super.onPause()
    }
}