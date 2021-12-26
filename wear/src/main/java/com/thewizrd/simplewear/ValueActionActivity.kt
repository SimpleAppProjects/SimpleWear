package com.thewizrd.simplewear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityValueactionBinding
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// TODO: Move volume actions into separate VolumeActionActivity
class ValueActionActivity : WearableListenerActivity() {
    companion object {
        const val EXTRA_STREAMTYPE = "SimpleWear.Droid.Wear.extra.STREAM_TYPE"
    }

    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivityValueactionBinding

    private var mAction: Actions? = null
    private var mRemoteValue: Float? = null

    private var mValueActionState: ValueActionState? = null
    private var mStreamType: AudioStreamType? = AudioStreamType.MUSIC

    private var timer: CountDownTimer? = null

    private val rsbScope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var rsbJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityValueactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (intent.hasExtra(EXTRA_ACTION)) {
            mAction = intent.getSerializableExtra(EXTRA_ACTION) as Actions
            when (mAction) {
                Actions.VOLUME -> {
                    if (intent.hasExtra(EXTRA_STREAMTYPE)) {
                        mStreamType =
                            intent.getSerializableExtra(EXTRA_STREAMTYPE) as? AudioStreamType
                                ?: AudioStreamType.MUSIC
                    }
                }
                Actions.BRIGHTNESS -> {
                    // Valid action
                }
                else -> {
                    // Not a ValueAction
                    setResult(RESULT_CANCELED)
                    finish()
                }
            }
        } else {
            // No action given
            setResult(RESULT_CANCELED)
            finish()
        }

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
                                    // Navigate
                                    startActivity(
                                        Intent(
                                            this@ValueActionActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    finishAffinity()
                                }
                                WearConnectionStatus.APPNOTINSTALLED -> {
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

                                    // Navigate
                                    startActivity(
                                        Intent(
                                            this@ValueActionActivity,
                                            PhoneSyncActivity::class.java
                                        )
                                    )
                                    finishAffinity()
                                }
                            }
                        } else if (WearableHelper.ActionsPath == intent.action) {
                            timer?.cancel()

                            val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                            val action = JSONParser.deserializer(jsonData, Action::class.java)
                            val actionSuccessful = action?.isActionSuccessful ?: false
                            val actionStatus = action?.actionStatus ?: ActionStatus.UNKNOWN

                            if (!actionSuccessful) {
                                lifecycleScope.launch {
                                    when (actionStatus) {
                                        ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        this@ValueActionActivity,
                                                        R.drawable.ws_full_sad
                                                    )
                                                )
                                                .setMessage(getString(R.string.error_actionfailed))
                                                .showOn(this@ValueActionActivity)
                                        }
                                        ActionStatus.PERMISSION_DENIED -> {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        this@ValueActionActivity,
                                                        R.drawable.ws_full_sad
                                                    )
                                                )
                                                .setMessage(getString(R.string.error_permissiondenied))
                                                .showOn(this@ValueActionActivity)

                                            openAppOnPhone(false)
                                        }
                                        ActionStatus.TIMEOUT -> {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        this@ValueActionActivity,
                                                        R.drawable.ws_full_sad
                                                    )
                                                )
                                                .setMessage(getString(R.string.error_sendmessage))
                                                .showOn(this@ValueActionActivity)
                                        }
                                        ActionStatus.SUCCESS -> {
                                        }
                                    }
                                }
                            }
                        } else if (ACTION_CHANGED == intent.action) {
                            val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                            val action = JSONParser.deserializer(jsonData, Action::class.java)
                            requestAction(jsonData)

                            lifecycleScope.launch {
                                timer?.cancel()
                                timer = object : CountDownTimer(3000, 500) {
                                    override fun onTick(millisUntilFinished: Long) {}
                                    override fun onFinish() {
                                        action!!.setActionSuccessful(ActionStatus.TIMEOUT)
                                        LocalBroadcastManager.getInstance(this@ValueActionActivity)
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
                                timer!!.start()
                            }
                        } else {
                            Logger.writeLine(
                                Log.INFO,
                                "%s: Unhandled action: %s",
                                "ValueActionActivity",
                                intent.action
                            )
                        }
                    }
                }
            }
        }

        binding.increaseBtn.setOnClickListener {
            val actionData = if (mAction == Actions.VOLUME && mStreamType != null) {
                VolumeAction(ValueDirection.UP, mStreamType)
            } else {
                ValueAction(mAction!!, ValueDirection.UP)
            }
            LocalBroadcastManager.getInstance(this@ValueActionActivity)
                .sendBroadcast(
                    Intent(ACTION_CHANGED)
                        .putExtra(
                            EXTRA_ACTIONDATA,
                            JSONParser.serializer(actionData, Action::class.java)
                        )
                )
        }
        binding.decreaseBtn.setOnClickListener {
            val actionData = if (mAction == Actions.VOLUME && mStreamType != null) {
                VolumeAction(ValueDirection.DOWN, mStreamType)
            } else {
                ValueAction(mAction!!, ValueDirection.DOWN)
            }
            LocalBroadcastManager.getInstance(this@ValueActionActivity)
                .sendBroadcast(
                    Intent(ACTION_CHANGED)
                        .putExtra(
                            EXTRA_ACTIONDATA,
                            JSONParser.serializer(actionData, Action::class.java)
                        )
                )
        }
        binding.actionIcon.setOnClickListener {
            if (mStreamType != null && mAction == Actions.VOLUME) {
                val maxStates = AudioStreamType.values().size
                var newValue = (mStreamType!!.value + 1) % maxStates
                if (newValue < 0) newValue += maxStates
                mStreamType = AudioStreamType.valueOf(newValue)

                lifecycleScope.launch {
                    requestAudioStreamState()
                }
            } else if (mAction == Actions.BRIGHTNESS) {
                lifecycleScope.launch {
                    requestToggleAutoBrightness()
                }
            }
        }

        when (mAction) {
            Actions.VOLUME -> {
                binding.actionIcon.setImageResource(R.drawable.ic_volume_up_white_24dp)
                binding.actionTitle.setText(R.string.action_volume)

                lifecycleScope.launch {
                    requestAudioStreamState()
                }
            }
            Actions.BRIGHTNESS -> {
                binding.actionIcon.setImageResource(R.drawable.ic_brightness_medium)
                binding.actionTitle.setText(R.string.action_brightness)

                lifecycleScope.launch {
                    requestValueState()
                }
            }
        }

        intentFilter = IntentFilter()
        intentFilter.addAction(ACTION_UPDATECONNECTIONSTATUS)
        intentFilter.addAction(ACTION_CHANGED)
        intentFilter.addAction(WearableHelper.ActionsPath)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            if (messageEvent.path == WearableHelper.AudioStatusPath && mAction == Actions.VOLUME) {
                val status = messageEvent.data?.let {
                    JSONParser.deserializer(it.bytesToString(), AudioStreamState::class.java)
                }
                mValueActionState = status

                if (status == null) {
                    mStreamType = null
                    binding.actionIcon.setImageResource(R.drawable.ic_volume_up_white_24dp)
                    binding.actionValueProgress.progress = 0
                } else {
                    mStreamType = status.streamType
                    when (status.streamType) {
                        AudioStreamType.MUSIC -> binding.actionIcon.setImageResource(R.drawable.ic_music_note_white_24dp)
                        AudioStreamType.RINGTONE -> binding.actionIcon.setImageResource(R.drawable.ic_baseline_ring_volume_24dp)
                        AudioStreamType.VOICE_CALL -> binding.actionIcon.setImageResource(R.drawable.ic_baseline_call_24dp)
                        AudioStreamType.ALARM -> binding.actionIcon.setImageResource(R.drawable.ic_alarm_white_24dp)
                    }

                    binding.actionValueProgress.max = status.maxVolume
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        binding.actionValueProgress.min = status.minVolume
                    }
                    binding.actionValueProgress.progress = status.currentVolume
                }
            } else if (messageEvent.path == WearableHelper.ValueStatusPath) {
                val status = messageEvent.data?.let {
                    JSONParser.deserializer(it.bytesToString(), ValueActionState::class.java)
                }
                mValueActionState = status

                if (status == null) {
                    binding.actionValueProgress.progress = 0
                } else {
                    binding.actionValueProgress.max = status.maxValue
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        binding.actionValueProgress.min = status.minValue
                    }
                    binding.actionValueProgress.progress = status.currentValue
                }
            } else if (messageEvent.path == WearableHelper.BrightnessModePath && mAction == Actions.BRIGHTNESS) {
                val enabled = messageEvent.data.bytesToBool()
                if (enabled) {
                    binding.actionIcon.setImageResource(R.drawable.ic_brightness_auto)
                } else {
                    binding.actionIcon.setImageResource(R.drawable.ic_brightness_medium)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        lifecycleScope.launch {
            updateConnectionStatus()
            when (mAction) {
                Actions.VOLUME -> {
                    requestAudioStreamState()
                }
            }
        }
    }

    override fun onDestroy() {
        rsbScope.cancel()
        super.onDestroy()
    }

    private suspend fun requestAudioStreamState() {
        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                WearableHelper.AudioStatusPath,
                mStreamType?.name?.stringToBytes()
            )
        }
    }

    private suspend fun requestValueState() {
        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                WearableHelper.ValueStatusPath,
                mAction?.value?.intToBytes()
            )
        }
    }

    private fun requestSetVolume(value: Int) {
        rsbJob?.cancel()
        rsbJob = rsbScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.AudioVolumePath,
                    (mValueActionState as? AudioStreamState)?.let {
                        JSONParser.serializer(
                            AudioStreamState(value, it.minVolume, it.maxVolume, it.streamType),
                            AudioStreamState::class.java
                        ).stringToBytes()
                    }
                )
            }
        }
    }

    private fun requestSetValue(value: Int) {
        rsbJob?.cancel()
        rsbJob = rsbScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.ValueStatusSetPath,
                    mValueActionState?.let {
                        JSONParser.serializer(
                            ValueActionState(value, it.minValue, it.maxValue, it.actionType),
                            ValueActionState::class.java
                        ).stringToBytes()
                    }
                )
            }
        }
    }

    private suspend fun requestToggleAutoBrightness() {
        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                WearableHelper.BrightnessModePath,
                null
            )
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL &&
            event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)
        ) {
            val scaleFactor = ViewConfigurationCompat.getScaledVerticalScrollFactor(
                ViewConfiguration.get(this), this
            )
            // Don't forget the negation here
            val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) * scaleFactor
            val scaleMax = 25 * scaleFactor

            // Scaling to (25 * scaleFactor) seems to be good
            // Emulator -> ~2400
            val valueState = mValueActionState

            if (valueState != null) {
                val maxValue =
                    scaleValueFromState(valueState.maxValue.toFloat(), valueState, 0f, scaleMax)
                val currValue = mRemoteValue ?: scaleValueFromState(
                    valueState.currentValue.toFloat(),
                    valueState,
                    0f,
                    scaleMax
                )
                val minValue =
                    scaleValueFromState(valueState.minValue.toFloat(), valueState, 0f, scaleMax)

                val scaledValue = currValue + delta
                mRemoteValue = normalize(scaledValue, 0f, scaleMax)
                val value = normalizeToState(
                    scaleValueToState(
                        scaledValue,
                        0f,
                        scaleMax,
                        valueState
                    ).roundToInt(), valueState
                )

                if (BuildConfig.DEBUG) {
                    Log.d(
                        "ValueActionScroller",
                        "currVal = ${(currValue).roundToInt()}, " +
                                "maxVal = ${(maxValue).roundToInt()}, " +
                                "minVal = ${(minValue).roundToInt()}, " +
                                "delta = $delta, " +
                                "scaleFactor = $scaleFactor, " +
                                "scaledValue = $scaledValue, " +
                                "setValue = $value"
                    )
                }

                if (valueState is AudioStreamState) {
                    requestSetVolume(value)
                } else {
                    requestSetValue(value)
                }
            }
        }

        return super.onGenericMotionEvent(event)
    }

    private fun scaleValue(
        value: Float,
        minValue: Float,
        maxValue: Float,
        scaleMin: Float,
        scaleMax: Float
    ): Float {
        return ((value - minValue) / (maxValue - minValue)) * (scaleMax - scaleMin) + scaleMin
    }

    private fun scaleValueFromState(
        value: Float,
        state: ValueActionState,
        scaleMin: Float,
        scaleMax: Float
    ): Float {
        return ((value - state.minValue) / (state.maxValue - state.minValue)) * (scaleMax - scaleMin) + scaleMin
    }

    private fun scaleValueToState(
        value: Float,
        minValue: Float,
        maxValue: Float,
        state: ValueActionState
    ): Float {
        return ((value - minValue) / (maxValue - minValue)) * (state.maxValue - state.minValue) + state.minValue
    }

    private fun normalize(value: Int, minValue: Int, maxValue: Int): Int {
        return min(maxValue, max(value, minValue))
    }

    private fun normalize(value: Float, minValue: Float, maxValue: Float): Float {
        return min(maxValue, max(value, minValue))
    }

    private fun normalizeToState(value: Int, state: ValueActionState): Int {
        return normalize(value, state.minValue, state.maxValue)
    }
}