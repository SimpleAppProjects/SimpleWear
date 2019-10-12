package com.google.android.aidl;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public class BaseStub extends Binder implements IInterface {
    public BaseStub(String var1) {
        this.attachInterface(this, var1);
    }

    public final IBinder asBinder() {
        return this;
    }

    public boolean dispatchTransaction$514KOOBECHP6UQB45TNN6BQGC5P66PBC7D662RJ4E9NMIP1FDTPIUK31E9HMAR1R94KLK___0(int var1, Parcel var2, Parcel var3) throws RemoteException {
        throw null;
    }

    public final boolean onTransact(int var1, Parcel var2, Parcel var3, int var4) throws RemoteException {
        if (var1 <= 16777215) {
            var2.enforceInterface(this.getInterfaceDescriptor());
        } else if (super.onTransact(var1, var2, var3, var4)) {
            return true;
        }

        return this.dispatchTransaction$514KOOBECHP6UQB45TNN6BQGC5P66PBC7D662RJ4E9NMIP1FDTPIUK31E9HMAR1R94KLK___0(var1, var2, var3);
    }
}
