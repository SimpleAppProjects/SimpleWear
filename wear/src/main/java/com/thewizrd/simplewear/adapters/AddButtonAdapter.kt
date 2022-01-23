package com.thewizrd.simplewear.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.LayoutDashAddButtonBinding

class AddButtonAdapter : RecyclerView.Adapter<AddButtonAdapter.ViewHolder>() {
    companion object {
        const val ITEM_TYPE = R.drawable.ic_add_white_24dp
    }

    private var onClickListener: View.OnClickListener? = null

    fun setOnClickListener(listener: View.OnClickListener?) {
        onClickListener = listener
    }

    inner class ViewHolder(binding: LayoutDashAddButtonBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val recyclerView = parent as RecyclerView
        val viewLayoutMgr = recyclerView.layoutManager
        val viewParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val binding = LayoutDashAddButtonBinding.inflate(LayoutInflater.from(parent.context))

        if (viewLayoutMgr is GridLayoutManager) {
            val buttonSize =
                parent.context.resources.getDimensionPixelSize(R.dimen.tile_action_button_size)
            val horizPadding = runCatching {
                val spanCount = viewLayoutMgr.spanCount
                val viewWidth = parent.getMeasuredWidth() - parent.paddingStart - parent.paddingEnd
                val colWidth = viewWidth / spanCount
                colWidth - buttonSize
            }.getOrNull() ?: 0
            val vertPadding = parent.getContext().dpToPx(6f).toInt()

            viewParams.setMargins(horizPadding / 2, vertPadding, horizPadding / 2, vertPadding)

            binding.root.layoutParams = viewParams
        }

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.setOnClickListener {
            onClickListener?.onClick(it)
        }
    }

    override fun getItemCount(): Int {
        return 1
    }

    override fun getItemViewType(position: Int): Int {
        return ITEM_TYPE
    }
}