// SPDX-License-Identifier: GPL-3.0-only
package android.health.connect.changelog;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class ChangeLogTokenResponse implements Parcelable {
    public static final Parcelable.Creator<ChangeLogTokenResponse> CREATOR =
            new Parcelable.Creator<ChangeLogTokenResponse>() {
                @Override
                public ChangeLogTokenResponse createFromParcel(Parcel in) {
                    String token = in.readString();
                    return new ChangeLogTokenResponse(token != null ? token : "");
                }

                @Override
                public ChangeLogTokenResponse[] newArray(int size) {
                    return new ChangeLogTokenResponse[size];
                }
            };

    private final String mToken;

    public ChangeLogTokenResponse(String token) {
        mToken = Objects.requireNonNull(token, "token must not be null");
    }

    public String getToken() {
        return mToken;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mToken);
    }
}
