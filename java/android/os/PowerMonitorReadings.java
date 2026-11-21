// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import java.util.Arrays;

/** Energy readings associated with a set of {@link PowerMonitor} instances. */
public final class PowerMonitorReadings {
    public static final int ENERGY_UNAVAILABLE = -1;

    private final PowerMonitor[] powerMonitors;
    private final long[] energyUws;
    private final long[] timestampsMs;

    public PowerMonitorReadings(PowerMonitor[] powerMonitors, long[] energyUws, long[] timestampsMs) {
        this.powerMonitors = powerMonitors == null ? new PowerMonitor[0] : powerMonitors.clone();
        this.energyUws = energyUws == null ? new long[0] : energyUws.clone();
        this.timestampsMs = timestampsMs == null ? new long[0] : timestampsMs.clone();
    }

    private int indexOf(PowerMonitor monitor) {
        if (monitor == null) {
            return -1;
        }
        for (int i = 0; i < powerMonitors.length; i++) {
            PowerMonitor candidate = powerMonitors[i];
            if (candidate != null
                    && candidate.getType() == monitor.getType()
                    && candidate.getName().equals(monitor.getName())) {
                return i;
            }
        }
        return -1;
    }

    public long getConsumedEnergy(PowerMonitor powerMonitor) {
        int idx = indexOf(powerMonitor);
        return idx >= 0 && idx < energyUws.length ? energyUws[idx] : ENERGY_UNAVAILABLE;
    }

    public long getTimestampMillis(PowerMonitor powerMonitor) {
        int idx = indexOf(powerMonitor);
        return idx >= 0 && idx < timestampsMs.length ? timestampsMs[idx] : ENERGY_UNAVAILABLE;
    }

    @Override
    public String toString() {
        return "PowerMonitorReadings{monitors=" + Arrays.toString(powerMonitors) + "}";
    }
}
