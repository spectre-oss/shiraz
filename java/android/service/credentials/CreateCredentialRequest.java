// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Final create-credential request passed to a credential provider UI after user selection. */
public final class CreateCredentialRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final String mType;
    private final Bundle mData;

    public CreateCredentialRequest(CallingAppInfo callingAppInfo, String type, Bundle data) {
        this.mCallingAppInfo =
                Objects.requireNonNull(callingAppInfo, "callingAppInfo must not be null");
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be null or empty");
        }
        this.mType = type;
        this.mData = new Bundle(Objects.requireNonNull(data, "data must not be null"));
    }

    private CreateCredentialRequest(Parcel in) {
        this.mCallingAppInfo =
                Objects.requireNonNull(
                        in.readTypedObject(CallingAppInfo.CREATOR),
                        "callingAppInfo must not be null");
        this.mType = Objects.requireNonNull(in.readString(), "type");
        Bundle b = in.readBundle(CreateCredentialRequest.class.getClassLoader());
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

    public static final Parcelable.Creator<CreateCredentialRequest> CREATOR =
            new Parcelable.Creator<CreateCredentialRequest>() {
                @Override
                public CreateCredentialRequest createFromParcel(Parcel in) {
                    return new CreateCredentialRequest(in);
                }

                @Override
                public CreateCredentialRequest[] newArray(int size) {
                    return new CreateCredentialRequest[size];
                }
            };
}
