package com.android.internal.telephony;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

public interface ITelephony extends IInterface {
    @DeprecatedSinceApi(api = Build.VERSION_CODES.P)
    void setDataEnabled(int subId, boolean enable);

    @DeprecatedSinceApi(api = Build.VERSION_CODES.P)
    boolean getDataEnabled(int subId);

    @RequiresApi(api = Build.VERSION_CODES.P)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.S)
    void setUserDataEnabled(int subId, boolean enable);

    @RequiresApi(api = Build.VERSION_CODES.P)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.S)
    boolean isUserDataEnabled(int subId);

    @RequiresApi(api = Build.VERSION_CODES.S)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU)
    void setDataEnabledForReason(int subId, int reason, boolean enable);

    @RequiresApi(api = Build.VERSION_CODES.S)
    boolean isDataEnabledForReason(int subId, int reason);

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    void setDataEnabledForReason(int subId, int reason, boolean enable, String callingPackage);

    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU)
    boolean enableDataConnectivity();

    @DeprecatedSinceApi(api = Build.VERSION_CODES.TIRAMISU)
    boolean disableDataConnectivity();

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    boolean enableDataConnectivity(String callingPackage);

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    boolean disableDataConnectivity(String callingPackage);

    abstract class Stub extends Binder implements ITelephony {
        public static ITelephony asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
