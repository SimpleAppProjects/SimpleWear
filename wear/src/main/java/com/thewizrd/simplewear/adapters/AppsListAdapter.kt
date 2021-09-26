package com.thewizrd.simplewear.adapters

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.helpers.ListAdapterOnClickInterface
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.WearChipButton

class AppsListAdapter : ListAdapter<AppItemViewModel, AppsListAdapter.ViewHolder>(AppItemDiffer()) {
    private var onClickListener: ListAdapterOnClickInterface<AppItemViewModel>? = null

    fun setOnClickListener(onClickListener: ListAdapterOnClickInterface<AppItemViewModel>?) {
        this.onClickListener = onClickListener
    }

    inner class ViewHolder(var mItem: WearChipButton) : RecyclerView.ViewHolder(mItem)

    @SuppressLint("NewApi")  // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // create a new view
        val v = WearChipButton(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        return ViewHolder(v)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val viewModel = getItem(position)
        holder.mItem.setIconDrawable(viewModel.bitmapIcon?.toDrawable(holder.itemView.context.resources))
        holder.mItem.setPrimaryText(viewModel.appLabel)
        holder.mItem.setOnClickListener { v ->
            onClickListener?.onClick(v, viewModel)
        }
    }
}