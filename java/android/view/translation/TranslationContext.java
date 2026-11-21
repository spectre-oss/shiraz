// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TranslationContext implements Parcelable {
    public static final int FLAG_LOW_LATENCY = 1;
    public static final int FLAG_TRANSLITERATION = 2;
    public static final int FLAG_DEFINITIONS = 4;

    private final TranslationSpec mSourceSpec;
    private final TranslationSpec mTargetSpec;
    private final int mTranslationFlags;

    TranslationContext(
            TranslationSpec sourceSpec,
            TranslationSpec targetSpec,
            int translationFlags) {
        mSourceSpec = Objects.requireNonNull(sourceSpec, "sourceSpec");
        mTargetSpec = Objects.requireNonNull(targetSpec, "targetSpec");
        mTranslationFlags = translationFlags;
    }

    private TranslationContext(Parcel in) {
        mSourceSpec = in.readTypedObject(TranslationSpec.CREATOR);
        mTargetSpec = in.readTypedObject(TranslationSpec.CREATOR);
        mTranslationFlags = in.readInt();
    }

    public TranslationSpec getSourceSpec() {
        return mSourceSpec;
    }

    public TranslationSpec getTargetSpec() {
        return mTargetSpec;
    }

    public int getTranslationFlags() {
        return mTranslationFlags;
    }

    @Override
    public String toString() {
        return "TranslationContext { sourceSpec = " + mSourceSpec
                + ", targetSpec = " + mTargetSpec
                + ", translationFlags = " + mTranslationFlags + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mSourceSpec, flags);
        dest.writeTypedObject(mTargetSpec, flags);
        dest.writeInt(mTranslationFlags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TranslationContext> CREATOR =
            new Parcelable.Creator<TranslationContext>() {
                @Override
                public TranslationContext createFromParcel(Parcel in) {
                    return new TranslationContext(in);
                }

                @Override
                public TranslationContext[] newArray(int size) {
                    return new TranslationContext[size];
                }
            };

    public static final class Builder {
        private final TranslationSpec mSourceSpec;
        private final TranslationSpec mTargetSpec;
        private int mTranslationFlags;

        public Builder(TranslationSpec sourceSpec, TranslationSpec targetSpec) {
            mSourceSpec = Objects.requireNonNull(sourceSpec, "sourceSpec");
            mTargetSpec = Objects.requireNonNull(targetSpec, "targetSpec");
        }

        public Builder setTranslationFlags(int value) {
            mTranslationFlags = value;
            return this;
        }

        public TranslationContext build() {
            return new TranslationContext(mSourceSpec, mTargetSpec, mTranslationFlags);
        }
    }
}
