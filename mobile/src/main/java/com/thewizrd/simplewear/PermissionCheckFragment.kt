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
import android.net.Uri
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
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.thewizrd.shared_resources.helpers.WearSettingsHelper
import com.thewizrd.shared_resources.lifecycle.LifecycleAwareFragment
import com.thewizrd.shared_resources.tasks.delayLaunch
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.databinding.FragmentPermcheckBinding
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isCameraPermissionEnabled
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isDeviceAdminEnabled
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isNotificationAccessAllowed
import com.thewizrd.simplewear.media.MediaControllerService
import com.thewizrd.simplewear.services.CallControllerService
import com.thewizrd.simplewear.services.InCallManagerService
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
        private const val MANAGECALLS_REQCODE = 2
        private const val BTCONNECT_REQCODE = 3
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

                runCatching {
                    // Launch the activity to have the user enable our admin.
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mScreenLockAdmin)
                    startActivityForResult(intent, DEVADMIN_REQCODE)
                }
            }
        }
        binding.dndPref.setOnClickListener {
            if (!isNotificationAccessAllowed(requireContext())) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        }
        binding.companionPairPref.setOnClickListener {
            startDevicePairing()
        }
        binding.companionPairPref.visibility =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) View.VISIBLE else View.GONE

        binding.notiflistenerPref.setOnClickListener {
            if (!NotificationListener.isEnabled(requireContext())) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            }
        }

        binding.uninstallPref.setOnClickListener {
            if (!isDeviceAdminEnabled(requireContext())) {
                // Uninstall app
                requestUninstall()
            } else {
                // Deactivate device admin
                val mDPM =
                    requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                val mScreenLockAdmin =
                    ComponentName(requireContext(), ScreenLockAdminReceiver::class.java)
                mDPM.removeActiveAdmin(mScreenLockAdmin)
                requestUninstall()
            }
        }

        binding.callcontrolPref.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_DENIED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    MANAGECALLS_REQCODE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !InCallManagerService.hasPermission(
                    requireContext()
                )
            ) {
                startDevicePairing()
            }
        }

        binding.bridgeCallsToggle.isChecked =
            com.thewizrd.simplewear.preferences.Settings.isBridgeCallsEnabled()
        binding.bridgeCallsToggle.setOnCheckedChangeListener { _, isChecked ->
            com.thewizrd.simplewear.preferences.Settings.setBridgeCallsEnabled(isChecked)
            if (isChecked) {
                CallControllerService.enqueueWork(
                    requireContext(),
                    Intent(context, CallControllerService::class.java)
                        .setAction(CallControllerService.ACTION_CONNECTCONTROLLER)
                )
            }
        }

        binding.bridgeCallsPref.setOnClickListener {
            if (!binding.bridgeCallsToggle.isChecked) {
                if (!CallControllerService.hasPermissions(it.context.applicationContext)) {
                    Toast.makeText(it.context, R.string.error_permissiondenied, Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
            }

            binding.bridgeCallsToggle.toggle()
        }

        binding.bridgeMediaToggle.isChecked =
            com.thewizrd.simplewear.preferences.Settings.isBridgeMediaEnabled()
        binding.bridgeMediaToggle.setOnCheckedChangeListener { _, isChecked ->
            com.thewizrd.simplewear.preferences.Settings.setBridgeMediaEnabled(isChecked)
            if (isChecked) {
                MediaControllerService.enqueueWork(
                    requireContext(),
                    Intent(context, MediaControllerService::class.java)
                        .setAction(MediaControllerService.ACTION_CONNECTCONTROLLER)
                        .putExtra(MediaControllerService.EXTRA_SOFTLAUNCH, true)
                )
            }
        }

        binding.bridgeMediaPref.setOnClickListener {
            if (!binding.bridgeMediaToggle.isChecked) {
                if (!NotificationListener.isEnabled(it.context.applicationContext)) {
                    Toast.makeText(it.context, R.string.error_permissiondenied, Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                }
            }

            binding.bridgeMediaToggle.toggle()
        }

        binding.wearsettingsPref.setOnClickListener {
            if (!WearSettingsHelper.isWearSettingsInstalled()) {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(getString(R.string.url_wearsettings_helper))
                }
                if (i.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(i)
                } else {
                    Toast.makeText(requireContext(), "Browser not available", Toast.LENGTH_SHORT)
                        .show()
                }
            } else {
                WearSettingsHelper.launchWearSettings()
            }
        }

        binding.systemsettingsPref.setOnClickListener {
            val ctx = it.context.applicationContext
            if (!PhoneStatusHelper.isWriteSystemSettingsPermissionEnabled(ctx)) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
        }

        return binding.root
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mReceiver)
        super.onPause()
    }

    private fun isBluetoothConnectPermGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun startDevicePairing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!isBluetoothConnectPermGranted()) {
                requestPermissions(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    BTCONNECT_REQCODE
                )
                return
            }
        }

        LocalBroadcastManager.getInstance(requireContext())
            .registerReceiver(
                mReceiver,
                IntentFilter(WearableDataListenerService.ACTION_GETCONNECTEDNODE)
            )
        if (timer == null) {
            timer = object : CountDownTimer(5000, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    if (context != null) {
                        Toast.makeText(
                            context,
                            R.string.message_watchbttimeout,
                            Toast.LENGTH_LONG
                        ).show()
                        binding.companionPairProgress.visibility = View.GONE
                        Logger.writeLine(Log.INFO, "%s: BT Request Timeout", TAG)
                        // Device not found showing all
                        pairDevice()
                    }
                }
            }
        }
        timer?.start()
        binding.companionPairProgress.visibility = View.VISIBLE
        enqueueAction(requireContext(), WearableWorker.ACTION_REQUESTBTDISCOVERABLE)
        Logger.writeLine(Log.INFO, "%s: ACTION_REQUESTBTDISCOVERABLE", TAG)
    }

    // Android Q+
    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (WearableDataListenerService.ACTION_GETCONNECTEDNODE == intent.action) {
                timer?.cancel()
                binding.companionPairProgress.visibility = View.GONE
                Logger.writeLine(Log.INFO, "%s: node received", TAG)
                pairDevice()
                LocalBroadcastManager.getInstance(context)
                        .unregisterReceiver(this)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.Q)
    private fun pairDevice() {
        runWithView {
            val deviceManager =
                requireContext().getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

            for (assoc in deviceManager.associations) {
                if (assoc != null) {
                    runCatching {
                        deviceManager.disassociate(assoc)
                    }.onFailure {
                        Logger.writeLine(Log.ERROR, it)
                    }
                }
            }
            updatePairPermText(false)

            val request = AssociationRequest.Builder().apply {
                if (BuildConfig.DEBUG) {
                    addDeviceFilter(
                        BluetoothDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                    addDeviceFilter(
                        WifiDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                    addDeviceFilter(
                        BluetoothLeDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                } else {
                    addDeviceFilter(
                        BluetoothDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                    addDeviceFilter(
                        BluetoothLeDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                }
            }
                .setSingleDevice(false)
                .build()

            // Verify bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isBluetoothConnectPermGranted()) {
                requestPermissions(
                    arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                    BTCONNECT_REQCODE
                )
                return@runWithView
            }

            Toast.makeText(requireContext(), R.string.message_watchbtdiscover, Toast.LENGTH_LONG)
                .show()

            delayLaunch(timeMillis = 5000) {
                Logger.writeLine(Log.INFO, "%s: sending pair request", TAG)
                // Enable Bluetooth to discover devices
                context?.let {
                    if (!PhoneStatusHelper.isBluetoothEnabled(it)) {
                        PhoneStatusHelper.setBluetoothEnabled(it, true)
                    }
                }
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

                    override fun onFailure(error: CharSequence?) {
                        Logger.writeLine(Log.ERROR, "%s: failed to find any devices; $error", TAG)
                        if (context == null) return
                        Toast.makeText(
                            context,
                            R.string.message_nodevices_found,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissions()
    }

    private fun updatePermissions() {
        updateCamPermText(isCameraPermissionEnabled(requireContext()))
        val mDPM =
            requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val mScreenLockAdmin = ComponentName(requireContext(), ScreenLockAdminReceiver::class.java)
        updateDeviceAdminText(mDPM.isAdminActive(mScreenLockAdmin))
        updateDNDAccessText(isNotificationAccessAllowed(requireContext()))
        updateUninstallText(mDPM.isAdminActive(mScreenLockAdmin))

        val notListenerEnabled = NotificationListener.isEnabled(requireContext())
        val phoneStatePermGranted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
        val hasManageCallsPerm =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S || InCallManagerService.hasPermission(
                requireContext()
            )

        updateNotifListenerText(notListenerEnabled)
        updateManageCallsText(notListenerEnabled && phoneStatePermGranted && hasManageCallsPerm)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val deviceManager =
                requireContext().getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
            updatePairPermText(deviceManager.associations.isNotEmpty())
        }

        binding.bridgeMediaPref.isEnabled = notListenerEnabled
        if (!notListenerEnabled && com.thewizrd.simplewear.preferences.Settings.isBridgeMediaEnabled()) {
            com.thewizrd.simplewear.preferences.Settings.setBridgeMediaEnabled(false)
        }
        binding.bridgeCallsPref.isEnabled = notListenerEnabled && phoneStatePermGranted
        if ((!notListenerEnabled || !phoneStatePermGranted) && com.thewizrd.simplewear.preferences.Settings.isBridgeCallsEnabled()) {
            com.thewizrd.simplewear.preferences.Settings.setBridgeCallsEnabled(false)
        }

        updateWearSettingsHelperPref(WearSettingsHelper.isWearSettingsInstalled())
        updateSystemSettingsPref(
            PhoneStatusHelper.isWriteSystemSettingsPermissionEnabled(
                requireContext()
            )
        )
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
        binding.companionPairSummary.setText(if (enabled) R.string.permission_pairdevice_enabled else R.string.permission_pairdevice_disabled)
        binding.companionPairSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    private fun updateNotifListenerText(enabled: Boolean) {
        binding.notiflistenerSummary.setText(if (enabled) R.string.prompt_notifservice_enabled else R.string.prompt_notifservice_disabled)
        binding.notiflistenerSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    private fun updateUninstallText(enabled: Boolean) {
        binding.uninstallTitle.setText(if (enabled) R.string.permission_title_deactivate_uninstall else R.string.permission_title_uninstall)
        binding.uninstallSummary.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun updateManageCallsText(enabled: Boolean) {
        binding.callcontrolSummary.setText(if (enabled) R.string.permission_callmanager_enabled else R.string.permission_callmanager_disabled)
        binding.callcontrolSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    private fun updateWearSettingsHelperPref(installed: Boolean) {
        binding.wearsettingsPrefSummary.setText(if (installed) R.string.preference_summary_wearsettings_installed else R.string.preference_summary_wearsettings_notinstalled)
        binding.wearsettingsPrefSummary.setTextColor(if (installed) Color.GREEN else Color.RED)
    }

    private fun updateSystemSettingsPref(enabled: Boolean) {
        binding.systemsettingsPrefSummary.setText(if (enabled) R.string.permission_systemsettings_enabled else R.string.permission_systemsettings_disabled)
        binding.systemsettingsPrefSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            DEVADMIN_REQCODE -> updateDeviceAdminText(resultCode == Activity.RESULT_OK)
            SELECT_DEVICE_REQUEST_CODE -> if (data != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val parcel =
                    data.getParcelableExtra<Parcelable>(CompanionDeviceManager.EXTRA_DEVICE)
                if (parcel is BluetoothDevice) {
                    if (parcel.bondState != BluetoothDevice.BOND_BONDED) {
                        parcel.createBond()
                    }
                }

                updatePermissions()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val permGranted =
            grantResults.isNotEmpty() && !grantResults.contains(PackageManager.PERMISSION_DENIED)

        when (requestCode) {
            CAMERA_REQCODE -> {
                // If request is cancelled, the result arrays are empty.
                if (permGranted) {
                    // permission was granted, yay!
                    // Do the task you need to do.
                    updateCamPermText(true)
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    updateCamPermText(false)
                    Toast.makeText(context, R.string.error_permissiondenied, Toast.LENGTH_SHORT)
                        .show()
                }
            }
            MANAGECALLS_REQCODE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !InCallManagerService.hasPermission(
                        requireContext()
                    )
                ) {
                    startDevicePairing()
                } else {
                    updateManageCallsText(permGranted)
                }
            }
            BTCONNECT_REQCODE -> {
                if (permGranted) {
                    startDevicePairing()
                } else {
                    Toast.makeText(context, R.string.error_permissiondenied, Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }
    }

    private fun requestUninstall() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            } catch (e: ActivityNotFoundException) {
            }
        } else {
            try {
                startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                })
            } catch (e: ActivityNotFoundException) {
            }
        }
    }
}