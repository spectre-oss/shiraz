// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Objects;

/** Federated compute task configuration identifying a population name. */
public final class FederatedComputeInput {
    private final String mPopulationName;

    private FederatedComputeInput(String populationName) {
        this.mPopulationName = Objects.requireNonNull(populationName, "populationName");
    }

    public String getPopulationName() {
        return mPopulationName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FederatedComputeInput)) {
            return false;
        }
        FederatedComputeInput that = (FederatedComputeInput) o;
        return Objects.equals(mPopulationName, that.mPopulationName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mPopulationName);
    }

    public static final class Builder {
        private String mPopulationName;

        public Builder() {
        }

        public Builder setPopulationName(String value) {
            this.mPopulationName = Objects.requireNonNull(value, "populationName");
            return this;
        }

        public FederatedComputeInput build() {
            return new FederatedComputeInput(mPopulationName);
        }
    }
}
