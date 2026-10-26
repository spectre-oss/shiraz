// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;
/** Physical sensor metadata; instances must originate in a hardware backend. */
public final class Sensor {
    public static final int TYPE_ALL = -1, TYPE_ACCELEROMETER = 1, TYPE_MAGNETIC_FIELD = 2, TYPE_ORIENTATION = 3,
        TYPE_GYROSCOPE = 4, TYPE_LIGHT = 5, TYPE_PRESSURE = 6, TYPE_TEMPERATURE = 7, TYPE_PROXIMITY = 8,
        TYPE_GRAVITY = 9, TYPE_LINEAR_ACCELERATION = 10, TYPE_ROTATION_VECTOR = 11, TYPE_RELATIVE_HUMIDITY = 12,
        TYPE_AMBIENT_TEMPERATURE = 13, TYPE_MAGNETIC_FIELD_UNCALIBRATED = 14, TYPE_GAME_ROTATION_VECTOR = 15,
        TYPE_GYROSCOPE_UNCALIBRATED = 16, TYPE_SIGNIFICANT_MOTION = 17, TYPE_STEP_DETECTOR = 18, TYPE_STEP_COUNTER = 19,
        TYPE_GEOMAGNETIC_ROTATION_VECTOR = 20, TYPE_HEART_RATE = 21, TYPE_POSE_6DOF = 28,
        TYPE_STATIONARY_DETECT = 29, TYPE_MOTION_DETECT = 30, TYPE_HEART_BEAT = 31, TYPE_LOW_LATENCY_OFFBODY_DETECT = 34,
        TYPE_ACCELEROMETER_UNCALIBRATED = 35;
    private final int type;
    private final String name, vendor;
    private final boolean wakeUp;
    Sensor(int type, String name, String vendor, boolean wakeUp) { this.type = type; this.name = name; this.vendor = vendor; this.wakeUp = wakeUp; }
    public int getType() { return type; }
    public String getName() { return name; }
    public String getVendor() { return vendor; }
    public boolean isWakeUpSensor() { return wakeUp; }
}
