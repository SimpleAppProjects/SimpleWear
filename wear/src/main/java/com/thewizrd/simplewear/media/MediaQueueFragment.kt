package com.thewizrd.simplewear.media

import android.content.Intent
import android.media.session.MediaSession
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.TypefaceSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.databinding.AppItemBinding
import com.thewizrd.simplewear.databinding.FragmentBrowserListBinding
import com.thewizrd.simplewear.helpers.SimpleRecyclerViewAdapterObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.collections.ArrayList

class MediaQueueFragment : Fragment(), DataClient.OnDataChangedListener {
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
        binding.listView.isEdgeItemsCenteringEnabled = true
        binding.listView.layoutManager = WearableLinearLayoutManager(requireContext()).also {
            mLayoutManager = it
        }
        binding.listView.adapter = MediaQueueItemsAdapter().also {
            mQueueItemsAdapter = it
        }

        mQueueItemsAdapter.setOnClickListener(object : MediaQueueItemsAdapter.OnClickListener {
            override fun onClick(item: MediaItemModel) {
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(
                        Intent(WearableHelper.MediaQueueItemsClickPath)
                            .putExtra(WearableHelper.KEY_MEDIAITEM_ID, item.id)
                    )
            }
        })

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(requireContext()).addListener(this)

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
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.listView.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private class MediaQueueItemsAdapter :
        ListAdapter<MediaItemModel, MediaQueueItemsAdapter.ViewHolder> {
        private var onClickListener: OnClickListener? = null
        var mActiveQueueItemId: Long = MediaSession.QueueItem.UNKNOWN_ID.toLong()

        constructor() : super(diffCallback)

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

        inner class ViewHolder(val binding: AppItemBinding) :
            RecyclerView.ViewHolder(binding.root) {
            init {
                binding.root.setOnClickListener {
                    onClickListener?.onClick(getItem(adapterPosition))
                }
            }

            fun bind(model: MediaItemModel, isActive: Boolean) {
                binding.appIcon.setImageBitmap(model.icon)
                bindTitle(model, isActive)
            }

            fun bindTitle(model: MediaItemModel, isActive: Boolean) {
                binding.appName.text = if (isActive) {
                    SpannableString(model.title).apply {
                        setSpan(
                            TypefaceSpan("sans-serif-medium"),
                            0,
                            this.length,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                } else {
                    model.title
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(AppItemBinding.inflate(inflater, parent, false))
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
                            WearableHelper.MediaQueueItemsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (WearableHelper.MediaQueueItemsPath == item.uri.path) {
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
        val items = dataMap.getDataMapArrayList(WearableHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(items.size)

        for (item in items) {
            val id = item.getLong(WearableHelper.KEY_MEDIAITEM_ID)
            val icon = try {
                ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(requireContext()),
                    item.getAsset(WearableHelper.KEY_MEDIAITEM_ICON)
                )
            } catch (e: Exception) {
                null
            }
            val title = item.getString(WearableHelper.KEY_MEDIAITEM_TITLE)

            mediaItems.add(MediaItemModel(id.toString()).apply {
                this.icon = icon
                this.title = title
            })
        }

        val newQueueId = dataMap.getLong(WearableHelper.KEY_MEDIA_ACTIVEQUEUEITEM_ID, -1)

        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(false)

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
                                        val view = mLayoutManager.findViewByPosition(0)!!
                                        val containerHeight = binding.listView.measuredHeight
                                        val totalViewsInContainer =
                                            containerHeight / view.measuredHeight
                                        mLayoutManager.scrollToPositionWithOffset(
                                            position + 1,
                                            view.measuredHeight * (totalViewsInContainer / 2)
                                        )
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
                    if (WearableHelper.MediaQueueItemsPath == item.uri.path) {
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
                    if (WearableHelper.MediaQueueItemsPath == item.uri.path) {
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