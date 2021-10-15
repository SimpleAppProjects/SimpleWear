package com.thewizrd.simplewear.media

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.lifecycle.LifecycleAwareFragment
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.adapters.SpacerAdapter
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.controls.WearChipButton
import com.thewizrd.simplewear.databinding.FragmentBrowserListBinding
import com.thewizrd.simplewear.helpers.CustomScrollingLayoutCallback
import com.thewizrd.simplewear.helpers.SpacerItemDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt

class MediaCustomControlsFragment : LifecycleAwareFragment(), MessageClient.OnMessageReceivedListener,
    DataClient.OnDataChangedListener {
    private lateinit var binding: FragmentBrowserListBinding
    private lateinit var mCustomControlsAdapter: MediaCustomControlsItemsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentBrowserListBinding.inflate(inflater, container, false)

        binding.listView.setHasFixedSize(true)
        binding.listView.isEdgeItemsCenteringEnabled = false
        binding.listView.layoutManager =
            WearableLinearLayoutManager(requireContext(), CustomScrollingLayoutCallback())
        mCustomControlsAdapter = MediaCustomControlsItemsAdapter()
        binding.listView.adapter = ConcatAdapter(
            SpacerAdapter(requireContext().dpToPx(48f).toInt()),
            mCustomControlsAdapter,
            SpacerAdapter(requireContext().dpToPx(48f).toInt())
        )
        binding.listView.addItemDecoration(
            SpacerItemDecoration(
                requireContext().dpToPx(16f).toInt(),
                requireContext().dpToPx(4f).toInt()
            )
        )

        mCustomControlsAdapter.setOnClickListener(object :
            MediaCustomControlsItemsAdapter.OnClickListener {
            override fun onClick(item: MediaItemModel) {
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(
                        Intent(MediaHelper.MediaActionsClickPath)
                            .putExtra(MediaHelper.KEY_MEDIA_ACTIONITEM_ACTION, item.id)
                    )
            }
        })

        binding.listView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                binding.timeText.apply {
                    translationY = -recyclerView.computeVerticalScrollOffset().toFloat()
                }
            }
        })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setOnGenericMotionListener { v, event ->
            if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
                // Don't forget the negation here
                val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                        ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(v.context), v.context
                        )

                // Swap these axes if you want to do horizontal scrolling instead
                binding.listView.scrollBy(0, delta.roundToInt())

                return@setOnGenericMotionListener true
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(requireContext()).addListener(this)
        Wearable.getDataClient(requireContext()).addListener(this)

        binding.listView.requestFocus()

        updateCustomControls()
    }

    override fun onPause() {
        Wearable.getMessageClient(requireContext()).removeListener(this)
        Wearable.getDataClient(requireContext()).removeListener(this)
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            binding.progressBar.show()
        } else {
            binding.progressBar.hide()
        }
        binding.listView.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private class MediaCustomControlsItemsAdapter() :
        ListAdapter<MediaItemModel, MediaCustomControlsItemsAdapter.ViewHolder>(diffCallback) {
        private var onClickListener: OnClickListener? = null

        companion object {
            private val diffCallback = object : DiffUtil.ItemCallback<MediaItemModel>() {
                override fun areItemsTheSame(
                    oldItem: MediaItemModel,
                    newItem: MediaItemModel
                ): Boolean {
                    return Objects.equals(oldItem.id, newItem.id)
                }

                override fun areContentsTheSame(
                    oldItem: MediaItemModel,
                    newItem: MediaItemModel
                ): Boolean {
                    return Objects.equals(oldItem, newItem)
                }
            }
        }

        fun setOnClickListener(listener: OnClickListener?) {
            this.onClickListener = listener
        }

        inner class ViewHolder(val button: WearChipButton) :
            RecyclerView.ViewHolder(button) {

            fun bind(model: MediaItemModel) {
                button.setIconDrawable(model.icon?.toDrawable(button.context.resources)?.let {
                    it.mutate().apply {
                        setTint(Color.WHITE)
                    }
                })
                button.setPrimaryText(model.title)
                button.setOnClickListener {
                    onClickListener?.onClick(model)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(WearChipButton(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            })
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        interface OnClickListener {
            fun onClick(item: MediaItemModel)
        }
    }

    private fun updateCustomControls() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(requireContext())
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaActionsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaActionsPath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updateCustomControls(dataMap)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private suspend fun updateCustomControls(dataMap: DataMap) {
        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(items.size)

        for (item in items) {
            val id = item.getString(MediaHelper.KEY_MEDIA_ACTIONITEM_ACTION)
            val icon = item.getAsset(MediaHelper.KEY_MEDIA_ACTIONITEM_ICON)?.let {
                try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(requireContext()),
                        it
                    )
                } catch (e: Exception) {
                    null
                }
            }
            val title = item.getString(MediaHelper.KEY_MEDIA_ACTIONITEM_TITLE)

            mediaItems.add(MediaItemModel(id).apply {
                this.icon = icon
                this.title = title
            })
        }

        runWithView {
            showLoading(false)
            mCustomControlsAdapter.submitList(mediaItems)
            binding.listView.requestFocus()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        lifecycleScope.launch {
            if (messageEvent.path == MediaHelper.MediaPlayPath) {
                val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                if (actionStatus == ActionStatus.TIMEOUT) {
                    CustomConfirmationOverlay()
                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                        .setCustomDrawable(R.drawable.ws_full_sad)
                        .setMessage(R.string.error_playback_failed)
                        .showAbove(binding.root)
                }
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (MediaHelper.MediaActionsPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateCustomControls(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            }
        }
    }
}