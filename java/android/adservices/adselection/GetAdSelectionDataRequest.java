// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

/** Request parameters for {@link AdSelectionManager#getAdSelectionData}. */
public final class GetAdSelectionDataRequest {
    private final AdTechIdentifier mSeller;
    private final Uri mCoordinatorOriginUri;

    private GetAdSelectionDataRequest(AdTechIdentifier seller, Uri coordinatorOriginUri) {
        this.mSeller = seller;
        this.mCoordinatorOriginUri = coordinatorOriginUri;
    }

    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    public Uri getCoordinatorOriginUri() {
        return mCoordinatorOriginUri;
    }

    public static final class Builder {
        private AdTechIdentifier mSeller;
        private Uri mCoordinatorOriginUri;

        public Builder() {
        }

        public Builder setSeller(AdTechIdentifier seller) {
            this.mSeller = seller;
            return this;
        }

        public Builder setCoordinatorOriginUri(Uri coordinatorOriginUri) {
            this.mCoordinatorOriginUri = coordinatorOriginUri;
            return this;
        }

        public GetAdSelectionDataRequest build() {
            return new GetAdSelectionDataRequest(mSeller, mCoordinatorOriginUri);
        }
    }
}
