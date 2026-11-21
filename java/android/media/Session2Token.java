// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Process;
import java.util.Objects;

/**
 * Represents an ongoing {@code MediaSession2} or a {@code MediaSession2Service}.
 */
public final class Session2Token implements Parcelable {
    public static final int TYPE_SESSION = 0;
    public static final int TYPE_SESSION_SERVICE = 1;

    private final int mUid;
    private final int mType;
    private final String mPackageName;
    private final String mServiceName;
    private final Bundle mExtras;

    public Session2Token(Context context, ComponentName serviceComponent) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(serviceComponent, "serviceComponent");
        mUid = Process.myUid();
        mType = TYPE_SESSION_SERVICE;
        mPackageName = serviceComponent.getPackageName();
        mServiceName = serviceComponent.getClassName();
        mExtras = new Bundle();
    }

    public Session2Token(int uid, int type, String packageName, String serviceName, Bundle extras) {
        mUid = uid;
        mType = type;
        mPackageName = packageName != null ? packageName : "";
        mServiceName = serviceName;
        mExtras = extras != null ? new Bundle(extras) : new Bundle();
    }

    Session2Token(Parcel in) {
        mUid = in.readInt();
        mType = in.readInt();
        mPackageName = in.readString();
        mServiceName = in.readString();
        Bundle b = in.readBundle();
        mExtras = b != null ? b : new Bundle();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUid);
        dest.writeInt(mType);
        dest.writeString(mPackageName);
        dest.writeString(mServiceName);
        dest.writeBundle(mExtras);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mType, mUid, mPackageName, mServiceName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Session2Token)) return false;
        Session2Token other = (Session2Token) obj;
        return mUid == other.mUid
                && mType == other.mType
                && Objects.equals(mPackageName, other.mPackageName)
                && Objects.equals(mServiceName, other.mServiceName);
    }

    @Override
    public String toString() {
        return "Session2Token{pkg=" + mPackageName + " type=" + mType
                + " service=" + mServiceName + " uid=" + mUid + "}";
    }

    public int getUid() {
        return mUid;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getServiceName() {
        return mServiceName;
    }

    public int getType() {
        return mType;
    }

    public Bundle getExtras() {
        return new Bundle(mExtras);
    }

    public static final Parcelable.Creator<Session2Token> CREATOR =
            new Parcelable.Creator<Session2Token>() {
                @Override
                public Session2Token createFromParcel(Parcel in) {
                    return new Session2Token(in);
                }

                @Override
                public Session2Token[] newArray(int size) {
                    return new Session2Token[size];
                }
            };
}
