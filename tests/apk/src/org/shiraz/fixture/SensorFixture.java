// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.hardware.*;
final class SensorFixture {
    static void check(Context context) {
        SensorManager manager = context.getSystemService(SensorManager.class);
        if (manager != context.getSystemService(Context.SENSOR_SERVICE) || !manager.getSensorList(Sensor.TYPE_ALL).isEmpty()
                || manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null || manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE, true) != null)
            throw new AssertionError("No-sensor host inventory/identity");
        SensorEventListener2 listener = new SensorEventListener2() {
            public void onSensorChanged(SensorEvent event) { throw new AssertionError("Fabricated sensor event"); }
            public void onAccuracyChanged(Sensor sensor, int accuracy) { throw new AssertionError("Fabricated sensor accuracy"); }
            public void onFlushCompleted(Sensor sensor) { throw new AssertionError("Fabricated sensor flush"); }
        };
        if (manager.registerListener(listener, null, SensorManager.SENSOR_DELAY_NORMAL) || manager.flush(listener))
            throw new AssertionError("Unavailable sensor subscription reported success");
        manager.unregisterListener(listener); manager.unregisterListener(listener, null);
        System.out.println("SHIRAZ_FIXTURE_SENSORS_OK");
    }
}
