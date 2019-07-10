package com.thewizrd.simplewear;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;

public class LaunchActivity extends WearableActivity {

    private static final String TAG = "LaunchActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.WearAppTheme);
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, PhoneSyncActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Navigate
        startActivity(intent);
        finishAffinity();
    }
}
