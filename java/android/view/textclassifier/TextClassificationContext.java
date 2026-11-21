// SPDX-License-Identifier: GPL-3.0-only

package android.view.textclassifier;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class TextClassificationContext implements Parcelable {
    private final String mPackageName;
    private final String mWidgetType;
    private final String mWidgetVersion;

    private TextClassificationContext(
            String packageName, String widgetType, String widgetVersion) {
        mPackageName = Objects.requireNonNull(packageName);
        mWidgetType = Objects.requireNonNull(widgetType);
        mWidgetVersion = widgetVersion;
    }

    private TextClassificationContext(Parcel in) {
        mPackageName = in.readString();
        mWidgetType = in.readString();
        mWidgetVersion = in.readString();
    }

    public String getPackageName() {
        return mPackageName;
    }

    public String getWidgetType() {
        return mWidgetType;
    }

    public String getWidgetVersion() {
        return mWidgetVersion;
    }

    @Override
    public String toString() {
        return String.format(
                "TextClassificationContext{packageName=%s, widgetType=%s, widgetVersion=%s}",
                mPackageName, mWidgetType, mWidgetVersion);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mPackageName);
        parcel.writeString(mWidgetType);
        parcel.writeString(mWidgetVersion);
    }

    public static final Parcelable.Creator<TextClassificationContext> CREATOR =
            new Parcelable.Creator<TextClassificationContext>() {
                @Override
                public TextClassificationContext createFromParcel(Parcel parcel) {
                    return new TextClassificationContext(parcel);
                }

                @Override
                public TextClassificationContext[] newArray(int size) {
                    return new TextClassificationContext[size];
                }
            };

    public static final class Builder {
        private final String mPackageName;
        private final String mWidgetType;
        private String mWidgetVersion;

        public Builder(String packageName, String widgetType) {
            mPackageName = Objects.requireNonNull(packageName);
            mWidgetType = Objects.requireNonNull(widgetType);
        }

        public Builder setWidgetVersion(String widgetVersion) {
            mWidgetVersion = widgetVersion;
            return this;
        }

        public TextClassificationContext build() {
            return new TextClassificationContext(mPackageName, mWidgetType, mWidgetVersion);
        }
    }
}
