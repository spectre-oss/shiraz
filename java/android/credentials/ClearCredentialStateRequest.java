// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Request for clearing a user's credential state across credential providers. */
public final class ClearCredentialStateRequest implements Parcelable {
    private final Bundle mData;

    public ClearCredentialStateRequest(Bundle data) {
        this.mData = new Bundle(Objects.requireNonNull(data, "data must not be null"));
    }

    private ClearCredentialStateRequest(Parcel in) {
        Bundle b = in.readBundle(ClearCredentialStateRequest.class.getClassLoader());
        this.mData = b != null ? b : new Bundle();
    }

    public Bundle getData() {
        return new Bundle(mData);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeBundle(mData);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "ClearCredentialStateRequest {data=" + mData + "}";
    }

    public static final Parcelable.Creator<ClearCredentialStateRequest> CREATOR =
            new Parcelable.Creator<ClearCredentialStateRequest>() {
                @Override
                public ClearCredentialStateRequest createFromParcel(Parcel in) {
                    return new ClearCredentialStateRequest(in);
                }

                @Override
                public ClearCredentialStateRequest[] newArray(int size) {
                    return new ClearCredentialStateRequest[size];
                }
            };
}
