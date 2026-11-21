// SPDX-License-Identifier: GPL-3.0-only
package android.os;

/** Hardware temperature, CPU usage, and fan telemetry manager. */
public class HardwarePropertiesManager {
    public static final int DEVICE_TEMPERATURE_CPU = 0;
    public static final int DEVICE_TEMPERATURE_GPU = 1;
    public static final int DEVICE_TEMPERATURE_BATTERY = 2;
    public static final int DEVICE_TEMPERATURE_SKIN = 3;

    public static final int TEMPERATURE_CURRENT = 0;
    public static final int TEMPERATURE_THROTTLING = 1;
    public static final int TEMPERATURE_SHUTDOWN = 2;
    public static final int TEMPERATURE_THROTTLING_BELOW_VR_MIN = 3;

    public static final float UNDEFINED_TEMPERATURE = -Float.MAX_VALUE;

    public HardwarePropertiesManager() {
    }

    public float[] getDeviceTemperatures(int type, int source) {
        if (type < DEVICE_TEMPERATURE_CPU || type > DEVICE_TEMPERATURE_SKIN) {
            throw new IllegalArgumentException("Unknown device temperature type: " + type);
        }
        if (source < TEMPERATURE_CURRENT || source > TEMPERATURE_THROTTLING_BELOW_VR_MIN) {
            throw new IllegalArgumentException("Unknown temperature source: " + source);
        }
        return new float[0];
    }

    public CpuUsageInfo[] getCpuUsages() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        long uptime = Math.max(0L, SystemClock.uptimeMillis());
        long cpu = Math.max(0L, Process.getElapsedCpuTime());
        long perCoreActive = Math.min(uptime, cpu / cores);
        CpuUsageInfo[] infos = new CpuUsageInfo[cores];
        for (int i = 0; i < cores; i++) {
            infos[i] = new CpuUsageInfo(perCoreActive, uptime);
        }
        return infos;
    }

    public float[] getFanSpeeds() {
        return new float[0];
    }
}
