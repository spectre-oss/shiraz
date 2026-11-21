// SPDX-License-Identifier: GPL-3.0-only
package android.app.assist;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** Holds contextual content about the current foreground activity for an assistant. */
public class AssistContent implements Parcelable {
    private Intent mIntent;
    private boolean mIsAppProvidedIntent;
    private ClipData mClipData;
    private String mStructuredData;
    private Uri mUri;
    private boolean mIsAppProvidedWebUri;
    private final Bundle mExtras;

    public AssistContent() {
        mExtras = new Bundle();
    }

    AssistContent(Parcel in) {
        mIntent = in.readTypedObject(Intent.CREATOR);
        mIsAppProvidedIntent = in.readInt() != 0;
        mClipData = in.readTypedObject(ClipData.CREATOR);
        mStructuredData = in.readString();
        mUri = in.readTypedObject(Uri.CREATOR);
        mIsAppProvidedWebUri = in.readInt() != 0;
        Bundle extras = in.readBundle(AssistContent.class.getClassLoader());
        mExtras = extras != null ? extras : new Bundle();
    }

    public void setIntent(Intent intent) {
        mIntent = intent;
        mIsAppProvidedIntent = true;
    }

    public Intent getIntent() {
        return mIntent;
    }

    public boolean isAppProvidedIntent() {
        return mIsAppProvidedIntent;
    }

    public void setClipData(ClipData clip) {
        mClipData = clip;
    }

    public ClipData getClipData() {
        return mClipData;
    }

    public void setStructuredData(String structuredData) {
        mStructuredData = structuredData;
    }

    public String getStructuredData() {
        return mStructuredData;
    }

    public void setWebUri(Uri uri) {
        mUri = uri;
        mIsAppProvidedWebUri = true;
    }

    public Uri getWebUri() {
        return mUri;
    }

    public boolean isAppProvidedWebUri() {
        return mIsAppProvidedWebUri;
    }

    public Bundle getExtras() {
        return mExtras;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mIntent, flags);
        dest.writeInt(mIsAppProvidedIntent ? 1 : 0);
        dest.writeTypedObject(mClipData, flags);
        dest.writeString(mStructuredData);
        dest.writeTypedObject(mUri, flags);
        dest.writeInt(mIsAppProvidedWebUri ? 1 : 0);
        dest.writeBundle(mExtras);
    }

    public static final Parcelable.Creator<AssistContent> CREATOR =
            new Parcelable.Creator<AssistContent>() {
                @Override
                public AssistContent createFromParcel(Parcel in) {
                    return new AssistContent(in);
                }

                @Override
                public AssistContent[] newArray(int size) {
                    return new AssistContent[size];
                }
            };
}
