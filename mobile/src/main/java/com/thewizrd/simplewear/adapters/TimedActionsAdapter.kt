package com.thewizrd.simplewear.adapters

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColor
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColorStateList
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.LayoutActionItemBinding
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal class TimedActionsDiffItemCallback : DiffUtil.ItemCallback<TimedAction>() {
    override fun areItemsTheSame(oldItem: TimedAction, newItem: TimedAction): Boolean {
        return oldItem.action.actionType == newItem.action.actionType
    }

    override fun areContentsTheSame(oldItem: TimedAction, newItem: TimedAction): Boolean {
        return oldItem.timeInMillis == newItem.timeInMillis && oldItem.action.actionType == newItem.action.actionType
    }
}

class TimedActionsAdapter : ListAdapter<TimedAction, TimedActionsAdapter.TimedActionViewHolder>(
    TimedActionsDiffItemCallback()
) {
    var selectionTracker: SelectionTracker<Long>? = null

    inner class TimedActionViewHolder(private val binding: LayoutActionItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val itemDetails = object : ItemDetails<Long>() {
            override fun getPosition(): Int = absoluteAdapterPosition
            override fun getSelectionKey(): Long = itemId
            override fun inSelectionHotspot(e: MotionEvent): Boolean {
                return true
            }
        }

        fun bind(action: TimedAction, isSelected: Boolean = false) {
            val model = ActionButtonViewModel(action.action)

            binding.actionTitle.setText(model.actionLabelResId)
            binding.actionState.setText(model.stateLabelResId)
            binding.actionTime.text = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(
                LocalTime.ofInstant(
                    Instant.ofEpochMilli(action.timeInMillis),
                    ZoneId.systemDefault()
                )
            )

            binding.root.isChecked = isSelected
            if (isSelected) {
                binding.actionIcon.setImageResource(R.drawable.ic_check_white_24dp)
                binding.actionIcon.setBackgroundColor(itemView.context.getAttrColor(R.attr.colorPrimaryContainer))
                binding.actionIcon.imageTintList =
                    itemView.context.getAttrColorStateList(R.attr.colorOnPrimaryContainer)
                binding.root.setCardBackgroundColor(itemView.context.getAttrColorStateList(R.attr.colorSurfaceContainerHighest))
            } else {
                binding.actionIcon.setImageResource(model.drawableResId)
                binding.actionIcon.setBackgroundColor(itemView.context.getAttrColor(R.attr.colorPrimaryContainer))
                binding.actionIcon.imageTintList =
                    itemView.context.getAttrColorStateList(R.attr.colorOnPrimaryContainer)
                binding.root.setCardBackgroundColor(itemView.context.getAttrColorStateList(R.attr.colorSurfaceContainer))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimedActionViewHolder {
        val binding = LayoutActionItemBinding.inflate(LayoutInflater.from(parent.context)).apply {
            root.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return TimedActionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimedActionViewHolder, position: Int) {
        holder.bind(getItem(position), selectionTracker?.isSelected(holder.itemId) ?: false)
    }

    override fun getItemId(position: Int): Long {
        return getItem(position).action.actionType.value.toLong()
    }
}