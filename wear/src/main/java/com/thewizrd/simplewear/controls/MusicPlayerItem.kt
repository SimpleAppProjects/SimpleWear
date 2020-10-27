package com.thewizrd.simplewear.controls

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.MusicplayerItemBinding

class MusicPlayerItem : LinearLayout {
    private lateinit var binding: MusicplayerItemBinding

    constructor(context: Context) : super(context) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(context)
    }

    private fun initialize(context: Context) {
        val inflater = LayoutInflater.from(context)
        binding = MusicplayerItemBinding.inflate(inflater, this, true)
    }

    fun updateItem(viewModel: MusicPlayerViewModel) {
        if (viewModel.bitmapIcon != null) binding.playerIcon.setImageBitmap(viewModel.bitmapIcon) else binding.playerIcon.setImageResource(R.drawable.ic_play_circle_filled_white_24dp)
        binding.playerName.text = viewModel.appLabel
    }
}