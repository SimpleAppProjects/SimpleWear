package com.thewizrd.simplewear.controls

import android.content.Context
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.ControlFabtogglebuttonBinding

class ActionButton : ConstraintLayout {
    private lateinit var binding: ControlFabtogglebuttonBinding
    private var _isExpanded = false

    constructor(context: Context) : super(context) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context)
    }

    private fun initialize(context: Context) {
        binding = ControlFabtogglebuttonBinding.inflate(LayoutInflater.from(context), this)
        binding.buttonState.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (isExpanded) {
                    binding.buttonState.visibility = if (s.isEmpty()) GONE else VISIBLE
                }
            }

            override fun afterTextChanged(s: Editable) {}
        })
        binding.fab.customSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36f, context.resources.displayMetrics).toInt()
        //binding.fab.setUseCompatPadding(true);
        clipChildren = false
        clipToPadding = false
        binding.fab.supportBackgroundTintList = ColorStateList.valueOf(context.getColor(R.color.colorPrimary))
        binding.fab.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_icon))
        isExpanded = _isExpanded
    }

    fun updateButton(viewModel: ActionButtonViewModel) {
        binding.fab.setImageDrawable(ContextCompat.getDrawable(context!!, viewModel.drawableID))
        binding.fab.supportBackgroundTintList = ColorStateList.valueOf(context!!.getColor(viewModel.buttonBackgroundColor))
        binding.buttonLabel.text = viewModel.actionLabel
        binding.buttonState.text = viewModel.stateLabel
    }

    val fabCustomSize: Int
        get() = binding.fab.customSize

    var isExpanded: Boolean
        get() = _isExpanded
        set(expanded) {
            _isExpanded = expanded
            setBackgroundResource(if (expanded) R.drawable.button_noborder else 0)
            this.isFocusable = expanded
            this.isClickable = expanded
            binding.fab.isFocusable = !expanded
            binding.fab.isClickable = !expanded
            binding.buttonLabel.visibility = if (expanded) VISIBLE else GONE
            binding.buttonState.visibility = if (expanded) VISIBLE else GONE
        }

    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
        binding.fab.setOnClickListener(l)
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        super.setOnLongClickListener(l)
        binding.fab.setOnLongClickListener(l)
    }
}