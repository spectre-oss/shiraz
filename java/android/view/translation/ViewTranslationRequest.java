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

public final class ViewTranslationRequest implements Parcelable {
    public static final String ID_TEXT = "android:text";

    private final AutofillId mAutofillId;
    private final Map<String, TranslationRequestValue> mTranslationRequestValues;

    ViewTranslationRequest(
            AutofillId autofillId, Map<String, TranslationRequestValue> values) {
        mAutofillId = Objects.requireNonNull(autofillId, "autofillId");
        mTranslationRequestValues = Collections.unmodifiableMap(
                new LinkedHashMap<String, TranslationRequestValue>(values));
    }

    private ViewTranslationRequest(Parcel in) {
        mAutofillId = in.readTypedObject(AutofillId.CREATOR);
        int size = in.readInt();
        Map<String, TranslationRequestValue> map =
                new LinkedHashMap<String, TranslationRequestValue>(size);
        for (int i = 0; i < size; i++) {
            String key = in.readString();
            TranslationRequestValue val = in.readTypedObject(TranslationRequestValue.CREATOR);
            map.put(key, val);
        }
        mTranslationRequestValues = Collections.unmodifiableMap(map);
    }

    public TranslationRequestValue getValue(String key) {
        Objects.requireNonNull(key, "key must not be null");
        if (!mTranslationRequestValues.containsKey(key)) {
            throw new IllegalArgumentException("Request does not contain value for key=" + key);
        }
        return mTranslationRequestValues.get(key);
    }

    public Set<String> getKeys() {
        return mTranslationRequestValues.keySet();
    }

    public AutofillId getAutofillId() {
        return mAutofillId;
    }

    @Override
    public String toString() {
        return "ViewTranslationRequest { autofillId = " + mAutofillId
                + ", translationRequestValues = " + mTranslationRequestValues + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ViewTranslationRequest)) return false;
        ViewTranslationRequest that = (ViewTranslationRequest) o;
        return Objects.equals(mAutofillId, that.mAutofillId)
                && Objects.equals(mTranslationRequestValues, that.mTranslationRequestValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAutofillId, mTranslationRequestValues);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mAutofillId, flags);
        dest.writeInt(mTranslationRequestValues.size());
        for (Map.Entry<String, TranslationRequestValue> e : mTranslationRequestValues.entrySet()) {
            dest.writeString(e.getKey());
            dest.writeTypedObject(e.getValue(), flags);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<ViewTranslationRequest> CREATOR =
            new Parcelable.Creator<ViewTranslationRequest>() {
                @Override
                public ViewTranslationRequest createFromParcel(Parcel in) {
                    return new ViewTranslationRequest(in);
                }

                @Override
                public ViewTranslationRequest[] newArray(int size) {
                    return new ViewTranslationRequest[size];
                }
            };

    public static final class Builder {
        private final AutofillId mAutofillId;
        private final Map<String, TranslationRequestValue> mValues =
                new LinkedHashMap<String, TranslationRequestValue>();

        public Builder(AutofillId autofillId) {
            mAutofillId = Objects.requireNonNull(autofillId, "autofillId");
        }

        public Builder(AutofillId autofillId, long virtualChildId) {
            Objects.requireNonNull(autofillId, "autofillId");
            mAutofillId = new AutofillId(autofillId, (int) virtualChildId);
        }

        public Builder setValue(String key, TranslationRequestValue value) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            mValues.put(key, value);
            return this;
        }

        public ViewTranslationRequest build() {
            return new ViewTranslationRequest(mAutofillId, mValues);
        }
    }
}
