package com.thewizrd.simplewear.controls

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IntDef
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.thewizrd.simplewear.R

@SuppressLint("RestrictedApi")
class WearChipButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = R.attr.wearChipButtonStyle,
    defStyleRes: Int = DEF_STYLE_RES
) : ConstraintLayout(context, attrs, defStyleAttr, defStyleRes), Checkable {
    companion object {
        private const val DEF_STYLE_RES = R.style.Widget_Wear_WearChipButton

        private val ENABLED_STATE_SET = intArrayOf(android.R.attr.state_enabled)
        private val CHECKABLE_STATE_SET = intArrayOf(android.R.attr.state_checkable)
        private val CHECKED_STATE_SET = intArrayOf(android.R.attr.state_checked)

        const val CONTROLTYPE_NONE = 0
        const val CONTROLTYPE_CHECKBOX = 1
        const val CONTROLTYPE_RADIO = 2
        const val CONTROLTYPE_TOGGLE = 3
    }

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        CONTROLTYPE_NONE,
        CONTROLTYPE_CHECKBOX,
        CONTROLTYPE_RADIO,
        CONTROLTYPE_TOGGLE
    )
    annotation class ControlType

    private val mIconView: ImageView
    private val mPrimaryTextView: TextView
    private val mSecondaryTextView: TextView
    private val mSelectionControlContainer: ViewGroup

    private var buttonBackgroundTint: ColorStateList? = null
    private var buttonControlTint: ColorStateList? = null
    private var iconTint: ColorStateList? = null

    var isCheckable: Boolean = false
        get() = field
        set(value) {
            field = value
            refreshDrawableState()
        }

    private var _isChecked = false

    init {
        LayoutInflater.from(context).inflate(R.layout.wear_chip_button_layout, this, true)
        mIconView = findViewById(R.id.wear_chip_icon)
        mPrimaryTextView = findViewById(R.id.wear_chip_primary_text)
        mSecondaryTextView = findViewById(R.id.wear_chip_secondary_text)
        mSelectionControlContainer = findViewById(R.id.wear_chip_selection_control_container)

        mPrimaryTextView.maxLines = 2
        mSecondaryTextView.maxLines = 1

        val a = context.obtainStyledAttributes(
            attrs,
            R.styleable.WearChipButton,
            defStyleAttr,
            defStyleRes
        )
        ViewCompat.saveAttributeDataForStyleable(
            this,
            context, R.styleable.WearChipButton,
            attrs, a, defStyleAttr, defStyleRes
        )

        try {
            if (a.hasValue(R.styleable.WearChipButton_icon)) {
                setIconDrawable(a.getDrawable(R.styleable.WearChipButton_icon))
            }
            if (a.hasValue(R.styleable.WearChipButton_primaryText)) {
                setPrimaryText(a.getString(R.styleable.WearChipButton_primaryText))
            }
            if (a.hasValue(R.styleable.WearChipButton_secondaryText)) {
                setSecondaryText(a.getString(R.styleable.WearChipButton_secondaryText))
            }
            if (a.hasValue(R.styleable.WearChipButton_backgroundTint)) {
                val colorResId = a.getResourceId(R.styleable.WearChipButton_backgroundTint, 0)
                if (colorResId != 0) {
                    val tint = ContextCompat.getColorStateList(context, colorResId)
                    if (tint != null) {
                        buttonBackgroundTint = tint
                    }
                }

                if (buttonBackgroundTint == null) {
                    buttonBackgroundTint =
                        a.getColorStateList(R.styleable.WearChipButton_backgroundTint)
                }
            }
            if (a.hasValue(R.styleable.WearChipButton_buttonTint)) {
                val colorResId = a.getResourceId(R.styleable.WearChipButton_buttonTint, 0)
                if (colorResId != 0) {
                    val tint = ContextCompat.getColorStateList(context, colorResId)
                    if (tint != null) {
                        buttonControlTint = tint
                    }
                }

                if (buttonControlTint == null) {
                    buttonControlTint =
                        a.getColorStateList(R.styleable.WearChipButton_buttonTint)
                }
            }
            if (a.hasValue(R.styleable.WearChipButton_iconTint)) {
                val colorResId = a.getResourceId(R.styleable.WearChipButton_iconTint, 0)
                if (colorResId != 0) {
                    val tint = ContextCompat.getColorStateList(context, colorResId)
                    if (tint != null) {
                        iconTint = tint
                    }
                }

                if (iconTint == null) {
                    iconTint =
                        a.getColorStateList(R.styleable.WearChipButton_iconTint)
                }

                setIconTint(iconTint)
            }
            if (a.hasValue(R.styleable.WearChipButton_android_checkable)) {
                isCheckable = a.getBoolean(R.styleable.WearChipButton_android_checkable, false)
            }
            if (a.hasValue(R.styleable.WearChipButton_android_checked)) {
                isChecked = a.getBoolean(R.styleable.WearChipButton_android_checked, false)
            }
            if (a.hasValue(R.styleable.WearChipButton_controlType)) {
                updateControlType(a.getInt(R.styleable.WearChipButton_controlType, 0))
            }
            if (a.hasValue(R.styleable.WearChipButton_minHeight)) {
                minHeight = a.getDimensionPixelSize(R.styleable.WearChipButton_minHeight, 0)
            }
        } finally {
            a.recycle()
        }

        updateBackgroundTint()
        updateButtonControlTint()
    }

    fun setIconResource(@DrawableRes resId: Int) {
        mIconView.setImageResource(resId)
        mIconView.visibility = if (resId == 0) View.GONE else View.VISIBLE
    }

    fun setIconDrawable(drawable: Drawable?) {
        mIconView.setImageDrawable(drawable)
        mIconView.visibility = if (drawable == null) View.GONE else View.VISIBLE
    }

    fun setIconTint(tint: ColorStateList?) {
        mIconView.imageTintList = tint
    }

    fun setIconTintResource(@ColorRes iconTintResId: Int) {
        setIconTint(ContextCompat.getColorStateList(context, iconTintResId))
    }

    fun setPrimaryText(@StringRes resId: Int) {
        if (resId == 0) {
            setPrimaryText(null)
        } else {
            mPrimaryTextView.setText(resId)
            mPrimaryTextView.visibility = if (resId == 0) View.GONE else View.VISIBLE
        }
    }

    fun setPrimaryText(text: CharSequence?) {
        mPrimaryTextView.text = text
        mPrimaryTextView.visibility = if (text == null) View.GONE else View.VISIBLE
    }

    fun setSecondaryText(@StringRes resId: Int) {
        if (resId == 0) {
            setSecondaryText(null)
        } else {
            mSecondaryTextView.setText(resId)
            mSecondaryTextView.visibility = if (resId == 0) View.GONE else View.VISIBLE
            if (resId == 0) {
                mPrimaryTextView.maxLines = 2
            } else {
                mPrimaryTextView.maxLines = 1
            }
        }
    }

    fun setSecondaryText(text: CharSequence?) {
        mSecondaryTextView.text = text
        mSecondaryTextView.visibility = if (text == null) View.GONE else View.VISIBLE
        if (text == null) {
            mPrimaryTextView.maxLines = 2
        } else {
            mPrimaryTextView.maxLines = 1
        }
    }

    fun setText(@StringRes primaryResId: Int, @StringRes secondaryResId: Int = 0) {
        setPrimaryText(primaryResId)
        setSecondaryText(secondaryResId)
    }

    fun setText(primaryText: CharSequence?, secondaryText: CharSequence? = null) {
        setPrimaryText(primaryText)
        setSecondaryText(secondaryText)
    }

    fun setControlView(view: View?) {
        mSelectionControlContainer.removeAllViews()
        if (view != null) {
            mSelectionControlContainer.addView(view)
            mSelectionControlContainer.visibility = View.VISIBLE
        } else {
            mSelectionControlContainer.visibility = View.GONE
        }
    }

    fun getControlView(): View? {
        return mSelectionControlContainer.getChildAt(0)
    }

    fun setControlViewVisibility(visibility: Int) {
        if (getControlView() != null) {
            mSelectionControlContainer.visibility = visibility
        }
    }

    fun getBackgroundColor(): ColorStateList? {
        return buttonBackgroundTint
    }

    fun setBackgroundColor(tint: ColorStateList?) {
        buttonBackgroundTint = tint
        updateBackgroundTint()
    }

    fun getControlButtonColor(): ColorStateList? {
        return buttonControlTint
    }

    fun setControlButtonColor(tint: ColorStateList?) {
        buttonControlTint = tint
        updateButtonControlTint()
    }

    fun updateControlType(@ControlType controlType: Int) {
        when (controlType) {
            CONTROLTYPE_CHECKBOX -> {
                setControlView(CheckBox(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    isClickable = false
                    isDuplicateParentStateEnabled = true
                    buttonDrawable =
                        ContextCompat.getDrawable(context, R.drawable.wear_checkbox_icon)
                    buttonTintList = buttonControlTint
                })
                setControlViewVisibility(View.VISIBLE)
            }
            CONTROLTYPE_RADIO -> {
                setControlView(RadioButton(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    isClickable = false
                    isDuplicateParentStateEnabled = true
                    buttonDrawable = ContextCompat.getDrawable(context, R.drawable.wear_radio_icon)
                    buttonTintList = buttonControlTint
                })
                setControlViewVisibility(View.VISIBLE)
            }
            CONTROLTYPE_TOGGLE -> {
                setControlView(CheckBox(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = Gravity.CENTER
                    }
                    isClickable = false
                    isDuplicateParentStateEnabled = true
                    buttonDrawable = ContextCompat.getDrawable(context, R.drawable.wear_switch_icon)
                    buttonTintList = buttonControlTint
                })
                setControlViewVisibility(View.VISIBLE)
            }
            else -> {
                setControlView(null)
            }
        }
    }

    private fun updateBackgroundTint() {
        val backgroundDrawable = background
        if (backgroundDrawable != null) {
            if (backgroundDrawable is LayerDrawable) {
                val layerCount = backgroundDrawable.numberOfLayers
                for (i in 1 until layerCount) {
                    val layer = backgroundDrawable.getDrawable(i)
                    val id = backgroundDrawable.getId(i)
                    if (id == R.id.start_accent) {
                        layer.alpha = 0xFF
                        backgroundDrawable.setDrawable(i, layer.setButtonBackgroundDrawableTint())
                    } else {
                        layer.alpha = 0
                    }
                }
                return
            }

            val tintable = DrawableCompat.wrap(backgroundDrawable).mutate()
            DrawableCompat.setTintList(tintable, buttonBackgroundTint)
            background = tintable
        }
    }

    private fun updateButtonControlTint() {
        val control = getControlView() as? CompoundButton
        control?.buttonTintList = buttonControlTint
    }

    private fun Drawable.setButtonBackgroundDrawableTint(): Drawable {
        val tintable = DrawableCompat.wrap(this).mutate()
        DrawableCompat.setTintList(tintable, buttonBackgroundTint)
        return tintable
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
        val drawableState = super.onCreateDrawableState(extraSpace + 3)

        if (isCheckable) {
            mergeDrawableStates(drawableState, CHECKABLE_STATE_SET)
        }

        if (isChecked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET)
        }

        if (isEnabled) {
            mergeDrawableStates(drawableState, ENABLED_STATE_SET)
        }

        return drawableState
    }
}