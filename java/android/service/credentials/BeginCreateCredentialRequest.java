// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Query request delivered to {@link CredentialProviderService#onBeginCreateCredential}. */
public final class BeginCreateCredentialRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final String mType;
    private final Bundle mData;

    public BeginCreateCredentialRequest(
            String type, Bundle data, CallingAppInfo callingAppInfo) {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be null or empty");
        }
        this.mType = type;
        this.mData = data == null ? new Bundle() : new Bundle(data);
        this.mCallingAppInfo = callingAppInfo;
    }

    public BeginCreateCredentialRequest(String type, Bundle data) {
        this(type, data, null);
    }

    private BeginCreateCredentialRequest(Parcel in) {
        this.mCallingAppInfo = in.readTypedObject(CallingAppInfo.CREATOR);
        this.mType = Objects.requireNonNull(in.readString(), "type");
        Bundle b = in.readBundle(BeginCreateCredentialRequest.class.getClassLoader());
        this.mData = b != null ? b : new Bundle();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCallingAppInfo, flags);
        dest.writeString(mType);
        dest.writeBundle(mData);
    }

    public CallingAppInfo getCallingAppInfo() {
        return mCallingAppInfo;
    }

    public String getType() {
        return mType;
    }

    public Bundle getData() {
        return new Bundle(mData);
    }

    public static final Parcelable.Creator<BeginCreateCredentialRequest> CREATOR =
            new Parcelable.Creator<BeginCreateCredentialRequest>() {
                @Override
                public BeginCreateCredentialRequest createFromParcel(Parcel in) {
                    return new BeginCreateCredentialRequest(in);
                }

                @Override
                public BeginCreateCredentialRequest[] newArray(int size) {
                    return new BeginCreateCredentialRequest[size];
                }
            };
}
