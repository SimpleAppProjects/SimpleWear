package android.net.wifi;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

public interface IWifiManager extends IInterface {
    @DeprecatedSinceApi(api = Build.VERSION_CODES.N_MR1)
    boolean setWifiEnabled(boolean enable);

    @RequiresApi(api = Build.VERSION_CODES.N_MR1)
    boolean setWifiEnabled(String packageName, boolean enable);

    abstract class Stub extends Binder implements IWifiManager {
        public static IWifiManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}