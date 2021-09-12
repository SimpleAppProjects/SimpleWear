package com.google.android.clockwork.tiles;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;
import android.widget.RemoteViews;

import com.google.android.aidl.BaseProxy;
import com.google.android.aidl.BaseStub;
import com.google.android.aidl.Codecs;

public interface ITilesHost extends IInterface {
    void updateRemoteViews(int var1, RemoteViews var2);

    void updateTileData(int var1, TileData var2) throws RemoteException;

    void updateWithOutdatedTime(int var1, RemoteViews var2, long var3);

    abstract class Stub extends BaseStub implements ITilesHost {
        public Stub() {
            super("com.google.android.clockwork.tiles.ITilesHost");
        }

        public final boolean dispatchTransaction(int var1, Parcel var2, Parcel var3) throws RemoteException {
            if (var1 != 1) {
                if (var1 != 2) {
                    if (var1 != 3) {
                        return false;
                    }

                    this.updateTileData(var2.readInt(), (TileData) Codecs.createParcelable(var2, TileData.CREATOR));
                } else {
                    this.updateWithOutdatedTime(var2.readInt(), (RemoteViews) Codecs.createParcelable(var2, RemoteViews.CREATOR), var2.readLong());
                }
            } else {
                this.updateRemoteViews(var2.readInt(), (RemoteViews) Codecs.createParcelable(var2, RemoteViews.CREATOR));
            }

            var3.writeNoException();
            return true;
        }

        static final class Proxy extends BaseProxy implements ITilesHost {
            Proxy(IBinder var1) {
                super(var1, "com.google.android.clockwork.tiles.ITilesHost");
            }

            public final void updateRemoteViews(int var1, RemoteViews var2) {
                throw null;
            }

            public final void updateWithOutdatedTime(int var1, RemoteViews var2, long var3) {
                throw null;
            }

            public final void updateTileData(int var1, TileData var2) throws RemoteException {
                Parcel var3 = this.obtainAndWriteInterfaceToken();
                var3.writeInt(var1);
                Codecs.writeParcelable(var3, var2);
                this.transactAndReadExceptionReturnVoid(3, var3);
            }
        }
    }
}
