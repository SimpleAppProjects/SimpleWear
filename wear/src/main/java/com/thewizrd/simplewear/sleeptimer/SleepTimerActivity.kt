package com.thewizrd.simplewear.sleeptimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.util.Pair
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearConnectionStatus.Companion.valueOf
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.JSONParser.serializer
import com.thewizrd.shared_resources.utils.Logger.writeLine
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.shared_resources.utils.intToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.WearableListenerActivity
import com.thewizrd.simplewear.databinding.ActivitySleeptimerBinding
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SleepTimerActivity : WearableListenerActivity() {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivitySleeptimerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySleeptimerBinding.inflate(layoutInflater)
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
                                    startActivity(Intent(this@SleepTimerActivity, PhoneSyncActivity::class.java)
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                                    finishAffinity()
                                }
                                WearConnectionStatus.APPNOTINSTALLED -> {
                                    // Open store on remote device
                                    val intentAndroid = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(WearableHelper.playStoreURI)
                                    RemoteIntent.startRemoteActivity(this@SleepTimerActivity, intentAndroid,
                                            ConfirmationResultReceiver(this@SleepTimerActivity))
                                }
                                else -> {
                                }
                            }
                        } else if (SleepTimerHelper.SleepTimerStartPath == intent.action) {
                            if (connect()) {
                                sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStartPath,
                                        intent.getIntExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, 0).intToBytes())

                                val selectedPlayer = ViewModelProvider(this@SleepTimerActivity, ViewModelProvider.NewInstanceFactory())
                                        .get(SelectedPlayerViewModel::class.java)
                                if (selectedPlayer.keyValue != null) {
                                    val data = selectedPlayer.keyValue!!.split("/").toTypedArray()
                                    if (data.size == 2) {
                                        val packageName = data[0]
                                        val activityName = data[1]
                                        sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.OpenMusicPlayerPath,
                                                serializer(Pair.create(packageName, activityName), Pair::class.java).stringToBytes())
                                    }
                                }
                            }
                        } else if (SleepTimerHelper.SleepTimerStopPath == intent.action) {
                            if (connect()) {
                                sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerStopPath, null)
                            }
                        } else if (WearableHelper.MusicPlayersPath == intent.action) {
                            if (connect()) {
                                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.MusicPlayersPath, null)
                            }
                        } else {
                            writeLine(Log.INFO, "%s: Unhandled action: %s", "MusicPlayerActivity", intent.action)
                        }
                    }
                }
            }
        }

        intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS)
        intentFilter.addAction(SleepTimerHelper.SleepTimerStartPath)
        intentFilter.addAction(SleepTimerHelper.SleepTimerStopPath)
        intentFilter.addAction(WearableHelper.MusicPlayersPath)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            if (messageEvent.data != null && messageEvent.path == SleepTimerHelper.SleepTimerEnabledPath) {
                val isEnabled: Boolean = messageEvent.data.bytesToBool()

                showProgressBar(false)
                if (isEnabled) {
                    binding.fragmentContainer.visibility = View.VISIBLE
                    binding.nosleeptimerMessageview.visibility = View.GONE
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, TimerStartFragment())
                            .commit()
                } else {
                    binding.fragmentContainer.visibility = View.GONE
                    binding.nosleeptimerMessageview.visibility = View.VISIBLE
                    binding.nosleeptimerMessageview.setOnClickListener {
                        val intentapp = Intent(Intent.ACTION_VIEW)
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                .setData(SleepTimerHelper.playStoreURI)

                        RemoteIntent.startRemoteActivity(this@SleepTimerActivity, intentapp,
                                ConfirmationResultReceiver(this@SleepTimerActivity))
                    }

                    supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    for (fragment in supportFragmentManager.fragments) {
                        if (fragment != null) {
                            supportFragmentManager.beginTransaction()
                                    .remove(fragment)
                                    .commit()
                        }
                    }
                }
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerStatusPath || messageEvent.path == SleepTimerHelper.SleepTimerStartPath) {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (fragment !is TimerProgressFragment) {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, TimerProgressFragment())
                            .commit()
                }
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerStopPath) {
                val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
                if (fragment !is TimerStartFragment) {
                    supportFragmentManager.beginTransaction()
                            .replace(R.id.fragment_container, TimerStartFragment())
                            .commit()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)

        // Update statuses
        showProgressBar(true)
        binding.nosleeptimerMessageview.visibility = View.GONE
        lifecycleScope.launch {
            updateConnectionStatus()
            if (mPhoneNodeWithApp != null) {
                sendMessage(mPhoneNodeWithApp!!.id, SleepTimerHelper.SleepTimerEnabledPath, null)
            }
        }
    }

    override fun onPause() {
        Wearable.getMessageClient(this).removeListener(this)
        super.onPause()
    }

    private fun showProgressBar(show: Boolean) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }
}