// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TranslationCapability implements Parcelable {
    public static final int STATE_AVAILABLE_TO_DOWNLOAD = 1;
    public static final int STATE_DOWNLOADING = 2;
    public static final int STATE_ON_DEVICE = 3;
    public static final int STATE_NOT_AVAILABLE = 4;

    private final int mState;
    private final TranslationSpec mSourceSpec;
    private final TranslationSpec mTargetSpec;
    private final boolean mUiTranslationEnabled;
    private final int mSupportedTranslationFlags;

    TranslationCapability(
            int state,
            TranslationSpec sourceSpec,
            TranslationSpec targetSpec,
            boolean uiTranslationEnabled,
            int supportedTranslationFlags) {
        mState = state;
        mSourceSpec = Objects.requireNonNull(sourceSpec, "sourceSpec");
        mTargetSpec = Objects.requireNonNull(targetSpec, "targetSpec");
        mUiTranslationEnabled = uiTranslationEnabled;
        mSupportedTranslationFlags = supportedTranslationFlags;
    }

    private TranslationCapability(Parcel in) {
        mState = in.readInt();
        mSourceSpec = in.readTypedObject(TranslationSpec.CREATOR);
        mTargetSpec = in.readTypedObject(TranslationSpec.CREATOR);
        mUiTranslationEnabled = in.readBoolean();
        mSupportedTranslationFlags = in.readInt();
    }

    public int getState() {
        return mState;
    }

    public TranslationSpec getSourceSpec() {
        return mSourceSpec;
    }

    public TranslationSpec getTargetSpec() {
        return mTargetSpec;
    }

    public boolean isUiTranslationEnabled() {
        return mUiTranslationEnabled;
    }

    public int getSupportedTranslationFlags() {
        return mSupportedTranslationFlags;
    }

    @Override
    public String toString() {
        return "TranslationCapability { state = " + mState
                + ", sourceSpec = " + mSourceSpec
                + ", targetSpec = " + mTargetSpec
                + ", uiTranslationEnabled = " + mUiTranslationEnabled
                + ", supportedTranslationFlags = " + mSupportedTranslationFlags + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mState);
        dest.writeTypedObject(mSourceSpec, flags);
        dest.writeTypedObject(mTargetSpec, flags);
        dest.writeBoolean(mUiTranslationEnabled);
        dest.writeInt(mSupportedTranslationFlags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TranslationCapability> CREATOR =
            new Parcelable.Creator<TranslationCapability>() {
                @Override
                public TranslationCapability createFromParcel(Parcel in) {
                    return new TranslationCapability(in);
                }

                @Override
                public TranslationCapability[] newArray(int size) {
                    return new TranslationCapability[size];
                }
            };
}
