// SPDX-License-Identifier: GPL-3.0-only
package android.hardware.camera2.params;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DeviceStateSensorOrientationMap {
    public static final long NORMAL = 0L;
    public static final long FOLDED = 4L;

    private final Map<Long, Integer> mDeviceStateOrientationMap;

    private DeviceStateSensorOrientationMap(Map<Long, Integer> map) {
        mDeviceStateOrientationMap = new LinkedHashMap<Long, Integer>(map);
    }

    public int getSensorOrientation(long deviceState) {
        Integer orientation = mDeviceStateOrientationMap.get(Long.valueOf(deviceState));
        if (orientation == null) {
            throw new IllegalArgumentException("Invalid device state: " + deviceState);
        }
        return orientation.intValue();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof DeviceStateSensorOrientationMap) {
            return Objects.equals(
                    mDeviceStateOrientationMap,
                    ((DeviceStateSensorOrientationMap) obj).mDeviceStateOrientationMap);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return mDeviceStateOrientationMap.hashCode();
    }

    public static final class Builder {
        private final LinkedHashMap<Long, Integer> mMap = new LinkedHashMap<Long, Integer>();

        public Builder() {
        }

        public Builder addOrientationForState(long deviceState, long angle) {
            if (angle % 90L != 0L || angle < 0L || angle > 270L) {
                throw new IllegalArgumentException("Invalid orientation angle: " + angle);
            }
            mMap.put(Long.valueOf(deviceState), Integer.valueOf((int) angle));
            return this;
        }

        public DeviceStateSensorOrientationMap build() {
            if (mMap.isEmpty()) {
                throw new IllegalStateException(
                        "Cannot build a DeviceStateSensorOrientationMap with zero elements");
            }
            return new DeviceStateSensorOrientationMap(mMap);
        }
    }
}
