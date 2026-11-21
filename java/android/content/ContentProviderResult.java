// SPDX-License-Identifier: GPL-3.0-only
package android.content;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** Contains the result of the application of a ContentProviderOperation. */
public class ContentProviderResult implements Parcelable {
    public final Uri uri;
    public final Integer count;
    public final Bundle extras;
    public final Throwable exception;

    public ContentProviderResult(Uri uri) {
        if (uri == null) throw new NullPointerException("uri");
        this.uri = uri;
        this.count = null;
        this.extras = null;
        this.exception = null;
    }

    public ContentProviderResult(int count) {
        this.uri = null;
        this.count = Integer.valueOf(count);
        this.extras = null;
        this.exception = null;
    }

    public ContentProviderResult(Bundle extras) {
        if (extras == null) throw new NullPointerException("extras");
        this.uri = null;
        this.count = null;
        this.extras = extras;
        this.exception = null;
    }

    public ContentProviderResult(Throwable exception) {
        if (exception == null) throw new NullPointerException("exception");
        this.uri = null;
        this.count = null;
        this.extras = null;
        this.exception = exception;
    }

    public ContentProviderResult(Parcel source) {
        int mask = source.readInt();
        this.uri = (mask & 1) != 0 ? source.readTypedObject(Uri.CREATOR) : null;
        this.count = (mask & 2) != 0 ? Integer.valueOf(source.readInt()) : null;
        this.extras = (mask & 4) != 0 ? source.readBundle(ContentProviderResult.class.getClassLoader()) : null;
        this.exception = (mask & 8) != 0 ? new RuntimeException(source.readString()) : null;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        int mask = 0;
        if (uri != null) mask |= 1;
        if (count != null) mask |= 2;
        if (extras != null) mask |= 4;
        if (exception != null) mask |= 8;
        dest.writeInt(mask);
        if (uri != null) dest.writeTypedObject(uri, flags);
        if (count != null) dest.writeInt(count.intValue());
        if (extras != null) dest.writeBundle(extras);
        if (exception != null) dest.writeString(exception.toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ContentProviderResult(");
        if (uri != null) sb.append("uri=").append(uri).append(" ");
        if (count != null) sb.append("count=").append(count).append(" ");
        if (extras != null) sb.append("extras=").append(extras).append(" ");
        if (exception != null) sb.append("exception=").append(exception).append(" ");
        if (sb.charAt(sb.length() - 1) == ' ') sb.setLength(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }

    public static final Parcelable.Creator<ContentProviderResult> CREATOR =
            new Parcelable.Creator<ContentProviderResult>() {
                @Override
                public ContentProviderResult createFromParcel(Parcel source) {
                    return new ContentProviderResult(source);
                }

                @Override
                public ContentProviderResult[] newArray(int size) {
                    return new ContentProviderResult[size];
                }
            };
}
