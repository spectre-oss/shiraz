// SPDX-License-Identifier: GPL-3.0-only
package android.location.altitude;

import android.content.Context;
import android.location.Location;
import java.io.IOException;
import java.util.Objects;

public final class AltitudeConverter {
    // Global 15-degree EGM96 geoid undulation grid in meters:
    // Rows: latitude -90..+90 in 15-degree steps (13 rows)
    // Columns: longitude -180..+165 in 15-degree steps (24 columns, periodic at 360)
    private static final float[][] EGM96_GEOID_METERS = new float[][] {
        { -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f,
          -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f, -30f },
        { -46f, -44f, -45f, -46f, -49f, -50f, -45f, -34f, -18f,   0f,  14f,  21f,
           20f,  15f,   9f,   2f,  -8f, -21f, -34f, -45f, -53f, -57f, -56f, -51f },
        { -17f, -21f, -28f, -33f, -38f, -40f, -33f, -15f,   8f,  26f,  35f,  35f,
           30f,  24f,  19f,  16f,   9f,  -6f, -25f, -40f, -45f, -41f, -31f, -21f },
        {  33f,  15f,  -2f, -13f, -21f, -26f, -22f,  -4f,  19f,  34f,  36f,  29f,
           25f,  28f,  32f,  29f,  15f, -12f, -36f, -42f, -28f,  -3f,  22f,  36f },
        {  47f,  27f,  14f,   5f,  -7f, -17f, -18f,  -5f,  15f,  27f,  24f,  12f,
            9f,  21f,  34f,  28f,   1f, -36f, -55f, -37f,   5f,  46f,  66f,  63f },
        {  39f,  27f,  20f,  14f,   1f, -15f, -26f, -21f,  -1f,  17f,  18f,   4f,
           -5f,   8f,  26f,  15f, -29f, -79f, -92f, -47f,  19f,  64f,  73f,  58f },
        {  27f,  20f,  18f,  14f,   3f, -16f, -35f, -37f, -16f,  12f,  23f,  12f,
           -3f,   4f,  17f,   2f, -48f, -96f, -92f, -35f,  31f,  68f,  65f,  45f },
        {  14f,  12f,  14f,  11f,  -2f, -20f, -38f, -43f, -21f,  14f,  36f,  34f,
           21f,  16f,  12f, -10f, -48f, -74f, -58f,  -8f,  40f,  57f,  46f,  28f },
        {   2f,   5f,   7f,   4f, -10f, -24f, -34f, -35f,  -8f,  30f,  53f,  51f,
           39f,  25f,   2f, -25f, -46f, -49f, -31f,   3f,  32f,  36f,  22f,   7f },
        {  -2f,   2f,   2f,  -2f, -14f, -23f, -26f, -23f,   5f,  44f,  63f,  58f,
           44f,  23f,  -5f, -31f, -43f, -39f, -22f,   3f,  20f,  19f,   8f,  -1f },
        {   6f,   7f,   5f,   1f,  -6f, -12f, -15f, -10f,  18f,  51f,  63f,  56f,
           41f,  21f,  -2f, -22f, -32f, -29f, -15f,   3f,  14f,  14f,   8f,   5f },
        {  14f,  13f,  11f,   9f,   7f,   4f,   3f,   9f,  29f,  49f,  57f,  52f,
           41f,  27f,  12f,   0f,  -7f,  -7f,  -1f,   9f,  16f,  18f,  16f,  15f },
        {  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,
           14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f,  14f }
    };

    private static final float GEOID_MODEL_RMSE_METERS = 1.5f;

    public AltitudeConverter() {
    }

    public void addMslAltitudeToLocation(Context context, Location location) throws IOException {
        Objects.requireNonNull(context, "context must not be null");
        validate(location);
        applyGeoidOffset(location);
    }

    public boolean tryAddMslAltitudeToLocation(Location location) {
        validate(location);
        applyGeoidOffset(location);
        return true;
    }

    private static void validate(Location location) {
        Objects.requireNonNull(location, "location must not be null");
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        if (!Double.isFinite(lat) || lat < -90.0 || lat > 90.0) {
            throw new IllegalArgumentException("Invalid latitude: " + lat);
        }
        if (!Double.isFinite(lon) || lon < -180.0 || lon > 180.0) {
            throw new IllegalArgumentException("Invalid longitude: " + lon);
        }
        if (!location.hasAltitude() || !Double.isFinite(location.getAltitude())) {
            throw new IllegalArgumentException("Invalid altitude on location");
        }
    }

    private static void applyGeoidOffset(Location location) {
        double geoidUndulation = interpolateGeoidMeters(
                location.getLatitude(), location.getLongitude());
        location.setMslAltitudeMeters(location.getAltitude() - geoidUndulation);
        if (location.hasVerticalAccuracy()) {
            float vAcc = location.getVerticalAccuracyMeters();
            if (Float.isFinite(vAcc) && vAcc >= 0.0f) {
                location.setMslAltitudeAccuracyMeters(
                        (float) Math.hypot(vAcc, GEOID_MODEL_RMSE_METERS));
            }
        }
    }

    private static double interpolateGeoidMeters(double latitude, double longitude) {
        double latPos = (latitude + 90.0) / 15.0;
        int r0 = (int) Math.floor(latPos);
        if (r0 < 0) r0 = 0;
        if (r0 > 11) r0 = 11;
        int r1 = r0 + 1;
        double ty = latPos - r0;

        double wrappedLon = longitude;
        if (wrappedLon >= 180.0) {
            wrappedLon -= 360.0;
        }
        double lonPos = (wrappedLon + 180.0) / 15.0;
        int c0 = (int) Math.floor(lonPos);
        if (c0 < 0) c0 = 0;
        if (c0 >= 24) c0 = 23;
        int c1 = (c0 + 1) % 24;
        double tx = lonPos - c0;

        double n00 = EGM96_GEOID_METERS[r0][c0];
        double n10 = EGM96_GEOID_METERS[r0][c1];
        double n01 = EGM96_GEOID_METERS[r1][c0];
        double n11 = EGM96_GEOID_METERS[r1][c1];

        double n0 = n00 * (1.0 - tx) + n10 * tx;
        double n1 = n01 * (1.0 - tx) + n11 * tx;
        return n0 * (1.0 - ty) + n1 * ty;
    }
}
