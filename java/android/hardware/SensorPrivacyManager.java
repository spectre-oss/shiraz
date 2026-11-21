// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

public final class SensorPrivacyManager {
    public static final int TOGGLE_TYPE_SOFTWARE = 1;
    public static final int TOGGLE_TYPE_HARDWARE = 2;

    public static class Sensors {
        public static final int MICROPHONE = 1;
        public static final int CAMERA = 2;

        Sensors() {
        }
    }

    public SensorPrivacyManager() {
    }

    public boolean supportsSensorToggle(int sensor) {
        return false;
    }

    public boolean supportsSensorToggle(int toggleType, int sensor) {
        return false;
    }
}
