package com.thewizrd.simplewear

import android.Manifest
import android.annotation.TargetApi
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothDevice
import android.companion.*
import android.content.*
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Parcelable
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.shared_resources.lifecycle.LifecycleAwareFragment
import com.thewizrd.shared_resources.tasks.delayLaunch
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.databinding.FragmentPermcheckBinding
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isCameraPermissionEnabled
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isDeviceAdminEnabled
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isNotificationAccessAllowed
import com.thewizrd.simplewear.services.NotificationListener
import com.thewizrd.simplewear.wearable.WearableDataListenerService
import com.thewizrd.simplewear.wearable.WearableWorker
import com.thewizrd.simplewear.wearable.WearableWorker.Companion.enqueueAction
import java.util.regex.Pattern

class PermissionCheckFragment : LifecycleAwareFragment() {
    companion object {
        private const val TAG = "PermissionCheckFragment"
        private const val CAMERA_REQCODE = 0
        private const val DEVADMIN_REQCODE = 1
        private const val SELECT_DEVICE_REQUEST_CODE = 42
    }

    private lateinit var binding: FragmentPermcheckBinding
    private var timer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentPermcheckBinding.inflate(inflater, container, false)
        binding.torchPref.setOnClickListener {
            if (!isCameraPermissionEnabled(requireContext())) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQCODE)
            }
        }
        binding.deviceadminPref.setOnClickListener {
            if (!isDeviceAdminEnabled(requireContext())) {
                val mScreenLockAdmin =
                    ComponentName(requireContext(), ScreenLockAdminReceiver::class.java)

                // Launch the activity to have the user enable our admin.
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mScreenLockAdmin)
                startActivityForResult(intent, DEVADMIN_REQCODE)
            }
        }
        binding.dndPref.setOnClickListener {
            if (!isNotificationAccessAllowed(requireContext())) {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                }
            }
        }
        binding.companionPairPref?.setOnClickListener {
            LocalBroadcastManager.getInstance(requireContext())
                    .registerReceiver(mReceiver, IntentFilter(WearableDataListenerService.ACTION_GETCONNECTEDNODE))
            if (timer == null) {
                timer = object : CountDownTimer(5000, 1000) {
                    override fun onTick(millisUntilFinished: Long) {}
                    override fun onFinish() {
                        if (context != null) {
                            Toast.makeText(context, R.string.message_watchbttimeout, Toast.LENGTH_LONG).show()
                            binding.companionPairProgress?.visibility = View.GONE
                            Logger.writeLine(Log.INFO, "%s: BT Request Timeout", TAG)
                        }
                    }
                }
            }
            timer?.start()
            binding.companionPairProgress?.visibility = View.VISIBLE
            enqueueAction(requireContext(), WearableWorker.ACTION_REQUESTBTDISCOVERABLE)
            Logger.writeLine(Log.INFO, "%s: ACTION_REQUESTBTDISCOVERABLE", TAG)
        }

        binding.notiflistenerPref.setOnClickListener {
            if (!NotificationListener.isEnabled(requireContext())) {
                try {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                }
            }
        }

        return binding.root
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver)
        super.onPause()
    }

    // Android Q+
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WearableDataListenerService.ACTION_GETCONNECTEDNODE == intent.action) {
                timer?.cancel()
                binding.companionPairProgress?.visibility = View.GONE
                Logger.writeLine(Log.INFO, "%s: node received", TAG)
                pairDevice(intent.getStringExtra(WearableDataListenerService.EXTRA_NODEDEVICENAME))
                LocalBroadcastManager.getInstance(context)
                        .unregisterReceiver(this)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun pairDevice(deviceName: String?) {
        runWithView {
            if (deviceName.isNullOrBlank()) return@runWithView

            val deviceManager =
                requireContext().getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

            for (assoc in deviceManager.associations) {
                deviceManager.disassociate(assoc)
            }
            updatePairPermText(false)

            val request = AssociationRequest.Builder()
                .addDeviceFilter(
                    BluetoothDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile("$deviceName.*", Pattern.DOTALL))
                        .build()
                )
                .addDeviceFilter(
                    BluetoothLeDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile("$deviceName.*", Pattern.DOTALL))
                        .build()
                )
                .addDeviceFilter(
                    WifiDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile("$deviceName.*", Pattern.DOTALL))
                        .build()
                )
                .apply {
                    if (BuildConfig.DEBUG) {
                        addDeviceFilter(
                            WifiDeviceFilter.Builder()
                                .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                                .build()
                        )
                    }
                }
                .setSingleDevice(true)
                .build()

            Toast.makeText(requireContext(), R.string.message_watchbtdiscover, Toast.LENGTH_LONG).show()

            delayLaunch(timeMillis = 5000) {
                Logger.writeLine(Log.INFO, "%s: sending pair request", TAG)
                deviceManager.associate(request, object : CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: IntentSender) {
                        if (context == null) return
                        try {
                            startIntentSenderForResult(
                                chooserLauncher,
                                SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0, null
                            )
                        } catch (e: SendIntentException) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }

                    override fun onFailure(error: CharSequence) {
                        Logger.writeLine(Log.ERROR, "%s: failed to find any devices; $error", TAG)
                    }
                }, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCamPermText(isCameraPermissionEnabled(requireContext()))
        val mDPM = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val mScreenLockAdmin = ComponentName(requireContext(), ScreenLockAdminReceiver::class.java)
        updateDeviceAdminText(mDPM.isAdminActive(mScreenLockAdmin))
        updateDNDAccessText(isNotificationAccessAllowed(requireContext()))
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            val deviceManager = requireContext().getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
            updatePairPermText(deviceManager.associations.isNotEmpty())
        }
        updateNotifListenerText(NotificationListener.isEnabled(requireContext()))
    }

    private fun updateCamPermText(enabled: Boolean) {
        binding.torchPrefSummary.setText(if (enabled) R.string.permission_camera_enabled else R.string.permission_camera_disabled)
        binding.torchPrefSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    private fun updateDeviceAdminText(enabled: Boolean) {
        binding.deviceadminSummary.setText(if (enabled) R.string.permission_admin_enabled else R.string.permission_admin_disabled)
        binding.deviceadminSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    private fun updateDNDAccessText(enabled: Boolean) {
        binding.dndSummary.setText(if (enabled) R.string.permission_dnd_enabled else R.string.permission_dnd_disabled)
        binding.dndSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    private fun updatePairPermText(enabled: Boolean) {
        binding.companionPairSummary?.setText(if (enabled) R.string.permission_pairdevice_enabled else R.string.permission_pairdevice_disabled)
        binding.companionPairSummary?.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    private fun updateNotifListenerText(enabled: Boolean) {
        binding.notiflistenerSummary.setText(if (enabled) R.string.prompt_notifservice_enabled else R.string.prompt_notifservice_disabled)
        binding.notiflistenerSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DEVADMIN_REQCODE -> updateDeviceAdminText(resultCode == Activity.RESULT_OK)
            SELECT_DEVICE_REQUEST_CODE -> if (data != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                val parcel =
                    data.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
                if (parcel is BluetoothDevice) {
                    if (parcel.bondState != BluetoothDevice.BOND_BONDED) {
                        parcel.createBond()
                    }
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            CAMERA_REQCODE ->
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay!
                    // Do the task you need to do.
                    updateCamPermText(true)
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    updateCamPermText(false)
                    Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                }
        }
    }
}