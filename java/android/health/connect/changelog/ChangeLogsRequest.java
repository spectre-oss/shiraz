// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.changelog;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ChangeLogsRequest implements Parcelable {
    public static final Parcelable.Creator<ChangeLogsRequest> CREATOR =
            new Parcelable.Creator<ChangeLogsRequest>() {
                @Override
                public ChangeLogsRequest createFromParcel(Parcel in) {
                    String token = in.readString();
                    int pageSize = in.readInt();
                    return new ChangeLogsRequest(token != null ? token : "", pageSize);
                }

                @Override
                public ChangeLogsRequest[] newArray(int size) {
                    return new ChangeLogsRequest[size];
                }
            };

    private final String mToken;
    private final int mPageSize;

    ChangeLogsRequest(String token, int pageSize) {
        mToken = Objects.requireNonNull(token, "token must not be null");
        mPageSize = pageSize;
    }

    public String getToken() {
        return mToken;
    }

    public int getPageSize() {
        return mPageSize;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mToken);
        dest.writeInt(mPageSize);
    }

    public static final class Builder {
        private final String mToken;
        private int mPageSize = 1000;

        public Builder(String token) {
            mToken = Objects.requireNonNull(token, "token must not be null");
        }

        public Builder setPageSize(int pageSize) {
            if (pageSize < 1 || pageSize > 5000) {
                throw new IllegalArgumentException("pageSize must be in [1, 5000]");
            }
            mPageSize = pageSize;
            return this;
        }

        public ChangeLogsRequest build() {
            return new ChangeLogsRequest(mToken, mPageSize);
        }
    }
}
