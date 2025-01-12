package android.net;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;

public interface IIntResultListener extends IInterface {
    void onResult(int resultCode);

    abstract class Stub extends Binder implements IIntResultListener {
        public Stub() {
            throw new UnsupportedOperationException();
        }

        @Override
        public android.os.IBinder asBinder() {
            throw new UnsupportedOperationException();
        }

        public static IIntResultListener asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}
