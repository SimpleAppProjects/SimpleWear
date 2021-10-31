package com.thewizrd.wearsettings

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.thewizrd.wearsettings.actions.checkSecureSettingsPermission
import com.thewizrd.wearsettings.databinding.ActivityMainBinding
import com.thewizrd.wearsettings.root.RootHelper
import kotlinx.coroutines.launch
import com.thewizrd.wearsettings.Settings as SettingsHelper

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private lateinit var mPowerMgr: PowerManager

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
    }

    override fun onResume() {
        super.onResume()

        lifecycleScope.launch {
            updateBgOptsPref(mPowerMgr.isIgnoringBatteryOptimizations(packageName))
            updateHideLauncherPref(isLauncherIconEnabled())

            val rootEnabled = SettingsHelper.isRootAccessEnabled() && RootHelper.isRootEnabled()
            updateSecureSettingsPref(checkSecureSettingsPermission(this@MainActivity) || rootEnabled)
            updateRootAccessPref(rootEnabled)
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
}