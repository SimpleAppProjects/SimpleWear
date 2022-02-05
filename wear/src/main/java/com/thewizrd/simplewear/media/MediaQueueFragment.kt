package com.thewizrd.simplewear.media

import android.content.Intent
import android.media.session.MediaSession
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
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
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.lifecycle.LifecycleAwareFragment
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColor
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.adapters.SpacerAdapter
import com.thewizrd.simplewear.controls.WearChipButton
import com.thewizrd.simplewear.databinding.FragmentBrowserListBinding
import com.thewizrd.simplewear.helpers.CustomScrollingLayoutCallback
import com.thewizrd.simplewear.helpers.SimpleRecyclerViewAdapterObserver
import com.thewizrd.simplewear.helpers.SpacerItemDecoration
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.math.roundToInt

class MediaQueueFragment : LifecycleAwareFragment(), DataClient.OnDataChangedListener {
    private lateinit var binding: FragmentBrowserListBinding
    private lateinit var mQueueItemsAdapter: MediaQueueItemsAdapter
    private lateinit var mLayoutManager: WearableLinearLayoutManager

    private var deleteJob: Job? = null

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
            WearableLinearLayoutManager(requireContext(), CustomScrollingLayoutCallback()).also {
                mLayoutManager = it
            }
        mQueueItemsAdapter = MediaQueueItemsAdapter()
        binding.listView.adapter = ConcatAdapter(
            SpacerAdapter(requireContext().dpToPx(48f).toInt()),
            mQueueItemsAdapter,
            SpacerAdapter(requireContext().dpToPx(48f).toInt())
        )
        binding.listView.addItemDecoration(
            SpacerItemDecoration(
                requireContext().dpToPx(16f).toInt(),
                requireContext().dpToPx(4f).toInt()
            )
        )

        mQueueItemsAdapter.setOnClickListener(object : MediaQueueItemsAdapter.OnClickListener {
            override fun onClick(item: MediaItemModel) {
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(
                        Intent(MediaHelper.MediaQueueItemsClickPath)
                            .putExtra(MediaHelper.KEY_MEDIAITEM_ID, item.id)
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
        Wearable.getDataClient(requireContext()).addListener(this)

        binding.listView.requestFocus()

        updateQueueItems()
    }

    override fun onPause() {
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

    private class MediaQueueItemsAdapter :
        ListAdapter<MediaItemModel, MediaQueueItemsAdapter.ViewHolder>(diffCallback) {
        private var onClickListener: OnClickListener? = null
        var mActiveQueueItemId: Long = MediaSession.QueueItem.UNKNOWN_ID.toLong()

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
            private val inActiveSpan =
                ForegroundColorSpan(button.context.getAttrColor(R.attr.colorOnSurfaceVariant))

            fun bind(model: MediaItemModel, isActive: Boolean) {
                button.setIconDrawable(model.icon?.toDrawable(button.context.resources))
                bindTitle(model, isActive)
                button.setOnClickListener {
                    onClickListener?.onClick(model)
                }
            }

            fun bindTitle(model: MediaItemModel, isActive: Boolean) {
                button.setPrimaryText(if (isActive) {
                    model.title
                } else {
                    SpannableString(model.title).apply {
                        setSpan(
                            inActiveSpan,
                            0,
                            this.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                })
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
            val item = getItem(position)
            holder.bind(item, item.id.toLong() == mActiveQueueItemId)
        }

        override fun onBindViewHolder(
            holder: ViewHolder,
            position: Int,
            payloads: MutableList<Any>
        ) {
            if (payloads.isNullOrEmpty()) {
                super.onBindViewHolder(holder, position, payloads)
            } else {
                val item = getItem(position)
                holder.bindTitle(item, item.id.toLong() == mActiveQueueItemId)
            }
        }

        interface OnClickListener {
            fun onClick(item: MediaItemModel)
        }
    }

    private fun updateQueueItems() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(requireContext())
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaQueueItemsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaQueueItemsPath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updateQueueItems(dataMap, true)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private suspend fun updateQueueItems(dataMap: DataMap, scrollToActive: Boolean = false) {
        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(items.size)

        for (item in items) {
            val id = item.getLong(MediaHelper.KEY_MEDIAITEM_ID)
            val icon = item.getAsset(MediaHelper.KEY_MEDIAITEM_ICON)?.let {
                try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(requireContext()),
                        it
                    )
                } catch (e: Exception) {
                    null
                }
            }
            val title = item.getString(MediaHelper.KEY_MEDIAITEM_TITLE)

            mediaItems.add(MediaItemModel(id.toString()).apply {
                this.icon = icon
                this.title = title
            })
        }

        val newQueueId = dataMap.getLong(MediaHelper.KEY_MEDIA_ACTIVEQUEUEITEM_ID, -1)

        runWithView {
            showLoading(false)
            binding.listView.requestFocus()

            if (newQueueId != mQueueItemsAdapter.mActiveQueueItemId) {
                mQueueItemsAdapter.mActiveQueueItemId = newQueueId
                mQueueItemsAdapter.notifyItemRangeChanged(
                    0,
                    mQueueItemsAdapter.itemCount,
                    newQueueId
                )
            }

            if (scrollToActive && mQueueItemsAdapter.mActiveQueueItemId >= 0) {
                // Register scroller
                mQueueItemsAdapter.registerAdapterDataObserver(object :
                    SimpleRecyclerViewAdapterObserver() {
                    override fun onChanged() {
                        val position = mQueueItemsAdapter.currentList.indexOfFirst {
                            it.id.toLong() == mQueueItemsAdapter.mActiveQueueItemId
                        }

                        if (position >= 0 && mQueueItemsAdapter.itemCount > position) {
                            mQueueItemsAdapter.unregisterAdapterDataObserver(this)

                            binding.listView.viewTreeObserver.addOnGlobalLayoutListener(object :
                                ViewTreeObserver.OnGlobalLayoutListener {
                                override fun onGlobalLayout() {
                                    binding.listView.viewTreeObserver.removeOnGlobalLayoutListener(
                                        this
                                    )
                                    binding.listView.postOnAnimation {
                                        mLayoutManager.findViewByPosition(0)?.let {
                                            val containerHeight = binding.listView.measuredHeight
                                            val totalViewsInContainer =
                                                containerHeight / it.measuredHeight
                                            mLayoutManager.scrollToPositionWithOffset(
                                                position + 1,
                                                it.measuredHeight * (totalViewsInContainer / 2)
                                            )
                                        }
                                    }
                                }
                            })
                        }
                    }
                })
            }

            mQueueItemsAdapter.submitList(mediaItems)
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (MediaHelper.MediaQueueItemsPath == item.uri.path) {
                        deleteJob?.cancel()
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateQueueItems(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                } else if (event.type == DataEvent.TYPE_DELETED) {
                    val item = event.dataItem
                    if (MediaHelper.MediaQueueItemsPath == item.uri.path) {
                        deleteJob = lifecycleScope.launch {
                            delay(1000)

                            if (!isActive) return@launch

                            mQueueItemsAdapter.mActiveQueueItemId = -1
                            mQueueItemsAdapter.notifyItemRangeChanged(
                                0,
                                mQueueItemsAdapter.itemCount,
                                -1
                            )
                        }
                    }
                }
            }
        }
    }
}