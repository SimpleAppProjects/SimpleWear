package com.thewizrd.simplewear.controls

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.AppItemBinding

class AppItem : LinearLayout {
    private lateinit var binding: AppItemBinding

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
        binding = AppItemBinding.inflate(inflater, this, true)
    }

    fun updateItem(viewModel: AppItemViewModel) {
        if (viewModel.bitmapIcon != null) {
            binding.appIcon.setImageBitmap(viewModel.bitmapIcon)
        } else {
            binding.appIcon.setImageResource(
                    if (viewModel.appType == AppItemViewModel.AppType.MUSIC_PLAYER) {
                        R.drawable.ic_play_circle_filled_white_24dp
                    } else {
                        R.drawable.ic_baseline_android_24dp
                    }
            )
        }
        binding.appName.text = viewModel.appLabel
    }
}