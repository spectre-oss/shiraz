// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.measurement;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.InputEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Web source registration request for Attribution Reporting. */
public final class WebSourceRegistrationRequest implements Parcelable {
    private final List<WebSourceParams> mWebSourceParams;
    private final Uri mTopOriginUri;
    private final InputEvent mInputEvent;
    private final Uri mAppDestination;
    private final Uri mWebDestination;
    private final Uri mVerifiedDestination;

    private WebSourceRegistrationRequest(
            List<WebSourceParams> webSourceParams,
            Uri topOriginUri,
            InputEvent inputEvent,
            Uri appDestination,
            Uri webDestination,
            Uri verifiedDestination) {
        Objects.requireNonNull(webSourceParams, "webSourceParams");
        if (webSourceParams.isEmpty()) {
            throw new IllegalArgumentException("webSourceParams must not be empty");
        }
        List<WebSourceParams> copy = new ArrayList<WebSourceParams>(webSourceParams.size());
        for (WebSourceParams p : webSourceParams) {
            copy.add(Objects.requireNonNull(p, "webSourceParam"));
        }
        this.mWebSourceParams = Collections.unmodifiableList(copy);
        this.mTopOriginUri = Objects.requireNonNull(topOriginUri, "topOriginUri");
        this.mInputEvent = inputEvent;
        this.mAppDestination = appDestination;
        this.mWebDestination = webDestination;
        this.mVerifiedDestination = verifiedDestination;
    }

    public List<WebSourceParams> getSourceParams() {
        return mWebSourceParams;
    }

    public Uri getTopOriginUri() {
        return mTopOriginUri;
    }

    public InputEvent getInputEvent() {
        return mInputEvent;
    }

    public Uri getAppDestination() {
        return mAppDestination;
    }

    public Uri getWebDestination() {
        return mWebDestination;
    }

    public Uri getVerifiedDestination() {
        return mVerifiedDestination;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebSourceRegistrationRequest)) {
            return false;
        }
        WebSourceRegistrationRequest that = (WebSourceRegistrationRequest) o;
        return Objects.equals(mWebSourceParams, that.mWebSourceParams)
                && Objects.equals(mTopOriginUri, that.mTopOriginUri)
                && Objects.equals(mInputEvent, that.mInputEvent)
                && Objects.equals(mAppDestination, that.mAppDestination)
                && Objects.equals(mWebDestination, that.mWebDestination)
                && Objects.equals(mVerifiedDestination, that.mVerifiedDestination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mWebSourceParams,
                mTopOriginUri,
                mInputEvent,
                mAppDestination,
                mWebDestination,
                mVerifiedDestination);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        Objects.requireNonNull(out, "out");
        out.writeTypedList(mWebSourceParams);
        out.writeString(mTopOriginUri.toString());
        SourceRegistrationRequest.writeInputEvent(out, mInputEvent);
        out.writeString(mAppDestination == null ? null : mAppDestination.toString());
        out.writeString(mWebDestination == null ? null : mWebDestination.toString());
        out.writeString(
                mVerifiedDestination == null ? null : mVerifiedDestination.toString());
    }

    public static final class Builder {
        private final List<WebSourceParams> mWebSourceParams;
        private final Uri mTopOriginUri;
        private InputEvent mInputEvent;
        private Uri mAppDestination;
        private Uri mWebDestination;
        private Uri mVerifiedDestination;

        public Builder(List<WebSourceParams> webSourceParams, Uri topOriginUri) {
            Objects.requireNonNull(webSourceParams, "webSourceParams");
            this.mWebSourceParams = new ArrayList<WebSourceParams>(webSourceParams);
            this.mTopOriginUri = Objects.requireNonNull(topOriginUri, "topOriginUri");
        }

        public Builder setInputEvent(InputEvent inputEvent) {
            this.mInputEvent = inputEvent;
            return this;
        }

        public Builder setAppDestination(Uri appDestination) {
            this.mAppDestination = appDestination;
            return this;
        }

        public Builder setWebDestination(Uri webDestination) {
            this.mWebDestination = webDestination;
            return this;
        }

        public Builder setVerifiedDestination(Uri verifiedDestination) {
            this.mVerifiedDestination = verifiedDestination;
            return this;
        }

        public WebSourceRegistrationRequest build() {
            return new WebSourceRegistrationRequest(
                    mWebSourceParams,
                    mTopOriginUri,
                    mInputEvent,
                    mAppDestination,
                    mWebDestination,
                    mVerifiedDestination);
        }
    }

    public static final Parcelable.Creator<WebSourceRegistrationRequest> CREATOR =
            new Parcelable.Creator<WebSourceRegistrationRequest>() {
                @Override
                public WebSourceRegistrationRequest createFromParcel(Parcel in) {
                    Objects.requireNonNull(in, "in");
                    List<WebSourceParams> params =
                            in.createTypedArrayList(WebSourceParams.CREATOR);
                    Uri topOrigin = Uri.parse(in.readString());
                    InputEvent event = SourceRegistrationRequest.readInputEvent(in);
                    String appStr = in.readString();
                    String webStr = in.readString();
                    String verStr = in.readString();
                    return new WebSourceRegistrationRequest(
                            params == null ? Collections.<WebSourceParams>emptyList() : params,
                            topOrigin,
                            event,
                            appStr == null ? null : Uri.parse(appStr),
                            webStr == null ? null : Uri.parse(webStr),
                            verStr == null ? null : Uri.parse(verStr));
                }

                @Override
                public WebSourceRegistrationRequest[] newArray(int size) {
                    return new WebSourceRegistrationRequest[size];
                }
            };
}
