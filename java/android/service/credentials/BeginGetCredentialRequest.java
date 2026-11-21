// SPDX-License-Identifier: GPL-3.0-only
package android.service.credentials;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Query request delivered to {@link CredentialProviderService#onBeginGetCredential}. */
public final class BeginGetCredentialRequest implements Parcelable {
    private final CallingAppInfo mCallingAppInfo;
    private final List<BeginGetCredentialOption> mBeginGetCredentialOptions;

    private BeginGetCredentialRequest(
            CallingAppInfo callingAppInfo,
            List<BeginGetCredentialOption> beginGetCredentialOptions) {
        this.mCallingAppInfo = callingAppInfo;
        this.mBeginGetCredentialOptions =
                Collections.unmodifiableList(
                        new ArrayList<BeginGetCredentialOption>(beginGetCredentialOptions));
    }

    private BeginGetCredentialRequest(Parcel in) {
        this.mCallingAppInfo = in.readTypedObject(CallingAppInfo.CREATOR);
        ArrayList<BeginGetCredentialOption> list =
                in.createTypedArrayList(BeginGetCredentialOption.CREATOR);
        this.mBeginGetCredentialOptions =
                Collections.unmodifiableList(
                        list != null ? list : new ArrayList<BeginGetCredentialOption>());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mCallingAppInfo, flags);
        dest.writeTypedList(mBeginGetCredentialOptions);
    }

    public CallingAppInfo getCallingAppInfo() {
        return mCallingAppInfo;
    }

    public List<BeginGetCredentialOption> getBeginGetCredentialOptions() {
        return mBeginGetCredentialOptions;
    }

    public static final class Builder {
        private CallingAppInfo mCallingAppInfo = null;
        private List<BeginGetCredentialOption> mBeginGetCredentialOptions =
                new ArrayList<BeginGetCredentialOption>();

        public Builder() {
        }

        public Builder setCallingAppInfo(CallingAppInfo callingAppInfo) {
            this.mCallingAppInfo = callingAppInfo;
            return this;
        }

        public Builder setBeginGetCredentialOptions(
                List<BeginGetCredentialOption> getBeginCredentialOptions) {
            Objects.requireNonNull(
                    getBeginCredentialOptions, "getBeginCredentialOptions must not be null");
            if (getBeginCredentialOptions.isEmpty()) {
                throw new IllegalArgumentException(
                        "getBeginCredentialOptions must not be empty");
            }
            ArrayList<BeginGetCredentialOption> copy =
                    new ArrayList<BeginGetCredentialOption>(getBeginCredentialOptions.size());
            for (BeginGetCredentialOption opt : getBeginCredentialOptions) {
                copy.add(
                        Objects.requireNonNull(
                                opt, "beginGetCredentialOption must not be null"));
            }
            this.mBeginGetCredentialOptions = copy;
            return this;
        }

        public Builder addBeginGetCredentialOption(
                BeginGetCredentialOption beginGetCredentialOption) {
            Objects.requireNonNull(
                    beginGetCredentialOption, "beginGetCredentialOption must not be null");
            this.mBeginGetCredentialOptions.add(beginGetCredentialOption);
            return this;
        }

        public BeginGetCredentialRequest build() {
            if (mBeginGetCredentialOptions.isEmpty()) {
                throw new IllegalArgumentException(
                        "beginGetCredentialOptions must not be empty");
            }
            return new BeginGetCredentialRequest(mCallingAppInfo, mBeginGetCredentialOptions);
        }
    }

    public static final Parcelable.Creator<BeginGetCredentialRequest> CREATOR =
            new Parcelable.Creator<BeginGetCredentialRequest>() {
                @Override
                public BeginGetCredentialRequest createFromParcel(Parcel in) {
                    return new BeginGetCredentialRequest(in);
                }

                @Override
                public BeginGetCredentialRequest[] newArray(int size) {
                    return new BeginGetCredentialRequest[size];
                }
            };
}
