// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;
/** An event payload. The current host backend emits no fabricated sensor events. */
public class SensorEvent {
    public Sensor sensor;
    public int accuracy;
    public long timestamp;
    public final float[] values;
    SensorEvent(int size) {
        if (size < 1 || size > 64) throw new IllegalArgumentException("Sensor payload size");
        values = new float[size];
    }
}
