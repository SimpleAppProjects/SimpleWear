package com.google.android.clockwork.tiles;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.RemoteViews;

public class TileData implements Parcelable {
    public static final Creator CREATOR = new TileDataCreator();
    public final Bundle fields;

    // $FF: synthetic method
    TileData(Bundle var1) {
        this.fields = var1;
    }

    // $FF: synthetic method
    TileData(Parcel var1) {
        this.fields = var1.readBundle(this.getClass().getClassLoader());
    }

    public final long getOutdatedTimeMs() {
        return this.fields.getLong("OUTDATED_TIME_MS", -1L);
    }

    public final RemoteViews getRemoteViews() {
        return (RemoteViews) this.fields.getParcelable("REMOTE_VIEWS");
    }

    public final boolean isLoading() {
        return this.fields.getBoolean("LOADING");
    }

    public final int describeContents() {
        return 0;
    }

    public final String toString() {
        String var1 = String.valueOf(this.fields);
        StringBuilder var2 = new StringBuilder(String.valueOf(var1).length() + 17);
        var2.append("TileData{fields=");
        var2.append(var1);
        var2.append('}');
        return var2.toString();
    }

    public final void writeToParcel(Parcel var1, int var2) {
        var1.writeBundle(this.fields);
    }

    static final class TileDataCreator implements Creator {
        public final /* bridge */ Object createFromParcel(final Parcel parcel) {
            return new TileData(parcel);
        }

        public final /* bridge */ Object[] newArray(final int size) {
            return new TileData[size];
        }
    }

    public static class Builder {
        private final Bundle fields = new Bundle();

        public final TileData build() {
            return new TileData(this.fields);
        }

        public final Builder setLoading(boolean var1) {
            this.fields.putBoolean("LOADING", var1);
            return this;
        }

        public final Builder setOutdatedTimeMs(long var1) {
            this.fields.putLong("OUTDATED_TIME_MS", var1);
            return this;
        }

        public final Builder setRemoteViews(RemoteViews var1) {
            if (var1 == null) {
                this.fields.remove("REMOTE_VIEWS");
//        } else if (var1 instanceof String) {
//            this.fields.putString("REMOTE_VIEWS", (String)var1);
            } else {
                if (!(var1 instanceof Parcelable)) {
                    String var2 = String.valueOf(var1.getClass());
                    StringBuilder var3 = new StringBuilder(String.valueOf(var2).length() + 24);
                    var3.append("Unexpected object type: ");
                    var3.append(var2);
                    throw new IllegalArgumentException(var3.toString());
                }

                this.fields.putParcelable("REMOTE_VIEWS", (Parcelable) var1);
            }

            return this;
        }

        public final Builder setFullUpdate(boolean var1) {
            this.fields.putBoolean("FULL_UPDATE", var1);
            return this;
        }
    }
}
