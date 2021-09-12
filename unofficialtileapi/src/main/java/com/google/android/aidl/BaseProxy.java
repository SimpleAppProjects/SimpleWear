package com.google.android.aidl;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public class BaseProxy implements IInterface {
    private final IBinder mRemote;
    private final String mDescriptor;

    protected BaseProxy(IBinder var1, String var2) {
        this.mRemote = var1;
        this.mDescriptor = var2;
    }

    protected final Parcel obtainAndWriteInterfaceToken() {
        Parcel var1 = Parcel.obtain();
        var1.writeInterfaceToken(this.mDescriptor);
        return var1;
    }

    protected final Parcel transactAndReadException(int var1, Parcel var2) throws RemoteException {
        Parcel var3 = Parcel.obtain();

        try {
            this.mRemote.transact(var1, var2, var3, 0);
            var3.readException();
        } catch (RuntimeException var7) {
            var3.recycle();
            throw var7;
        } finally {
            var2.recycle();
        }

        return var3;
    }

    public final IBinder asBinder() {
        return this.mRemote;
    }

    protected final void transactAndReadExceptionReturnVoid(int var1, Parcel var2) throws RemoteException {
        Parcel var3 = Parcel.obtain();

        try {
            this.mRemote.transact(var1, var2, var3, 0);
            var3.readException();
        } finally {
            var2.recycle();
            var3.recycle();
        }

    }

    protected final void transactOneway(int var1, Parcel var2) throws RemoteException {
        try {
            this.mRemote.transact(var1, var2, (Parcel) null, 1);
        } finally {
            var2.recycle();
        }

    }
}
