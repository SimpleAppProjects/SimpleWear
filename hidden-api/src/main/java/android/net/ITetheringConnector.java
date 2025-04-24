package android.net;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

public interface ITetheringConnector extends IInterface {
    @RequiresApi(Build.VERSION_CODES.R)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.S)
    void startTethering(TetheringRequestParcel request, String callerPkg, IIntResultListener receiver);

    @RequiresApi(Build.VERSION_CODES.R)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.S)
    void stopTethering(int type, String callerPkg, IIntResultListener receiver);

    @RequiresApi(Build.VERSION_CODES.S)
    void startTethering(TetheringRequestParcel request, String callerPkg, String callingAttributionTag, IIntResultListener receiver);

    @RequiresApi(Build.VERSION_CODES.S)
    void stopTethering(int type, String callerPkg, String callingAttributionTag, IIntResultListener receiver);

    abstract class Stub extends Binder implements ITetheringConnector {
        public static ITetheringConnector asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}