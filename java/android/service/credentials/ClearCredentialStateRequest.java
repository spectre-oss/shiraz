// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Clear-credential-state request delivered to {@link CredentialProviderService#onClearCredentialState}. */
public final class ClearCredentialStateRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final Bundle mData;

    public ClearCredentialStateRequest(CallingAppInfo callingAppInfo, Bundle data) {
        this.mCallingAppInfo =
                Objects.requireNonNull(callingAppInfo, "callingAppInfo must not be null");
        this.mData = new Bundle(Objects.requireNonNull(data, "data must not be null"));
    }

    private ClearCredentialStateRequest(Parcel in) {
        this.mCallingAppInfo =
                Objects.requireNonNull(
                        in.readTypedObject(CallingAppInfo.CREATOR),
                        "callingAppInfo must not be null");
        Bundle b = in.readBundle(ClearCredentialStateRequest.class.getClassLoader());
        this.mData = b != null ? b : new Bundle();
    }

    public Bundle getData() {
        return new Bundle(mData);
    }

    public CallingAppInfo getCallingAppInfo() {
        return mCallingAppInfo;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCallingAppInfo, flags);
        dest.writeBundle(mData);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "ClearCredentialStateRequest {callingAppInfo="
                + mCallingAppInfo
                + ", data="
                + mData
                + "}";
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
