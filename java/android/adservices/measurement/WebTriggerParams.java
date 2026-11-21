// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.measurement;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Parameters for an individual web attribution trigger registration URI. */
public final class WebTriggerParams implements Parcelable {
    private final Uri mRegistrationUri;
    private final boolean mDebugKeyAllowed;

    private WebTriggerParams(Uri registrationUri, boolean debugKeyAllowed) {
        this.mRegistrationUri =
                Objects.requireNonNull(registrationUri, "registrationUri");
        this.mDebugKeyAllowed = debugKeyAllowed;
    }

    public Uri getRegistrationUri() {
        return mRegistrationUri;
    }

    public boolean isDebugKeyAllowed() {
        return mDebugKeyAllowed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebTriggerParams)) {
            return false;
        }
        WebTriggerParams that = (WebTriggerParams) o;
        return mDebugKeyAllowed == that.mDebugKeyAllowed
                && Objects.equals(mRegistrationUri, that.mRegistrationUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRegistrationUri, Boolean.valueOf(mDebugKeyAllowed));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        Objects.requireNonNull(out, "out");
        out.writeString(mRegistrationUri.toString());
        out.writeBoolean(mDebugKeyAllowed);
    }

    public static final class Builder {
        private final Uri mRegistrationUri;
        private boolean mDebugKeyAllowed;

        public Builder(Uri registrationUri) {
            this.mRegistrationUri =
                    Objects.requireNonNull(registrationUri, "registrationUri");
        }

        public Builder setDebugKeyAllowed(boolean debugKeyAllowed) {
            this.mDebugKeyAllowed = debugKeyAllowed;
            return this;
        }

        public WebTriggerParams build() {
            return new WebTriggerParams(mRegistrationUri, mDebugKeyAllowed);
        }
    }

    public static final Parcelable.Creator<WebTriggerParams> CREATOR =
            new Parcelable.Creator<WebTriggerParams>() {
                @Override
                public WebTriggerParams createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    Uri uri = Uri.parse(in.readString());
                    boolean debug = in.readBoolean();
                    return new WebTriggerParams(uri, debug);
                }

                @Override
                public WebTriggerParams[] newArray(int size) {
                    return new WebTriggerParams[size];
                }
            };
}
