package com.thewizrd.simplewear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.wearable.view.ConfirmationOverlay
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearConnectionStatus.Companion.valueOf
import com.thewizrd.shared_resources.helpers.WearableHelper.playStoreURI
import com.thewizrd.simplewear.databinding.ActivitySetupSyncBinding
import kotlinx.coroutines.launch

class PhoneSyncActivity : WearableListenerActivity() {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivitySetupSyncBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create your application here
        binding = ActivitySetupSyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.circularProgress.isIndeterminate = true

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_UPDATECONNECTIONSTATUS == intent.action) {
                    val connStatus = valueOf(intent.getIntExtra(EXTRA_CONNECTIONSTATUS, 0))
                    when (connStatus) {
                        WearConnectionStatus.DISCONNECTED -> {
                            binding.message.setText(R.string.status_disconnected)
                            binding.button.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_phonelink_erase_white_24dp))
                            stopProgressBar()
                        }
                        WearConnectionStatus.CONNECTING -> {
                            binding.message.setText(R.string.status_connecting)
                            binding.button.setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_popup_sync))
                        }
                        WearConnectionStatus.APPNOTINSTALLED -> {
                            binding.message.setText(R.string.error_notinstalled)

                            binding.circularProgress.setOnClickListener { // Open store on remote device
                                val intentAndroid = Intent(Intent.ACTION_VIEW)
                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                        .setData(playStoreURI)

                                RemoteIntent.startRemoteActivity(this@PhoneSyncActivity, intentAndroid, null)

                                // Show open on phone animation
                                ConfirmationOverlay().setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                                        .setMessage(this@PhoneSyncActivity.getString(R.string.message_openedonphone))
                                        .showOn(this@PhoneSyncActivity)
                            }
                            binding.button.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.common_full_open_on_phone))

                            stopProgressBar()
                        }
                        WearConnectionStatus.CONNECTED -> {
                            binding.message.setText(R.string.status_connected)
                            binding.button.setImageDrawable(ContextCompat.getDrawable(context, android.R.drawable.ic_popup_sync))

                            // Continue operation
                            startActivity(Intent(this@PhoneSyncActivity, DashboardActivity::class.java)
                                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                            stopProgressBar()
                        }
                    }
                } else if (ACTION_OPENONPHONE == intent.action) {
                    val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)

                    ConfirmationOverlay()
                            .setType(if (success) ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION else ConfirmationOverlay.FAILURE_ANIMATION)
                            .showOn(this@PhoneSyncActivity)

                    if (!success) {
                        binding.message.setText(R.string.error_syncing)
                    }
                }
            }
        }

        binding.message.setText(R.string.message_gettingstatus)

        intentFilter = IntentFilter(ACTION_UPDATECONNECTIONSTATUS)
    }

    private fun stopProgressBar() {
        binding.circularProgress.isIndeterminate = false
        binding.circularProgress.totalTime = 1
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        lifecycleScope.launch {
            updateConnectionStatus()
        }
    }

    override fun onPause() {
        super.onPause()
    }
}