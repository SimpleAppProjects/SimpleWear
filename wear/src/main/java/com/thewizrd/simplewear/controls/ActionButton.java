package com.thewizrd.simplewear.controls;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.thewizrd.simplewear.R;

public class ActionButton extends FloatingActionButton {
    private Context context;

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

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        setLayoutParams(params);
        setCustomSize((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, context.getResources().getDisplayMetrics()));
        //setUseCompatPadding(true);

        setSupportBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.colorPrimary)));
        setImageDrawable(context.getDrawable(R.drawable.ic_icon));
    }

    public void updateButton(ActionButtonViewModel viewModel) {
        setImageDrawable(context.getDrawable(viewModel.getDrawableID()));
        setSupportBackgroundTintList(ColorStateList.valueOf(context.getColor(viewModel.getButtonBackgroundColor())));
    }
}
