// SPDX-License-Identifier: GPL-3.0-only
package android.media.tv.interactive;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class AppLinkInfo implements Parcelable {
    private final ComponentName mComponentName;
    private final Uri mUri;

    public AppLinkInfo(String packageName, String className, String uriString) {
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(className, "className must not be null");
        mComponentName = new ComponentName(packageName, className);
        mUri = uriString != null ? Uri.parse(uriString) : null;
    }

    AppLinkInfo(ComponentName componentName, Uri uri) {
        mComponentName = componentName;
        mUri = uri;
    }

    public ComponentName getComponentName() {
        return mComponentName;
    }

    public Uri getUri() {
        return mUri;
    }

    @Override
    public String toString() {
        return "AppLinkInfo { packageName = "
                + (mComponentName != null ? mComponentName.getPackageName() : null)
                + ", className = "
                + (mComponentName != null ? mComponentName.getClassName() : null)
                + ", uri = " + (mUri != null ? mUri.toString() : null) + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (mComponentName != null) {
            dest.writeInt(1);
            dest.writeString(mComponentName.getPackageName());
            dest.writeString(mComponentName.getClassName());
        } else {
            dest.writeInt(0);
        }
        dest.writeString(mUri != null ? mUri.toString() : null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<AppLinkInfo> CREATOR =
            new Parcelable.Creator<AppLinkInfo>() {
                @Override
                public AppLinkInfo createFromParcel(Parcel in) {
                    ComponentName cn = null;
                    if (in.readInt() != 0) {
                        String pkg = in.readString();
                        String cls = in.readString();
                        if (pkg != null && cls != null) {
                            cn = new ComponentName(pkg, cls);
                        }
                    }
                    String u = in.readString();
                    return new AppLinkInfo(cn, u != null ? Uri.parse(u) : null);
                }

                @Override
                public AppLinkInfo[] newArray(int size) {
                    return new AppLinkInfo[size];
                }
            };
}
