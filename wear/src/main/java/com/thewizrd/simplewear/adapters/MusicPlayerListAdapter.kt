package com.thewizrd.simplewear.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.helpers.ContextUtils.dpToPx
import com.thewizrd.shared_resources.helpers.ListAdapterOnClickInterface
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.WearChipButton
import com.thewizrd.simplewear.databinding.LayoutListHeaderBinding

class MusicPlayerListAdapter :
    ListAdapter<AppItemViewModel, RecyclerView.ViewHolder>(AppItemDiffer()) {
    companion object {
        private const val ITEMTYPE_HEADER = -1
        private const val ITEMTYPE_APPITEM = 0
    }

    private var onClickListener: ListAdapterOnClickInterface<AppItemViewModel>? = null

    fun setOnClickListener(onClickListener: ListAdapterOnClickInterface<AppItemViewModel>?) {
        this.onClickListener = onClickListener
    }

    inner class ViewHolder(var mItem: WearChipButton) : RecyclerView.ViewHolder(mItem)
    inner class HeaderViewHolder(val binding: LayoutListHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == ITEMTYPE_HEADER) {
            HeaderViewHolder(LayoutListHeaderBinding.inflate(LayoutInflater.from(parent.context))).apply {
                binding.root.layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        } else {
            // create a new view
            val v = WearChipButton(parent.context).apply {
                val height = parent.context.dpToPx(52f)
                minHeight = height.toInt()
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            ViewHolder(v)
        }
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.binding.header.setText(R.string.action_apps)
        } else if (holder is ViewHolder) {
            val viewModel = getItem(position - 1)
            if (viewModel.bitmapIcon != null) {
                holder.mItem.setIconDrawable(viewModel.bitmapIcon?.toDrawable(holder.itemView.context.resources))
            } else {
                holder.mItem.setIconResource(R.drawable.ic_play_circle_filled_white_24dp)
            }
            holder.mItem.setPrimaryText(viewModel.appLabel)
            holder.mItem.setOnClickListener { v ->
                onClickListener?.onClick(v, viewModel)
            }
        }
    }

    override fun getItemCount(): Int {
        val dataCount = super.getItemCount()
        return if (dataCount > 0) {
            dataCount + 1
        } else {
            dataCount
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            ITEMTYPE_HEADER
        } else {
            ITEMTYPE_APPITEM
        }
    }
}