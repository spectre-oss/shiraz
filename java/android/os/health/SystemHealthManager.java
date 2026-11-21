// SPDX-License-Identifier: GPL-3.0-only
package android.os.health;

import android.os.OutcomeReceiver;
import android.os.PowerMonitor;
import android.os.PowerMonitorReadings;
import android.os.Process;
import android.os.SystemClock;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Provides process and UID health snapshots and host power monitor queries. */
public class SystemHealthManager {
    public SystemHealthManager() {
    }

    public HealthStats takeUidSnapshot(int uid) {
        TreeMap<Integer, TimerStat> timers = new TreeMap<Integer, TimerStat>();
        TreeMap<Integer, Long> measurements = new TreeMap<Integer, Long>();
        TreeMap<Integer, Map<String, HealthStats>> stats = new TreeMap<Integer, Map<String, HealthStats>>();
        TreeMap<Integer, Map<String, TimerStat>> namedTimers = new TreeMap<Integer, Map<String, TimerStat>>();
        TreeMap<Integer, Map<String, Long>> namedMeasurements = new TreeMap<Integer, Map<String, Long>>();

        long realtimeMs = Math.max(0L, SystemClock.elapsedRealtime());
        long uptimeMs = Math.max(0L, SystemClock.uptimeMillis());
        measurements.put(Integer.valueOf(UidHealthStats.MEASUREMENT_REALTIME_BATTERY_MS), Long.valueOf(realtimeMs));
        measurements.put(Integer.valueOf(UidHealthStats.MEASUREMENT_UPTIME_BATTERY_MS), Long.valueOf(uptimeMs));

        if (uid == Process.myUid()) {
            long cpuMs = Math.max(0L, Process.getElapsedCpuTime());
            measurements.put(Integer.valueOf(UidHealthStats.MEASUREMENT_USER_CPU_TIME_MS), Long.valueOf(cpuMs));
            measurements.put(Integer.valueOf(UidHealthStats.MEASUREMENT_SYSTEM_CPU_TIME_MS), Long.valueOf(0L));
        }

        return new HealthStats("UidHealthStats", timers, measurements, stats, namedTimers, namedMeasurements);
    }

    public HealthStats takeMyUidSnapshot() {
        return takeUidSnapshot(Process.myUid());
    }

    public HealthStats[] takeUidSnapshots(int[] uids) {
        Objects.requireNonNull(uids, "uids");
        HealthStats[] result = new HealthStats[uids.length];
        for (int i = 0; i < uids.length; i++) {
            result[i] = takeUidSnapshot(uids[i]);
        }
        return result;
    }

    public void getSupportedPowerMonitors(
            Executor executor, final Consumer<List<PowerMonitor>> onResult) {
        Objects.requireNonNull(onResult, "onResult");
        final List<PowerMonitor> empty = Collections.emptyList();
        if (executor != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    onResult.accept(empty);
                }
            });
        } else {
            onResult.accept(empty);
        }
    }

    public void getPowerMonitorReadings(
            List<PowerMonitor> powerMonitors,
            Executor executor,
            final OutcomeReceiver<PowerMonitorReadings, RuntimeException> onResult) {
        Objects.requireNonNull(powerMonitors, "powerMonitors");
        Objects.requireNonNull(onResult, "onResult");
        PowerMonitor[] monitors = powerMonitors.toArray(new PowerMonitor[powerMonitors.size()]);
        long[] energy = new long[monitors.length];
        long[] timestamps = new long[monitors.length];
        for (int i = 0; i < monitors.length; i++) {
            energy[i] = PowerMonitorReadings.ENERGY_UNAVAILABLE;
            timestamps[i] = PowerMonitorReadings.ENERGY_UNAVAILABLE;
        }
        final PowerMonitorReadings readings = new PowerMonitorReadings(monitors, energy, timestamps);
        if (executor != null) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    onResult.onResult(readings);
                }
            });
        } else {
            onResult.onResult(readings);
        }
    }
}
