package com.thewizrd.simplewear.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColorStateList
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.databinding.LayoutDashButtonBinding

class TileActionAdapter : ListAdapter<ActionButtonViewModel, TileActionAdapter.ViewHolder>(
    ActionButtonViewModel.DIFF_CALLBACK
) {
    private var checkedPosition = RecyclerView.NO_POSITION
    private var recyclerView: RecyclerView? = null

    init {
        setHasStableIds(true)
    }

    inner class ViewHolder(private val binding: LayoutDashButtonBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(model: ActionButtonViewModel, isChecked: Boolean) {
            if (isChecked) {
                binding.button.setImageResource(R.drawable.ic_close_white_24dp)
                ImageViewCompat.setImageTintList(
                    binding.button,
                    itemView.context.getAttrColorStateList(R.attr.colorSurface)
                )
                ViewCompat.setBackgroundTintList(
                    binding.button,
                    itemView.context.getAttrColorStateList(R.attr.colorOnSurface)
                )
                itemView.requestFocus()
            } else {
                binding.button.setImageResource(model.drawableID)
                ImageViewCompat.setImageTintList(
                    binding.button,
                    itemView.context.getAttrColorStateList(R.attr.colorOnSurface)
                )
                ViewCompat.setBackgroundTintList(binding.button, null)
                itemView.clearFocus()
            }
        }
    }

    var onLongClickListener: ((RecyclerView.ViewHolder) -> Unit)? = null
    var onListChanged: ((List<ActionButtonViewModel>) -> Unit)? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val recyclerView = parent as RecyclerView
        val viewLayoutMgr = recyclerView.layoutManager
        val viewParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val binding = LayoutDashButtonBinding.inflate(LayoutInflater.from(parent.context))

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
        val isChecked = position == checkedPosition
        holder.bind(getItem(position), isChecked)

        holder.itemView.setOnClickListener {
            Log.d("TileAdapter", "onclick $position")

            if (isChecked) {
                checkedPosition = RecyclerView.NO_POSITION
                // remove item
                removeAction(holder.bindingAdapterPosition)
            } else {
                val oldPosition = checkedPosition
                checkedPosition = holder.bindingAdapterPosition

                if (oldPosition >= 0) {
                    notifyItemChanged(oldPosition)
                }
                notifyItemChanged(checkedPosition)
            }
        }
        holder.itemView.setOnLongClickListener {
            onLongClickListener?.invoke(holder)
            true
        }
    }

    private fun removeAction(position: Int) {
        val items = currentList.toMutableList()
        items.removeAt(position)
        submitList(items)
    }

    fun removeAction(action: Actions) {
        val items = currentList.toMutableList()
        items.removeIf { it.actionType == action }
        submitList(items)
    }

    fun addAction(action: Actions) {
        val items = currentList.toMutableList()
        items.add(ActionButtonViewModel.getViewModelFromAction(action))
        submitList(items)
    }

    fun submitActions(actions: List<Actions>?) {
        submitList(actions?.map {
            ActionButtonViewModel.getViewModelFromAction(it)
        } ?: emptyList())
    }

    fun getActions(): List<Actions> {
        return currentList.map { it.actionType }
    }

    override fun getItemViewType(position: Int): Int {
        return R.layout.layout_dash_button
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).actionType.value.toLong()
    }

    override fun onCurrentListChanged(
        previousList: MutableList<ActionButtonViewModel>,
        currentList: MutableList<ActionButtonViewModel>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        onListChanged?.invoke(currentList)
    }

    fun clearSelection() {
        val oldPosition = checkedPosition
        checkedPosition = RecyclerView.NO_POSITION

        if (oldPosition >= 0) {
            notifyItemChanged(oldPosition)
        }
    }

    fun getSelection(): View? {
        if (checkedPosition >= 0) {
            return recyclerView?.findViewHolderForLayoutPosition(checkedPosition)?.itemView
        }

        return null
    }
}