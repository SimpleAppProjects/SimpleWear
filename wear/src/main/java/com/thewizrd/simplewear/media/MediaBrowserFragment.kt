package com.thewizrd.simplewear.media

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.AppItemBinding
import com.thewizrd.simplewear.databinding.FragmentBrowserListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.collections.ArrayList

class MediaBrowserFragment : Fragment(), DataClient.OnDataChangedListener {
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
        binding.listView.isEdgeItemsCenteringEnabled = true
        binding.listView.layoutManager = WearableLinearLayoutManager(requireContext())
        binding.listView.adapter = MediaBrowserItemsAdapter().also {
            mBrowserAdapter = it
        }

        mBrowserAdapter.setOnClickListener(object : MediaBrowserItemsAdapter.OnClickListener {
            override fun onClick(item: MediaItemModel) {
                if (item.id == WearableHelper.ACTIONITEM_BACK) {
                    LocalBroadcastManager.getInstance(requireContext())
                        .sendBroadcast(Intent(WearableHelper.MediaBrowserItemsBackPath))
                } else {
                    LocalBroadcastManager.getInstance(requireContext())
                        .sendBroadcast(
                            Intent(WearableHelper.MediaBrowserItemsClickPath)
                                .putExtra(WearableHelper.KEY_MEDIAITEM_ID, item.id)
                        )
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
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.listView.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private class MediaBrowserItemsAdapter :
        ListAdapter<MediaItemModel, MediaBrowserItemsAdapter.ViewHolder> {
        private var onClickListener: OnClickListener? = null

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

            fun bind(model: MediaItemModel) {
                if (model.id == WearableHelper.ACTIONITEM_BACK) {
                    binding.appIcon.setImageResource(R.drawable.ic_baseline_arrow_back_24)
                    binding.appName.setText(R.string.label_back)
                } else {
                    binding.appIcon.setImageBitmap(model.icon)
                    binding.appName.text = model.title
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return ViewHolder(AppItemBinding.inflate(inflater, parent, false))
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
                            WearableHelper.MediaBrowserItemsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (WearableHelper.MediaBrowserItemsPath == item.uri.path) {
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
        val isRoot = dataMap.getBoolean(WearableHelper.KEY_MEDIAITEM_ISROOT)
        val items = dataMap.getDataMapArrayList(WearableHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(if (isRoot) items.size else items.size + 1)
        if (!isRoot) {
            mediaItems.add(MediaItemModel(WearableHelper.ACTIONITEM_BACK))
        }

        for (item in items) {
            val id = item.getString(WearableHelper.KEY_MEDIAITEM_ID)
            val icon = try {
                ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(requireContext()),
                    item.getAsset(WearableHelper.KEY_MEDIAITEM_ICON)
                )
            } catch (e: Exception) {
                null
            }
            val title = item.getString(WearableHelper.KEY_MEDIAITEM_TITLE)

            mediaItems.add(MediaItemModel(id).apply {
                this.icon = icon
                this.title = title
            })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(false)
            mBrowserAdapter.submitList(mediaItems)
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        lifecycleScope.launch {
            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (WearableHelper.MediaBrowserItemsPath == item.uri.path) {
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