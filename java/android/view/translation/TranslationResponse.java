// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import java.util.Objects;

public final class TranslationResponse implements Parcelable {
    public static final int TRANSLATION_STATUS_SUCCESS = 0;
    public static final int TRANSLATION_STATUS_UNKNOWN_ERROR = 1;
    public static final int TRANSLATION_STATUS_CONTEXT_UNSUPPORTED = 2;

    private final int mTranslationStatus;
    private final SparseArray<TranslationResponseValue> mTranslationResponseValues;
    private final SparseArray<ViewTranslationResponse> mViewTranslationResponses;
    private final boolean mFinalResponse;

    TranslationResponse(
            int translationStatus,
            SparseArray<TranslationResponseValue> translationResponseValues,
            SparseArray<ViewTranslationResponse> viewTranslationResponses,
            boolean finalResponse) {
        mTranslationStatus = translationStatus;
        mTranslationResponseValues = translationResponseValues != null
                ? translationResponseValues
                : new SparseArray<TranslationResponseValue>();
        mViewTranslationResponses = viewTranslationResponses != null
                ? viewTranslationResponses
                : new SparseArray<ViewTranslationResponse>();
        mFinalResponse = finalResponse;
    }

    private TranslationResponse(Parcel in) {
        mTranslationStatus = in.readInt();
        int valCount = in.readInt();
        mTranslationResponseValues = new SparseArray<TranslationResponseValue>(valCount);
        for (int i = 0; i < valCount; i++) {
            int k = in.readInt();
            TranslationResponseValue v = in.readTypedObject(TranslationResponseValue.CREATOR);
            mTranslationResponseValues.put(k, v);
        }
        int viewCount = in.readInt();
        mViewTranslationResponses = new SparseArray<ViewTranslationResponse>(viewCount);
        for (int i = 0; i < viewCount; i++) {
            int k = in.readInt();
            ViewTranslationResponse v = in.readTypedObject(ViewTranslationResponse.CREATOR);
            mViewTranslationResponses.put(k, v);
        }
        mFinalResponse = in.readBoolean();
    }

    public int getTranslationStatus() {
        return mTranslationStatus;
    }

    public SparseArray<TranslationResponseValue> getTranslationResponseValues() {
        return mTranslationResponseValues;
    }

    public SparseArray<ViewTranslationResponse> getViewTranslationResponses() {
        return mViewTranslationResponses;
    }

    public boolean isFinalResponse() {
        return mFinalResponse;
    }

    @Override
    public String toString() {
        return "TranslationResponse { translationStatus = " + mTranslationStatus
                + ", translationResponseValues = " + mTranslationResponseValues
                + ", viewTranslationResponses = " + mViewTranslationResponses
                + ", finalResponse = " + mFinalResponse + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mTranslationStatus);
        int valCount = mTranslationResponseValues.size();
        dest.writeInt(valCount);
        for (int i = 0; i < valCount; i++) {
            dest.writeInt(mTranslationResponseValues.keyAt(i));
            dest.writeTypedObject(mTranslationResponseValues.valueAt(i), flags);
        }
        int viewCount = mViewTranslationResponses.size();
        dest.writeInt(viewCount);
        for (int i = 0; i < viewCount; i++) {
            dest.writeInt(mViewTranslationResponses.keyAt(i));
            dest.writeTypedObject(mViewTranslationResponses.valueAt(i), flags);
        }
        dest.writeBoolean(mFinalResponse);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TranslationResponse> CREATOR =
            new Parcelable.Creator<TranslationResponse>() {
                @Override
                public TranslationResponse createFromParcel(Parcel in) {
                    return new TranslationResponse(in);
                }

                @Override
                public TranslationResponse[] newArray(int size) {
                    return new TranslationResponse[size];
                }
            };

    public static final class Builder {
        private final int mTranslationStatus;
        private SparseArray<TranslationResponseValue> mTranslationResponseValues =
                new SparseArray<TranslationResponseValue>();
        private SparseArray<ViewTranslationResponse> mViewTranslationResponses =
                new SparseArray<ViewTranslationResponse>();
        private boolean mFinalResponse = true;

        public Builder(int translationStatus) {
            mTranslationStatus = translationStatus;
        }

        public Builder setTranslationResponseValues(
                SparseArray<TranslationResponseValue> value) {
            mTranslationResponseValues = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder setViewTranslationResponses(
                SparseArray<ViewTranslationResponse> value) {
            mViewTranslationResponses = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder setFinalResponse(boolean value) {
            mFinalResponse = value;
            return this;
        }

        public TranslationResponse build() {
            return new TranslationResponse(
                    mTranslationStatus,
                    mTranslationResponseValues,
                    mViewTranslationResponses,
                    mFinalResponse);
        }

        public Builder setTranslationResponseValue(
                int index, TranslationResponseValue value) {
            Objects.requireNonNull(value, "value");
            mTranslationResponseValues.put(index, value);
            return this;
        }

        public Builder setViewTranslationResponse(
                int index, ViewTranslationResponse value) {
            Objects.requireNonNull(value, "value");
            mViewTranslationResponses.put(index, value);
            return this;
        }
    }
}
