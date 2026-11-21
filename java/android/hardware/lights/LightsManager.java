// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.lights;

import java.util.List;

public abstract class LightsManager {
    public LightsManager() {
    }

    public abstract List<Light> getLights();

    public abstract LightState getLightState(Light light);

    public abstract LightsSession openSession();

    public abstract static class LightsSession implements AutoCloseable {
        public LightsSession() {
        }

        public abstract void requestLights(LightsRequest request);

        @Override
        public abstract void close();
    }
}
