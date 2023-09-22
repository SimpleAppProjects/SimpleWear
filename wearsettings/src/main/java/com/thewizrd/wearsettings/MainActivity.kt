package com.thewizrd.wearsettings

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.PermissionChecker
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
    }

    private lateinit var binding: ActivityMainBinding

    private lateinit var mPowerMgr: PowerManager

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mPowerMgr = getSystemService(PowerManager::class.java)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bgoptsPref.setOnClickListener {
            if (!mPowerMgr.isIgnoringBatteryOptimizations(this.packageName)) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${packageName}")
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
                    data = Uri.parse(getString(R.string.url_secure_settings_help))
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
                            data = Uri.parse(getString(R.string.url_root_access_help))
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
                                            data = Uri.parse("package:${it.context.packageName}")
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

    private fun updateBgOptsPref(enabled: Boolean) {
        binding.bgoptsPrefSummary.setText(if (enabled) R.string.message_bgopts_enabled else R.string.message_bgopts_disabled)
        binding.bgoptsPrefSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
    }

    private fun updateSecureSettingsPref(enabled: Boolean) {
        binding.securesettingsPrefSummary.setText(if (enabled) R.string.message_securesettings_enabled else R.string.message_securesettings_disabled)
        binding.securesettingsPrefSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
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
        binding.btPrefSummary.setTextColor(if (enabled) Color.GREEN else Color.RED)
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
        binding.shizukuPrefSummary.setTextColor(if (state == ShizukuState.RUNNING) Color.GREEN else Color.RED)
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
}