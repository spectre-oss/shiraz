// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Response object returned from a create-credential operation. */
public final class CreateCredentialResponse implements Parcelable {
    private final Bundle mData;

    public CreateCredentialResponse(Bundle data) {
        this.mData = new Bundle(Objects.requireNonNull(data, "data must not be null"));
    }

    private CreateCredentialResponse(Parcel in) {
        Bundle b = in.readBundle(CreateCredentialResponse.class.getClassLoader());
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
        return "CreateCredentialResponse {data=" + mData + "}";
    }

    public static final Parcelable.Creator<CreateCredentialResponse> CREATOR =
            new Parcelable.Creator<CreateCredentialResponse>() {
                @Override
                public CreateCredentialResponse createFromParcel(Parcel in) {
                    return new CreateCredentialResponse(in);
                }

                @Override
                public CreateCredentialResponse[] newArray(int size) {
                    return new CreateCredentialResponse[size];
                }
            };
}
