// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

@Deprecated
public interface SensorListener {
    @Deprecated
    void onSensorChanged(int sensor, float[] values);

    @Deprecated
    void onAccuracyChanged(int sensor, int accuracy);
}
