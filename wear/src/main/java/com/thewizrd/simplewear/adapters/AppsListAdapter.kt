package com.thewizrd.simplewear.adapters

import android.annotation.SuppressLint
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.simplewear.controls.AppItem
import com.thewizrd.simplewear.controls.AppItemViewModel

class AppsListAdapter : ListAdapter<AppItemViewModel, RecyclerView.ViewHolder>(AppItemDiffer()) {
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    fun setOnClickListener(onClickListener: RecyclerOnClickListenerInterface?) {
        this.onClickListener = onClickListener
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    internal inner class ViewHolder(var mItem: AppItem) : RecyclerView.ViewHolder(mItem) {
        init {
            mItem.setOnClickListener { v -> onClickListener?.onClick(v, adapterPosition) }
        }
    }

    @SuppressLint("NewApi")  // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // create a new view
        val v = AppItem(parent.context)
        return ViewHolder(v)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val vh = holder as ViewHolder
        val viewModel = getItem(position)
        vh.mItem.updateItem(viewModel)
    }
}