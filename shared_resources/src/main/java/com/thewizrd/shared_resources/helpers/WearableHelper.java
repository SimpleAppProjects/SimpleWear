package com.thewizrd.shared_resources.helpers;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.wearable.PutDataRequest;
import com.thewizrd.shared_resources.SimpleLibrary;
import com.thewizrd.shared_resources.utils.Logger;

public class WearableHelper {
    // Name of capability listed in Phone app's wear.xml
    public static final String CAPABILITY_PHONE_APP = "com.thewizrd.simplewear_phone_app";
    // Name of capability listed in Wear app's wear.xml
    public static final String CAPABILITY_WEAR_APP = "com.thewizrd.simplewear_wear_app";

    // Link to Play Store listing
    public static final String PLAY_STORE_APP_URI = "market://details?id=com.thewizrd.simplewear";

    public static Uri getPlayStoreURI() {
        return Uri.parse(PLAY_STORE_APP_URI);
    }

    // For WearableListenerService
    public static final String StartActivityPath = "/start-activity";
    public static final String AppStatePath = "/app_state";
    public static final String ActionsPath = "/actions";
    public static final String StatusPath = "/status";
    public static final String BatteryPath = "/status/battery";
    public static final String BluetoothPath = "/status/bt";
    public static final String WifiPath = "/status/wifi";
    public static final String UpdatePath = "/update/all";

    public static boolean isGooglePlayServicesInstalled() {
        int queryResult = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(SimpleLibrary.getInstance().getApp().getAppContext());
        if (queryResult == ConnectionResult.SUCCESS) {
            Logger.writeLine(Log.INFO, "App: Google Play Services is installed on this device.");
            return true;
        }

        if (GoogleApiAvailability.getInstance().isUserResolvableError(queryResult)) {
            String errorString = GoogleApiAvailability.getInstance().getErrorString(queryResult);
            Logger.writeLine(Log.INFO,
                    "App: There is a problem with Google Play Services on this device: %s - %s",
                    queryResult, errorString);
        }

        return false;
    }

    public static Uri getWearDataUri(String NodeId, String Path) {
        return new Uri.Builder()
                .scheme(PutDataRequest.WEAR_URI_SCHEME)
                .authority(NodeId)
                .path(Path)
                .build();
    }
}
