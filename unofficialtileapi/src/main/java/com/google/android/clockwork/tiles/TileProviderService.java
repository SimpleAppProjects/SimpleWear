package com.google.android.clockwork.tiles;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.Trace;
import android.util.ArraySet;
import android.util.Log;

import java.util.Iterator;
import java.util.Set;

public abstract class TileProviderService extends Service {
    private ITileProvider.Stub binder;
    public final Set hosts = new ArraySet();
    public final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    public static boolean isIdForDummyData(int var0) {
        return var0 <= -2;
    }

//    public final boolean inRetailMode() {
//        ComponentName var1 = new ComponentName("com.google.android.apps.wearable.settings", "com.google.android.clockwork.settings.RetailStatusService");
//        return this.getPackageManager().getComponentEnabledSetting(var1) == 1;
//    }

    public final IBinder onBind(Intent var1) {
        if ("com.google.android.clockwork.ACTION_TILE_UPDATE_REQUEST".equals(var1.getAction())) {
            if (this.binder == null) {
                this.binder = new ITileProvider.Stub(this);
            }

            return this.binder;
        } else {
            return null;
        }
    }

    public void onTileBlur(int tileId) {
    }

    public void onTileFocus(int tileId) {
    }

    public abstract void onTileUpdate(int tileId);

    // Use sendUpdate() instead. This method was named wrong when the unofficial API was first released.
    @Deprecated
    public final void sendData(int tileId, TileData tileData) {
        sendUpdate(tileId, tileData);
    }

    public final void sendUpdate(int tileId, TileData tileData) {
        Trace.beginSection("sendUpdate");
        Iterator var3 = this.hosts.iterator();

        while (var3.hasNext()) {
            ITilesHost var4 = (ITilesHost) var3.next();
            if (var4 != null) {
                try {
                    var4.updateTileData(tileId, tileData);
                } catch (RemoteException var5) {
                    Log.w("TileProviderService", "Error sending update.", var5);
                }
            }
        }

        Trace.endSection();
    }


    // $FF: synthetic class
    static final class runUpdate implements Runnable {
        private final TileProviderService arg$1;
        private final int arg$2;

        runUpdate(TileProviderService var1, int var2) {
            this.arg$1 = var1;
            this.arg$2 = var2;
        }

        public final void run() {
            this.arg$1.onTileUpdate(this.arg$2);
        }
    }

    // $FF: synthetic class
    static final class runFocus implements Runnable {
        private final TileProviderService arg$1;
        private final int arg$2;

        runFocus(TileProviderService var1, int var2) {
            this.arg$1 = var1;
            this.arg$2 = var2;
        }

        public final void run() {
            this.arg$1.onTileFocus(this.arg$2);
        }
    }

    // $FF: synthetic class
    static final class runBlur implements Runnable {
        private final TileProviderService arg$1;
        private final int arg$2;

        runBlur(TileProviderService var1, int var2) {
            this.arg$1 = var1;
            this.arg$2 = var2;
        }

        public final void run() {
            this.arg$1.onTileBlur(this.arg$2);
        }
    }
}
