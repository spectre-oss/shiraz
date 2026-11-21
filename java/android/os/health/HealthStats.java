// SPDX-License-Identifier: GPL-3.0-only
package android.os.health;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/** Keyed snapshot of health timers, measurements, and nested {@link HealthStats} maps. */
public class HealthStats {
    private final String dataType;
    private final int[] timerKeys;
    private final TimerStat[] timerValues;
    private final int[] measurementKeys;
    private final long[] measurementValues;
    private final int[] statsKeys;
    private final Map<String, HealthStats>[] statsValues;
    private final int[] timersKeys;
    private final Map<String, TimerStat>[] timersValues;
    private final int[] measurementsKeys;
    private final Map<String, Long>[] measurementsValues;

    @SuppressWarnings("unchecked")
    public HealthStats(String dataType) {
        this(
                dataType,
                new TreeMap<Integer, TimerStat>(),
                new TreeMap<Integer, Long>(),
                new TreeMap<Integer, Map<String, HealthStats>>(),
                new TreeMap<Integer, Map<String, TimerStat>>(),
                new TreeMap<Integer, Map<String, Long>>());
    }

    @SuppressWarnings("unchecked")
    HealthStats(
            String dataType,
            TreeMap<Integer, TimerStat> timers,
            TreeMap<Integer, Long> measurements,
            TreeMap<Integer, Map<String, HealthStats>> stats,
            TreeMap<Integer, Map<String, TimerStat>> namedTimers,
            TreeMap<Integer, Map<String, Long>> namedMeasurements) {
        this.dataType = dataType;

        this.timerKeys = new int[timers.size()];
        this.timerValues = new TimerStat[timers.size()];
        int idx = 0;
        for (Map.Entry<Integer, TimerStat> e : timers.entrySet()) {
            timerKeys[idx] = e.getKey().intValue();
            timerValues[idx] = e.getValue();
            idx++;
        }

        this.measurementKeys = new int[measurements.size()];
        this.measurementValues = new long[measurements.size()];
        idx = 0;
        for (Map.Entry<Integer, Long> e : measurements.entrySet()) {
            measurementKeys[idx] = e.getKey().intValue();
            measurementValues[idx] = e.getValue().longValue();
            idx++;
        }

        this.statsKeys = new int[stats.size()];
        this.statsValues = (Map<String, HealthStats>[]) new Map<?, ?>[stats.size()];
        idx = 0;
        for (Map.Entry<Integer, Map<String, HealthStats>> e : stats.entrySet()) {
            statsKeys[idx] = e.getKey().intValue();
            statsValues[idx] = Collections.unmodifiableMap(e.getValue());
            idx++;
        }

        this.timersKeys = new int[namedTimers.size()];
        this.timersValues = (Map<String, TimerStat>[]) new Map<?, ?>[namedTimers.size()];
        idx = 0;
        for (Map.Entry<Integer, Map<String, TimerStat>> e : namedTimers.entrySet()) {
            timersKeys[idx] = e.getKey().intValue();
            timersValues[idx] = Collections.unmodifiableMap(e.getValue());
            idx++;
        }

        this.measurementsKeys = new int[namedMeasurements.size()];
        this.measurementsValues = (Map<String, Long>[]) new Map<?, ?>[namedMeasurements.size()];
        idx = 0;
        for (Map.Entry<Integer, Map<String, Long>> e : namedMeasurements.entrySet()) {
            measurementsKeys[idx] = e.getKey().intValue();
            measurementsValues[idx] = Collections.unmodifiableMap(e.getValue());
            idx++;
        }
    }

    private static int findKey(int[] keys, int key) {
        int lo = 0;
        int hi = keys.length - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int val = keys[mid];
            if (val < key) {
                lo = mid + 1;
            } else if (val > key) {
                hi = mid - 1;
            } else {
                return mid;
            }
        }
        return -1;
    }

    public String getDataType() {
        return dataType;
    }

    public boolean hasTimer(int key) {
        return findKey(timerKeys, key) >= 0;
    }

    public TimerStat getTimer(int key) {
        int index = findKey(timerKeys, key);
        if (index < 0) {
            throw new IndexOutOfBoundsException("Bad timer key dataType=" + dataType + " key=" + key);
        }
        TimerStat src = timerValues[index];
        return new TimerStat(src.getCount(), src.getTime());
    }

    public int getTimerCount(int key) {
        int index = findKey(timerKeys, key);
        if (index < 0) {
            throw new IndexOutOfBoundsException("Bad timer key dataType=" + dataType + " key=" + key);
        }
        return timerValues[index].getCount();
    }

    public long getTimerTime(int key) {
        int index = findKey(timerKeys, key);
        if (index < 0) {
            throw new IndexOutOfBoundsException("Bad timer key dataType=" + dataType + " key=" + key);
        }
        return timerValues[index].getTime();
    }

    public int getTimerKeyCount() {
        return timerKeys.length;
    }

    public int getTimerKeyAt(int index) {
        return timerKeys[index];
    }

    public boolean hasMeasurement(int key) {
        return findKey(measurementKeys, key) >= 0;
    }

    public long getMeasurement(int key) {
        int index = findKey(measurementKeys, key);
        if (index < 0) {
            throw new IndexOutOfBoundsException("Bad measurement key dataType=" + dataType + " key=" + key);
        }
        return measurementValues[index];
    }

    public int getMeasurementKeyCount() {
        return measurementKeys.length;
    }

    public int getMeasurementKeyAt(int index) {
        return measurementKeys[index];
    }

    public boolean hasStats(int key) {
        return findKey(statsKeys, key) >= 0;
    }

    public Map<String, HealthStats> getStats(int key) {
        int index = findKey(statsKeys, key);
        if (index < 0) {
            throw new IndexOutOfBoundsException("Bad stats key dataType=" + dataType + " key=" + key);
        }
        return statsValues[index];
    }

    public int getStatsKeyCount() {
        return statsKeys.length;
    }

    public int getStatsKeyAt(int index) {
        return statsKeys[index];
    }

    public boolean hasTimers(int key) {
        return findKey(timersKeys, key) >= 0;
    }

    public Map<String, TimerStat> getTimers(int key) {
        int index = findKey(timersKeys, key);
        if (index < 0) {
            throw new IndexOutOfBoundsException("Bad timers key dataType=" + dataType + " key=" + key);
        }
        return timersValues[index];
    }

    public int getTimersKeyCount() {
        return timersKeys.length;
    }

    public int getTimersKeyAt(int index) {
        return timersKeys[index];
    }

    public boolean hasMeasurements(int key) {
        return findKey(measurementsKeys, key) >= 0;
    }

    public Map<String, Long> getMeasurements(int key) {
        int index = findKey(measurementsKeys, key);
        if (index < 0) {
            throw new IndexOutOfBoundsException("Bad measurements key dataType=" + dataType + " key=" + key);
        }
        return measurementsValues[index];
    }

    public int getMeasurementsKeyCount() {
        return measurementsKeys.length;
    }

    public int getMeasurementsKeyAt(int index) {
        return measurementsKeys[index];
    }
}
