package com.thewizrd.simplewear

import android.bluetooth.BluetoothAdapter
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.support.wearable.view.ConfirmationOverlay
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplewear.databinding.ActivitySetupSyncBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PhoneSyncActivity : WearableListenerActivity() {
    companion object {
        private const val ENABLE_BT_REQUEST_CODE = 0
    }

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

        binding.wifiButton.setOnClickListener {
            runCatching {
                startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
            }
        }

        binding.bluetoothButton.setOnClickListener {
            runCatching {
                startActivityForResult(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                    ENABLE_BT_REQUEST_CODE
                )
            }
        }

        startProgressBar()

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (ACTION_UPDATECONNECTIONSTATUS == intent.action) {
                    when (WearConnectionStatus.valueOf(
                        intent.getIntExtra(
                            EXTRA_CONNECTIONSTATUS,
                            0
                        )
                    )) {
                        WearConnectionStatus.DISCONNECTED -> {
                            binding.message.setText(R.string.status_disconnected)
                            binding.button.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.ic_phonelink_erase_white_24dp
                                )
                            )
                            binding.circularProgress.setOnClickListener {
                                lifecycleScope.launch {
                                    startProgressBar()
                                    updateConnectionStatus()
                                }
                            }
                            checkNetworkStatus()
                            stopProgressBar()
                        }
                        WearConnectionStatus.CONNECTING -> {
                            binding.message.setText(R.string.status_connecting)
                            binding.button.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    android.R.drawable.ic_popup_sync
                                )
                            )
                            binding.wifiButton.visibility = View.GONE
                            binding.bluetoothButton.visibility = View.GONE
                        }
                        WearConnectionStatus.APPNOTINSTALLED -> {
                            binding.message.setText(R.string.error_notinstalled)

                            binding.circularProgress.setOnClickListener {
                                // Open store on remote device
                                val intentAndroid = Intent(Intent.ACTION_VIEW)
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                    .setData(WearableHelper.getPlayStoreURI())

                                RemoteIntent.startRemoteActivity(
                                    this@PhoneSyncActivity,
                                    intentAndroid,
                                    null
                                )

                                // Show open on phone animation
                                ConfirmationOverlay().setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                                    .setMessage(this@PhoneSyncActivity.getString(R.string.message_openedonphone))
                                    .showOn(this@PhoneSyncActivity)
                            }
                            binding.button.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.open_on_phone
                                )
                            )
                            binding.wifiButton.visibility = View.GONE
                            binding.bluetoothButton.visibility = View.GONE

                            stopProgressBar()
                        }
                        WearConnectionStatus.CONNECTED -> {
                            binding.message.setText(R.string.status_connected)
                            binding.button.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    android.R.drawable.ic_popup_sync
                                )
                            )
                            binding.bluetoothButton.visibility = View.GONE

                            lifecycleScope.launch {
                                // Verify connection by sending a 'ping'
                                runCatching {
                                    sendPing(mPhoneNodeWithApp!!.id)
                                }.onSuccess {
                                    // Continue operation
                                    startActivity(
                                        Intent(
                                            this@PhoneSyncActivity,
                                            DashboardActivity::class.java
                                        )
                                            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    )
                                    stopProgressBar()
                                }.onFailure {
                                    setConnectionStatus(WearConnectionStatus.DISCONNECTED)
                                }
                            }
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
    }

    private fun startProgressBar() {
        binding.circularProgress.isIndeterminate = true
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        lifecycleScope.launch {
            updateConnectionStatus()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            ENABLE_BT_REQUEST_CODE -> {
                if (resultCode == RESULT_OK) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        delay(2000)
                        startProgressBar()
                        delay(10000)
                        if (isActive) {
                            stopProgressBar()
                        }
                    }
                }
            }
        }
    }

    private fun checkNetworkStatus() {
        val btAdapter = BluetoothAdapter.getDefaultAdapter()
        if (btAdapter != null) {
            if (btAdapter.isEnabled || btAdapter.state == BluetoothAdapter.STATE_TURNING_ON) {
                binding.bluetoothButton.visibility = View.GONE
            } else {
                Toast.makeText(this, R.string.message_enablebt, Toast.LENGTH_SHORT).show()
                binding.bluetoothButton.visibility = View.VISIBLE
            }
        } else {
            binding.bluetoothButton.visibility = View.GONE
        }

        val wifiMgr = ContextCompat.getSystemService(this, WifiManager::class.java)
        if (wifiMgr != null && packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)) {
            if (!wifiMgr.isWifiEnabled) {
                binding.wifiButton.visibility = View.VISIBLE
            } else {
                binding.wifiButton.visibility = View.GONE
            }
        } else {
            binding.wifiButton.visibility = View.GONE
        }
    }
}