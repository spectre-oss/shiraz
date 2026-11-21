// SPDX-License-Identifier: GPL-3.0-only
package android.telephony.euicc;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

public final class DownloadableSubscription implements Parcelable {
    private final String encodedActivationCode;
    private final String confirmationCode;

    private DownloadableSubscription(String encodedActivationCode, String confirmationCode) {
        this.encodedActivationCode = encodedActivationCode;
        this.confirmationCode = confirmationCode;
    }

    private DownloadableSubscription(Parcel in) {
        encodedActivationCode = in.readString();
        confirmationCode = in.readString();
    }

    public String getEncodedActivationCode() {
        return encodedActivationCode;
    }

    public static DownloadableSubscription forActivationCode(String encodedActivationCode) {
        Objects.requireNonNull(encodedActivationCode, "Activation code may not be null");
        return new DownloadableSubscription(encodedActivationCode, null);
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(encodedActivationCode);
        dest.writeString(confirmationCode);
    }

    public int describeContents() {
        return 0;
    }

    public static final class Builder {
        private String encodedActivationCode;
        private String confirmationCode;

        public Builder(DownloadableSubscription baseSubscription) {
            encodedActivationCode = baseSubscription.getEncodedActivationCode();
            confirmationCode = baseSubscription.getConfirmationCode();
        }

        public Builder(String encodedActivationCode) {
            this.encodedActivationCode = encodedActivationCode;
        }

        public DownloadableSubscription build() {
            return new DownloadableSubscription(encodedActivationCode, confirmationCode);
        }

        public Builder setEncodedActivationCode(String value) {
            encodedActivationCode = value;
            return this;
        }

        public Builder setConfirmationCode(String value) {
            confirmationCode = value;
            return this;
        }
    }

    public static final Parcelable.Creator<DownloadableSubscription> CREATOR =
            new Parcelable.Creator<DownloadableSubscription>() {
                public DownloadableSubscription createFromParcel(Parcel in) {
                    return new DownloadableSubscription(in);
                }

                public DownloadableSubscription[] newArray(int size) {
                    return new DownloadableSubscription[size];
                }
            };
}
