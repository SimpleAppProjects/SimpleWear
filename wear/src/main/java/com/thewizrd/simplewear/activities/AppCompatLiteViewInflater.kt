package com.thewizrd.simplewear.activities

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textview.MaterialTextView

class AppCompatLiteViewInflater : LayoutInflater.Factory2 {
    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        return createView(parent, name, context, attrs)
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return createView(null, name, context, attrs)
    }

    protected fun createView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        var view: View? = null

        // We need to 'inject' our tint aware Views in place of the standard framework versions
        when (name) {
            "TextView" -> {
                view = MaterialTextView(context, attrs)
                verifyNotNull(view, name)
            }
            "ImageView" -> {
                view = AppCompatImageView(context, attrs)
                verifyNotNull(view, name)
            }
            "Button" -> {
                view = MaterialButton(context, attrs)
                verifyNotNull(view, name)
            }
            "EditText" -> {
                view = AppCompatEditText(context, attrs)
                verifyNotNull(view, name)
            }
            "Spinner" -> {
                view = AppCompatSpinner(context, attrs)
                verifyNotNull(view, name)
            }
            "ImageButton" -> {
                view = AppCompatImageButton(context, attrs)
                verifyNotNull(view, name)
            }
            "CheckBox" -> {
                view = MaterialCheckBox(context, attrs)
                verifyNotNull(view, name)
            }
            "RadioButton" -> {
                view = MaterialRadioButton(context, attrs)
                verifyNotNull(view, name)
            }
            "CheckedTextView" -> {
                view = AppCompatCheckedTextView(context, attrs)
                verifyNotNull(view, name)
            }
            "AutoCompleteTextView" -> {
                view = MaterialAutoCompleteTextView(context, attrs)
                verifyNotNull(view, name)
            }
            "MultiAutoCompleteTextView" -> {
                view = AppCompatMultiAutoCompleteTextView(context, attrs)
                verifyNotNull(view, name)
            }
            "RatingBar" -> {
                view = AppCompatRatingBar(context, attrs)
                verifyNotNull(view, name)
            }
            "SeekBar" -> {
                view = AppCompatSeekBar(context, attrs)
                verifyNotNull(view, name)
            }
            "ToggleButton" -> {
                view = AppCompatToggleButton(context, attrs)
                verifyNotNull(view, name)
            }
        }

        return view
    }

    private fun verifyNotNull(view: View?, name: String) {
        checkNotNull(view) {
            ("${this.javaClass.name} asked to inflate view for <$name>, but returned null")
        }
    }
}