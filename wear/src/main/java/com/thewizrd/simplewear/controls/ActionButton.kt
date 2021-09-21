package com.thewizrd.simplewear.controls

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.ControlFabtogglebuttonBinding

class ActionButton : ConstraintLayout, Checkable {
    companion object {
        private val ENABLED_STATE_SET = intArrayOf(android.R.attr.state_enabled)
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }

    private lateinit var binding: ControlFabtogglebuttonBinding
    private var _isExpanded = false
    private var _isChecked = false

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
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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
        clipChildren = false
        clipToPadding = false
        isClickable = true
        isFocusable = true
        this.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        this.foreground = ContextCompat.getDrawable(context, R.drawable.round_selectable_item)
        this.background = ContextCompat.getDrawable(context, R.drawable.action_button_background)
        binding.actionIcon.setImageResource(R.drawable.ic_icon)
        isExpanded = _isExpanded
    }

    fun updateButton(viewModel: ActionButtonViewModel) {
        binding.actionIcon.setImageResource(viewModel.drawableID)
        binding.buttonLabel.text = viewModel.actionLabel
        binding.buttonState.text = viewModel.stateLabel

        if (viewModel.buttonState == null) {
            // Indeterminate state
            isEnabled = false
            isChecked = false
        } else {
            isEnabled = true
            isChecked = viewModel.buttonState!!
        }
        refreshDrawableState()
    }

    var isExpanded: Boolean
        get() = _isExpanded
        set(expanded) {
            _isExpanded = expanded
            binding.buttonLabel.visibility = if (expanded) VISIBLE else GONE
            binding.buttonState.visibility = if (expanded) VISIBLE else GONE
            binding.spacerGroup.visibility = if (expanded) VISIBLE else GONE
        }

    override fun setOnClickListener(l: OnClickListener?) {
        super.setOnClickListener(l)
        binding.actionIcon.setOnClickListener(l)
    }

    override fun setOnLongClickListener(l: OnLongClickListener?) {
        super.setOnLongClickListener(l)
        binding.actionIcon.setOnLongClickListener(l)
    }

    override fun setChecked(checked: Boolean) {
        _isChecked = checked
        refreshDrawableState()
    }

    override fun isChecked(): Boolean {
        return _isChecked
    }

    override fun toggle() {
        isChecked = !isChecked
    }

    override fun onCreateDrawableState(extraSpace: Int): IntArray {
        val drawableState = super.onCreateDrawableState(extraSpace + 2)

        if (isChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }

        if (isEnabled) {
            mergeDrawableStates(drawableState, ENABLED_STATE_SET)
        }

        return drawableState
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        // Disable touch events on children
        return true
    }
}