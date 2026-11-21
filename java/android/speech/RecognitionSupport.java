// SPDX-License-Identifier: GPL-3.0-only
package android.speech;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RecognitionSupport implements Parcelable {
    private final List<String> mInstalledOnDeviceLanguages;
    private final List<String> mPendingOnDeviceLanguages;
    private final List<String> mSupportedOnDeviceLanguages;
    private final List<String> mOnlineLanguages;

    private RecognitionSupport(
            List<String> installedOnDeviceLanguages,
            List<String> pendingOnDeviceLanguages,
            List<String> supportedOnDeviceLanguages,
            List<String> onlineLanguages) {
        mInstalledOnDeviceLanguages = freezeList(installedOnDeviceLanguages);
        mPendingOnDeviceLanguages = freezeList(pendingOnDeviceLanguages);
        mSupportedOnDeviceLanguages = freezeList(supportedOnDeviceLanguages);
        mOnlineLanguages = freezeList(onlineLanguages);
    }

    private RecognitionSupport(Parcel in) {
        mInstalledOnDeviceLanguages = readStringList(in);
        mPendingOnDeviceLanguages = readStringList(in);
        mSupportedOnDeviceLanguages = readStringList(in);
        mOnlineLanguages = readStringList(in);
    }

    private static List<String> freezeList(List<String> list) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(list));
    }

    private static List<String> readStringList(Parcel in) {
        String[] arr = in.createStringArray();
        if (arr == null || arr.length == 0) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(arr)));
    }

    public List<String> getInstalledOnDeviceLanguages() {
        return mInstalledOnDeviceLanguages;
    }

    public List<String> getPendingOnDeviceLanguages() {
        return mPendingOnDeviceLanguages;
    }

    public List<String> getSupportedOnDeviceLanguages() {
        return mSupportedOnDeviceLanguages;
    }

    public List<String> getOnlineLanguages() {
        return mOnlineLanguages;
    }

    @Override
    public String toString() {
        return "RecognitionSupport { installedOnDeviceLanguages = "
                + mInstalledOnDeviceLanguages
                + ", pendingOnDeviceLanguages = " + mPendingOnDeviceLanguages
                + ", supportedOnDeviceLanguages = " + mSupportedOnDeviceLanguages
                + ", onlineLanguages = " + mOnlineLanguages + " }";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RecognitionSupport that = (RecognitionSupport) o;
        return Objects.equals(mInstalledOnDeviceLanguages, that.mInstalledOnDeviceLanguages)
                && Objects.equals(mPendingOnDeviceLanguages, that.mPendingOnDeviceLanguages)
                && Objects.equals(mSupportedOnDeviceLanguages, that.mSupportedOnDeviceLanguages)
                && Objects.equals(mOnlineLanguages, that.mOnlineLanguages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mInstalledOnDeviceLanguages,
                mPendingOnDeviceLanguages,
                mSupportedOnDeviceLanguages,
                mOnlineLanguages);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(mInstalledOnDeviceLanguages.toArray(
                new String[mInstalledOnDeviceLanguages.size()]));
        dest.writeStringArray(mPendingOnDeviceLanguages.toArray(
                new String[mPendingOnDeviceLanguages.size()]));
        dest.writeStringArray(mSupportedOnDeviceLanguages.toArray(
                new String[mSupportedOnDeviceLanguages.size()]));
        dest.writeStringArray(mOnlineLanguages.toArray(
                new String[mOnlineLanguages.size()]));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<RecognitionSupport> CREATOR =
            new Parcelable.Creator<RecognitionSupport>() {
                @Override
                public RecognitionSupport createFromParcel(Parcel in) {
                    return new RecognitionSupport(in);
                }

                @Override
                public RecognitionSupport[] newArray(int size) {
                    return new RecognitionSupport[size];
                }
            };

    public static final class Builder {
        private ArrayList<String> mInstalledOnDeviceLanguages = new ArrayList<String>();
        private ArrayList<String> mPendingOnDeviceLanguages = new ArrayList<String>();
        private ArrayList<String> mSupportedOnDeviceLanguages = new ArrayList<String>();
        private ArrayList<String> mOnlineLanguages = new ArrayList<String>();

        public Builder() {
        }

        public Builder setInstalledOnDeviceLanguages(List<String> value) {
            Objects.requireNonNull(value, "value must not be null");
            mInstalledOnDeviceLanguages = new ArrayList<String>(value);
            return this;
        }

        public Builder addInstalledOnDeviceLanguage(String value) {
            Objects.requireNonNull(value, "value must not be null");
            mInstalledOnDeviceLanguages.add(value);
            return this;
        }

        public Builder setPendingOnDeviceLanguages(List<String> value) {
            Objects.requireNonNull(value, "value must not be null");
            mPendingOnDeviceLanguages = new ArrayList<String>(value);
            return this;
        }

        public Builder addPendingOnDeviceLanguage(String value) {
            Objects.requireNonNull(value, "value must not be null");
            mPendingOnDeviceLanguages.add(value);
            return this;
        }

        public Builder setSupportedOnDeviceLanguages(List<String> value) {
            Objects.requireNonNull(value, "value must not be null");
            mSupportedOnDeviceLanguages = new ArrayList<String>(value);
            return this;
        }

        public Builder addSupportedOnDeviceLanguage(String value) {
            Objects.requireNonNull(value, "value must not be null");
            mSupportedOnDeviceLanguages.add(value);
            return this;
        }

        public Builder setOnlineLanguages(List<String> value) {
            Objects.requireNonNull(value, "value must not be null");
            mOnlineLanguages = new ArrayList<String>(value);
            return this;
        }

        public Builder addOnlineLanguage(String value) {
            Objects.requireNonNull(value, "value must not be null");
            mOnlineLanguages.add(value);
            return this;
        }

        public RecognitionSupport build() {
            return new RecognitionSupport(
                    mInstalledOnDeviceLanguages,
                    mPendingOnDeviceLanguages,
                    mSupportedOnDeviceLanguages,
                    mOnlineLanguages);
        }
    }
}
