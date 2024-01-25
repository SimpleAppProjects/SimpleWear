package com.thewizrd.simplewear

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.wear.widget.ConfirmationOverlay
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplewear.databinding.ActivitySetupSyncBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PhoneSyncActivity : WearableListenerActivity() {
    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivitySetupSyncBinding

    private lateinit var bluetoothRequestLauncher: ActivityResultLauncher<Intent>
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && PermissionChecker.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PermissionChecker.PERMISSION_GRANTED
                ) {
                    permissionRequestLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                } else {
                    bluetoothRequestLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
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

                                lifecycleScope.launch {
                                    runCatching {
                                        remoteActivityHelper.startRemoteActivity(intentAndroid)
                                            .await()

                                        // Show open on phone animation
                                        ConfirmationOverlay()
                                            .setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
                                            .setMessage(this@PhoneSyncActivity.getString(R.string.message_openedonphone))
                                            .showOn(this@PhoneSyncActivity)
                                    }
                                }
                            }
                            binding.button.setImageDrawable(
                                ContextCompat.getDrawable(
                                    context,
                                    R.drawable.common_full_open_on_phone
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
                                        ).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                    )
                                    stopProgressBar()
                                    finishAfterTransition()
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

        bluetoothRequestLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == RESULT_OK) {
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

        permissionRequestLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissions.entries.forEach { (permission, granted) ->
                    when (permission) {
                        Manifest.permission.BLUETOOTH_CONNECT -> {
                            if (granted) {
                                bluetoothRequestLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                            }
                        }
                    }
                }
            }
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

    private fun checkNetworkStatus() {
        val btAdapter = getSystemService(BluetoothManager::class.java)?.adapter
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