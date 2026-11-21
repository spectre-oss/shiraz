// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.lights;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LightsRequest {
    private final Map<Light, LightState> mRequests;
    private final List<Integer> mLightIds;
    private final List<LightState> mLightStates;

    private LightsRequest(Map<Light, LightState> requests) {
        LinkedHashMap<Light, LightState> mapCopy = new LinkedHashMap<Light, LightState>(requests);
        mRequests = Collections.unmodifiableMap(mapCopy);
        ArrayList<Integer> ids = new ArrayList<Integer>(mapCopy.size());
        ArrayList<LightState> states = new ArrayList<LightState>(mapCopy.size());
        for (Map.Entry<Light, LightState> entry : mapCopy.entrySet()) {
            ids.add(Integer.valueOf(entry.getKey().getId()));
            states.add(entry.getValue());
        }
        mLightIds = Collections.unmodifiableList(ids);
        mLightStates = Collections.unmodifiableList(states);
    }

    public List<Integer> getLights() {
        return mLightIds;
    }

    public List<LightState> getLightStates() {
        return mLightStates;
    }

    public Map<Light, LightState> getLightsAndStates() {
        return mRequests;
    }

    public static final class Builder {
        private final LinkedHashMap<Light, LightState> mChanges =
                new LinkedHashMap<Light, LightState>();

        public Builder() {
        }

        public Builder addLight(Light light, LightState state) {
            Objects.requireNonNull(light, "light");
            Objects.requireNonNull(state, "state");
            mChanges.put(light, state);
            return this;
        }

        public Builder clearLight(Light light) {
            Objects.requireNonNull(light, "light");
            mChanges.put(light, null);
            return this;
        }

        public LightsRequest build() {
            return new LightsRequest(mChanges);
        }
    }
}
