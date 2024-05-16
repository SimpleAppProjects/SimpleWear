package android.net;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ResultReceiver;

import androidx.annotation.DeprecatedSinceApi;

public interface IConnectivityManager extends IInterface {
    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    void startTethering(int type, ResultReceiver receiver, boolean showProvisioningUi);

    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    void stopTethering(int type);

    abstract class Stub extends Binder implements IConnectivityManager {
        public static IConnectivityManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}