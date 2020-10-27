package com.thewizrd.simplewear.sleeptimer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.MusicPlayerViewModel
import com.thewizrd.simplewear.databinding.MusicplayerItemSleeptimerBinding

class PlayerListAdapter(owner: ViewModelStoreOwner) : RecyclerView.Adapter<PlayerListAdapter.ViewHolder>() {
    private val mDiffer: AsyncListDiffer<MusicPlayerViewModel>
    private var mCheckedPosition = RecyclerView.NO_POSITION
    private var onClickListener: RecyclerOnClickListenerInterface? = null

    private val selectedPlayer: SelectedPlayerViewModel

    private val diffCallback: DiffUtil.ItemCallback<MusicPlayerViewModel> = object : DiffUtil.ItemCallback<MusicPlayerViewModel>() {
        override fun areItemsTheSame(oldItem: MusicPlayerViewModel, newItem: MusicPlayerViewModel): Boolean {
            return oldItem.packageName == newItem.packageName && oldItem.activityName == newItem.activityName
        }

        override fun areContentsTheSame(oldItem: MusicPlayerViewModel, newItem: MusicPlayerViewModel): Boolean {
            return oldItem == newItem
        }
    }

    init {
        mDiffer = AsyncListDiffer(this, diffCallback)
        selectedPlayer = ViewModelProvider(owner, ViewModelProvider.NewInstanceFactory())
                .get(SelectedPlayerViewModel::class.java)
    }

    private object Payload {
        var RADIOBUTTON_UPDATE = 0
    }

    fun setOnClickListener(listener: RecyclerOnClickListenerInterface?) {
        onClickListener = listener
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val binding = MusicplayerItemSleeptimerBinding.bind(itemView)

        fun bindModel(viewModel: MusicPlayerViewModel) {
            if (viewModel.bitmapIcon == null) {
                binding.playerIcon.setImageResource(R.drawable.ic_play_circle_filled_white_24dp)
            } else {
                binding.playerIcon.setImageBitmap(viewModel.bitmapIcon)
            }
            binding.playerName.text = viewModel.appLabel
        }

        fun updateRadioButtom() {
            if (mCheckedPosition == RecyclerView.NO_POSITION) {
                binding.radioButton.isChecked = false
            } else {
                binding.radioButton.isChecked = mCheckedPosition == adapterPosition
            }

            val clickListener = View.OnClickListener {
                val oldPosition = mCheckedPosition
                if (mCheckedPosition != adapterPosition) {
                    mCheckedPosition = adapterPosition
                    notifyItemChanged(oldPosition, Payload.RADIOBUTTON_UPDATE)
                    notifyItemChanged(mCheckedPosition, Payload.RADIOBUTTON_UPDATE)
                } else {
                    // uncheck
                    mCheckedPosition = RecyclerView.NO_POSITION
                    notifyItemChanged(oldPosition, Payload.RADIOBUTTON_UPDATE)
                }

                if (selectedItem != null) {
                    selectedPlayer.setKey(selectedItem!!.key)
                } else {
                    selectedPlayer.setKey(null)
                }

                onClickListener?.onClick(itemView, adapterPosition)
            }
            itemView.setOnClickListener(clickListener)
            binding.radioButton.setOnClickListener(clickListener)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.musicplayer_item_sleeptimer, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        return mDiffer.currentList.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bindModel(mDiffer.currentList[position])
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        val radioBtnUpdateOnly = if (payloads.isNotEmpty()) {
            payloads[0] == Payload.RADIOBUTTON_UPDATE
        } else {
            false
        }

        if (!radioBtnUpdateOnly) {
            super.onBindViewHolder(holder, position, payloads)
        }

        holder.updateRadioButtom()
    }

    fun updateItems(dataset: List<MusicPlayerViewModel>) {
        val currentPref = selectedPlayer.key.value
        var item: MusicPlayerViewModel? = null

        for (it in dataset) {
            if (it.key != null && it.key == currentPref) {
                item = it
                break
            }
        }

        mCheckedPosition = if (item != null) {
            dataset.indexOf(item)
        } else {
            RecyclerView.NO_POSITION
        }

        mDiffer.submitList(dataset)
    }

    val selectedItem: MusicPlayerViewModel?
        get() {
            if (mCheckedPosition != RecyclerView.NO_POSITION) {
                return mDiffer.currentList[mCheckedPosition]
            }

            return null
        }
}