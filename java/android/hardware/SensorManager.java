// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

import android.os.Handler;
import android.os.MemoryFile;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Enumeration/registration subset plus the static orientation math. Real hardware event delivery is pending. */
public abstract class SensorManager {
    @Deprecated public static final int SENSOR_ORIENTATION = 1;
    @Deprecated public static final int SENSOR_ACCELEROMETER = 2;
    @Deprecated public static final int SENSOR_TEMPERATURE = 4;
    @Deprecated public static final int SENSOR_MAGNETIC_FIELD = 8;
    @Deprecated public static final int SENSOR_LIGHT = 16;
    @Deprecated public static final int SENSOR_PROXIMITY = 32;
    @Deprecated public static final int SENSOR_TRICORDER = 64;
    @Deprecated public static final int SENSOR_ORIENTATION_RAW = 128;
    @Deprecated public static final int SENSOR_ALL = 127;
    @Deprecated public static final int SENSOR_MIN = 1;
    @Deprecated public static final int SENSOR_MAX = 64;

    @Deprecated public static final int DATA_X = 0;
    @Deprecated public static final int DATA_Y = 1;
    @Deprecated public static final int DATA_Z = 2;
    @Deprecated public static final int RAW_DATA_INDEX = 3;
    @Deprecated public static final int RAW_DATA_X = 3;
    @Deprecated public static final int RAW_DATA_Y = 4;
    @Deprecated public static final int RAW_DATA_Z = 5;

    public static final float STANDARD_GRAVITY = 9.80665f;
    public static final float GRAVITY_SUN = 275.0f;
    public static final float GRAVITY_MERCURY = 3.7f;
    public static final float GRAVITY_VENUS = 8.87f;
    public static final float GRAVITY_EARTH = 9.80665f;
    public static final float GRAVITY_MOON = 1.6f;
    public static final float GRAVITY_MARS = 3.71f;
    public static final float GRAVITY_JUPITER = 23.12f;
    public static final float GRAVITY_SATURN = 8.96f;
    public static final float GRAVITY_URANUS = 8.69f;
    public static final float GRAVITY_NEPTUNE = 11.0f;
    public static final float GRAVITY_PLUTO = 0.6f;
    public static final float GRAVITY_DEATH_STAR_I = 3.5303614e-7f;
    public static final float GRAVITY_THE_ISLAND = 4.815162f;

    public static final float MAGNETIC_FIELD_EARTH_MAX = 60.0f;
    public static final float MAGNETIC_FIELD_EARTH_MIN = 30.0f;
    public static final float PRESSURE_STANDARD_ATMOSPHERE = 1013.25f;

    public static final float LIGHT_SUNLIGHT_MAX = 120000.0f;
    public static final float LIGHT_SUNLIGHT = 110000.0f;
    public static final float LIGHT_SHADE = 20000.0f;
    public static final float LIGHT_OVERCAST = 10000.0f;
    public static final float LIGHT_SUNRISE = 400.0f;
    public static final float LIGHT_CLOUDY = 100.0f;
    public static final float LIGHT_FULLMOON = 0.25f;
    public static final float LIGHT_NO_MOON = 0.001f;

    public static final int SENSOR_DELAY_FASTEST = 0;
    public static final int SENSOR_DELAY_GAME = 1;
    public static final int SENSOR_DELAY_UI = 2;
    public static final int SENSOR_DELAY_NORMAL = 3;

    public static final int SENSOR_STATUS_NO_CONTACT = -1;
    public static final int SENSOR_STATUS_UNRELIABLE = 0;
    public static final int SENSOR_STATUS_ACCURACY_LOW = 1;
    public static final int SENSOR_STATUS_ACCURACY_MEDIUM = 2;
    public static final int SENSOR_STATUS_ACCURACY_HIGH = 3;

    public static final int AXIS_X = 1;
    public static final int AXIS_Y = 2;
    public static final int AXIS_Z = 3;
    public static final int AXIS_MINUS_X = 129;
    public static final int AXIS_MINUS_Y = 130;
    public static final int AXIS_MINUS_Z = 131;

    protected SensorManager() {
    }

