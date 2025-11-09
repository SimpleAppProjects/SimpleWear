package android.app;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.DeprecatedSinceApi;
import androidx.annotation.RequiresApi;

public interface INotificationManager {

    @RequiresApi(Build.VERSION_CODES.M)
    @DeprecatedSinceApi(api = Build.VERSION_CODES.VANILLA_ICE_CREAM)
    void setInterruptionFilter(String pkg, int interruptionFilter);

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    void setInterruptionFilter(String pkg, int interruptionFilter, boolean fromUser);

    abstract class Stub extends Binder implements INotificationManager {
        public static INotificationManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
