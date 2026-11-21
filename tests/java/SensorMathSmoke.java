// SPDX-License-Identifier: GPL-3.0-only
// SensorManager static orientation math, as OsmAndLocationProvider.processSensorEvent uses it for the compass.
import android.hardware.SensorManager;

public final class SensorMathSmoke {
    private SensorMathSmoke() {}

    static void check(boolean ok, String why) {
        if (!ok) {
            throw new AssertionError(why);
        }
    }

    static boolean near(float a, double b) {
        return Math.abs(a - b) < 1e-4;
    }

    static void matrix(float[] m, float[] expected, String why) {
        for (int i = 0; i < expected.length; i++) {
            check(near(m[i], expected[i]), why + " [" + i + "]=" + m[i]);
        }
    }

    static final float[] IDENTITY = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    public static void main(String[] args) {
        float[] r = new float[9], inclination = new float[9], values = new float[3];
        // Flat, screen up, top edge to magnetic north, field dipping 60 degrees down.
        float[] gravity = {0, 0, 9.81f}, field = {0, 20, -34.641f};
        check(SensorManager.getRotationMatrix(r, inclination, gravity, field), "Flat device");
        matrix(r, IDENTITY, "Flat device matrix");
        check(near(SensorManager.getInclination(inclination), -Math.toRadians(60)), "Inclination " + SensorManager.getInclination(inclination));
        SensorManager.getOrientation(r, values);
        check(near(values[0], 0) && near(values[1], 0) && near(values[2], 0), "Flat orientation");

        // Device top pointing east: field comes in along the device -X axis.
        check(SensorManager.getRotationMatrix(r, null, gravity, new float[] {-20, 0, -34.641f}), "East device");
        SensorManager.getOrientation(r, values);
        check(near(values[0], Math.PI / 2), "Azimuth east " + values[0]);

        check(!SensorManager.getRotationMatrix(r, null, new float[] {0, 0, 0.5f}, field), "Free fall");
        check(!SensorManager.getRotationMatrix(r, null, gravity, new float[] {0, 0, -40}), "Field parallel to gravity");

        // Rotation vector for 90 degrees about Z, with and without the scalar part.
        float half = (float) Math.sqrt(0.5);
        float[] r16 = new float[16];
        SensorManager.getRotationMatrixFromVector(r, new float[] {0, 0, half, half});
        matrix(r, new float[] {0, -1, 0, 1, 0, 0, 0, 0, 1}, "Rotation vector 4");
        SensorManager.getRotationMatrixFromVector(r16, new float[] {0, 0, half});
        matrix(r16, new float[] {0, -1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}, "Rotation vector 3 into 4x4");
        SensorManager.getOrientation(r16, values);
        check(near(values[0], -Math.PI / 2), "4x4 azimuth " + values[0]);
        float[] q = new float[4];
        SensorManager.getQuaternionFromVector(q, new float[] {0, 0, half});
        check(near(q[0], half) && near(q[3], half), "Quaternion");

        // Landscape remap (OsmAnd: AXIS_Y, AXIS_MINUS_X), in place.
        float[] remapped = IDENTITY.clone();
        check(SensorManager.remapCoordinateSystem(remapped, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remapped), "Landscape remap");
        matrix(remapped, new float[] {0, 1, 0, -1, 0, 0, 0, 0, 1}, "Landscape remap matrix");
        float[] out = new float[9];
        check(SensorManager.remapCoordinateSystem(IDENTITY, SensorManager.AXIS_X, SensorManager.AXIS_Z, out), "Upright remap");
        matrix(out, new float[] {1, 0, 0, 0, 0, 1, 0, -1, 0}, "Upright remap matrix");
        check(!SensorManager.remapCoordinateSystem(IDENTITY, SensorManager.AXIS_X, SensorManager.AXIS_X, out), "Same axis rejected");
        check(!SensorManager.remapCoordinateSystem(IDENTITY, SensorManager.AXIS_X, SensorManager.AXIS_Y, new float[16]), "Length mismatch rejected");
        System.out.println("SENSOR_MATH_OK");
    }
}