    @Deprecated
    public int getSensors() {
        int result = 0;
        for (Sensor sensor : getSensorList(Sensor.TYPE_ALL)) {
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    result |= SENSOR_ACCELEROMETER;
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    result |= SENSOR_MAGNETIC_FIELD;
                    break;
                case Sensor.TYPE_ORIENTATION:
                    result |= SENSOR_ORIENTATION | SENSOR_ORIENTATION_RAW;
                    break;
                case Sensor.TYPE_LIGHT:
                    result |= SENSOR_LIGHT;
                    break;
                case Sensor.TYPE_PROXIMITY:
                    result |= SENSOR_PROXIMITY;
                    break;
                case Sensor.TYPE_TEMPERATURE:
                    result |= SENSOR_TEMPERATURE;
                    break;
                default:
                    break;
            }
        }
        return result;
    }

    public abstract List<Sensor> getSensorList(int type);

    public List<Sensor> getDynamicSensorList(int type) {
        return Collections.emptyList();
    }

    public Sensor getDefaultSensor(int type) {
        return getDefaultSensor(type, false);
    }

    public Sensor getDefaultSensor(int type, boolean wakeUp) {
        for (Sensor sensor : getSensorList(type)) {
            if (sensor.isWakeUpSensor() == wakeUp) {
                return sensor;
            }
        }
        return null;
    }

    @Deprecated
    public boolean registerListener(SensorListener listener, int sensors) {
        return registerListener(listener, sensors, SENSOR_DELAY_NORMAL);
    }

    @Deprecated
    public boolean registerListener(SensorListener listener, int sensors, int rate) {
        return false;
    }

    @Deprecated
    public void unregisterListener(SensorListener listener) {
    }

    @Deprecated
    public void unregisterListener(SensorListener listener, int sensors) {
    }

    public boolean registerListener(SensorEventListener listener, Sensor sensor, int delay) {
        return registerListener(listener, sensor, delay, 0, null);
    }

    public boolean registerListener(
            SensorEventListener listener, Sensor sensor, int delay, Handler handler) {
        return registerListener(listener, sensor, delay, 0, handler);
    }

    public boolean registerListener(
            SensorEventListener listener, Sensor sensor, int delay, int latency) {
        return registerListener(listener, sensor, delay, latency, null);
    }

    public abstract boolean registerListener(
            SensorEventListener listener, Sensor sensor, int delay, int latency, Handler handler);

    public abstract void unregisterListener(SensorEventListener listener);

    public abstract void unregisterListener(SensorEventListener listener, Sensor sensor);

    public abstract boolean flush(SensorEventListener listener);

    public SensorDirectChannel createDirectChannel(MemoryFile mem) {
        Objects.requireNonNull(mem, "mem cannot be null");
        if (mem.length() < 104) {
            throw new IllegalArgumentException(
                    "Size of MemoryFile has to be greater than 104");
        }
        return new SensorDirectChannel(SensorDirectChannel.TYPE_MEMORY_FILE);
    }

    public SensorDirectChannel createDirectChannel(HardwareBuffer mem) {
        Objects.requireNonNull(mem, "mem cannot be null");
        if (mem.getFormat() != HardwareBuffer.BLOB) {
            throw new IllegalArgumentException("Format of HardwareBuffer must be BLOB");
        }
        if (mem.isClosed()) {
            throw new IllegalArgumentException("HardwareBuffer is closed");
        }
        return new SensorDirectChannel(SensorDirectChannel.TYPE_HARDWARE_BUFFER);
    }

    public void registerDynamicSensorCallback(DynamicSensorCallback callback) {
        registerDynamicSensorCallback(callback, null);
    }

    public void registerDynamicSensorCallback(DynamicSensorCallback callback, Handler handler) {
    }

    public void unregisterDynamicSensorCallback(DynamicSensorCallback callback) {
    }

    public boolean isDynamicSensorDiscoverySupported() {
        return false;
    }

    public boolean requestTriggerSensor(TriggerEventListener listener, Sensor sensor) {
        return false;
    }

    public boolean cancelTriggerSensor(TriggerEventListener listener, Sensor sensor) {
        return false;
    }

    public static float getAltitude(float p0, float p) {
        final float coef = 1.0f / 5.255f;
        return 44330.0f * (1.0f - (float) Math.pow(p / p0, coef));
    }

    public static void getAngleChange(float[] angleChange, float[] R, float[] prevR) {
        float rd1 = 0, rd4 = 0, rd6 = 0, rd7 = 0, rd8 = 0;
        float ri0 = 0, ri1 = 0, ri2 = 0, ri3 = 0, ri4 = 0, ri5 = 0, ri6 = 0, ri7 = 0, ri8 = 0;
        float pri0 = 0, pri1 = 0, pri2 = 0, pri3 = 0, pri4 = 0, pri5 = 0, pri6 = 0, pri7 = 0, pri8 = 0;

        if (R.length == 9) {
            ri0 = R[0]; ri1 = R[1]; ri2 = R[2];
            ri3 = R[3]; ri4 = R[4]; ri5 = R[5];
            ri6 = R[6]; ri7 = R[7]; ri8 = R[8];
        } else if (R.length == 16) {
            ri0 = R[0]; ri1 = R[1]; ri2 = R[2];
            ri3 = R[4]; ri4 = R[5]; ri5 = R[6];
            ri6 = R[8]; ri7 = R[9]; ri8 = R[10];
        }

        if (prevR.length == 9) {
            pri0 = prevR[0]; pri1 = prevR[1]; pri2 = prevR[2];
            pri3 = prevR[3]; pri4 = prevR[4]; pri5 = prevR[5];
            pri6 = prevR[6]; pri7 = prevR[7]; pri8 = prevR[8];
        } else if (prevR.length == 16) {
            pri0 = prevR[0]; pri1 = prevR[1]; pri2 = prevR[2];
            pri3 = prevR[4]; pri4 = prevR[5]; pri5 = prevR[6];
            pri6 = prevR[8]; pri7 = prevR[9]; pri8 = prevR[10];
        }

        rd1 = pri0 * ri1 + pri3 * ri4 + pri6 * ri7;
        rd4 = pri1 * ri1 + pri4 * ri4 + pri7 * ri7;
        rd6 = pri2 * ri0 + pri5 * ri3 + pri8 * ri6;
        rd7 = pri2 * ri1 + pri5 * ri4 + pri8 * ri7;
        rd8 = pri2 * ri2 + pri5 * ri5 + pri8 * ri8;

        angleChange[0] = (float) Math.atan2(rd1, rd4);
        angleChange[1] = (float) Math.asin(-rd7);
        angleChange[2] = (float) Math.atan2(-rd6, rd8);
    }

    /** Rows of 3 or 4 floats: writes a 3x3 block (and identity padding for 4x4); other lengths are left untouched, as in AOSP. */
    private static void put(float[] m, float a, float b, float c, float d, float e, float f, float g, float h, float i) {
        if (m == null) return;
        if (m.length == 9) {
            m[0] = a; m[1] = b; m[2] = c; m[3] = d; m[4] = e; m[5] = f; m[6] = g; m[7] = h; m[8] = i;
        } else if (m.length == 16) {
            m[0] = a; m[1] = b; m[2] = c; m[3] = 0; m[4] = d; m[5] = e; m[6] = f; m[7] = 0;
            m[8] = g; m[9] = h; m[10] = i; m[11] = 0; m[12] = 0; m[13] = 0; m[14] = 0; m[15] = 1;
        }
    }

    /** World frame: X = east (gravity x field), Y = magnetic north, Z = up. False in free fall or near the poles of the field. */
    public static boolean getRotationMatrix(float[] R, float[] I, float[] gravity, float[] geomagnetic) {
        float ax = gravity[0], ay = gravity[1], az = gravity[2];
        float normA2 = ax * ax + ay * ay + az * az;
        if (normA2 < GRAVITY_EARTH * GRAVITY_EARTH * 0.01f) return false;
        float ex = geomagnetic[0], ey = geomagnetic[1], ez = geomagnetic[2];
        float hx = ey * az - ez * ay, hy = ez * ax - ex * az, hz = ex * ay - ey * ax;
        float normH = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
        if (normH < 0.1f) return false;
        hx /= normH; hy /= normH; hz /= normH;
        float invA = 1f / (float) Math.sqrt(normA2);
        ax *= invA; ay *= invA; az *= invA;
        float mx = ay * hz - az * hy, my = az * hx - ax * hz, mz = ax * hy - ay * hx;
        put(R, hx, hy, hz, mx, my, mz, ax, ay, az);
        if (I != null) {
            float invE = 1f / (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
            float c = (ex * mx + ey * my + ez * mz) * invE, s = (ex * ax + ey * ay + ez * az) * invE;
            put(I, 1, 0, 0, 0, c, s, 0, -s, c);
        }
        return true;
    }

    public static float getInclination(float[] I) {
        return I.length == 9 ? (float) Math.atan2(I[5], I[4]) : (float) Math.atan2(I[6], I[5]);
    }

    /** Unit quaternion (x, y, z[, w]) to rotation matrix; w is derived when the vector has three components. */
    public static void getRotationMatrixFromVector(float[] R, float[] rotationVector) {
        float x = rotationVector[0], y = rotationVector[1], z = rotationVector[2], w;
        if (rotationVector.length >= 4) {
            w = rotationVector[3];
        } else {
            float rest = 1 - x * x - y * y - z * z;
            w = rest > 0 ? (float) Math.sqrt(rest) : 0;
        }
        float xx = 2 * x * x, yy = 2 * y * y, zz = 2 * z * z;
        float xy = 2 * x * y, zw = 2 * z * w, xz = 2 * x * z, yw = 2 * y * w, yz = 2 * y * z, xw = 2 * x * w;
        put(R, 1 - yy - zz, xy - zw, xz + yw, xy + zw, 1 - xx - zz, yz - xw, xz - yw, yz + xw, 1 - xx - yy);
    }

    public static void getQuaternionFromVector(float[] Q, float[] rv) {
        if (rv.length >= 4) {
            Q[0] = rv[3];
        } else {
            float rest = 1 - rv[0] * rv[0] - rv[1] * rv[1] - rv[2] * rv[2];
            Q[0] = rest > 0 ? (float) Math.sqrt(rest) : 0;
        }
        Q[1] = rv[0]; Q[2] = rv[1]; Q[3] = rv[2];
    }

    /** Azimuth (about -Z), pitch (about -X) and roll (about Y) in radians. */
    public static float[] getOrientation(float[] R, float[] values) {
        if (R.length == 9) {
            values[0] = (float) Math.atan2(R[1], R[4]);
            values[1] = (float) Math.asin(-R[7]);
            values[2] = (float) Math.atan2(-R[6], R[8]);
        } else {
            values[0] = (float) Math.atan2(R[1], R[5]);
            values[1] = (float) Math.asin(-R[9]);
            values[2] = (float) Math.atan2(-R[8], R[10]);
        }
        return values;
    }

    /** Re-expresses R with device axes X and Y mapped onto the given AXIS_* values; Z follows the right-hand rule. */
    public static boolean remapCoordinateSystem(float[] inR, int X, int Y, float[] outR) {
        if (inR == outR) {
            float[] copy = inR.clone();
            return remapCoordinateSystem(copy, X, Y, outR);
        }
        if (inR.length != outR.length || (inR.length != 9 && inR.length != 16)) return false;
        if ((X & 0x7C) != 0 || (Y & 0x7C) != 0) return false;
        if ((X & 3) == 0 || (Y & 3) == 0 || (X & 3) == (Y & 3)) return false;
        int Z = X ^ Y;
        int x = (X & 3) - 1, y = (Y & 3) - 1, z = (Z & 3) - 1;
        if (((x ^ ((z + 1) % 3)) | (y ^ ((z + 2) % 3))) != 0) Z ^= 0x80; // Odd permutation flips Z.
        boolean sx = X >= 0x80, sy = Y >= 0x80, sz = Z >= 0x80;
        int row = outR.length == 16 ? 4 : 3;
        for (int j = 0; j < 3; j++) {
            int o = j * row;
            for (int i = 0; i < 3; i++) {
                if (x == i) outR[o + i] = sx ? -inR[o] : inR[o];
                if (y == i) outR[o + i] = sy ? -inR[o + 1] : inR[o + 1];
                if (z == i) outR[o + i] = sz ? -inR[o + 2] : inR[o + 2];
            }
        }
        if (row == 4) {
            outR[3] = outR[7] = outR[11] = outR[12] = outR[13] = outR[14] = 0;
            outR[15] = 1;
        }
        return true;
    }

    public abstract static class DynamicSensorCallback {
        public DynamicSensorCallback() {
        }

        public void onDynamicSensorConnected(Sensor sensor) {
        }

        public void onDynamicSensorDisconnected(Sensor sensor) {
        }
    }
}
