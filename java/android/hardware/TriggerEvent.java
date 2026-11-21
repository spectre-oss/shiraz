// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

public final class TriggerEvent {
    public final float[] values;
    public Sensor sensor;
    public long timestamp;

    TriggerEvent(int size) {
        values = new float[size];
    }
}
