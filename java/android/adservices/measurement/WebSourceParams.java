// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.measurement;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.Objects;

/** Parameters for an individual web attribution source registration URI. */
public final class WebSourceParams implements Parcelable {
    private final Uri mSourceRegistrationUri;
    private final boolean mDebugKeyAllowed;

    private WebSourceParams(Uri sourceRegistrationUri, boolean debugKeyAllowed) {
        this.mSourceRegistrationUri =
                Objects.requireNonNull(sourceRegistrationUri, "sourceRegistrationUri");
        this.mDebugKeyAllowed = debugKeyAllowed;
    }

    public Uri getRegistrationUri() {
        return mSourceRegistrationUri;
    }

    public boolean isDebugKeyAllowed() {
        return mDebugKeyAllowed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebSourceParams)) {
            return false;
        }
        WebSourceParams that = (WebSourceParams) o;
        return mDebugKeyAllowed == that.mDebugKeyAllowed
                && Objects.equals(mSourceRegistrationUri, that.mSourceRegistrationUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mSourceRegistrationUri, Boolean.valueOf(mDebugKeyAllowed));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        Objects.requireNonNull(out, "out");
        out.writeString(mSourceRegistrationUri.toString());
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

        public WebSourceParams build() {
            return new WebSourceParams(mRegistrationUri, mDebugKeyAllowed);
        }
    }

    public static final Parcelable.Creator<WebSourceParams> CREATOR =
            new Parcelable.Creator<WebSourceParams>() {
                @Override
                public WebSourceParams createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    Uri uri = Uri.parse(in.readString());
                    boolean debug = in.readBoolean();
                    return new WebSourceParams(uri, debug);
                }

                @Override
                public WebSourceParams[] newArray(int size) {
                    return new WebSourceParams[size];
                }
            };
}
