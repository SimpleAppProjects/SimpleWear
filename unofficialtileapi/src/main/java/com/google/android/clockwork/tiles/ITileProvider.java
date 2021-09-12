package com.google.android.clockwork.tiles;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import com.google.android.aidl.BaseProxy;
import com.google.android.aidl.BaseStub;

import java.lang.ref.WeakReference;
import java.util.Set;

public interface ITileProvider extends IInterface {
    void onTileBlur(int var1) throws RemoteException;

    void onTileFocus(int var1, IBinder var2) throws RemoteException;

    void onTileUpdate(int var1, IBinder var2) throws RemoteException;

    final class Stub extends BaseStub implements ITileProvider {
        private final WeakReference<TileProviderService> serviceRef;

//    public Stub() {
//        super("com.google.android.clockwork.tiles.ITileProvider");
//    }

        Stub(TileProviderService var1) {
            super("com.google.android.clockwork.tiles.ITileProvider");
            this.serviceRef = new WeakReference<>(var1);
        }

        public final boolean dispatchTransaction(int var1, Parcel var2, Parcel var3) {
            if (var1 != 1) {
                if (var1 != 2) {
                    if (var1 != 3) {
                        return false;
                    }

                    this.onTileBlur(var2.readInt());
                } else {
                    this.onTileFocus(var2.readInt(), var2.readStrongBinder());
                }
            } else {
                this.onTileUpdate(var2.readInt(), var2.readStrongBinder());
            }

            var3.writeNoException();
            return true;
        }

        public final void onTileBlur(int var1) {
            TileProviderService var2 = (TileProviderService) this.serviceRef.get();
            if (var2 != null) {
                var2.mainThreadHandler.post(new TileProviderService.runBlur(var2, var1));
            }

        }

        public final void onTileFocus(int var1, IBinder var2) {
            TileProviderService var3 = (TileProviderService) this.serviceRef.get();
            if (var3 != null) {
                Set var4 = var3.hosts;
                Object var6;
                if (var2 != null) {
                    IInterface var5 = var2.queryLocalInterface("com.google.android.clockwork.tiles.ITilesHost");
                    if (var5 instanceof ITilesHost) {
                        var6 = (ITilesHost) var5;
                    } else {
                        var6 = new ITilesHost.Stub.Proxy(var2);
                    }
                } else {
                    var6 = null;
                }

                var4.add(var6);
                var3.mainThreadHandler.post(new TileProviderService.runFocus(var3, var1));
            }

        }

        public final void onTileUpdate(int var1, IBinder var2) {
            TileProviderService var3 = (TileProviderService) this.serviceRef.get();
            if (var3 != null) {
                Set var4 = var3.hosts;
                Object var6;
                if (var2 != null) {
                    IInterface var5 = var2.queryLocalInterface("com.google.android.clockwork.tiles.ITilesHost");
                    if (var5 instanceof ITilesHost) {
                        var6 = (ITilesHost) var5;
                    } else {
                        var6 = new ITilesHost.Stub.Proxy(var2);
                    }
                } else {
                    var6 = null;
                }

                var4.add(var6);
                var3.mainThreadHandler.post(new TileProviderService.runUpdate(var3, var1));
            }

        }

        public static final class Proxy extends BaseProxy implements ITileProvider {
            public Proxy(IBinder var1) {
                super(var1, "com.google.android.clockwork.tiles.ITileProvider");
            }

            public final void onTileBlur(int var1) throws RemoteException {
                Parcel var2 = this.obtainAndWriteInterfaceToken();
                var2.writeInt(var1);
                this.transactAndReadExceptionReturnVoid(3, var2);
            }

            public final void onTileFocus(int var1, IBinder var2) throws RemoteException {
                Parcel var3 = this.obtainAndWriteInterfaceToken();
                var3.writeInt(var1);
                var3.writeStrongBinder(var2);
                this.transactAndReadExceptionReturnVoid(2, var3);
            }

            public final void onTileUpdate(int var1, IBinder var2) throws RemoteException {
                Parcel var3 = this.obtainAndWriteInterfaceToken();
                var3.writeInt(var1);
                var3.writeStrongBinder(var2);
                this.transactAndReadExceptionReturnVoid(1, var3);
            }
        }
    }
}
