package com.thewizrd.wearsettings

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
import androidx.core.graphics.ColorUtils
import androidx.core.net.toUri
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.wearsettings.actions.checkSecureSettingsPermission
import com.thewizrd.wearsettings.databinding.ActivityMainBinding
import com.thewizrd.wearsettings.root.RootHelper
import com.thewizrd.wearsettings.shizuku.ShizukuState
import com.thewizrd.wearsettings.shizuku.ShizukuUtils
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import com.thewizrd.wearsettings.Settings as SettingsHelper

class MainActivity : AppCompatActivity(), Shizuku.OnRequestPermissionResultListener {
    companion object {
        private const val BTCONNECT_REQCODE = 0
        private const val SHIZUKU_REQCODE = 1

        private const val CORNERS_FULL = 0
        private const val CORNERS_TOP = 1
        private const val CORNERS_CENTER = 2
        private const val CORNERS_BOTTOM = 3
    }

    private lateinit var binding: ActivityMainBinding

    private lateinit var mPowerMgr: PowerManager

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        mPowerMgr = getSystemService(PowerManager::class.java)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.scrollView.children.firstOrNull().let { root ->
            val parent = root as ViewGroup

            parent.viewTreeObserver.addOnGlobalLayoutListener {
                updateRoundedBackground(parent)
            }
        }

