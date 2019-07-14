package com.thewizrd.simplewear;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class PermissionCheckFragment extends Fragment {

    private AppCompatActivity mActivity;

    private TextView mCAMPermText;

    private static final int CAMERA_REQCODE = 0;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mActivity = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mActivity = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_permcheck, container, false);

        View flashLayout = view.findViewById(R.id.torch_pref);
        flashLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQCODE);
                }
            }
        });

        mCAMPermText = view.findViewById(R.id.torch_pref_summary);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCamPermText(ContextCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
    }

    private void runOnUiThread(Runnable action) {
        if (mActivity != null)
            mActivity.runOnUiThread(action);
    }

    private void updateCamPermText(boolean enabled) {
        mCAMPermText.setText(enabled ? R.string.permission_camera_enabled : R.string.permission_camera_disabled);
        mCAMPermText.setTextColor(enabled ? Color.GREEN : Color.RED);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case CAMERA_REQCODE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    // permission was granted, yay!
                    // Do the task you need to do.
                    updateCamPermText(true);
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    updateCamPermText(false);
                    Toast.makeText(mActivity, "Permission denied", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }
}
