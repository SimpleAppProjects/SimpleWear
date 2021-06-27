package com.thewizrd.simplewear

import android.content.*
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Resources
import android.os.Bundle
import android.os.CountDownTimer
import android.support.wearable.input.RotaryEncoder
import android.support.wearable.view.ConfirmationOverlay
import android.util.ArrayMap
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.drawer.WearableDrawerLayout
import androidx.wear.widget.drawer.WearableDrawerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.adapters.ActionItemAdapter
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityDashboardBinding
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.launch
import java.util.*
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
                                WearConnectionStatus.DISCONNECTED ->
                                    lifecycleScope.launch {
                                        binding.deviceStatText.setText(R.string.status_disconnected)

                                        // Navigate
                                        startActivity(
                                            Intent(
                                                this@DashboardActivity,
                                                PhoneSyncActivity::class.java
                                            )
                                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        )
                                        finishAffinity()
                                    }
                                WearConnectionStatus.CONNECTING ->
                                    lifecycleScope.launch {
                                        binding.deviceStatText.setText(R.string.status_connecting)
                                        if (binding.actionsList.isEnabled) binding.actionsList.isEnabled =
                                            false
                                    }
                                WearConnectionStatus.APPNOTINSTALLED ->
                                    lifecycleScope.launch {
                                        binding.deviceStatText.setText(R.string.error_notinstalled)

                                        // Open store on remote device
                                        val intentAndroid = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(WearableHelper.getPlayStoreURI())
                                        RemoteIntent.startRemoteActivity(
                                            this@DashboardActivity, intentAndroid,
                                            ConfirmationResultReceiver(this@DashboardActivity)
                                        )
                                        if (binding.actionsList.isEnabled) binding.actionsList.isEnabled =
                                            false
                                    }
                                WearConnectionStatus.CONNECTED ->
                                    lifecycleScope.launch {
                                        binding.deviceStatText.setText(R.string.status_connected)
                                        if (!binding.actionsList.isEnabled) binding.actionsList.isEnabled =
                                            true
                                    }
                            }
                        } else if (ACTION_OPENONPHONE == intent.action) {
                            val success = intent.getBooleanExtra(EXTRA_SUCCESS, false)
                            val showAni = intent.getBooleanExtra(EXTRA_SHOWANIMATION, false)
                            if (showAni) {
                                lifecycleScope.launch {
                                    ConfirmationOverlay()
                                        .setType(if (success) ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION else ConfirmationOverlay.FAILURE_ANIMATION)
                                        .showOn(this@DashboardActivity)
                                    if (!success) {
                                        binding.deviceStatText.setText(R.string.error_syncing)
                                    }
                                }
                            }
                        } else if (WearableHelper.BatteryPath == intent.action) {
                            showProgressBar(false)
                            cancelTimer(TIMER_SYNC)
                            cancelTimer(TIMER_SYNC_NORESPONSE)

                            val jsonData = intent.getStringExtra(EXTRA_STATUS)
                            lifecycleScope.launch {
                                var value = getString(R.string.state_unknown)
                                if (!jsonData.isNullOrBlank()) {
                                    val status =
                                        JSONParser.deserializer(jsonData, BatteryStatus::class.java)
                                    value = String.format(
                                        Locale.ROOT, "%d%%, %s", status!!.batteryLevel,
                                        if (status.isCharging) getString(R.string.batt_state_charging) else getString(
                                            R.string.batt_state_discharging
                                        )
                                    )
                                }
                                binding.battStatText.text = value
                            }
                        } else if (WearableHelper.ActionsPath == intent.action) {
                            val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                            val action = JSONParser.deserializer(jsonData, Action::class.java)!!

                            cancelTimer(action.actionType)

                            lifecycleScope.launch {
                                mAdapter.updateButton(ActionButtonViewModel(action))
                            }

                            if (!action.isActionSuccessful) {
                                when (action.actionStatus) {
                                    ActionStatus.UNKNOWN, ActionStatus.FAILURE ->
                                        lifecycleScope.launch {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        this@DashboardActivity,
                                                        R.drawable.ic_full_sad
                                                    )
                                                )
                                                .setMessage(this@DashboardActivity.getString(R.string.error_actionfailed))
                                                .showOn(this@DashboardActivity)
                                        }
                                    ActionStatus.PERMISSION_DENIED -> {
                                        lifecycleScope.launch {
                                            if (action.actionType == Actions.TORCH) {
                                                CustomConfirmationOverlay()
                                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                    .setCustomDrawable(
                                                        ContextCompat.getDrawable(
                                                            this@DashboardActivity,
                                                            R.drawable.ic_full_sad
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
                                                                R.drawable.ic_full_sad
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
                                                            R.drawable.ic_full_sad
                                                        )
                                                    )
                                                    .setMessage(this@DashboardActivity.getString(R.string.error_permissiondenied))
                                                    .showOn(this@DashboardActivity)
                                            }
                                        }

                                        openAppOnPhone(false)
                                    }
                                    ActionStatus.TIMEOUT ->
                                        lifecycleScope.launch {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        this@DashboardActivity,
                                                        R.drawable.ic_full_sad
                                                    )
                                                )
                                                .setMessage(this@DashboardActivity.getString(R.string.error_sendmessage))
                                                .showOn(this@DashboardActivity)
                                        }
                                    ActionStatus.SUCCESS -> {
                                    }
                                }
                            }

                            lifecycleScope.launch {
                                // Re-enable click action
                                mAdapter.isItemsClickable = true
                            }
                        } else if (ACTION_CHANGED == intent.action) {
                            val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                            val action = JSONParser.deserializer(jsonData, Action::class.java)!!
                            requestAction(jsonData)

                            lifecycleScope.launch {
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
                            }
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
                        binding.bottomActionDrawer.isPeeking && binding.bottomActionDrawer.hasFocus()) {
                    binding.bottomActionDrawer.clearFocus()
                }
            }
        })

        binding.bottomActionDrawer.setIsAutoPeekEnabled(true)
        binding.bottomActionDrawer.isPeekOnScrollDownEnabled = true

        binding.swipeLayout.setColorSchemeColors(getColor(R.color.colorPrimary))
        binding.swipeLayout.setOnRefreshListener {
            lifecycleScope.launch {
                requestUpdate()
            }
            binding.swipeLayout.isRefreshing = false
        }

        binding.swipeLayout.viewTreeObserver.addOnPreDrawListener(object :
            ViewTreeObserver.OnPreDrawListener {
            /* BoxInsetLayout impl */
            private val FACTOR = 0.146447f //(1 - sqrt(2)/2)/2
            private val mIsRound = resources.configuration.isScreenRound

            override fun onPreDraw(): Boolean {
                binding.swipeLayout.viewTreeObserver.removeOnPreDrawListener(this)

                val innerLayout = binding.scrollView.getChildAt(0)
                val peekContainer =
                    binding.bottomActionDrawer.findViewById<View>(R.id.ws_drawer_view_peek_container)

                lifecycleScope.launch {
                    val verticalPadding =
                        resources.getDimensionPixelSize(R.dimen.inner_frame_layout_padding)
                    val mScreenHeight = Resources.getSystem().displayMetrics.heightPixels
                    val mScreenWidth = Resources.getSystem().displayMetrics.widthPixels
                    val rightEdge = Math.min(binding.swipeLayout.measuredWidth, mScreenWidth)
                    val bottomEdge = Math.min(binding.swipeLayout.measuredHeight, mScreenHeight)
                    val verticalInset = (FACTOR * Math.max(rightEdge, bottomEdge)).toInt()
                    innerLayout.setPaddingRelative(
                        innerLayout.paddingStart,
                        if (mIsRound) verticalInset else verticalPadding,
                        innerLayout.paddingEnd,
                        peekContainer.height
                    )
                }

                return true
            }
        })

        binding.deviceStatText.setText(R.string.message_gettingstatus)

        binding.actionsList.isEdgeItemsCenteringEnabled = false
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
                                R.drawable.ic_full_sad
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
            binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun updateLayoutPref() {
        val useGridLayout = Settings.useGridLayout()
        val fab = findViewById<FloatingActionButton>(R.id.layout_pref_icon)
        val prefSummary = findViewById<TextView>(R.id.layout_pref_summary)
        fab.setImageResource(if (useGridLayout) R.drawable.ic_apps_white_24dp else R.drawable.ic_view_list_white_24dp)
        prefSummary.setText(if (useGridLayout) R.string.option_grid else R.string.option_list)
    }

    private fun setLayoutManager() {
        if (Settings.useGridLayout()) {
            binding.actionsList.layoutManager = GridLayoutManager(this, 3)
        } else {
            binding.actionsList.layoutManager = WearableLinearLayoutManager(this, null)
        }
        binding.actionsList.adapter = mAdapter
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && RotaryEncoder.isFromRotaryEncoder(event)) {
            // Don't forget the negation here
            val delta = -RotaryEncoder.getRotaryAxisValue(event) * RotaryEncoder.getScaledScrollFactor(
                    this@DashboardActivity)

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

    override fun onResume() {
        super.onResume()

        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this)

        binding.scrollView.requestFocus()

        // Update statuses
        binding.battStatText.setText(R.string.state_syncing)
        showProgressBar(true)
        lifecycleScope.launch {
            updateConnectionStatus()
            requestUpdate()
            startTimer(TIMER_SYNC)
        }
    }

    override fun onPause() {
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        when (key) {
            Settings.KEY_LAYOUTMODE -> {
                updateLayoutPref()
                setLayoutManager()
            }
        }
    }
}