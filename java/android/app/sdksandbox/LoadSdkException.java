// SPDX-License-Identifier: GPL-3.0-only
package android.app.sdksandbox;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public final class LoadSdkException extends Exception implements Parcelable {
    private final int mLoadSdkErrorCode;
    private final Bundle mExtraInformation;

    public LoadSdkException(Throwable cause, Bundle extraInfo) {
        this(SdkSandboxManager.LOAD_SDK_SDK_DEFINED_ERROR,
                cause != null ? cause.getMessage() : null,
                cause,
                extraInfo);
    }

    public LoadSdkException(int loadSdkErrorCode, String message) {
        this(loadSdkErrorCode, message, null, new Bundle());
    }

    LoadSdkException(int loadSdkErrorCode, String message, Throwable cause, Bundle extraInfo) {
        super(message, cause);
        mLoadSdkErrorCode = loadSdkErrorCode;
        mExtraInformation = extraInfo != null ? extraInfo : new Bundle();
    }

    LoadSdkException(Parcel in) {
        this(in.readInt(), in.readString(), null, in.readBundle());
    }

    public int getLoadSdkErrorCode() {
        return mLoadSdkErrorCode;
    }

    public Bundle getExtraInformation() {
        return mExtraInformation;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mLoadSdkErrorCode);
        dest.writeString(getMessage());
        dest.writeBundle(mExtraInformation);
    }

    public static final Parcelable.Creator<LoadSdkException> CREATOR =
            new Parcelable.Creator<LoadSdkException>() {
                @Override
                public LoadSdkException createFromParcel(Parcel in) {
                    return new LoadSdkException(in);
                }

                @Override
                public LoadSdkException[] newArray(int size) {
                    return new LoadSdkException[size];
                }
            };
}
