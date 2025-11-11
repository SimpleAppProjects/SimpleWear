package android.nfc;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

public interface INfcAdapter extends IInterface {
    @DeprecatedSinceApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    boolean enable();

    @DeprecatedSinceApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    boolean disable(boolean saveState);

    @RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    boolean enable(String pkg);

    @RequiresApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    boolean disable(boolean saveState, String pkg);

    abstract class Stub extends Binder implements INfcAdapter {
        public static INfcAdapter asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}