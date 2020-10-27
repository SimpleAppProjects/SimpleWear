package com.thewizrd.simplewear.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.simplewear.controls.MusicPlayerItem
import com.thewizrd.simplewear.controls.MusicPlayerViewModel
import java.util.*

class MusicPlayerListAdapter(activity: Activity) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val mDataset: MutableList<MusicPlayerViewModel>
    private val mActivityContext: Activity
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    // Provide a suitable constructor (depends on the kind of dataset)
    init {
        mDataset = ArrayList()
        mActivityContext = activity
    }

    fun setOnClickListener(onClickListener: RecyclerOnClickListenerInterface?) {
        this.onClickListener = onClickListener
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    internal inner class ViewHolder(var mItem: MusicPlayerItem) : RecyclerView.ViewHolder(mItem) {
        init {
            mItem.setOnClickListener { v -> onClickListener?.onClick(v, adapterPosition) }
        }
    }

    val dataset: List<MusicPlayerViewModel>
        get() = mDataset

    @SuppressLint("NewApi")  // Create new views (invoked by the layout manager)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        // create a new view
        val v = MusicPlayerItem(parent.context)
        return ViewHolder(v)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val vh = holder as ViewHolder
        val viewModel = mDataset[position]
        vh.mItem.updateItem(viewModel)
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return mDataset.size
    }

    fun updateItems(viewModels: List<MusicPlayerViewModel>) {
        mDataset.clear()
        mDataset.addAll(viewModels)
        notifyDataSetChanged()
    }
}