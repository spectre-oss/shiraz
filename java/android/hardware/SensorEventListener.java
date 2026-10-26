// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;
public interface SensorEventListener {
    void onSensorChanged(SensorEvent event);
    void onAccuracyChanged(Sensor sensor, int accuracy);
}
