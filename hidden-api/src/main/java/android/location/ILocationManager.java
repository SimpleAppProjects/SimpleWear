package android.location;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.RequiresApi;

public interface ILocationManager extends IInterface {

    @RequiresApi(api = Build.VERSION_CODES.P)
    void setLocationEnabledForUser(boolean enabled, int userId);

    abstract class Stub extends Binder implements ILocationManager {
        public static ILocationManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}