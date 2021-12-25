package com.thewizrd.simplewear.media

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.wear.ambient.AmbientModeSupport
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.WearableListenerActivity
import com.thewizrd.simplewear.controls.AmbientModeViewModel
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.ActivityMusicplaybackBinding
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import kotlinx.coroutines.*
import kotlinx.coroutines.guava.await
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
    private val mAmbientMode: AmbientModeViewModel by viewModels()

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
                                                this@MediaPlayerActivity,
                                                PhoneSyncActivity::class.java
                                            )
                                        )
                                        finishAffinity()
                                    }
                                }
                            }
                            MediaHelper.MediaPlayPath,
                            MediaHelper.MediaPausePath,
                            MediaHelper.MediaPreviousPath,
                            MediaHelper.MediaNextPath,
                            MediaHelper.MediaBrowserItemsBackPath,
                            MediaHelper.MediaVolumeUpPath,
                            MediaHelper.MediaVolumeDownPath,
                            MediaHelper.MediaVolumeStatusPath -> {
                                lifecycleScope.launch {
                                    if (connect()) {
                                        mPhoneNodeWithApp?.id?.let { nodeID ->
                                            sendMessage(
                                                nodeID,
                                                MediaHelper.MediaPlayerConnectPath,
                                                if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                                            )
                                            sendMessage(nodeID, intent.action!!, null)
                                        }
                                    }
                                }
                            }
                            MediaHelper.MediaSetVolumePath -> {
                                lifecycleScope.launch {
                                    if (connect()) {
                                        mPhoneNodeWithApp?.id?.let { nodeID ->
                                            sendMessage(
                                                nodeID,
                                                MediaHelper.MediaPlayerConnectPath,
                                                if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                                            )
                                            sendMessage(
                                                nodeID,
                                                intent.action!!,
                                                intent.getIntExtra(MediaHelper.KEY_VOLUME, 0)
                                                    .intToBytes()
                                            )
                                        }
                                    }
                                }
                            }
                            MediaHelper.MediaBrowserItemsClickPath,
                            MediaHelper.MediaBrowserItemsExtraSuggestedClickPath,
                            MediaHelper.MediaQueueItemsClickPath -> {
                                val id = intent.getStringExtra(MediaHelper.KEY_MEDIAITEM_ID)

                                lifecycleScope.launch {
                                    if (connect()) {
                                        mPhoneNodeWithApp?.id?.let { nodeID ->
                                            sendMessage(
                                                nodeID,
                                                MediaHelper.MediaPlayerConnectPath,
                                                if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                                            )
                                            sendMessage(
                                                nodeID,
                                                intent.action!!,
                                                id!!.stringToBytes()
                                            )
                                        }
                                    }
                                }
                            }
                            MediaHelper.MediaActionsClickPath -> {
                                val id =
                                    intent.getStringExtra(MediaHelper.KEY_MEDIA_ACTIONITEM_ACTION)

                                lifecycleScope.launch {
                                    if (connect()) {
                                        mPhoneNodeWithApp?.id?.let { nodeID ->
                                            sendMessage(
                                                nodeID,
                                                MediaHelper.MediaPlayerConnectPath,
                                                if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                                            )
                                            sendMessage(
                                                nodeID,
                                                intent.action!!,
                                                id!!.stringToBytes()
                                            )
                                        }
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

        binding.mediaViewpager.adapter = MediaFragmentPagerAdapter(this).also {
            mViewPagerAdapter = it
        }

        binding.mediaViewpagerIndicator.dotFadeWhenIdle = false
        binding.mediaViewpagerIndicator.setPager(binding.mediaViewpager)

        binding.retryFab.setOnClickListener {
            lifecycleScope.launch {
                updateConnectionStatus()
                requestPlayerConnect()
                updatePager()
            }
        }

        intentFilter = IntentFilter().apply {
            addAction(ACTION_UPDATECONNECTIONSTATUS)
            addAction(MediaHelper.MediaPlayPath)
            addAction(MediaHelper.MediaPausePath)
            addAction(MediaHelper.MediaPreviousPath)
            addAction(MediaHelper.MediaNextPath)
            addAction(MediaHelper.MediaBrowserItemsClickPath)
            addAction(MediaHelper.MediaBrowserItemsBackPath)
            addAction(MediaHelper.MediaActionsClickPath)
            addAction(MediaHelper.MediaQueueItemsClickPath)
            addAction(MediaHelper.MediaVolumeUpPath)
            addAction(MediaHelper.MediaVolumeDownPath)
            addAction(MediaHelper.MediaVolumeStatusPath)
            addAction(MediaHelper.MediaSetVolumePath)
        }

        mAmbientController = AmbientModeSupport.attach(this)
        mAmbientMode.ambientModeEnabled.value = mAmbientController.isAmbient
    }

    override fun onStart() {
        super.onStart()

        mAmbientMode.ambientModeEnabled.observe(this) { enabled ->
            if (enabled) {
                binding.mediaViewpagerIndicator.visibility = View.INVISIBLE
                binding.mediaViewpager.setCurrentItem(0, false)
            } else {
                binding.mediaViewpagerIndicator.visibility = View.VISIBLE
            }
        }
    }

    private enum class MediaPageType(val value: Int) {
        Player(1),
        CustomControls(2),
        Browser(3),
        Queue(4);

        companion object {
            fun valueOf(value: Int) = MediaPageType.values().firstOrNull() { it.value == value }
        }
    }

    private inner class MediaFragmentPagerAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {
        private val supportedPageTypes = mutableListOf(MediaPageType.Player)

        override fun getItemCount(): Int {
            return supportedPageTypes.size
        }

        override fun createFragment(position: Int): Fragment {
            val type = supportedPageTypes[position]

            return when (type) {
                MediaPageType.Player -> {
                    MediaPlayerControlsFragment()
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

        @SuppressLint("NotifyDataSetChanged")
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

        override fun getItemId(position: Int): Long {
            return if (position >= 0 && position < supportedPageTypes.size) {
                supportedPageTypes[position].value.toLong()
            } else {
                RecyclerView.NO_ID
            }
        }

        override fun containsItem(itemId: Long): Boolean {
            val pageType = MediaPageType.valueOf(itemId.toInt())
            return pageType != null && supportedPageTypes.contains(pageType)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        lifecycleScope.launch {
            when (messageEvent.path) {
                MediaHelper.MediaPlayerConnectPath,
                MediaHelper.MediaPlayerAutoLaunchPath -> {
                    val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                    if (actionStatus == ActionStatus.PERMISSION_DENIED) {
                        CustomConfirmationOverlay()
                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                            .setCustomDrawable(
                                ContextCompat.getDrawable(
                                    this@MediaPlayerActivity,
                                    R.drawable.ws_full_sad
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
                MediaHelper.MediaBrowserItemsClickPath,
                MediaHelper.MediaBrowserItemsExtraSuggestedClickPath -> {
                    val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                    if (actionStatus == ActionStatus.SUCCESS) {
                        binding.mediaViewpager.currentItem = 0
                    }
                }
            }
        }
    }

    private fun showNoPlayersView(show: Boolean) {
        if (!mAmbientController.isAmbient) {
            binding.noplayersView.visibility = if (show) View.VISIBLE else View.GONE
            binding.mediaViewpager.visibility = if (show) View.INVISIBLE else View.VISIBLE
            binding.mediaViewpagerIndicator.visibility = if (show) View.INVISIBLE else View.VISIBLE
        }
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
                        MediaHelper.MediaBrowserItemsPath -> {
                            supportsBrowser = false
                        }
                        MediaHelper.MediaActionsPath -> {
                            supportsCustomActions = false
                        }
                        MediaHelper.MediaQueueItemsPath -> {
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
            MediaHelper.MediaBrowserItemsPath -> {
                supportsBrowser = try {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS)
                    !items.isNullOrEmpty()
                } catch (e: Exception) {
                    Logger.writeLine(Log.ERROR, e)
                    false
                }
            }
            MediaHelper.MediaActionsPath -> {
                supportsCustomActions = try {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS)
                    !items.isNullOrEmpty()
                } catch (e: Exception) {
                    Logger.writeLine(Log.ERROR, e)
                    false
                }
            }
            MediaHelper.MediaQueueItemsPath -> {
                supportsQueue = try {
                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                    val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS)
                    !items.isNullOrEmpty()
                } catch (e: Exception) {
                    Logger.writeLine(Log.ERROR, e)
                    false
                }
            }
        }
    }

    private fun requestPlayerConnect() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    MediaHelper.MediaPlayerConnectPath,
                    if (isAutoLaunch) isAutoLaunch.booleanToBytes() else mMediaPlayerDetails.packageName?.stringToBytes()
                )
            }
        }
    }

    private fun requestPlayerDisconnect() {
        lifecycleScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayerDisconnectPath, null)
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
        override fun onEnterAmbient(ambientDetails: Bundle) {
            super.onEnterAmbient(ambientDetails)

            val isLowBitAmbient =
                ambientDetails.getBoolean(AmbientModeSupport.EXTRA_LOWBIT_AMBIENT, false)
            val doBurnInProtection =
                ambientDetails.getBoolean(AmbientModeSupport.EXTRA_BURN_IN_PROTECTION, false)

            mAmbientMode.isLowBitAmbient.value = isLowBitAmbient
            mAmbientMode.doBurnInProtection.value = doBurnInProtection
            mAmbientMode.ambientModeEnabled.value = true
        }

        override fun onExitAmbient() {
            super.onExitAmbient()
            mAmbientMode.ambientModeEnabled.value = false
        }

        override fun onUpdateAmbient() {
            super.onUpdateAmbient()

            mAmbientMode.ambientModeEnabled.value = true

            LocalBroadcastManager.getInstance(this@MediaPlayerActivity)
                .sendBroadcast(Intent(ACTION_UPDATEAMBIENTMODE))
        }
    }
}