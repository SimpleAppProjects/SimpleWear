package com.thewizrd.simplewear

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanFilter
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.WifiDeviceFilter
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.thewizrd.shared_resources.helpers.WearSettingsHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.lifecycle.LifecycleAwareFragment
import com.thewizrd.shared_resources.tasks.delayLaunch
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.databinding.FragmentPermcheckBinding
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.canScheduleExactAlarms
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.deActivateDeviceAdmin
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isCameraPermissionEnabled
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isDeviceAdminEnabled
import com.thewizrd.simplewear.helpers.PhoneStatusHelper.isNotificationAccessAllowed
import com.thewizrd.simplewear.media.MediaControllerService
import com.thewizrd.simplewear.services.CallControllerService
import com.thewizrd.simplewear.services.InCallManagerService
import com.thewizrd.simplewear.services.NotificationListener
import com.thewizrd.simplewear.services.WearAccessibilityService
import com.thewizrd.simplewear.telephony.SubscriptionListener
import com.thewizrd.simplewear.utils.associate
import com.thewizrd.simplewear.utils.disassociateAll
import com.thewizrd.simplewear.utils.hasAssociations
import com.thewizrd.simplewear.wearable.WearableWorker
import com.thewizrd.simplewear.wearable.WearableWorker.Companion.enqueueAction
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class PermissionCheckFragment : LifecycleAwareFragment() {
    companion object {
        private const val TAG = "PermissionCheckFragment"

        private const val CORNERS_FULL = 0
        private const val CORNERS_TOP = 1
        private const val CORNERS_CENTER = 2
        private const val CORNERS_BOTTOM = 3
    }

    private lateinit var binding: FragmentPermcheckBinding

    private lateinit var permissionRequestLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var devAdminResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var companionDeviceResultLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var companionBTPermRequestLauncher: ActivityResultLauncher<String>
    private lateinit var requestBtResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionRequestLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                permissions.entries.forEach { (permission, granted) ->
                    when (permission) {
                        Manifest.permission.CAMERA -> {
                            if (granted) {
                                updateCamPermText(true)
                            } else {
                                updateCamPermText(false)
                                Toast.makeText(
                                    context,
                                    R.string.error_permissiondenied,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        Manifest.permission.READ_PHONE_STATE -> {
                            if (granted) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !InCallManagerService.hasPermission(
                                        requireContext()
                                    )
                                ) {
                                    startDevicePairing()
                                }

                                // Register listener for state changes
                                if (!SubscriptionListener.isRegistered) {
                                    SubscriptionListener.registerListener(requireContext())
                                }
                            }

                            updatePermissions()
                        }

                        Manifest.permission.BLUETOOTH_CONNECT -> {
                            if (granted) {
                                updateBTPref(true)
                            } else {
                                updateBTPref(false)
                                Toast.makeText(
                                    context,
                                    R.string.error_permissiondenied,
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
                        }

                        Manifest.permission.POST_NOTIFICATIONS -> {
                            if (granted) {
                                updateNotificationPref(true)
                            } else {
                                updateNotificationPref(false)
                                Toast.makeText(
                                    context,
                                    R.string.error_permissiondenied,
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                            }
                        }
                    }
                }
            }

        devAdminResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                updateDeviceAdminText(it.resultCode == Activity.RESULT_OK)
            }

        companionDeviceResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                updatePermissions()
            }

        companionBTPermRequestLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) {
                    startDevicePairing()
                } else {
                    Toast.makeText(
                        context,
                        R.string.error_permissiondenied,
                        Toast.LENGTH_SHORT
                    )
                        .show()

                    binding.companionPairProgress.hide()
                }
            }

        requestBtResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    pairDevice()
                } else {
                    binding.companionPairProgress.hide()
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentPermcheckBinding.inflate(inflater, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            binding.bottom.updateLayoutParams {
                val sysBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                height = sysBarInsets.top + sysBarInsets.bottom
            }

            insets
        }

        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.timed_actions -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragment_container, TimedActionsFragment())
                        .addToBackStack("timedActions")
                        .commit()
                    true
                }
            }

            false
        }
        binding.scrollView.children.firstOrNull().let { root ->
            val parent = root as ViewGroup

            parent.viewTreeObserver.addOnGlobalLayoutListener {
                updateRoundedBackground(parent)
            }
        }
        binding.torchPref.setOnClickListener {
            if (!isCameraPermissionEnabled(requireContext())) {
                permissionRequestLauncher.launch(arrayOf(Manifest.permission.CAMERA))
            }
        }
        binding.lockscreenPref.setOnClickListener {
            if (isDeviceAdminEnabled(requireContext()) || WearAccessibilityService.isServiceBound()) {
                if (isDeviceAdminEnabled(requireContext())) {
                    deActivateDeviceAdmin(requireContext())
                }
                if (WearAccessibilityService.isServiceBound()) {
                    WearAccessibilityService.getInstance()?.disableSelf()
                }

                lifecycleScope.delayLaunch(timeMillis = 1000) {
                    updatePermissions()
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    MaterialAlertDialogBuilder(it.context)
                        .setTitle(R.string.title_lockscreen_choice)
                        .setItems(R.array.items_choice_lockscreen) { d, which ->
                            if (which == 0) {
                                showAccessibilityServiceDialog()
                            } else {
                                showDeviceAdminDialog()
                            }

                            d.dismiss()
                        }
                        .setCancelable(true)
                        .show()
                } else {
                    showDeviceAdminDialog()
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
                permissionRequestLauncher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
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
            if (!WearSettingsHelper.isWearSettingsInstalled() || !WearSettingsHelper.isWearSettingsUpToDate()) {
                val i = Intent(Intent.ACTION_VIEW).apply {
                    data = getString(R.string.url_wearsettings_helper).toUri()
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
                        data = "package:${ctx.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
        }

        binding.notifPref.setOnClickListener {
            val ctx = it.context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_DENIED
            ) {
                permissionRequestLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }
        binding.notifPref.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

        binding.btPref.setOnClickListener {
            val ctx = it.context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !PhoneStatusHelper.isBluetoothConnectPermGranted(
                    ctx
                )
            ) {
                permissionRequestLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            }
        }
        binding.btPref.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        binding.gesturesPref.setOnClickListener {
            if (WearAccessibilityService.isServiceBound()) {
                WearAccessibilityService.getInstance()?.disableSelf()
            } else {
                showAccessibilityServiceDialog()
            }
        }

        binding.alarmsPref.setOnClickListener {
            val ctx = it.context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactAlarms(ctx)) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = "package:${ctx.packageName}".toUri()
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                }
            }
        }
        binding.alarmsPref.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        return binding.root
    }

    override fun onPause() {
        super.onPause()
    }

    private fun startDevicePairing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!PhoneStatusHelper.isBluetoothConnectPermGranted(requireContext())) {
                companionBTPermRequestLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                return
            }
        }

        binding.companionPairProgress.show()
        enqueueAction(requireContext(), WearableWorker.ACTION_REQUESTBTDISCOVERABLE)
        pairDevice()
    }

    @SuppressLint("WrongConstant", "UseRequiresApi")
    @TargetApi(Build.VERSION_CODES.Q)
    private fun pairDevice() {
        runWithView {
            val deviceManager =
                requireContext().getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager

            deviceManager.disassociateAll()
            updatePairPermText(false)

            val request = AssociationRequest.Builder().apply {
                addDeviceFilter(
                    BluetoothDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                        .build()
                )
                if (BuildConfig.DEBUG) {
                    addDeviceFilter(
                        WifiDeviceFilter.Builder()
                            .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                            .build()
                    )
                }
                addDeviceFilter(
                    BluetoothLeDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile(".*", Pattern.DOTALL))
                        .setScanFilter(
                            ScanFilter.Builder()
                                .setServiceUuid(WearableHelper.getBLEServiceUUID())
                                .build()
                        )
                        .build()
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setDeviceProfile(AssociationRequest.DEVICE_PROFILE_WATCH)
                }
            }
                .setSingleDevice(false)
                .build()

            // Verify bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !PhoneStatusHelper.isBluetoothConnectPermGranted(
                    requireContext()
                )
            ) {
                companionBTPermRequestLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                return@runWithView
            }

            if (!PhoneStatusHelper.isBluetoothEnabled(requireContext())) {
                runCatching {
                    requestBtResultLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
                return@runWithView
            }

            Toast.makeText(requireContext(), R.string.message_watchbtdiscover, Toast.LENGTH_LONG)
                .show()

            delayLaunch(timeMillis = 3500) {
                Logger.writeLine(Log.INFO, "%s: sending pair request", TAG)

                deviceManager.associate(
                    request,
                    onDeviceFound = {
                        context?.let { _ ->
                            runCatching {
                                lifecycleScope.launch {
                                    binding.companionPairProgress.hide()
                                }
                                companionDeviceResultLauncher.launch(
                                    IntentSenderRequest.Builder(it)
                                        .build()
                                )
                            }.onFailure {
                                Logger.writeLine(Log.ERROR, it)
                            }
                        }
                    },
                    onFailure = { error ->
                        Logger.writeLine(Log.ERROR, "%s: failed to find any devices; $error", TAG)

                        context?.let { ctx ->
                            lifecycleScope.launch {
                                binding.companionPairProgress.hide()
                                Toast.makeText(
                                    ctx,
                                    R.string.message_nodevices_found,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && WearAccessibilityService.isServiceBound()) {
            updateLockScreenText(true)
        } else if (mDPM.isAdminActive(mScreenLockAdmin)) {
            updateDeviceAdminText(true)
        } else {
            updateLockScreenText(false)
        }
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
        val hasBTConnectPerm = PhoneStatusHelper.isBluetoothConnectPermGranted(requireContext())

        updateNotifListenerText(notListenerEnabled)
        updateManageCallsText(notListenerEnabled && phoneStatePermGranted && hasManageCallsPerm)
        updateBTPref(hasBTConnectPerm)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val deviceManager =
                requireContext().getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
            updatePairPermText(deviceManager.hasAssociations())
        }

        binding.bridgeMediaPref.isEnabled = notListenerEnabled
        binding.bridgeMediaToggle.isEnabled = notListenerEnabled
        if (!notListenerEnabled && com.thewizrd.simplewear.preferences.Settings.isBridgeMediaEnabled()) {
            com.thewizrd.simplewear.preferences.Settings.setBridgeMediaEnabled(false)
        }
        binding.bridgeCallsPref.isEnabled =
            notListenerEnabled && phoneStatePermGranted && hasBTConnectPerm
        binding.bridgeCallsToggle.isEnabled =
            notListenerEnabled && phoneStatePermGranted && hasBTConnectPerm
        if ((!notListenerEnabled || !phoneStatePermGranted || !hasBTConnectPerm) && com.thewizrd.simplewear.preferences.Settings.isBridgeCallsEnabled()) {
            com.thewizrd.simplewear.preferences.Settings.setBridgeCallsEnabled(false)
        }

        updateWearSettingsHelperPref(
            WearSettingsHelper.isWearSettingsInstalled(),
            WearSettingsHelper.isWearSettingsUpToDate()
        )
        updateSystemSettingsPref(
            PhoneStatusHelper.isWriteSystemSettingsPermissionEnabled(
                requireContext()
            )
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notifPermGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            updateNotificationPref(notifPermGranted)
        }

        updateGesturesPref(WearAccessibilityService.isServiceBound())
        updateExactAlarmsPref(canScheduleExactAlarms(requireContext()))
    }

    private fun updateCamPermText(enabled: Boolean) {
        binding.torchPrefSummary.setText(if (enabled) R.string.permission_camera_enabled else R.string.permission_camera_disabled)
        binding.torchPrefSummary.setTextColor(
            getTextColor(
                binding.torchPrefSummary.context,
                enabled
            )
        )
    }

    private fun updateDeviceAdminText(enabled: Boolean) {
        binding.lockscreenSummary.setText(if (enabled) R.string.permission_admin_enabled else R.string.permission_lockscreen_disabled)
        binding.lockscreenSummary.setTextColor(
            getTextColor(
                binding.lockscreenSummary.context,
                enabled
            )
        )
    }

    private fun updateLockScreenText(enabled: Boolean) {
        binding.lockscreenSummary.setText(if (enabled) R.string.permission_lockscreen_enabled else R.string.permission_lockscreen_disabled)
        binding.lockscreenSummary.setTextColor(
            getTextColor(
                binding.lockscreenSummary.context,
                enabled
            )
        )
    }

    private fun updateDNDAccessText(enabled: Boolean) {
        binding.dndSummary.setText(if (enabled) R.string.permission_dnd_enabled else R.string.permission_dnd_disabled)
        binding.dndSummary.setTextColor(getTextColor(binding.dndSummary.context, enabled))
    }

    private fun updatePairPermText(enabled: Boolean) {
        binding.companionPairSummary.setText(if (enabled) R.string.permission_pairdevice_enabled else R.string.permission_pairdevice_disabled)
        binding.companionPairSummary.setTextColor(
            getTextColor(
                binding.companionPairSummary.context,
                enabled
            )
        )
    }

    private fun updateNotifListenerText(enabled: Boolean) {
        binding.notiflistenerSummary.setText(if (enabled) R.string.prompt_notifservice_enabled else R.string.prompt_notifservice_disabled)
        binding.notiflistenerSummary.setTextColor(
            getTextColor(
                binding.notiflistenerSummary.context,
                enabled
            )
        )
    }

    private fun updateUninstallText(enabled: Boolean) {
        binding.uninstallTitle.setText(if (enabled) R.string.permission_title_deactivate_uninstall else R.string.permission_title_uninstall)
        binding.uninstallSummary.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    private fun updateManageCallsText(enabled: Boolean) {
        binding.callcontrolSummary.setText(if (enabled) R.string.permission_callmanager_enabled else R.string.permission_callmanager_disabled)
        binding.callcontrolSummary.setTextColor(
            getTextColor(
                binding.callcontrolSummary.context,
                enabled
            )
        )
    }

    private fun updateWearSettingsHelperPref(installed: Boolean, upToDate: Boolean) {
        binding.wearsettingsPrefSummary.setText(
            if (installed) {
                if (upToDate) {
                    R.string.preference_summary_wearsettings_installed
                } else {
                    R.string.preference_summary_wearsettings_outdated
                }
            } else {
                R.string.preference_summary_wearsettings_notinstalled
            }
        )
        binding.wearsettingsPrefSummary.setTextColor(
            if (installed) {
                if (upToDate) {
                    getTextColor(binding.wearsettingsPrefSummary.context, true)
                } else {
                    Color.argb(0xFF, 0xFF, 0xA5, 0)
                }
            } else {
                getTextColor(binding.wearsettingsPrefSummary.context, false)
            }
        )
    }

    private fun updateSystemSettingsPref(enabled: Boolean) {
        binding.systemsettingsPrefSummary.setText(if (enabled) R.string.permission_systemsettings_enabled else R.string.permission_systemsettings_disabled)
        binding.systemsettingsPrefSummary.setTextColor(
            getTextColor(
                binding.systemsettingsPrefSummary.context,
                enabled
            )
        )
    }

    private fun updateNotificationPref(enabled: Boolean) {
        binding.notifPrefSummary.setText(if (enabled) R.string.permission_notifications_enabled else R.string.permission_notifications_disabled)
        binding.notifPrefSummary.setTextColor(
            getTextColor(
                binding.notifPrefSummary.context,
                enabled
            )
        )
    }

    private fun updateBTPref(enabled: Boolean) {
        binding.btPrefSummary.setText(if (enabled) R.string.permission_bt_enabled else R.string.permission_bt_disabled)
        binding.btPrefSummary.setTextColor(getTextColor(binding.btPrefSummary.context, enabled))
    }

    private fun updateGesturesPref(enabled: Boolean) {
        binding.gesturesSummary.setText(if (enabled) R.string.permission_gestures_enabled else R.string.permission_gestures_disabled)
        binding.gesturesSummary.setTextColor(getTextColor(binding.gesturesSummary.context, enabled))
    }

    private fun updateExactAlarmsPref(enabled: Boolean) {
        binding.alarmsSummary.setText(if (enabled) R.string.permission_exact_alarms_enabled else R.string.permission_exact_alarms_disabled)
        binding.alarmsSummary.setTextColor(getTextColor(binding.alarmsSummary.context, enabled))
    }

    @Suppress("DEPRECATION")
    private fun requestUninstall() {
        val ctx = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${ctx.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }
        } else {
            runCatching {
                startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = "package:${ctx.packageName}".toUri()
                })
            }
        }
    }

    private fun showDeviceAdminDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage(R.string.prompt_alert_message_device_admin)
            .setPositiveButton(android.R.string.ok) { d, which ->
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    val mScreenLockAdmin =
                        ComponentName(requireContext(), ScreenLockAdminReceiver::class.java)

                    runCatching {
                        // Launch the activity to have the user enable our admin.
                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        intent.putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            mScreenLockAdmin
                        )
                        devAdminResultLauncher.launch(intent)
                    }
                }

                d.dismiss()
            }
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAccessibilityServiceDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage(R.string.prompt_alert_message_accessibility_svc)
            .setPositiveButton(android.R.string.ok) { d, which ->
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    runCatching {
                        // Launch the activity to have the user enable our service.
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                    }
                }

                d.dismiss()
            }
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    @ColorInt
    private fun getTextColor(context: Context, enabled: Boolean): Int {
        return when (enabled) {
            true -> if ((context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
                Color.GREEN
            } else {
                ColorUtils.blendARGB(Color.GREEN, Color.BLACK, 0.25f)
            }

            false -> if ((context.resources.configuration.uiMode and UI_MODE_NIGHT_MASK) == UI_MODE_NIGHT_YES) {
                ColorUtils.blendARGB(Color.RED, Color.WHITE, 0.25f)
            } else {
                Color.RED
            }
        }
    }

    private fun updateRoundedBackground(parent: ViewGroup) {
        val permissionsPreferences =
            parent.children.filter { it.tag == "permissions" && it.isVisible }.toList()
        val settingsPreferences =
            parent.children.filter { it.tag == "settings" && it.isVisible }.toList()

        permissionsPreferences.forEachIndexed { index, view ->
            val cornerType = when {
                permissionsPreferences.size <= 1 -> CORNERS_FULL
                index == 0 -> CORNERS_TOP
                index == permissionsPreferences.size - 1 -> CORNERS_BOTTOM
                else -> CORNERS_CENTER
            }

            when (cornerType) {
                CORNERS_FULL -> view.setBackgroundResource(R.drawable.preference_round_background)
                CORNERS_TOP -> view.setBackgroundResource(R.drawable.preference_round_background_top)
                CORNERS_BOTTOM -> view.setBackgroundResource(R.drawable.preference_round_background_bottom)
                CORNERS_CENTER -> view.setBackgroundResource(R.drawable.preference_round_background_center)
            }
        }

        settingsPreferences.forEachIndexed { index, view ->
            val cornerType = when {
                settingsPreferences.size <= 1 -> CORNERS_FULL
                index == 0 -> CORNERS_TOP
                index == settingsPreferences.size - 1 -> CORNERS_BOTTOM
                else -> CORNERS_CENTER
            }

            when (cornerType) {
                CORNERS_FULL -> view.setBackgroundResource(R.drawable.preference_round_background)
                CORNERS_TOP -> view.setBackgroundResource(R.drawable.preference_round_background_top)
                CORNERS_BOTTOM -> view.setBackgroundResource(R.drawable.preference_round_background_bottom)
                CORNERS_CENTER -> view.setBackgroundResource(R.drawable.preference_round_background_center)
            }
        }
    }
}