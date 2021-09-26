package com.thewizrd.simplewear.controls

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.Checkable
import androidx.annotation.Px
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.setPadding
import androidx.core.view.updateLayoutParams
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.databinding.ControlFabtogglebuttonBinding

class ActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.wearActionButtonStyle,
    defStyleRes: Int = DEF_STYLE_RES
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Checkable {
    companion object {
        private const val DEF_STYLE_RES = R.style.Widget_Wear_ActionButton

        private val ENABLED_STATE_SET = intArrayOf(android.R.attr.state_enabled)
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)
    }

    private lateinit var binding: ControlFabtogglebuttonBinding
    private var _isExpanded = false
    private var _isChecked = false

    init {
        initialize(context)

        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.ActionButton,
            defStyleAttr,
            defStyleRes
        )
        try {
            if (a.hasValue(R.styleable.ActionButton_minHeight)) {
                minHeight =
                    a.getDimensionPixelSize(R.styleable.ActionButton_minHeight, 0)
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

    internal fun setIconSize(@Px size: Int, padding: Int? = null) {
        binding.actionIcon.updateLayoutParams {
            height = size
            width = size
        }

        if (padding != null) {
            binding.actionIcon.setPadding(padding)
        }
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