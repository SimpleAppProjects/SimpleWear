package com.thewizrd.simplewear.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.simplewear.databinding.LayoutListHeaderBinding

class ListHeaderAdapter : RecyclerView.Adapter<ListHeaderAdapter.ViewHolder> {
    var headerText: CharSequence = ""
        set(value) {
            field = value
            notifyItemChanged(0)
        }

    constructor(headerText: CharSequence) : super() {
        this.headerText = headerText
    }

    inner class ViewHolder(val binding: LayoutListHeaderBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutListHeaderBinding.inflate(LayoutInflater.from(parent.context)).apply {
                root.layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.binding.header.text = headerText
    }

    override fun getItemCount(): Int {
        return 1
    }
}