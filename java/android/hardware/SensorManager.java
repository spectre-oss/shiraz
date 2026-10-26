// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;
import android.os.Handler;
import java.util.List;
/** Enumeration/registration subset. Math helpers and real hardware event delivery are pending. */
public abstract class SensorManager {
    public static final int SENSOR_DELAY_FASTEST = 0, SENSOR_DELAY_GAME = 1, SENSOR_DELAY_UI = 2, SENSOR_DELAY_NORMAL = 3;
    public static final int SENSOR_STATUS_NO_CONTACT = -1, SENSOR_STATUS_UNRELIABLE = 0, SENSOR_STATUS_ACCURACY_LOW = 1,
        SENSOR_STATUS_ACCURACY_MEDIUM = 2, SENSOR_STATUS_ACCURACY_HIGH = 3;
    public static final int AXIS_X = 1, AXIS_Y = 2, AXIS_Z = 3, AXIS_MINUS_X = 129, AXIS_MINUS_Y = 130, AXIS_MINUS_Z = 131;
    public static final float GRAVITY_EARTH = 9.80665f;
    protected SensorManager() { }
    public abstract List<Sensor> getSensorList(int type);
    public Sensor getDefaultSensor(int type) { return getDefaultSensor(type, false); }
    public Sensor getDefaultSensor(int type, boolean wakeUp) {
        for (Sensor sensor : getSensorList(type)) if (sensor.isWakeUpSensor() == wakeUp) return sensor;
        return null;
    }
    public boolean registerListener(SensorEventListener listener, Sensor sensor, int delay) { return registerListener(listener, sensor, delay, 0, null); }
    public boolean registerListener(SensorEventListener listener, Sensor sensor, int delay, Handler handler) { return registerListener(listener, sensor, delay, 0, handler); }
    public boolean registerListener(SensorEventListener listener, Sensor sensor, int delay, int latency) { return registerListener(listener, sensor, delay, latency, null); }
    public abstract boolean registerListener(SensorEventListener listener, Sensor sensor, int delay, int latency, Handler handler);
    public abstract void unregisterListener(SensorEventListener listener);
    public abstract void unregisterListener(SensorEventListener listener, Sensor sensor);
    public abstract boolean flush(SensorEventListener listener);
}
