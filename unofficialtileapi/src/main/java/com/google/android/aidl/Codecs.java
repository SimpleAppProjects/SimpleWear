package com.google.android.aidl;

import android.os.IInterface;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.text.TextUtils;

public final class Codecs {
    static {
        Codecs.class.getClassLoader();
    }

    private Codecs() {
    }

    public static Parcelable createParcelable(Parcel var0, Creator var1) {
        return var0.readInt() != 0 ? (Parcelable) var1.createFromParcel(var0) : null;
    }

    public static void writeStrongBinder(Parcel var0, IInterface var1) {
        var0.writeStrongBinder(var1.asBinder());
    }

    public static void writeParcelable(Parcel var0, Parcelable var1) {
        if (var1 != null) {
            var0.writeInt(1);
            var1.writeToParcel(var0, 0);
        } else {
            var0.writeInt(0);
        }
    }

//    public static void writeBoolean(Parcel var0, boolean var1) {
//        var0.writeInt(var1);
//    }

    public static boolean createBoolean(Parcel var0) {
        return var0.readInt() != 0;
    }

    public static CharSequence createCharSequence(Parcel var0) {
        return var0.readInt() != 0 ? (CharSequence) TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(var0) : null;
    }
}
