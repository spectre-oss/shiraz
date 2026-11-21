// SPDX-License-Identifier: GPL-3.0-only
package android.service.autofill;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Defines user data used for Autofill field classification. */
public final class UserData implements Parcelable {
    private final String mId;
    private final String mDefaultAlgorithm;
    private final Bundle mDefaultArgs;
    private final LinkedHashMap<String, String> mCategoryAlgorithms;
    private final LinkedHashMap<String, Bundle> mCategoryArgs;
    private final ArrayList<String> mCategoryIds;
    private final ArrayList<String> mValues;

    UserData(Builder b) {
        mId = b.mId;
        mDefaultAlgorithm = b.mDefaultAlgorithm;
        mDefaultArgs = b.mDefaultArgs;
        mCategoryAlgorithms = new LinkedHashMap<String, String>(b.mCategoryAlgorithms);
        mCategoryArgs = new LinkedHashMap<String, Bundle>(b.mCategoryArgs);
        mCategoryIds = new ArrayList<String>(b.mCategoryIds);
        mValues = new ArrayList<String>(b.mValues);
    }

    UserData(Parcel in) {
        mId = in.readString();
        mDefaultAlgorithm = in.readString();
        mDefaultArgs = in.readBundle(UserData.class.getClassLoader());
        int n = in.readInt();
        mCategoryIds = new ArrayList<String>(n);
        mValues = new ArrayList<String>(n);
        for (int i = 0; i < n; i++) {
            mCategoryIds.add(in.readString());
            mValues.add(in.readString());
        }
        int cn = in.readInt();
        mCategoryAlgorithms = new LinkedHashMap<String, String>(cn);
        mCategoryArgs = new LinkedHashMap<String, Bundle>(cn);
        for (int i = 0; i < cn; i++) {
            String cat = in.readString();
            String alg = in.readString();
            Bundle args = in.readBundle(UserData.class.getClassLoader());
            mCategoryAlgorithms.put(cat, alg);
            if (args != null) mCategoryArgs.put(cat, args);
        }
    }

    public String getFieldClassificationAlgorithm() {
        return mDefaultAlgorithm;
    }

    public String getFieldClassificationAlgorithmForCategory(String categoryId) {
        if (categoryId == null) throw new NullPointerException("categoryId");
        String alg = mCategoryAlgorithms.get(categoryId);
        return alg != null ? alg : mDefaultAlgorithm;
    }

    public String getId() {
        return mId;
    }

    @Override
    public String toString() {
        return "UserData: [id=" + mId + ", entries=" + mValues.size() + "]";
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mId);
        parcel.writeString(mDefaultAlgorithm);
        parcel.writeBundle(mDefaultArgs);
        parcel.writeInt(mCategoryIds.size());
        for (int i = 0; i < mCategoryIds.size(); i++) {
            parcel.writeString(mCategoryIds.get(i));
            parcel.writeString(mValues.get(i));
        }
        parcel.writeInt(mCategoryAlgorithms.size());
        for (Map.Entry<String, String> e : mCategoryAlgorithms.entrySet()) {
            parcel.writeString(e.getKey());
            parcel.writeString(e.getValue());
            parcel.writeBundle(mCategoryArgs.get(e.getKey()));
        }
    }

    public static int getMaxUserDataSize() {
        return 50;
    }

    public static int getMaxFieldClassificationIdsSize() {
        return 10;
    }

    public static int getMaxCategoryCount() {
        return 10;
    }

    public static int getMinValueLength() {
        return 3;
    }

    public static int getMaxValueLength() {
        return 100;
    }

    public static final Parcelable.Creator<UserData> CREATOR =
            new Parcelable.Creator<UserData>() {
                @Override
                public UserData createFromParcel(Parcel parcel) {
                    return new UserData(parcel);
                }

                @Override
                public UserData[] newArray(int size) {
                    return new UserData[size];
                }
            };

    public static final class Builder {
        final String mId;
        String mDefaultAlgorithm;
        Bundle mDefaultArgs;
        final LinkedHashMap<String, String> mCategoryAlgorithms = new LinkedHashMap<String, String>();
        final LinkedHashMap<String, Bundle> mCategoryArgs = new LinkedHashMap<String, Bundle>();
        final ArrayList<String> mCategoryIds = new ArrayList<String>();
        final ArrayList<String> mValues = new ArrayList<String>();
        private boolean mDestroyed;

        public Builder(String id, String value, String categoryId) {
            if (TextUtils.isEmpty(id)) throw new IllegalArgumentException("id cannot be empty");
            mId = id;
            add(value, categoryId);
        }

        public Builder setFieldClassificationAlgorithm(String name, Bundle args) {
            mDefaultAlgorithm = name;
            mDefaultArgs = args;
            return this;
        }

        public Builder setFieldClassificationAlgorithmForCategory(
                String categoryId, String name, Bundle args) {
            if (categoryId == null) throw new NullPointerException("categoryId");
            mCategoryAlgorithms.put(categoryId, name);
            if (args != null) mCategoryArgs.put(categoryId, args);
            return this;
        }

        public Builder add(String value, String categoryId) {
            if (mDestroyed) throw new IllegalStateException("Already built");
            if (TextUtils.isEmpty(categoryId)) throw new IllegalArgumentException("categoryId");
            if (TextUtils.isEmpty(value)) throw new IllegalArgumentException("value");
            mCategoryIds.add(categoryId);
            mValues.add(value);
            return this;
        }

        public UserData build() {
            if (mDestroyed) throw new IllegalStateException("Already built");
            mDestroyed = true;
            return new UserData(this);
        }
    }
}
