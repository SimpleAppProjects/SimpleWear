package com.thewizrd.simplewear.adapters

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.helpers.ContextUtils.dpToPx
import com.thewizrd.shared_resources.helpers.ListAdapterOnClickInterface
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.WearChipButton

class MusicPlayerListAdapter :
    ListAdapter<AppItemViewModel, MusicPlayerListAdapter.ViewHolder>(AppItemDiffer()) {
    private var onClickListener: ListAdapterOnClickInterface<AppItemViewModel>? = null

    fun setOnClickListener(onClickListener: ListAdapterOnClickInterface<AppItemViewModel>?) {
        this.onClickListener = onClickListener
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    inner class ViewHolder(var mItem: WearChipButton) : RecyclerView.ViewHolder(mItem)

    @SuppressLint("NewApi")  // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // create a new view
        val v = WearChipButton(parent.context).apply {
            val height = parent.context.dpToPx(52f)
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return ViewHolder(v)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val viewModel = getItem(position)
        if (viewModel.bitmapIcon != null) {
            holder.mItem.setIconDrawable(viewModel.bitmapIcon?.toDrawable(holder.itemView.context.resources))
        } else {
            holder.mItem.setIconResource(R.drawable.ic_play_circle_filled_white_24dp)
        }
        holder.mItem.setPrimaryText(viewModel.appLabel)
        holder.mItem.setOnClickListener { v ->
            onClickListener?.onClick(v, position, viewModel)
        }
    }
}