        binding.bgoptsPref.setOnClickListener {
            if (!mPowerMgr.isIgnoringBatteryOptimizations(this.packageName)) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = "package:${packageName}".toUri()
                        }
                    )
                }
            } else {
                runCatching {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }

        binding.securesettingsPref.setOnClickListener {
            if (!checkSecureSettingsPermission(this)) {
                // Show a dialog detailing how to set this permission
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = getString(R.string.url_secure_settings_help).toUri()
                })
            }
        }

        binding.rootaccessPref.setOnClickListener {
            // Root shell access should run off main thread
            lifecycleScope.launch {
                if (!SettingsHelper.isRootAccessEnabled()) {
                    // Request root permission
                    if (!RootHelper.isRootEnabled()) {
                        // Show dialog about root access
                        startActivity(Intent(Intent.ACTION_VIEW).apply {
                            data = getString(R.string.url_root_access_help).toUri()
                        })
                    } else {
                        SettingsHelper.setRootAccessEnabled(true)
                        updateRootAccessPref(true)
                    }
                } else {
                    SettingsHelper.setRootAccessEnabled(false)
                    updateRootAccessPref(false)
                }
            }
        }

        binding.hidelauncherPref.setOnClickListener {
            val state = !isLauncherIconEnabled()
            setLauncherIconEnabled(state)
            updateHideLauncherPref(state)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            binding.hidelauncherPref.isVisible = false
        }

        binding.btPref.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isBluetoothConnectPermGranted()) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 0)
            }
        }

        binding.btPref.isVisible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

        binding.dndPref.setOnClickListener {
            if (!isNotificationAccessAllowed()) {
                runCatching {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        }

        binding.shizukuPref.setOnClickListener {
            runCatching {
                val shizukuState = ShizukuUtils.getShizukuState(this)

                when (shizukuState) {
                    ShizukuState.RUNNING -> { /* no-op */
                    }

                    ShizukuState.NOT_INSTALLED -> {
                        showShizukuInstallDialog()
                    }

                    ShizukuState.NOT_RUNNING -> {
                        ShizukuUtils.startShizukuActivity(this)
                    }

                    ShizukuState.PERMISSION_DENIED -> {
                        if (Shizuku.shouldShowRequestPermissionRationale()) {
                            Snackbar.make(
                                binding.root,
                                R.string.message_shizuku_disabled,
                                Snackbar.LENGTH_LONG
                            ).apply {
                                setAction(R.string.title_settings) {
                                    runCatching {
                                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                            data = "package:${it.context.packageName}".toUri()
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        })
                                    }
                                }
                            }
                        } else {
                            ShizukuUtils.requestPermission(this, SHIZUKU_REQCODE)
                        }
                    }
                }
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
        }

        Shizuku.addRequestPermissionResultListener(this)
    }

    private fun showShizukuInstallDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_title_shizuku)
            .setMessage(R.string.message_shizuku_alert)
            .setCancelable(true)
            .setNegativeButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
            }
            .setPositiveButton(android.R.string.ok) { d, which ->
                ShizukuUtils.openPlayStoreListing(this)
            }
            .show()
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            updateBgOptsPref(mPowerMgr.isIgnoringBatteryOptimizations(packageName))
            updateHideLauncherPref(isLauncherIconEnabled())

            val rootEnabled = SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()
            val shizukuState = ShizukuUtils.getShizukuState(this@MainActivity)
            updateBTPref(isBluetoothConnectPermGranted() || rootEnabled || shizukuState == ShizukuState.RUNNING)
            updateDNDAccessText(isNotificationAccessAllowed() || rootEnabled || shizukuState == ShizukuState.RUNNING)
            updateSecureSettingsPref(checkSecureSettingsPermission(this@MainActivity) || rootEnabled || shizukuState == ShizukuState.RUNNING)
            updateRootAccessPref(rootEnabled)
            updateShizukuPref(shizukuState)
        }
    }

    private fun isBluetoothConnectPermGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PermissionChecker.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PermissionChecker.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun isNotificationAccessAllowed(): Boolean {
        val notMan =
            applicationContext.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return notMan.isNotificationPolicyAccessGranted
    }

    private fun updateBgOptsPref(enabled: Boolean) {
        binding.bgoptsPrefSummary.setText(if (enabled) R.string.message_bgopts_enabled else R.string.message_bgopts_disabled)
        binding.bgoptsPrefSummary.setTextColor(
            getTextColor(
                binding.bgoptsPrefSummary.context,
                enabled
            )
        )
    }

    private fun updateSecureSettingsPref(enabled: Boolean) {
        binding.securesettingsPrefSummary.setText(if (enabled) R.string.message_securesettings_enabled else R.string.message_securesettings_disabled)
        binding.securesettingsPrefSummary.setTextColor(
            getTextColor(
                binding.securesettingsPrefSummary.context,
                enabled
            )
        )
    }

    private fun updateRootAccessPref(enabled: Boolean) {
        binding.rootaccessPrefSummary.setText(if (enabled) R.string.message_rootaccess_enabled else R.string.message_rootaccess_disabled)
        binding.rootaccessPrefToggle.isChecked = enabled
    }

    private fun updateHideLauncherPref(enabled: Boolean) {
        binding.hidelauncherPrefToggle.isChecked = enabled
    }

    private fun updateBTPref(enabled: Boolean) {
        binding.btPrefSummary.setText(if (enabled) R.string.permission_bt_enabled else R.string.permission_bt_disabled)
        binding.btPrefSummary.setTextColor(getTextColor(binding.btPrefSummary.context, enabled))
    }

    private fun updateDNDAccessText(enabled: Boolean) {
        binding.dndSummary.setText(if (enabled) R.string.permission_dnd_enabled else R.string.permission_dnd_disabled)
        binding.dndSummary.setTextColor(getTextColor(binding.dndSummary.context, enabled))
    }

    private fun updateShizukuPref(state: ShizukuState) {
        binding.shizukuPrefSummary.setText(
            when (state) {
                ShizukuState.NOT_INSTALLED -> R.string.message_shizuku_not_installed
                ShizukuState.NOT_RUNNING -> R.string.message_shizuku_not_running
                ShizukuState.PERMISSION_DENIED -> R.string.message_shizuku_disabled
                ShizukuState.RUNNING -> R.string.message_shizuku_running
            }
        )
        binding.shizukuPrefSummary.setTextColor(
            getTextColor(
                binding.shizukuPrefSummary.context,
                state == ShizukuState.RUNNING
            )
        )
    }

    private fun isLauncherIconEnabled(): Boolean {
        val componentState = packageManager.getComponentEnabledSetting(
            ComponentName(this, LauncherActivity::class.java),
        )
        return componentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED || componentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
    }

    private fun setLauncherIconEnabled(enable: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, LauncherActivity::class.java),
            if (enable) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val permGranted =
            grantResults.isNotEmpty() && !grantResults.contains(PackageManager.PERMISSION_DENIED)

        when (requestCode) {
            BTCONNECT_REQCODE -> {
                updateBTPref(permGranted)
            }
        }
    }

    // Shizuku
    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            // granted!!
            lifecycleScope.launch {
                updateShizukuPref(ShizukuUtils.getShizukuState(this@MainActivity))
            }
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(this)
        super.onDestroy()
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

        permissionsPreferences.forEachIndexed { index, view ->
            val cornerType = when {
                permissionsPreferences.size <= 1 -> CORNERS_FULL
                index == 0 -> CORNERS_TOP
                index == permissionsPreferences.size - 1 -> CORNERS_BOTTOM
                else -> CORNERS_CENTER
            }

            when (cornerType) {
                CORNERS_FULL -> view.setBackgroundResource(
                    R.drawable.preference_round_background
                )

                CORNERS_TOP -> view.setBackgroundResource(
                    R.drawable.preference_round_background_top
                )

                CORNERS_BOTTOM -> view.setBackgroundResource(
                    R.drawable.preference_round_background_bottom
                )

                CORNERS_CENTER -> view.setBackgroundResource(R.drawable.preference_round_background_center)
            }
        }
    }
}