package com.thewizrd.simplewear.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.databinding.MediaplayerlistItemBinding

class MediaPlayerListAdapter : ListAdapter<AppItemViewModel, MediaPlayerListAdapter.ViewHolder> {
    private val CHECKED_PAYLOAD = -1

    private val selectedItems: MutableSet<String>

    constructor() : super(AppItemDiffer()) {
        this.selectedItems = HashSet()
    }

    constructor(selectedItems: Collection<String>) : super(AppItemDiffer()) {
        this.selectedItems = HashSet(selectedItems)
    }

    inner class ViewHolder(val binding: MediaplayerlistItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(model: AppItemViewModel, onlyChecked: Boolean = false) {
            if (!onlyChecked) {
                binding.playerlistItem.text = model.appLabel
            }
            binding.playerlistItem.isChecked = selectedItems.contains(model.packageName)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(MediaplayerlistItemBinding.inflate(LayoutInflater.from(parent.context)))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {}

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        val onlyChecked = payloads.firstOrNull() == CHECKED_PAYLOAD

        val viewModel = getItem(position)
        holder.bind(viewModel, onlyChecked)

        if (!onlyChecked) {
            holder.itemView.setOnClickListener {
                holder.binding.playerlistItem.toggle()
                if (holder.binding.playerlistItem.isChecked) {
                    selectedItems.add(viewModel.packageName!!)
                } else {
                    selectedItems.remove(viewModel.packageName)
                }
            }
        }
    }

    fun getSelectedItems(): Set<String> {
        return selectedItems.toSet()
    }

    fun setSelectedItems(items: Collection<String>) {
        selectedItems.clear()
        selectedItems.addAll(items)
    }

    fun clearSelections() {
        selectedItems.clear()
        notifyItemRangeChanged(0, itemCount, CHECKED_PAYLOAD)
    }
}