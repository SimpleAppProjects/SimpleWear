package com.thewizrd.simplewear

import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.ArrayMap
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.wear.widget.ConfirmationOverlay
import androidx.wear.widget.drawer.WearableDrawerLayout
import androidx.wear.widget.drawer.WearableDrawerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColor
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.adapters.ActionItemAdapter
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.controls.WearChipButton
import com.thewizrd.simplewear.databinding.ActivityDashboardBinding
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import com.thewizrd.simplewear.preferences.DashboardConfigActivity
import com.thewizrd.simplewear.preferences.DashboardTileConfigActivity
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DashboardActivity : WearableListenerActivity(), OnSharedPreferenceChangeListener {
    companion object {
        private const val TIMER_SYNC = "key_synctimer"
        private const val TIMER_SYNC_NORESPONSE = "key_synctimer_noresponse"
    }

    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var mAdapter: ActionItemAdapter
    private lateinit var activeTimers: ArrayMap<String, CountDownTimer>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lifecycleScope.launch {
                    if (intent.action != null) {
                        if (ACTION_UPDATECONNECTIONSTATUS == intent.action) {
                            when (WearConnectionStatus.valueOf(
                                intent.getIntExtra(
                                    EXTRA_CONNECTIONSTATUS,
                                    0
                                )
                            )) {
                                WearConnectionStatus.DISCONNECTED -> {
                                    binding.deviceState.setPrimaryText(R.string.status_disconnected)

                                    // Navigate
                                    startActivity(
                                        Intent(
                                            this@DashboardActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    finishAffinity()
                                }
                                WearConnectionStatus.CONNECTING -> {
                                    binding.deviceState.setPrimaryText(R.string.status_connecting)
                                    if (binding.actionsList.isEnabled) binding.actionsList.isEnabled =
                                        false
                                }
                                WearConnectionStatus.APPNOTINSTALLED -> {
                                    binding.deviceState.setPrimaryText(R.string.error_notinstalled)

                                    // Open store on remote device
                                    val intentAndroid = Intent(Intent.ACTION_VIEW)
                                        .addCategory(Intent.CATEGORY_BROWSABLE)
                                        .setData(WearableHelper.getPlayStoreURI())

                                    runCatching {
                                        remoteActivityHelper.startRemoteActivity(intentAndroid)
                                            .await()

                                        showConfirmationOverlay(true)
                                    }.onFailure {
                                        if (it !is CancellationException) {
                                            showConfirmationOverlay(false)
                                        }
                                    }

                                    if (binding.actionsList.isEnabled) binding.actionsList.isEnabled =
                                        false

                                    // Navigate
                                    startActivity(
                                        Intent(
                                            this@DashboardActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    finishAffinity()
                                }
                                WearConnectionStatus.CONNECTED -> {
                                    binding.deviceState.setPrimaryText(R.string.status_connected)
                                    if (!binding.actionsList.isEnabled) binding.actionsList.isEnabled =
                                        true
                                }
                            }
                        } else if (ACTION_OPENONPHONE == intent.action) {
                            val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)
                            val showAni = intent.getBooleanExtra(EXTRA_SHOWANIMATION, false)
                            if (showAni) {
                                    ConfirmationOverlay()
                                        .setType(if (success) ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION else ConfirmationOverlay.FAILURE_ANIMATION)
                                        .showOn(this@DashboardActivity)
                                    if (!success) {
                                        binding.deviceState.setPrimaryText(R.string.error_syncing)
                                    }
                            }
                        } else if (WearableHelper.BatteryPath == intent.action) {
                            showProgressBar(false)
                            cancelTimer(TIMER_SYNC)
                            cancelTimer(TIMER_SYNC_NORESPONSE)

                            val jsonData = intent.getStringExtra(EXTRA_STATUS)
                            var value = getString(R.string.state_unknown)
                            var battLevel: String? = null
                            if (!jsonData.isNullOrBlank()) {
                                val status =
                                    JSONParser.deserializer(jsonData, BatteryStatus::class.java)
                                battLevel = "${status!!.batteryLevel}%"
                                value =
                                    if (status.isCharging) getString(R.string.batt_state_charging) else getString(
                                        R.string.batt_state_discharging
                                    )
                            }

                            if (battLevel != null) {
                                binding.battStat.setText(battLevel, value)
                            } else {
                                binding.battStat.setText(value)
                            }
                        } else if (WearableHelper.ActionsPath == intent.action) {
                            val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                            val action = JSONParser.deserializer(jsonData, Action::class.java)!!

                            cancelTimer(action.actionType)

                            mAdapter.updateButton(ActionButtonViewModel(action))
                            val actionStatus = action.actionStatus ?: ActionStatus.UNKNOWN

                            if (!action.isActionSuccessful) {
                                when (actionStatus) {
                                    ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@DashboardActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(this@DashboardActivity.getString(R.string.error_actionfailed))
                                            .showOn(this@DashboardActivity)
                                    }
                                    ActionStatus.PERMISSION_DENIED -> {
                                        if (action.actionType == Actions.TORCH) {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        this@DashboardActivity,
                                                        R.drawable.ws_full_sad
                                                    )
                                                )
                                                .setMessage(this@DashboardActivity.getString(R.string.error_torch_action))
                                                .showOn(this@DashboardActivity)
                                        } else if (action.actionType == Actions.SLEEPTIMER) {
                                            // Open store on device
                                            val intentAndroid = Intent(Intent.ACTION_VIEW)
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                                .setData(SleepTimerHelper.getPlayStoreURI())

                                            if (intentAndroid.resolveActivity(packageManager) != null) {
                                                startActivity(intentAndroid)
                                                Toast.makeText(
                                                    this@DashboardActivity,
                                                    R.string.error_sleeptimer_notinstalled,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                CustomConfirmationOverlay()
                                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                    .setCustomDrawable(
                                                        ContextCompat.getDrawable(
                                                            this@DashboardActivity,
                                                            R.drawable.ws_full_sad
                                                        )
                                                    )
                                                    .setMessage(
                                                        this@DashboardActivity.getString(
                                                            R.string.error_sleeptimer_notinstalled
                                                        )
                                                    )
                                                    .showOn(this@DashboardActivity)
                                            }
                                        } else {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        this@DashboardActivity,
                                                        R.drawable.ws_full_sad
                                                    )
                                                )
                                                .setMessage(this@DashboardActivity.getString(R.string.error_permissiondenied))
                                                .showOn(this@DashboardActivity)
                                        }

                                        openAppOnPhone(false)
                                    }
                                    ActionStatus.TIMEOUT -> {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@DashboardActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(this@DashboardActivity.getString(R.string.error_sendmessage))
                                            .showOn(this@DashboardActivity)
                                    }
                                    ActionStatus.REMOTE_FAILURE -> {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@DashboardActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(this@DashboardActivity.getString(R.string.error_remoteactionfailed))
                                            .showOn(this@DashboardActivity)
                                    }
                                    ActionStatus.REMOTE_PERMISSION_DENIED -> {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@DashboardActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(this@DashboardActivity.getString(R.string.error_permissiondenied))
                                            .showOn(this@DashboardActivity)
                                    }
                                    ActionStatus.SUCCESS -> {
                                    }
                                }
                            }

                            // Re-enable click action
                            mAdapter.isItemsClickable = true
                        } else if (ACTION_CHANGED == intent.action) {
                            val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                            val action = JSONParser.deserializer(jsonData, Action::class.java)!!
                            requestAction(jsonData)

                                val timer: CountDownTimer = object : CountDownTimer(3000, 500) {
                                    override fun onTick(millisUntilFinished: Long) {}

                                    override fun onFinish() {
                                        action.setActionSuccessful(ActionStatus.TIMEOUT)
                                        LocalBroadcastManager.getInstance(this@DashboardActivity)
                                            .sendBroadcast(
                                                Intent(WearableHelper.ActionsPath)
                                                    .putExtra(
                                                        EXTRA_ACTIONDATA,
                                                        JSONParser.serializer(
                                                            action,
                                                            Action::class.java
                                                        )
                                                    )
                                            )
                                    }
                                }
                                timer.start()
                                activeTimers[action.actionType.name] = timer

                                // Disable click action for all items until a response is received
                                mAdapter.isItemsClickable = false
                        } else {
                            Logger.writeLine(
                                Log.INFO,
                                "%s: Unhandled action: %s",
                                "DashboardActivity",
                                intent.action
                            )
                        }
                    }
                }
            }
        }

        binding.drawerLayout.setDrawerStateCallback(object : WearableDrawerLayout.DrawerStateCallback() {
            override fun onDrawerOpened(layout: WearableDrawerLayout, drawerView: WearableDrawerView) {
                super.onDrawerOpened(layout, drawerView)
                drawerView.requestFocus()
            }

            override fun onDrawerClosed(layout: WearableDrawerLayout, drawerView: WearableDrawerView) {
                super.onDrawerClosed(layout, drawerView)
                drawerView.clearFocus()
            }

            override fun onDrawerStateChanged(layout: WearableDrawerLayout, newState: Int) {
                super.onDrawerStateChanged(layout, newState)
                if (newState == WearableDrawerView.STATE_IDLE &&
                    binding.bottomActionDrawer.isPeeking && binding.bottomActionDrawer.hasFocus()
                ) {
                    binding.bottomActionDrawer.clearFocus()
                }
            }
        })

        binding.bottomActionDrawer.setIsAutoPeekEnabled(true)
        binding.bottomActionDrawer.isPeekOnScrollDownEnabled = true
        val drawerScrollView = binding.bottomActionDrawer.findViewById<View>(R.id.drawer_content)
        binding.bottomActionDrawer.setOnGenericMotionListener { _, event ->
            if (drawerScrollView != null && event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(
                    InputDeviceCompat.SOURCE_ROTARY_ENCODER
                )
            ) {
                // Don't forget the negation here
                val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                        ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(this@DashboardActivity), this@DashboardActivity
                        )

                // Swap these axes if you want to do horizontal scrolling instead
                drawerScrollView.scrollBy(0, delta.roundToInt())

                return@setOnGenericMotionListener true
            }

            false
        }

        binding.swipeLayout.setProgressBackgroundColorSchemeColor(getAttrColor(R.attr.colorSurface))
        binding.swipeLayout.setColorSchemeColors(getAttrColor(R.attr.colorAccent))
        binding.swipeLayout.setOnRefreshListener {
            lifecycleScope.launch {
                requestUpdate()
            }
            binding.swipeLayout.isRefreshing = false
        }

        binding.deviceState.setPrimaryText(R.string.message_gettingstatus)

        binding.battStat.setControlView(CircularProgressIndicator(this).apply {
            val height = dpToPx(24f).toInt()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            isIndeterminate = true
            indicatorSize = height
        })

        binding.actionsList.isFocusable = false
        binding.actionsList.clearFocus()

        mAdapter = ActionItemAdapter(this)
        binding.actionsList.adapter = mAdapter
        binding.actionsList.isEnabled = false
        setLayoutManager()

        findViewById<View>(R.id.layout_pref).setOnClickListener {
            Settings.setGridLayout(!Settings.useGridLayout())
        }
        updateLayoutPref()

        findViewById<View>(R.id.dashconfig_pref).setOnClickListener {
            startActivity(Intent(this, DashboardConfigActivity::class.java))
        }

        findViewById<View>(R.id.tiledashconfig_pref).setOnClickListener {
            startActivity(Intent(this, DashboardTileConfigActivity::class.java))
        }

        findViewById<WearChipButton>(R.id.media_ctrlr_pref).also { mediaCtrlrPref ->
            val mediaCtrlrComponent =
                ComponentName(applicationContext, "com.thewizrd.simplewear.MediaControllerActivity")

            mediaCtrlrPref.setOnClickListener {
                mediaCtrlrPref.toggle()

                packageManager.setComponentEnabledSetting(
                    mediaCtrlrComponent,
                    if (mediaCtrlrPref.isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            mediaCtrlrPref.isChecked =
                packageManager.getComponentEnabledSetting(mediaCtrlrComponent) <= PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }

        intentFilter = IntentFilter().apply {
            addAction(ACTION_UPDATECONNECTIONSTATUS)
            addAction(ACTION_OPENONPHONE)
            addAction(ACTION_CHANGED)
            addAction(WearableHelper.BatteryPath)
            addAction(WearableHelper.ActionsPath)
        }

        activeTimers = ArrayMap()
        activeTimers[TIMER_SYNC] = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                lifecycleScope.launch {
                    updateConnectionStatus()
                    requestUpdate()
                    startTimer(TIMER_SYNC_NORESPONSE)
                }
            }
        }

        activeTimers[TIMER_SYNC_NORESPONSE] = object : CountDownTimer(2000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                lifecycleScope.launch {
                    CustomConfirmationOverlay()
                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                        .setCustomDrawable(
                            ContextCompat.getDrawable(
                                this@DashboardActivity,
                                R.drawable.ws_full_sad
                            )
                        )
                        .setMessage(this@DashboardActivity.getString(R.string.error_sendmessage))
                        .showOn(this@DashboardActivity)
                }
            }
        }
    }

    private fun showProgressBar(show: Boolean) {
        lifecycleScope.launch {
            binding.battStat.setControlViewVisibility(if (show) View.VISIBLE else View.GONE)
        }
    }

    private fun updateLayoutPref() {
        val useGridLayout = Settings.useGridLayout()
        val pref = findViewById<WearChipButton>(R.id.layout_pref)
        pref.setIconResource(if (useGridLayout) R.drawable.ic_apps_white_24dp else R.drawable.ic_view_list_white_24dp)
        pref.setSecondaryText(if (useGridLayout) R.string.option_grid else R.string.option_list)
    }

    private fun setLayoutManager() {
        if (Settings.useGridLayout()) {
            binding.actionsList.layoutManager = GridLayoutManager(this, 3)
        } else {
            binding.actionsList.layoutManager = LinearLayoutManager(this)
        }
        binding.actionsList.adapter = mAdapter
    }

    private fun updateDashboard() {
        val actions = Settings.getDashboardConfig()
        mAdapter.updateActions(actions)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            // Don't forget the negation here
            val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                    ViewConfigurationCompat.getScaledVerticalScrollFactor(
                        ViewConfiguration.get(this@DashboardActivity), this@DashboardActivity
                    )

            // Swap these axes if you want to do horizontal scrolling instead
            binding.scrollView.scrollBy(0, delta.roundToInt())

            return true
        }
        return super.onGenericMotionEvent(event)
    }

    private fun cancelTimer(action: Actions) {
        cancelTimer(action.name, true)
    }

    @Synchronized
    private fun cancelTimer(timerKey: String, remove: Boolean = false) {
        var timer = activeTimers[timerKey]
        if (timer != null) {
            timer.cancel()
            if (remove) {
                activeTimers.remove(timerKey)
                timer = null
            }
        }
    }

    private fun startTimer(timerKey: String) {
        val timer = activeTimers[timerKey]
        timer?.start()
    }

    override fun onStart() {
        super.onStart()
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onResume() {
        super.onResume()

        binding.scrollView.requestFocus()

        // Update statuses
        binding.battStat.setText(R.string.state_syncing)
        showProgressBar(true)
        lifecycleScope.launch {
            updateConnectionStatus()
            requestUpdate()
            startTimer(TIMER_SYNC)
        }
    }

    override fun onStop() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            Settings.KEY_LAYOUTMODE -> {
                lifecycleScope.launch {
                    runCatching {
                        withStarted {
                            updateLayoutPref()
                            setLayoutManager()
                        }
                    }
                }
            }
            Settings.KEY_DASHCONFIG -> {
                lifecycleScope.launch {
                    runCatching {
                        withStarted {
                            updateDashboard()
                        }
                    }
                }
            }
        }
    }
}