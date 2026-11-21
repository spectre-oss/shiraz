// SPDX-License-Identifier: GPL-3.0-only

package android.view.translation;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.autofill.AutofillId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ViewTranslationResponse implements Parcelable {
    private final AutofillId mAutofillId;
    private final Map<String, TranslationResponseValue> mTranslationResponseValues;

    ViewTranslationResponse(
            AutofillId autofillId, Map<String, TranslationResponseValue> values) {
        mAutofillId = Objects.requireNonNull(autofillId, "autofillId");
        mTranslationResponseValues = Collections.unmodifiableMap(
                new LinkedHashMap<String, TranslationResponseValue>(values));
    }

    private ViewTranslationResponse(Parcel in) {
        mAutofillId = in.readTypedObject(AutofillId.CREATOR);
        int size = in.readInt();
        Map<String, TranslationResponseValue> map =
                new LinkedHashMap<String, TranslationResponseValue>(size);
        for (int i = 0; i < size; i++) {
            String key = in.readString();
            TranslationResponseValue val = in.readTypedObject(TranslationResponseValue.CREATOR);
            map.put(key, val);
        }
        mTranslationResponseValues = Collections.unmodifiableMap(map);
    }

    public TranslationResponseValue getValue(String key) {
        Objects.requireNonNull(key, "key");
        if (!mTranslationResponseValues.containsKey(key)) {
            throw new IllegalArgumentException("Response does not contain value for key=" + key);
        }
        return mTranslationResponseValues.get(key);
    }

    public Set<String> getKeys() {
        return mTranslationResponseValues.keySet();
    }

    public AutofillId getAutofillId() {
        return mAutofillId;
    }

    @Override
    public String toString() {
        return "ViewTranslationResponse { autofillId = " + mAutofillId
                + ", translationResponseValues = " + mTranslationResponseValues + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ViewTranslationResponse)) return false;
        ViewTranslationResponse that = (ViewTranslationResponse) o;
        return Objects.equals(mAutofillId, that.mAutofillId)
                && Objects.equals(mTranslationResponseValues, that.mTranslationResponseValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAutofillId, mTranslationResponseValues);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mAutofillId, flags);
        dest.writeInt(mTranslationResponseValues.size());
        for (Map.Entry<String, TranslationResponseValue> e :
                mTranslationResponseValues.entrySet()) {
            dest.writeString(e.getKey());
            dest.writeTypedObject(e.getValue(), flags);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ViewTranslationResponse> CREATOR =
            new Parcelable.Creator<ViewTranslationResponse>() {
                @Override
                public ViewTranslationResponse createFromParcel(Parcel in) {
                    return new ViewTranslationResponse(in);
                }

                @Override
                public ViewTranslationResponse[] newArray(int size) {
                    return new ViewTranslationResponse[size];
                }
            };

    public static final class Builder {
        private final AutofillId mAutofillId;
        private final Map<String, TranslationResponseValue> mValues =
                new LinkedHashMap<String, TranslationResponseValue>();

        public Builder(AutofillId autofillId) {
            mAutofillId = Objects.requireNonNull(autofillId, "autofillId");
        }

        public ViewTranslationResponse build() {
            return new ViewTranslationResponse(mAutofillId, mValues);
        }

        public Builder setValue(String key, TranslationResponseValue value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            mValues.put(key, value);
            return this;
        }
    }
}
