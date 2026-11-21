// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.credentials.CredentialOption;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Final get-credential request passed to a credential provider UI after user selection. */
public final class GetCredentialRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final List<CredentialOption> mCredentialOptions;

    public GetCredentialRequest(
            CallingAppInfo callingAppInfo, List<CredentialOption> credentialOptions) {
        this.mCallingAppInfo =
                Objects.requireNonNull(callingAppInfo, "callingAppInfo must not be null");
        Objects.requireNonNull(credentialOptions, "credentialOptions must not be null");
        if (credentialOptions.isEmpty()) {
            throw new IllegalArgumentException("credentialOptions must not be empty");
        }
        ArrayList<CredentialOption> copy =
                new ArrayList<CredentialOption>(credentialOptions.size());
        for (CredentialOption option : credentialOptions) {
            copy.add(Objects.requireNonNull(option, "credentialOption must not be null"));
        }
        this.mCredentialOptions = Collections.unmodifiableList(copy);
    }

    private GetCredentialRequest(Parcel in) {
        this.mCallingAppInfo =
                Objects.requireNonNull(
                        in.readTypedObject(CallingAppInfo.CREATOR),
                        "callingAppInfo must not be null");
        ArrayList<CredentialOption> list = in.createTypedArrayList(CredentialOption.CREATOR);
        this.mCredentialOptions =
                Collections.unmodifiableList(
                        list != null ? list : new ArrayList<CredentialOption>());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCallingAppInfo, flags);
        dest.writeTypedList(mCredentialOptions);
    }

    public CallingAppInfo getCallingAppInfo() {
        return mCallingAppInfo;
    }

    public List<CredentialOption> getCredentialOptions() {
        return mCredentialOptions;
    }

    public static final Parcelable.Creator<GetCredentialRequest> CREATOR =
            new Parcelable.Creator<GetCredentialRequest>() {
                @Override
                public GetCredentialRequest createFromParcel(Parcel in) {
                    return new GetCredentialRequest(in);
                }

                @Override
                public GetCredentialRequest[] newArray(int size) {
                    return new GetCredentialRequest[size];
                }
            };
}
