// SPDX-License-Identifier: GPL-3.0-only

package android.view.inputmethod;

import android.content.ClipDescription;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.security.InvalidParameterException;

public final class InputContentInfo implements Parcelable {
    private final Uri mContentUri;
    private final ClipDescription mDescription;
    private final Uri mLinkUri;

    public InputContentInfo(Uri contentUri, ClipDescription description) {
        this(contentUri, description, null);
    }

    public InputContentInfo(Uri contentUri, ClipDescription description, Uri linkUri) {
        if (contentUri == null) {
            throw new NullPointerException("contentUri");
        }
        if (description == null) {
            throw new NullPointerException("description");
        }
        if (!"content".equals(contentUri.getScheme())) {
            throw new InvalidParameterException("contentUri must have content scheme");
        }
        if (linkUri != null) {
            String scheme = linkUri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
                throw new InvalidParameterException("linkUri must have either http or https scheme");
            }
        }
        mContentUri = contentUri;
        mDescription = description;
        mLinkUri = linkUri;
    }

    private InputContentInfo(Parcel source) {
        mContentUri = Uri.parse(source.readString());
        String label = source.readString();
        String[] mimeTypes = source.createStringArray();
        mDescription = new ClipDescription(label, mimeTypes != null ? mimeTypes : new String[0]);
        String linkStr = source.readString();
        mLinkUri = linkStr != null ? Uri.parse(linkStr) : null;
    }

    public Uri getContentUri() {
        return mContentUri;
    }

    public ClipDescription getDescription() {
        return mDescription;
    }

    public Uri getLinkUri() {
        return mLinkUri;
    }

    public void requestPermission() {
    }

    public void releasePermission() {
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mContentUri.toString());
        CharSequence label = mDescription.getLabel();
        dest.writeString(label != null ? label.toString() : null);
        int count = mDescription.getMimeTypeCount();
        String[] mimeTypes = new String[count];
        for (int i = 0; i < count; i++) {
            mimeTypes[i] = mDescription.getMimeType(i);
        }
        dest.writeStringArray(mimeTypes);
        dest.writeString(mLinkUri != null ? mLinkUri.toString() : null);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<InputContentInfo> CREATOR =
            new Parcelable.Creator<InputContentInfo>() {
                @Override
                public InputContentInfo createFromParcel(Parcel source) {
                    return new InputContentInfo(source);
                }

                @Override
                public InputContentInfo[] newArray(int size) {
                    return new InputContentInfo[size];
                }
            };
}
