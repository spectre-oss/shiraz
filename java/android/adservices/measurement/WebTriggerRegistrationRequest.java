// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.measurement;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Web trigger registration request for Attribution Reporting. */
public final class WebTriggerRegistrationRequest implements Parcelable {
    private final List<WebTriggerParams> mWebTriggerParams;
    private final Uri mDestination;

    private WebTriggerRegistrationRequest(
            List<WebTriggerParams> webTriggerParams, Uri destination) {
        Objects.requireNonNull(webTriggerParams, "webTriggerParams");
        if (webTriggerParams.isEmpty()) {
            throw new IllegalArgumentException("webTriggerParams must not be empty");
        }
        List<WebTriggerParams> copy = new ArrayList<WebTriggerParams>(webTriggerParams.size());
        for (WebTriggerParams p : webTriggerParams) {
            copy.add(Objects.requireNonNull(p, "webTriggerParam"));
        }
        this.mWebTriggerParams = Collections.unmodifiableList(copy);
        this.mDestination = Objects.requireNonNull(destination, "destination");
    }

    public List<WebTriggerParams> getTriggerParams() {
        return mWebTriggerParams;
    }

    public Uri getDestination() {
        return mDestination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebTriggerRegistrationRequest)) {
            return false;
        }
        WebTriggerRegistrationRequest that = (WebTriggerRegistrationRequest) o;
        return Objects.equals(mWebTriggerParams, that.mWebTriggerParams)
                && Objects.equals(mDestination, that.mDestination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mWebTriggerParams, mDestination);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        Objects.requireNonNull(out, "out");
        out.writeTypedList(mWebTriggerParams);
        out.writeString(mDestination.toString());
    }

    public static final class Builder {
        private final List<WebTriggerParams> mWebTriggerParams;
        private final Uri mDestination;

        public Builder(List<WebTriggerParams> webTriggerParams, Uri destination) {
            Objects.requireNonNull(webTriggerParams, "webTriggerParams");
            this.mWebTriggerParams = new ArrayList<WebTriggerParams>(webTriggerParams);
            this.mDestination = Objects.requireNonNull(destination, "destination");
        }

        public WebTriggerRegistrationRequest build() {
            return new WebTriggerRegistrationRequest(mWebTriggerParams, mDestination);
        }
    }

    public static final Parcelable.Creator<WebTriggerRegistrationRequest> CREATOR =
            new Parcelable.Creator<WebTriggerRegistrationRequest>() {
                @Override
                public WebTriggerRegistrationRequest createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    List<WebTriggerParams> params =
                            in.createTypedArrayList(WebTriggerParams.CREATOR);
                    Uri dest = Uri.parse(in.readString());
                    return new WebTriggerRegistrationRequest(
                            params == null ? Collections.<WebTriggerParams>emptyList() : params,
                            dest);
                }

                @Override
                public WebTriggerRegistrationRequest[] newArray(int size) {
                    return new WebTriggerRegistrationRequest[size];
                }
            };
}
