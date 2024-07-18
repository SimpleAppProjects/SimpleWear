package android.view;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;

public interface IWindowManager extends IInterface {

    /**
     * Lock the device immediately with the specified options (can be null).
     */
    void lockNow(Bundle options);

    abstract class Stub extends Binder implements IWindowManager {
        public static IWindowManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}