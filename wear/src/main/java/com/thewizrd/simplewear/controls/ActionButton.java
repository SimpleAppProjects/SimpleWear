package com.thewizrd.simplewear.controls;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.thewizrd.simplewear.R;

public class ActionButton extends ConstraintLayout {
    private Context context;
    private FloatingActionButton fab;
    private TextView mButtonLabel;
    private TextView mButtonState;
    private boolean isExpanded;

    public ActionButton(Context context) {
        super(context);
        initialize(context);
    }

    public ActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public ActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        this.context = context;
        inflate(context, R.layout.control_fabtogglebutton, this);

        fab = findViewById(R.id.fab);
        mButtonLabel = findViewById(R.id.button_label);
        mButtonState = findViewById(R.id.button_state);
        mButtonState.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isExpanded) {
                    mButtonState.setVisibility(s == null || s.length() == 0 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        fab.setCustomSize((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, context.getResources().getDisplayMetrics()));
        //fab.setUseCompatPadding(true);

        setClipChildren(false);
        setClipToPadding(false);

        fab.setSupportBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.colorPrimary)));
        fab.setImageDrawable(context.getDrawable(R.drawable.ic_icon));

        setExpanded(isExpanded);
    }

    public void updateButton(ActionButtonViewModel viewModel) {
        fab.setImageDrawable(context.getDrawable(viewModel.getDrawableID()));
        fab.setSupportBackgroundTintList(ColorStateList.valueOf(context.getColor(viewModel.getButtonBackgroundColor())));
        mButtonLabel.setText(viewModel.getActionLabel());
        mButtonState.setText(viewModel.getStateLabel());
    }

    public int getFabCustomSize() {
        return fab.getCustomSize();
    }

    public boolean isExpanded() {
        return isExpanded;
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        super.setOnClickListener(l);
        fab.setOnClickListener(l);
    }

    @Override
    public void setOnLongClickListener(@Nullable OnLongClickListener l) {
        super.setOnLongClickListener(l);
        fab.setOnLongClickListener(l);
    }

    public void setExpanded(boolean expanded) {
        isExpanded = expanded;
        this.setBackgroundResource(expanded ? R.drawable.button_noborder : 0);
        this.setFocusable(expanded);
        this.setClickable(expanded);
        fab.setFocusable(!expanded);
        fab.setClickable(!expanded);
        mButtonLabel.setVisibility(expanded ? View.VISIBLE : View.GONE);
        mButtonState.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }
}
