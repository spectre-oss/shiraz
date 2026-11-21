// SPDX-License-Identifier: GPL-3.0-only
package android.net.wifi;

import android.os.Parcel;
import android.os.Parcelable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

public final class WifiSsid implements Parcelable {
    private final byte[] mBytes;

    private WifiSsid(byte[] bytes) {
        mBytes = bytes != null ? bytes.clone() : new byte[0];
    }

    public static WifiSsid fromBytes(byte[] bytes) {
        if (bytes == null) {
            bytes = new byte[0];
        }
        if (bytes.length > 32) {
            throw new IllegalArgumentException("Max SSID length is 32 bytes, got " + bytes.length);
        }
        return new WifiSsid(bytes);
    }

    public byte[] getBytes() {
        return mBytes.clone();
    }

    @Override
    public String toString() {
        if (mBytes.length == 0) {
            return "";
        }
        return "\"" + new String(mBytes, StandardCharsets.UTF_8) + "\"";
    }

    @Override
    public boolean equals(Object thatObject) {
        if (this == thatObject) return true;
        if (!(thatObject instanceof WifiSsid)) return false;
        return Arrays.equals(mBytes, ((WifiSsid) thatObject).mBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(mBytes));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByteArray(mBytes);
    }

    public static final Creator<WifiSsid> CREATOR = new Creator<WifiSsid>() {
        @Override
        public WifiSsid createFromParcel(Parcel in) {
            return new WifiSsid(in.createByteArray());
        }

        @Override
        public WifiSsid[] newArray(int size) {
            return new WifiSsid[size];
        }
    };
}
