package com.thewizrd.simplewear;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Parcelable;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.thewizrd.shared_resources.utils.AsyncTask;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.helpers.PhoneStatusHelper;
import com.thewizrd.simplewear.wearable.WearableDataListenerService;

import java.util.regex.Pattern;

public class PermissionCheckFragment extends Fragment {
    private static final String TAG = "PermissionCheckFragment";

    private AppCompatActivity mActivity;

    private TextView mCAMPermText;
    private TextView mDevAdminText;
    private TextView mDNDText;
    private TextView mPairPermText;

    private ProgressBar mCompanionProgress;
    private CountDownTimer timer;

    private static final int CAMERA_REQCODE = 0;
    private static final int DEVADMIN_REQCODE = 1;
    private static final int SELECT_DEVICE_REQUEST_CODE = 42;

    @Override
    public void onAttach(@NonNull Context context) {
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
        view.findViewById(R.id.dnd_pref).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!PhoneStatusHelper.isNotificationAccessAllowed(mActivity)) {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                }
            }
        });
        View companionPref = view.findViewById(R.id.companion_pair_pref);
        if (companionPref != null) {
            mCompanionProgress = view.findViewById(R.id.companion_pair_progress);

            companionPref.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    LocalBroadcastManager.getInstance(mActivity)
                            .registerReceiver(mReceiver, new IntentFilter(WearableDataListenerService.ACTION_GETCONNECTEDNODE));

                    if (timer == null) {
                        timer = new CountDownTimer(5000, 1000) {
                            @Override
                            public void onTick(long millisUntilFinished) {
                            }

                            @Override
                            public void onFinish() {
                                if (mActivity != null) {
                                    Toast.makeText(mActivity, R.string.message_watchbttimeout, Toast.LENGTH_LONG).show();
                                    mCompanionProgress.setVisibility(View.GONE);
                                    Logger.writeLine(Log.INFO, "%s: BT Request Timeout", TAG);
                                }
                            }
                        };
                    }
                    timer.start();
                    mCompanionProgress.setVisibility(View.VISIBLE);

                    WearableDataListenerService.enqueueWork(mActivity,
                            new Intent(mActivity, WearableDataListenerService.class)
                                    .setAction(WearableDataListenerService.ACTION_REQUESTBTDISCOVERABLE));

                    Logger.writeLine(Log.INFO, "%s: ACTION_REQUESTBTDISCOVERABLE", TAG);
                }
            });
        }

        mCAMPermText = view.findViewById(R.id.torch_pref_summary);
        mDevAdminText = view.findViewById(R.id.deviceadmin_summary);
        mDNDText = view.findViewById(R.id.dnd_summary);
        mPairPermText = view.findViewById(R.id.companion_pair_summary);

        return view;
    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(mActivity)
                .unregisterReceiver(mReceiver);
        super.onPause();
    }

    // Android Q+
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (WearableDataListenerService.ACTION_GETCONNECTEDNODE.equals(intent.getAction())) {
                if (timer != null) timer.cancel();
                if (mCompanionProgress != null) mCompanionProgress.setVisibility(View.GONE);

                Logger.writeLine(Log.INFO, "%s: node received", TAG);

                pairDevice(intent.getStringExtra(WearableDataListenerService.EXTRA_NODEDEVICENAME));

                LocalBroadcastManager.getInstance(mActivity)
                        .unregisterReceiver(mReceiver);
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.Q)
    private void pairDevice(final String deviceName) {
        if (mActivity == null) return;

        final CompanionDeviceManager deviceManager = (CompanionDeviceManager) mActivity.getSystemService(Context.COMPANION_DEVICE_SERVICE);

        for (String assoc : deviceManager.getAssociations()) {
            deviceManager.disassociate(assoc);
        }
        updatePairPermText(false);

        final AssociationRequest request = new AssociationRequest.Builder()
                .addDeviceFilter(new BluetoothDeviceFilter.Builder()
                        .setNamePattern(Pattern.compile(deviceName + ".*", Pattern.DOTALL))
                        .build())
                .setSingleDevice(true)
                .build();

        Toast.makeText(mActivity, R.string.message_watchbtdiscover, Toast.LENGTH_LONG).show();

        AsyncTask.run(new Runnable() {
            @Override
            public void run() {
                Logger.writeLine(Log.INFO, "%s: sending pair request", TAG);

                deviceManager.associate(request, new CompanionDeviceManager.Callback() {
                    @Override
                    public void onDeviceFound(IntentSender chooserLauncher) {
                        if (mActivity == null) return;
                        try {
                            startIntentSenderForResult(chooserLauncher,
                                    SELECT_DEVICE_REQUEST_CODE, null, 0, 0, 0, null);
                        } catch (IntentSender.SendIntentException e) {
                            Logger.writeLine(Log.ERROR, e);
                        }
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        Logger.writeLine(Log.ERROR, "%s: failed to find any devices; " + error, TAG);
                    }
                }, null);
            }
        }, 5000);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateCamPermText(PhoneStatusHelper.isCameraPermissionEnabled(mActivity));

        DevicePolicyManager mDPM = (DevicePolicyManager) mActivity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName mScreenLockAdmin = new ComponentName(mActivity, ScreenLockAdminReceiver.class);
        updateDeviceAdminText(mDPM.isAdminActive(mScreenLockAdmin));

        updateDNDAccessText(PhoneStatusHelper.isNotificationAccessAllowed(mActivity));

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            CompanionDeviceManager deviceManager = (CompanionDeviceManager) mActivity.getSystemService(Context.COMPANION_DEVICE_SERVICE);
            updatePairPermText(!deviceManager.getAssociations().isEmpty());
        }
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

    private void updateDNDAccessText(boolean enabled) {
        mDNDText.setText(enabled ? R.string.permission_dnd_enabled : R.string.permission_dnd_disabled);
        mDNDText.setTextColor(enabled ? Color.GREEN : Color.RED);
    }

    private void updatePairPermText(boolean enabled) {
        mPairPermText.setText(enabled ? R.string.permission_pairdevice_enabled : R.string.permission_pairdevice_disabled);
        mPairPermText.setTextColor(enabled ? Color.GREEN : Color.RED);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case DEVADMIN_REQCODE:
                updateDeviceAdminText(resultCode == Activity.RESULT_OK);
                break;
            case SELECT_DEVICE_REQUEST_CODE:
                if (data != null && Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    Parcelable parcel = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
                    if (parcel instanceof BluetoothDevice) {
                        BluetoothDevice device = (BluetoothDevice) parcel;
                        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                            device.createBond();
                        }
                    }
                }
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
