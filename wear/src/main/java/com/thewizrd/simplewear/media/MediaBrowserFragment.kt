package com.thewizrd.simplewear.media

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
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
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.adapters.SpacerAdapter
import com.thewizrd.simplewear.controls.WearChipButton
import com.thewizrd.simplewear.databinding.FragmentBrowserListBinding
import com.thewizrd.simplewear.helpers.CustomScrollingLayoutCallback
import com.thewizrd.simplewear.helpers.SpacerItemDecoration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.collections.ArrayList

class MediaBrowserFragment : LifecycleAwareFragment(), DataClient.OnDataChangedListener {
    private lateinit var binding: FragmentBrowserListBinding
    private lateinit var mBrowserAdapter: MediaBrowserItemsAdapter

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
        binding.listView.addItemDecoration(
            SpacerItemDecoration(
                requireContext().dpToPx(16f).toInt(),
                requireContext().dpToPx(4f).toInt()
            )
        )

        binding.listView.layoutManager =
            WearableLinearLayoutManager(requireContext(), CustomScrollingLayoutCallback())

        mBrowserAdapter = MediaBrowserItemsAdapter()
        binding.listView.adapter = ConcatAdapter(
            SpacerAdapter(requireContext().dpToPx(48f).toInt()),
            mBrowserAdapter,
            SpacerAdapter(requireContext().dpToPx(48f).toInt())
        )

        mBrowserAdapter.setOnClickListener(object : MediaBrowserItemsAdapter.OnClickListener {
            override fun onClick(item: MediaItemModel) {
                if (item.id == MediaHelper.ACTIONITEM_BACK) {
                    LocalBroadcastManager.getInstance(requireContext())
                        .sendBroadcast(Intent(MediaHelper.MediaBrowserItemsBackPath))
                } else {
                    LocalBroadcastManager.getInstance(requireContext())
                        .sendBroadcast(
                            Intent(MediaHelper.MediaBrowserItemsClickPath)
                                .putExtra(MediaHelper.KEY_MEDIAITEM_ID, item.id)
                        )
                }
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
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(requireContext()).addListener(this)

        binding.listView.requestFocus()

        updateBrowserItems()
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

    private class MediaBrowserItemsAdapter :
        ListAdapter<MediaItemModel, MediaBrowserItemsAdapter.ViewHolder>(diffCallback) {
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
                if (model.id == MediaHelper.ACTIONITEM_BACK) {
                    button.setIconResource(R.drawable.ic_baseline_arrow_back_24)
                    button.setPrimaryText(R.string.label_back)
                } else {
                    button.setIconDrawable(model.icon?.toDrawable(button.context.resources))
                    button.setPrimaryText(model.title)
                }
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

    private fun updateBrowserItems() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(requireContext())
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaBrowserItemsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaBrowserItemsPath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updateBrowserItems(dataMap)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private suspend fun updateBrowserItems(dataMap: DataMap) {
        val isRoot = dataMap.getBoolean(MediaHelper.KEY_MEDIAITEM_ISROOT)
        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(if (isRoot) items.size else items.size + 1)
        if (!isRoot) {
            mediaItems.add(MediaItemModel(MediaHelper.ACTIONITEM_BACK))
        }

        for (item in items) {
            val id = item.getString(MediaHelper.KEY_MEDIAITEM_ID)
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

            mediaItems.add(MediaItemModel(id).apply {
                this.icon = icon
                this.title = title
            })
        }

        runWithView {
            showLoading(false)
            mBrowserAdapter.submitList(mediaItems)
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (MediaHelper.MediaBrowserItemsPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateBrowserItems(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            }
        }
    }
}