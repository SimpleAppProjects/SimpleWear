package com.thewizrd.simplewear;

import android.Manifest;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
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
import androidx.fragment.app.Fragment;

import com.thewizrd.simplewear.helpers.PhoneStatusHelper;

public class PermissionCheckFragment extends Fragment {

    private AppCompatActivity mActivity;

    private TextView mCAMPermText;
    private TextView mDevAdminText;

    private static final int CAMERA_REQCODE = 0;
    private static final int DEVADMIN_REQCODE = 1;

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

        view.findViewById(R.id.torch_pref).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!PhoneStatusHelper.isCameraPermissionEnabled(mActivity)) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQCODE);
                }
            }
        });
        view.findViewById(R.id.deviceadmin_pref).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!PhoneStatusHelper.isDeviceAdminEnabled(mActivity)) {
                    ComponentName mScreenLockAdmin = new ComponentName(mActivity, ScreenLockAdminReceiver.class);

                    // Launch the activity to have the user enable our admin.
                    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mScreenLockAdmin);
                    startActivityForResult(intent, DEVADMIN_REQCODE);
                }
            }
        });

        mCAMPermText = view.findViewById(R.id.torch_pref_summary);
        mDevAdminText = view.findViewById(R.id.deviceadmin_summary);

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCamPermText(PhoneStatusHelper.isCameraPermissionEnabled(mActivity));

        DevicePolicyManager mDPM = (DevicePolicyManager) mActivity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName mScreenLockAdmin = new ComponentName(mActivity, ScreenLockAdminReceiver.class);
        updateDeviceAdminText(mDPM.isAdminActive(mScreenLockAdmin));
    }

    private void runOnUiThread(Runnable action) {
        if (mActivity != null)
            mActivity.runOnUiThread(action);
    }

    private void updateCamPermText(boolean enabled) {
        mCAMPermText.setText(enabled ? R.string.permission_camera_enabled : R.string.permission_camera_disabled);
        mCAMPermText.setTextColor(enabled ? Color.GREEN : Color.RED);
    }

    private void updateDeviceAdminText(boolean enabled) {
        mDevAdminText.setText(enabled ? R.string.permission_admin_enabled : R.string.permission_admin_disabled);
        mDevAdminText.setTextColor(enabled ? Color.GREEN : Color.RED);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case DEVADMIN_REQCODE:
                updateDeviceAdminText(resultCode == Activity.RESULT_OK);
                break;
        }
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
