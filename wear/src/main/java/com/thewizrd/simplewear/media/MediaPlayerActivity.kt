package com.thewizrd.simplewear.media

import android.content.*
import android.database.DataSetObserver
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.ambient.AmbientModeSupport
import com.google.android.gms.wearable.*
import com.google.android.wearable.intent.RemoteIntent
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.WearableListenerActivity
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityMusicplaybackBinding
import com.thewizrd.simplewear.helpers.ConfirmationResultReceiver
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*

class MediaPlayerActivity : WearableListenerActivity(), AmbientModeSupport.AmbientCallbackProvider,
    DataClient.OnDataChangedListener {
    companion object {
        private const val KEY_APPDETAILS = "SimpleWear.Droid.extra.APP_DETAILS"
        private const val KEY_AUTOLAUNCH = "SimpleWear.Droid.extra.AUTO_LAUNCH"

        const val ACTION_ENTERAMBIENTMODE = "SimpleWear.Droid.action.ENTER_AMBIENT_MODE"
        const val ACTION_EXITAMBIENTMODE = "SimpleWear.Droid.action.EXIT_AMBIENT_MODE"
        const val ACTION_UPDATEAMBIENTMODE = "SimpleWear.Droid.action.UPDATE_AMBIENT_MODE"

        fun buildIntent(context: Context, appDetails: AppItemViewModel): Intent {
            val intent = Intent(context, MediaPlayerActivity::class.java)
            intent.putExtra(
                KEY_APPDETAILS,
                JSONParser.serializer(appDetails, AppItemViewModel::class.java)
            )
            return intent
        }

        fun buildAutoLaunchIntent(context: Context): Intent {
            val intent = Intent(context, MediaPlayerActivity::class.java)
            intent.putExtra(KEY_AUTOLAUNCH, true)
            return intent
        }
    }

    override lateinit var broadcastReceiver: BroadcastReceiver
        private set
    override lateinit var intentFilter: IntentFilter
        private set

    private lateinit var binding: ActivityMusicplaybackBinding
    private val mMediaPlayerDetails: AppItemViewModel by viewModels()

    private lateinit var mViewPagerAdapter: MediaFragmentPagerAdapter
    private var supportsBrowser: Boolean = false
    private var supportsCustomActions: Boolean = false
    private var supportsQueue: Boolean = false

    private var updateJob: Job? = null

    private lateinit var mAmbientController: AmbientModeSupport.AmbientController

    private var isAutoLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        binding = ActivityMusicplaybackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        broadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                lifecycleScope.launch {
                    if (intent.action != null) {
                        when (intent.action) {
                            ACTION_UPDATECONNECTIONSTATUS -> {
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
                                                this@MediaPlayerActivity,
                                                PhoneSyncActivity::class.java
                                            )
                                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        )
                                        finishAffinity()
                                    }
                                    WearConnectionStatus.APPNOTINSTALLED -> {
                                        // Open store on remote device
                                        val intentAndroid = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(WearableHelper.getPlayStoreURI())

                                        RemoteIntent.startRemoteActivity(
                                            this@MediaPlayerActivity, intentAndroid,
                                            ConfirmationResultReceiver(this@MediaPlayerActivity)
                                        )

                                        // Navigate
                                        startActivity(
                                            Intent(
                                                this@MediaPlayerActivity,
                                                PhoneSyncActivity::class.java
                                            )
                                                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                        )
                                        finishAffinity()
                                    }
                                }
                            }
                            WearableHelper.AudioStatusPath -> {
                                requestAudioStreamState()
                            }
                            WearableHelper.MediaPlayPath,
                            WearableHelper.MediaPausePath,
                            WearableHelper.MediaPreviousPath,
                            WearableHelper.MediaNextPath,
                            WearableHelper.MediaBrowserItemsBackPath -> {
                                lifecycleScope.launch {
                                    if (connect()) {
                                        sendMessage(
                                            mPhoneNodeWithApp!!.id,
                                            WearableHelper.MediaPlayerConnectPath,
                                            if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                                        )
                                        sendMessage(mPhoneNodeWithApp!!.id, intent.action!!, null)
                                    }
                                }
                            }
                            WearableHelper.MediaBrowserItemsClickPath,
                            WearableHelper.MediaBrowserItemsExtraSuggestedClickPath,
                            WearableHelper.MediaQueueItemsClickPath -> {
                                val id = intent.getStringExtra(WearableHelper.KEY_MEDIAITEM_ID)

                                lifecycleScope.launch {
                                    if (connect()) {
                                        sendMessage(
                                            mPhoneNodeWithApp!!.id,
                                            WearableHelper.MediaPlayerConnectPath,
                                            if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                                        )
                                        sendMessage(
                                            mPhoneNodeWithApp!!.id,
                                            intent.action!!,
                                            id!!.stringToBytes()
                                        )
                                    }
                                }
                            }
                            WearableHelper.MediaActionsClickPath -> {
                                val id =
                                    intent.getStringExtra(WearableHelper.KEY_MEDIA_ACTIONITEM_ACTION)

                                lifecycleScope.launch {
                                    if (connect()) {
                                        sendMessage(
                                            mPhoneNodeWithApp!!.id,
                                            WearableHelper.MediaPlayerConnectPath,
                                            if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                                        )
                                        sendMessage(
                                            mPhoneNodeWithApp!!.id,
                                            intent.action!!,
                                            id!!.stringToBytes()
                                        )
                                    }
                                }
                            }
                            ACTION_CHANGED -> {
                                val jsonData = intent.getStringExtra(EXTRA_ACTIONDATA)
                                requestAction(jsonData)
                            }
                            else -> {
                                Logger.writeLine(
                                    Log.INFO,
                                    "%s: Unhandled action: %s",
                                    "MediaPlayerActivity",
                                    intent.action
                                )
                            }
                        }
                    }
                }
            }
        }

        binding.mediaViewpager.adapter = MediaFragmentPagerAdapter(supportFragmentManager).also {
            mViewPagerAdapter = it
        }

        binding.mediaViewpagerIndicator.dotFadeWhenIdle = false
        binding.mediaViewpagerIndicator.setPager(binding.mediaViewpager)

        mViewPagerAdapter.registerDataSetObserver(object : DataSetObserver() {
            override fun onChanged() {
                binding.mediaViewpagerIndicator.notifyDataSetChanged()
            }
        })

        binding.retryFab.setOnClickListener {
            lifecycleScope.launch {
                updateConnectionStatus()
                requestPlayerConnect()
                updatePager()
            }
        }

        intentFilter = IntentFilter().apply {
            addAction(ACTION_UPDATECONNECTIONSTATUS)
            addAction(ACTION_CHANGED)
            addAction(WearableHelper.AudioStatusPath)
            addAction(WearableHelper.MediaPlayPath)
            addAction(WearableHelper.MediaPausePath)
            addAction(WearableHelper.MediaPreviousPath)
            addAction(WearableHelper.MediaNextPath)
            addAction(WearableHelper.MediaBrowserItemsClickPath)
            addAction(WearableHelper.MediaBrowserItemsBackPath)
            addAction(WearableHelper.MediaActionsClickPath)
            addAction(WearableHelper.MediaQueueItemsClickPath)
        }

        mAmbientController = AmbientModeSupport.attach(this)
    }

    private enum class MediaPageType {
        Player,
        CustomControls,
        Browser,
        Queue
    }

    private inner class MediaFragmentPagerAdapter(fragmentMgr: FragmentManager) :
        FragmentPagerAdapter(fragmentMgr) {
        var isAmbientMode = false
        var isLowBitAmbient = false
        var doBurnInProtection = false

        private val supportedPageTypes = mutableListOf(MediaPageType.Player)

        override fun getCount(): Int {
            return supportedPageTypes.size
        }

        override fun getItem(position: Int): Fragment {
            val type = supportedPageTypes[position]

            val args = Bundle().apply {
                putBoolean(AmbientModeSupport.FRAGMENT_TAG, isAmbientMode)
                putBoolean(AmbientModeSupport.EXTRA_LOWBIT_AMBIENT, isLowBitAmbient)
                putBoolean(AmbientModeSupport.EXTRA_BURN_IN_PROTECTION, doBurnInProtection)
            }

            return when (type) {
                MediaPageType.Player -> {
                    MediaPlayerControlsFragment().apply {
                        arguments = args
                    }
                }
                MediaPageType.Browser -> {
                    MediaBrowserFragment()
                }
                MediaPageType.CustomControls -> {
                    MediaCustomControlsFragment()
                }
                MediaPageType.Queue -> {
                    MediaQueueFragment()
                }
            }
        }

        @Synchronized
        fun updateSupportedPages(
            supportsBrowser: Boolean,
            supportsQueue: Boolean,
            supportsCustomActions: Boolean
        ) {
            supportedPageTypes.clear()
            supportedPageTypes.add(MediaPageType.Player)
            if (supportsCustomActions) {
                supportedPageTypes.add(MediaPageType.CustomControls)
            }
            if (supportsQueue) {
                supportedPageTypes.add(MediaPageType.Queue)
            }
            if (supportsBrowser) {
                supportedPageTypes.add(MediaPageType.Browser)
            }
            this.notifyDataSetChanged()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            when (messageEvent.path) {
                WearableHelper.MediaPlayerConnectPath,
                WearableHelper.MediaPlayerAutoLaunchPath -> {
                    val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                    if (actionStatus == ActionStatus.PERMISSION_DENIED) {
                        CustomConfirmationOverlay()
                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                            .setCustomDrawable(
                                ContextCompat.getDrawable(
                                    this@MediaPlayerActivity,
                                    R.drawable.ic_full_sad
                                )
                            )
                            .setMessage(getString(R.string.error_permissiondenied))
                            .showOn(this@MediaPlayerActivity)

                        openAppOnPhone(false)

                        showNoPlayersView(true)
                    } else if (actionStatus == ActionStatus.SUCCESS) {
                        showNoPlayersView(false)
                    }
                }
                WearableHelper.MediaBrowserItemsClickPath,
                WearableHelper.MediaBrowserItemsExtraSuggestedClickPath -> {
                    val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                    if (actionStatus == ActionStatus.SUCCESS) {
                        binding.mediaViewpager.currentItem = 0
                    }
                }
            }
        }
    }

    private fun showNoPlayersView(show: Boolean) {
        binding.noplayersView.visibility = if (show) View.VISIBLE else View.GONE
        binding.mediaViewpager.visibility = if (show) View.INVISIBLE else View.VISIBLE
        binding.mediaViewpagerIndicator.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            updateJob?.cancel()

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    updatePager(item)
                } else if (event.type == DataEvent.TYPE_DELETED) {
                    val item = event.dataItem
                    when (item.uri.path) {
                        WearableHelper.MediaBrowserItemsPath -> {
                            supportsBrowser = false
                        }
                        WearableHelper.MediaActionsPath -> {
                            supportsCustomActions = false
                        }
                        WearableHelper.MediaQueueItemsPath -> {
                            supportsQueue = false
                        }
                    }
                }
            }

            updateJob = lifecycleScope.launch updateJob@{
                delay(1000)

                if (!isActive) return@updateJob

                mViewPagerAdapter.updateSupportedPages(
                    supportsBrowser,
                    supportsQueue,
                    supportsCustomActions
                )
            }
        }
    }

    private fun updatePager() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(this@MediaPlayerActivity)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            "/media"
                        ),
                        DataClient.FILTER_PREFIX
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    updatePager(item)
                }

                buff.release()

                lifecycleScope.launch(Dispatchers.Main) {
                    mViewPagerAdapter.updateSupportedPages(
                        supportsBrowser,
                        supportsQueue,
                        supportsCustomActions
                    )
                }
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private fun updatePager(item: DataItem) {
        when (item.uri.path) {
            WearableHelper.MediaBrowserItemsPath -> {
                try {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val items = dataMap.getDataMapArrayList(WearableHelper.KEY_MEDIAITEMS)
                    supportsBrowser = !items.isNullOrEmpty()
                } catch (e: Exception) {
                    Logger.writeLine(Log.ERROR, e)
                    supportsBrowser = false
                }
            }
            WearableHelper.MediaActionsPath -> {
                try {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val items = dataMap.getDataMapArrayList(WearableHelper.KEY_MEDIAITEMS)
                    supportsCustomActions = !items.isNullOrEmpty()
                } catch (e: Exception) {
                    Logger.writeLine(Log.ERROR, e)
                    supportsCustomActions = false
                }
            }
            WearableHelper.MediaQueueItemsPath -> {
                try {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val items = dataMap.getDataMapArrayList(WearableHelper.KEY_MEDIAITEMS)
                    supportsQueue = !items.isNullOrEmpty()
                } catch (e: Exception) {
                    Logger.writeLine(Log.ERROR, e)
                    supportsQueue = false
                }
            }
        }
    }

    private fun requestPlayerConnect() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.MediaPlayerConnectPath,
                    if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                )
            }
        }
    }

    private fun requestPlayerDisconnect() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.MediaPlayerDisconnectPath, null)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)

        // Update statuses
        lifecycleScope.launch {
            updateConnectionStatus()
            requestPlayerConnect()
            updatePager()
        }
    }

    override fun onPause() {
        requestPlayerDisconnect()
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getDataClient(this).removeListener(this)
        super.onPause()
    }

    private fun requestAudioStreamState() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.AudioStatusPath,
                    AudioStreamType.MUSIC.name.stringToBytes()
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.extras?.getBoolean(KEY_AUTOLAUNCH) == true) {
            isAutoLaunch = true
            return
        }

        val model = intent.extras?.getString(KEY_APPDETAILS)?.let {
            JSONParser.deserializer(it, AppItemViewModel::class.java)
        }

        if (model != null) {
            mMediaPlayerDetails.apply {
                appLabel = model.appLabel
                packageName = model.packageName
                activityName = model.activityName
            }
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback {
        return MediaPlayerAmbientCallback()
    }

    private inner class MediaPlayerAmbientCallback : AmbientModeSupport.AmbientCallback() {
        /**
         * If the display is low-bit in ambient mode. i.e. it requires anti-aliased fonts.
         */
        private var isLowBitAmbient = false

        /**
         * If the display requires burn-in protection in ambient mode, rendered pixels need to be
         * intermittently offset to avoid screen burn-in.
         */
        private var doBurnInProtection = false

        override fun onEnterAmbient(ambientDetails: Bundle) {
            super.onEnterAmbient(ambientDetails)
            isLowBitAmbient =
                ambientDetails.getBoolean(AmbientModeSupport.EXTRA_LOWBIT_AMBIENT, false)
            doBurnInProtection =
                ambientDetails.getBoolean(AmbientModeSupport.EXTRA_BURN_IN_PROTECTION, false)

            binding.mediaViewpagerIndicator.visibility = View.INVISIBLE

            mViewPagerAdapter.isAmbientMode = true
            mViewPagerAdapter.isLowBitAmbient = isLowBitAmbient
            mViewPagerAdapter.doBurnInProtection = doBurnInProtection

            if (binding.mediaViewpager.currentItem != 0) {
                binding.mediaViewpager.currentItem = 0
            }

            LocalBroadcastManager.getInstance(this@MediaPlayerActivity)
                .sendBroadcast(
                    Intent(ACTION_ENTERAMBIENTMODE)
                        .putExtra(AmbientModeSupport.EXTRA_LOWBIT_AMBIENT, isLowBitAmbient)
                        .putExtra(AmbientModeSupport.EXTRA_BURN_IN_PROTECTION, doBurnInProtection)
                )
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            binding.mediaViewpagerIndicator.visibility = View.VISIBLE

            mViewPagerAdapter.isAmbientMode = false

            LocalBroadcastManager.getInstance(this@MediaPlayerActivity)
                .sendBroadcast(Intent(ACTION_EXITAMBIENTMODE))
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()

            mViewPagerAdapter.isAmbientMode = true

            LocalBroadcastManager.getInstance(this@MediaPlayerActivity)
                .sendBroadcast(Intent(ACTION_UPDATEAMBIENTMODE))
        }
    }
}