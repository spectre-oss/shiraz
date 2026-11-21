// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Response object encapsulating the {@link Credential} returned from a get-credential flow. */
public final class GetCredentialResponse implements Parcelable {
    private final Credential mCredential;

    public GetCredentialResponse(Credential credential) {
        this.mCredential = Objects.requireNonNull(credential, "credential must not be null");
    }

    private GetCredentialResponse(Parcel in) {
        this.mCredential =
                Objects.requireNonNull(
                        in.readTypedObject(Credential.CREATOR), "credential must not be null");
    }

    public Credential getCredential() {
        return mCredential;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCredential, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return "GetCredentialResponse {credential=" + mCredential + "}";
    }

    public static final Parcelable.Creator<GetCredentialResponse> CREATOR =
            new Parcelable.Creator<GetCredentialResponse>() {
                @Override
                public GetCredentialResponse createFromParcel(Parcel in) {
                    return new GetCredentialResponse(in);
                }

                @Override
                public GetCredentialResponse[] newArray(int size) {
                    return new GetCredentialResponse[size];
                }
            };
}
