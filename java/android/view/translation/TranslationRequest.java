// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TranslationRequest implements Parcelable {
    public static final int FLAG_TRANSLATION_RESULT = 1;
    public static final int FLAG_DICTIONARY_RESULT = 2;
    public static final int FLAG_TRANSLITERATION_RESULT = 4;
    public static final int FLAG_PARTIAL_RESPONSES = 8;

    private final int mFlags;
    private final List<TranslationRequestValue> mTranslationRequestValues;
    private final List<ViewTranslationRequest> mViewTranslationRequests;

    TranslationRequest(
            int flags,
            List<TranslationRequestValue> translationRequestValues,
            List<ViewTranslationRequest> viewTranslationRequests) {
        mFlags = flags;
        mTranslationRequestValues = translationRequestValues != null
                ? Collections.unmodifiableList(
                        new ArrayList<TranslationRequestValue>(translationRequestValues))
                : Collections.<TranslationRequestValue>emptyList();
        mViewTranslationRequests = viewTranslationRequests != null
                ? Collections.unmodifiableList(
                        new ArrayList<ViewTranslationRequest>(viewTranslationRequests))
                : Collections.<ViewTranslationRequest>emptyList();
    }

    private TranslationRequest(Parcel in) {
        mFlags = in.readInt();
        List<TranslationRequestValue> reqVals =
                in.createTypedArrayList(TranslationRequestValue.CREATOR);
        mTranslationRequestValues = reqVals != null
                ? Collections.unmodifiableList(reqVals)
                : Collections.<TranslationRequestValue>emptyList();
        List<ViewTranslationRequest> viewVals =
                in.createTypedArrayList(ViewTranslationRequest.CREATOR);
        mViewTranslationRequests = viewVals != null
                ? Collections.unmodifiableList(viewVals)
                : Collections.<ViewTranslationRequest>emptyList();
    }

    public int getFlags() {
        return mFlags;
    }

    public List<TranslationRequestValue> getTranslationRequestValues() {
        return mTranslationRequestValues;
    }

    public List<ViewTranslationRequest> getViewTranslationRequests() {
        return mViewTranslationRequests;
    }

    @Override
    public String toString() {
        return "TranslationRequest { flags = " + mFlags
                + ", translationRequestValues = " + mTranslationRequestValues
                + ", viewTranslationRequests = " + mViewTranslationRequests + " }";
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mFlags);
        dest.writeTypedList(mTranslationRequestValues);
        dest.writeTypedList(mViewTranslationRequests);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<TranslationRequest> CREATOR =
            new Parcelable.Creator<TranslationRequest>() {
                @Override
                public TranslationRequest createFromParcel(Parcel in) {
                    return new TranslationRequest(in);
                }

                @Override
                public TranslationRequest[] newArray(int size) {
                    return new TranslationRequest[size];
                }
            };

    public static final class Builder {
        private int mFlags = FLAG_TRANSLATION_RESULT;
        private List<TranslationRequestValue> mTranslationRequestValues =
                Collections.emptyList();
        private List<ViewTranslationRequest> mViewTranslationRequests =
                Collections.emptyList();

        public Builder() {
        }

        public Builder setFlags(int value) {
            mFlags = value;
            return this;
        }

        public Builder setTranslationRequestValues(List<TranslationRequestValue> value) {
            mTranslationRequestValues = Objects.requireNonNull(value, "value");
            return this;
        }

        public Builder setViewTranslationRequests(List<ViewTranslationRequest> value) {
            mViewTranslationRequests = Objects.requireNonNull(value, "value");
            return this;
        }

        public TranslationRequest build() {
            return new TranslationRequest(
                    mFlags, mTranslationRequestValues, mViewTranslationRequests);
        }
    }
}
