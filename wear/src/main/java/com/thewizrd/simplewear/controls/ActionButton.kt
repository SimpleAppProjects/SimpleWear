package com.thewizrd.simplewear.controls

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Checkable
import androidx.constraintlayout.widget.ConstraintLayout
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.ControlFabtogglebuttonBinding

class ActionButton : ConstraintLayout, Checkable {
    companion object {
        private const val DEF_STYLE_RES = R.style.Widget_ActionButton

        private val ENABLED_STATE_SET = intArrayOf(android.R.attr.state_enabled)
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }

    private lateinit var binding: ControlFabtogglebuttonBinding
    private var _isExpanded = false
    private var _isChecked = false

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(
        context,
        attrs,
        defStyleAttr,
        DEF_STYLE_RES
    )

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(context)

        val a = context.obtainStyledAttributes(attrs, R.styleable.ActionButton, 0, DEF_STYLE_RES)
        try {
            if (a.hasValue(R.styleable.ActionButton_android_minHeight)) {
                minimumHeight =
                    a.getDimensionPixelSize(R.styleable.ActionButton_android_minHeight, 0)
            }
        } finally {
            a.recycle()
        }
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