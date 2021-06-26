package com.thewizrd.simplewear.media

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.databinding.AppItemBinding
import com.thewizrd.simplewear.databinding.FragmentBrowserListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*
import kotlin.collections.ArrayList

class MediaCustomControlsFragment : Fragment(), MessageClient.OnMessageReceivedListener,
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
        binding.listView.isEdgeItemsCenteringEnabled = true
        binding.listView.layoutManager = WearableLinearLayoutManager(requireContext())
        binding.listView.adapter = MediaCustomControlsItemsAdapter().also {
            mCustomControlsAdapter = it
        }

        mCustomControlsAdapter.setOnClickListener(object :
            MediaCustomControlsItemsAdapter.OnClickListener {
            override fun onClick(item: MediaItemModel) {
                LocalBroadcastManager.getInstance(requireContext())
                    .sendBroadcast(
                        Intent(WearableHelper.MediaActionsClickPath)
                            .putExtra(WearableHelper.KEY_MEDIA_ACTIONITEM_ACTION, item.id)
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
        Wearable.getMessageClient(requireContext()).addListener(this)
        Wearable.getDataClient(requireContext()).addListener(this)

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
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.listView.visibility = if (show) View.INVISIBLE else View.VISIBLE
    }

    private class MediaCustomControlsItemsAdapter :
        ListAdapter<MediaItemModel, MediaCustomControlsItemsAdapter.ViewHolder> {
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
                binding.appIcon.setImageBitmap(model.icon)
                binding.appName.text = model.title
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

    private fun updateCustomControls() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(requireContext())
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            WearableHelper.MediaActionsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (WearableHelper.MediaActionsPath == item.uri.path) {
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
        val items = dataMap.getDataMapArrayList(WearableHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(items.size)

        for (item in items) {
            val id = item.getString(WearableHelper.KEY_MEDIA_ACTIONITEM_ACTION)
            val icon = try {
                ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(requireContext()),
                    item.getAsset(WearableHelper.KEY_MEDIA_ACTIONITEM_ICON)
                )
            } catch (e: Exception) {
                null
            }
            val title = item.getString(WearableHelper.KEY_MEDIA_ACTIONITEM_TITLE)

            mediaItems.add(MediaItemModel(id).apply {
                this.icon = icon
                this.title = title
            })
        }

        viewLifecycleOwner.lifecycleScope.launch {
            showLoading(false)
            mCustomControlsAdapter.submitList(mediaItems)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        lifecycleScope.launch {
            if (messageEvent.path == WearableHelper.MediaPlayPath) {
                val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                if (actionStatus == ActionStatus.TIMEOUT) {
                    CustomConfirmationOverlay()
                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                        .setCustomDrawable(
                            ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.ic_full_sad
                            )
                        )
                        .setMessage(getString(R.string.error_playback_failed))
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
                    if (WearableHelper.MediaActionsPath == item.uri.path) {
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