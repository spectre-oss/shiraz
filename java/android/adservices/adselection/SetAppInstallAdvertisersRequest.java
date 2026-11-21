// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.adselection;

import android.adservices.common.AdTechIdentifier;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Request payload for {@link AdSelectionManager#setAppInstallAdvertisers}. */
public class SetAppInstallAdvertisersRequest {
    private final Set<AdTechIdentifier> mAdvertisers;

    private SetAppInstallAdvertisersRequest(Set<AdTechIdentifier> advertisers) {
        Objects.requireNonNull(advertisers, "advertisers");
        Set<AdTechIdentifier> copy = new LinkedHashSet<AdTechIdentifier>(advertisers.size());
        for (AdTechIdentifier adTech : advertisers) {
            copy.add(Objects.requireNonNull(adTech, "advertiser"));
        }
        this.mAdvertisers = Collections.unmodifiableSet(copy);
    }

    public Set<AdTechIdentifier> getAdvertisers() {
        return mAdvertisers;
    }

    public static final class Builder {
        private Set<AdTechIdentifier> mAdvertisers = Collections.emptySet();

        public Builder() {
        }

        public Builder setAdvertisers(Set<AdTechIdentifier> advertisers) {
            Objects.requireNonNull(advertisers, "advertisers");
            this.mAdvertisers = new LinkedHashSet<AdTechIdentifier>(advertisers);
            return this;
        }

        public SetAppInstallAdvertisersRequest build() {
            return new SetAppInstallAdvertisersRequest(mAdvertisers);
        }
    }
}
