// SPDX-License-Identifier: GPL-3.0-only
package android.adservices.ondevicepersonalization;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Output structure and tensor buffers produced by {@link ModelManager#run}. */
public final class InferenceOutput {
    private final Map<Integer, Object> mDataOutputs;

    private InferenceOutput(Map<Integer, Object> dataOutputs) {
        Map<Integer, Object> copy = new LinkedHashMap<Integer, Object>();
        if (dataOutputs != null) {
            for (Map.Entry<Integer, Object> e : dataOutputs.entrySet()) {
                copy.put(
                        Objects.requireNonNull(e.getKey(), "index"),
                        Objects.requireNonNull(e.getValue(), "output"));
            }
        }
        this.mDataOutputs = Collections.unmodifiableMap(copy);
    }

    public Map<Integer, Object> getDataOutputs() {
        return mDataOutputs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof InferenceOutput)) {
            return false;
        }
        InferenceOutput that = (InferenceOutput) o;
        return Objects.equals(mDataOutputs, that.mDataOutputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mDataOutputs);
    }

    public static final class Builder {
        private Map<Integer, Object> mDataOutputs = new LinkedHashMap<Integer, Object>();

        public Builder() {
        }

        public Builder setDataOutputs(Map<Integer, Object> value) {
            Objects.requireNonNull(value, "dataOutputs");
            this.mDataOutputs = new LinkedHashMap<Integer, Object>(value);
            return this;
        }

        public Builder addDataOutput(int key, Object value) {
            this.mDataOutputs.put(
                    Integer.valueOf(key), Objects.requireNonNull(value, "value"));
            return this;
        }

        public InferenceOutput build() {
            return new InferenceOutput(mDataOutputs);
        }
    }
}
