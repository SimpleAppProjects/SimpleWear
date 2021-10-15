package com.thewizrd.simplewear.activities;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;

import androidx.annotation.ContentView;
import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.core.view.LayoutInflaterCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * Adds custom view inflater to inflate AppCompat views instead of inheriting AppCompatActivity
 */
public class AppCompatLiteActivity extends FragmentActivity {
    private static final String TAG = "AppCompatLiteActivity";

    public AppCompatLiteActivity() {
        super();
        initDelegate();
    }

    @ContentView
    public AppCompatLiteActivity(@LayoutRes int contentLayoutId) {
        super(contentLayoutId);
        initDelegate();
    }

    private void initDelegate() {
        addOnContextAvailableListener(context -> installViewFactory());
    }

    private void installViewFactory() {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        if (layoutInflater.getFactory() == null) {
            LayoutInflaterCompat.setFactory2(layoutInflater, new AppCompatLiteViewInflater());
        } else {
            if (!(layoutInflater.getFactory2() instanceof AppCompatLiteViewInflater)) {
                Log.i(TAG, "The Activity's LayoutInflater already has a Factory installed"
                        + " so we can not install AppCompat's");
            }
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
}