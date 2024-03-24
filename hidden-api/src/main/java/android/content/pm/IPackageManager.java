package android.content.pm;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;

import androidx.annotation.DeprecatedSinceApi;

public interface IPackageManager extends IInterface {

    void grantRuntimePermission(String packageName, String permissionName, int userId);

    @DeprecatedSinceApi(api = Build.VERSION_CODES.R)
    void revokeRuntimePermission(String packageName, String permissionName, int userId);

    abstract class Stub extends Binder implements IPackageManager {
        public static IPackageManager asInterface(IBinder obj) {
            throw new RuntimeException("Stub!");
        }
    }
}