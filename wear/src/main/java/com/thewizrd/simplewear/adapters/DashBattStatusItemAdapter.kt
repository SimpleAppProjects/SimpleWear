package com.thewizrd.simplewear.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColorStateList
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.WearChipButton

class DashBattStatusItemAdapter : RecyclerView.Adapter<DashBattStatusItemAdapter.ViewHolder>() {
    companion object {
        const val ITEM_TYPE = R.drawable.ic_battery_std_white_24dp
    }

    private var itemPosition = RecyclerView.NO_POSITION
    private var recyclerView: RecyclerView? = null

    var isVisible: Boolean = true
        set(value) {
            val oldValue = field
            field = value

            if (oldValue != value) {
                notifyItemChanged(0)
            }
        }

    var isChecked: Boolean = false
        set(value) {
            val oldValue = field
            field = value

            if (oldValue != value) {
                notifyItemChanged(0)
            }
        }

    inner class ViewHolder(private val button: WearChipButton) :
        RecyclerView.ViewHolder(button) {
        fun bind(isChecked: Boolean, isVisible: Boolean) {
            if (!isVisible) {
                button.setIconResource(R.drawable.ic_add_white_24dp)
                button.setIconTint(button.context.getAttrColorStateList(R.attr.colorSurface))
                button.setBackgroundColor(button.context.getAttrColorStateList(R.attr.colorOnSurface))
                button.findViewById<TextView>(R.id.wear_chip_primary_text)?.let {
                    it.setTextColor(button.context.getAttrColorStateList(R.attr.colorSurface))
                    it.setText(R.string.action_add_batt_state)
                }
            } else if (isChecked) {
                button.setIconResource(R.drawable.ic_close_white_24dp)
                button.setIconTint(button.context.getAttrColorStateList(R.attr.colorSurface))
                button.setBackgroundColor(button.context.getAttrColorStateList(R.attr.colorOnSurface))
                button.findViewById<TextView>(R.id.wear_chip_primary_text)?.let {
                    it.setTextColor(button.context.getAttrColorStateList(R.attr.colorSurface))
                    it.setText(R.string.action_remove_batt_state)
                }
            } else {
                button.setIconTint(button.context.getAttrColorStateList(R.attr.colorOnSurface))
                button.setIconResource(R.drawable.ic_battery_std_white_24dp)
                button.setBackgroundColor(
                    ContextCompat.getColor(
                        button.context,
                        R.color.buttonDisabled
                    )
                )
                button.findViewById<TextView>(R.id.wear_chip_primary_text)?.let {
                    it.setTextColor(
                        ContextCompat.getColor(
                            it.context,
                            R.color.wear_chip_primary_text_color
                        )
                    )
                    it.setText(R.string.title_batt_state)
                }
            }

            button.requestFocus()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(WearChipButton(parent.context).apply {
            setIconResource(R.drawable.ic_battery_std_white_24dp)
            setText(R.string.title_batt_state)
            isCheckable = false
        })
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        itemPosition = holder.bindingAdapterPosition
        holder.bind(isChecked, isVisible)

        holder.itemView.setOnClickListener {
            if (!isVisible) {
                isVisible = true
                isChecked = false
            } else if (isChecked) {
                isVisible = false
                isChecked = false
            } else {
                isChecked = true
            }
        }
    }

    override fun getItemCount(): Int {
        return 1
    }

    override fun getItemViewType(position: Int): Int {
        return ITEM_TYPE
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        this.recyclerView = null
    }

    fun getSelection(): View? {
        if (itemPosition >= 0) {
            return recyclerView?.findViewHolderForLayoutPosition(itemPosition)?.itemView
        }

        return null
    }
}