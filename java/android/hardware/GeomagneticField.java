// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Estimates Earth's magnetic field at a given WGS-84 geodetic location and epoch using the
 * public-domain NOAA/NCEI World Magnetic Model (WMM2020, base epoch 2020.0, degree 12).
 */
public class GeomagneticField {
    private static final double EARTH_SEMI_MAJOR_AXIS_KM = 6378.137;
    private static final double EARTH_SEMI_MINOR_AXIS_KM = 6356.7523142;
    private static final double EARTH_REFERENCE_RADIUS_KM = 6371.2;
    private static final int MAX_DEGREE = 12;
    private static final long BASE_TIME_MILLIS;

    static {
        GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        cal.clear();
        cal.set(2020, 0, 1, 0, 0, 0);
        BASE_TIME_MILLIS = cal.getTimeInMillis();
    }

    // WMM2020 main-field Gauss coefficients g_n^m (nT) for n = 0..12, m = 0..n.
    private static final float[][] G_COEFF = new float[][] {
        { 0.0f },
        { -29404.5f, -1450.7f },
        { -2500.0f, 2982.0f, 1676.8f },
        { 1363.9f, -2381.0f, 1236.2f, 525.7f },
        { 903.1f, 809.4f, 86.2f, -309.4f, 47.9f },
        { -234.4f, 363.1f, 187.8f, -140.7f, -151.2f, 13.7f },
        { 65.9f, 65.6f, 73.0f, -121.5f, -36.2f, 13.5f, -64.7f },
        { 80.6f, -76.8f, -8.3f, 56.5f, 15.8f, 6.4f, -7.2f, 9.8f },
        { 23.6f, 9.8f, -17.5f, -0.4f, -21.1f, 15.3f, 13.7f, -16.5f, -0.3f },
        { 5.0f, 8.2f, 2.9f, -1.4f, -1.1f, -13.3f, 1.1f, 8.9f, -9.3f, -11.9f },
        { -1.9f, -6.2f, -0.1f, 1.7f, -0.9f, 0.6f, -0.9f, 1.9f, 1.4f, -2.4f, -3.9f },
        { 3.0f, -1.4f, -2.5f, 2.4f, -0.9f, 0.3f, -0.7f, -0.1f, 1.4f, -0.6f, 0.2f, 3.1f },
        { -2.0f, -0.1f, 0.5f, 1.3f, -1.2f, 0.7f, 0.3f, 0.5f, -0.2f, -0.5f, 0.1f, -1.1f, -0.3f }
    };

    // WMM2020 main-field Gauss coefficients h_n^m (nT) for n = 0..12, m = 0..n.
    private static final float[][] H_COEFF = new float[][] {
        { 0.0f },
        { 0.0f, 4652.9f },
        { 0.0f, -2991.6f, -734.8f },
        { 0.0f, -82.2f, 241.8f, -542.9f },
        { 0.0f, 282.0f, -158.4f, 199.8f, -350.1f },
        { 0.0f, 47.7f, 208.4f, -121.3f, 32.2f, 99.1f },
        { 0.0f, -19.1f, 25.0f, 52.7f, -64.4f, 9.0f, 68.1f },
        { 0.0f, -51.4f, -16.8f, 2.3f, 23.5f, -2.2f, -27.2f, -1.9f },
        { 0.0f, 8.4f, -15.3f, 12.8f, -11.8f, 14.9f, 3.6f, -6.9f, 2.8f },
        { 0.0f, -23.3f, 11.1f, 9.8f, -5.1f, -6.2f, 7.8f, 0.4f, -1.5f, 9.7f },
        { 0.0f, 3.4f, -0.2f, 3.5f, 4.8f, -8.6f, -0.1f, -4.2f, -3.4f, -0.1f, -8.8f },
        { 0.0f, 0.0f, 2.6f, -0.5f, -0.4f, 0.6f, -0.2f, -1.7f, -1.6f, -3.0f, -2.0f, -2.6f },
        { 0.0f, -1.2f, 0.5f, 1.3f, -1.8f, 0.1f, 0.7f, -0.1f, 0.6f, 0.2f, -0.9f, 0.0f, 0.5f }
    };

    // WMM2020 secular variation dg_n^m (nT/year).
    private static final float[][] DELTA_G = new float[][] {
        { 0.0f },
        { 6.7f, 7.7f },
        { -11.5f, -7.1f, -2.2f },
        { 2.8f, -6.2f, 3.4f, -12.2f },
        { -1.1f, -1.6f, -6.0f, 5.4f, -5.5f },
        { -0.3f, 0.6f, -0.7f, 0.1f, 1.2f, 1.0f },
        { -0.6f, -0.4f, 0.5f, 1.4f, -1.4f, 0.0f, 0.8f },
        { -0.1f, -0.3f, -0.1f, 0.7f, 0.2f, -0.5f, -0.8f, 1.0f },
        { -0.1f, 0.1f, -0.1f, 0.5f, -0.1f, 0.4f, 0.5f, 0.0f, 0.4f },
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f }
    };

    // WMM2020 secular variation dh_n^m (nT/year).
    private static final float[][] DELTA_H = new float[][] {
        { 0.0f },
        { 0.0f, -25.1f },
        { 0.0f, -30.2f, -23.9f },
        { 0.0f, 5.7f, -1.0f, 1.1f },
        { 0.0f, 0.2f, 6.9f, 3.7f, -5.6f },
        { 0.0f, 0.2f, 2.5f, -0.9f, 3.0f, 0.5f },
        { 0.0f, 0.1f, -1.8f, -1.4f, 0.9f, 0.1f, 1.0f },
        { 0.0f, 0.5f, 0.6f, -0.7f, -0.2f, -1.2f, 0.2f, 0.3f },
        { 0.0f, -0.3f, 0.7f, -0.2f, 0.5f, -0.3f, -0.5f, 0.4f, 0.1f },
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f },
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f }
    };

    private final float mX;
    private final float mY;
    private final float mZ;

    public GeomagneticField(
            float gdLatitudeDeg, float gdLongitudeDeg, float altitudeMeters, long timeMillis) {
        double clampedLatDeg = Math.max(-89.9999, Math.min(89.9999, gdLatitudeDeg));
        double gdLatRad = Math.toRadians(clampedLatDeg);
        double gdLonRad = Math.toRadians(gdLongitudeDeg);
        double altKm = altitudeMeters / 1000.0;

        // Geodetic (gdLatRad, altKm) to geocentric spherical (gcLatRad, rKm) on WGS-84.
        double a2 = EARTH_SEMI_MAJOR_AXIS_KM * EARTH_SEMI_MAJOR_AXIS_KM;
        double b2 = EARTH_SEMI_MINOR_AXIS_KM * EARTH_SEMI_MINOR_AXIS_KM;
        double sinLat = Math.sin(gdLatRad);
        double cosLat = Math.cos(gdLatRad);
        double sin2 = sinLat * sinLat;
        double cos2 = cosLat * cosLat;
        double rc = a2 / Math.sqrt(a2 * cos2 + b2 * sin2);
        double xp = (rc + altKm) * cosLat;
        double zp = (rc * (b2 / a2) + altKm) * sinLat;
        double rKm = Math.sqrt(xp * xp + zp * zp);
        double gcLatRad = Math.asin(zp / rKm);

        double yearsSinceBase =
                (timeMillis - BASE_TIME_MILLIS) / (365.2425 * 24.0 * 3600.0 * 1000.0);

        // Compute Schmidt semi-normalized associated Legendre functions P_n^m(sin(gcLatRad))
        // and derivatives dP_n^m / d(gcLatRad) via co-latitude theta = pi/2 - gcLatRad.
        // Note: d/d(phi') = -d/d(theta).
        double sinPhi = Math.sin(gcLatRad);
        double cosPhi = Math.cos(gcLatRad);
        if (Math.abs(cosPhi) < 1.0e-10) {
            cosPhi = 1.0e-10;
        }

        double[][] p = new double[MAX_DEGREE + 1][MAX_DEGREE + 1];
        double[][] dpDTheta = new double[MAX_DEGREE + 1][MAX_DEGREE + 1];
        // In co-latitude theta: cos(theta) = sinPhi, sin(theta) = cosPhi.
        double cosTheta = sinPhi;
        double sinTheta = cosPhi;
        p[0][0] = 1.0;
        dpDTheta[0][0] = 0.0;

        for (int n = 1; n <= MAX_DEGREE; n++) {
            // Diagonal recurrence in unnormalized Gauss form, converted to Schmidt quasi-norm:
            // P_n^n(cos theta) = sqrt((2n - 1) / (2n)) * sin(theta) * P_{n-1}^{n-1} (for n > 1),
            // and for n == 1: P_1^1(cos theta) = sin(theta).
            double factor = (n == 1) ? 1.0 : Math.sqrt((2.0 * n - 1.0) / (2.0 * n));
            p[n][n] = factor * sinTheta * p[n - 1][n - 1];
            dpDTheta[n][n] =
                    factor * (cosTheta * p[n - 1][n - 1] + sinTheta * dpDTheta[n - 1][n - 1]);

            for (int m = 0; m < n; m++) {
                double norm1 = (2.0 * n - 1.0) / Math.sqrt((double) (n * n - m * m));
                double pPrev2 = (n - 2 >= m) ? p[n - 2][m] : 0.0;
                double dpPrev2 = (n - 2 >= m) ? dpDTheta[n - 2][m] : 0.0;
                double norm2 = 0.0;
                if (n - 2 >= m) {
                    norm2 = Math.sqrt(
                            ((double) ((n - 1) * (n - 1) - m * m)) / (double) (n * n - m * m));
                }
                p[n][m] = norm1 * cosTheta * p[n - 1][m] - norm2 * pPrev2;
                dpDTheta[n][m] =
                        norm1 * (-sinTheta * p[n - 1][m] + cosTheta * dpDTheta[n - 1][m])
                                - norm2 * dpPrev2;
            }
        }

        double[] cosMLon = new double[MAX_DEGREE + 1];
        double[] sinMLon = new double[MAX_DEGREE + 1];
        for (int m = 0; m <= MAX_DEGREE; m++) {
            cosMLon[m] = Math.cos(m * gdLonRad);
            sinMLon[m] = Math.sin(m * gdLonRad);
        }

        double aOverR = EARTH_REFERENCE_RADIUS_KM / rKm;
        double ratioPow = aOverR * aOverR; // (a/r)^(n+2) starting at n=1 gives (a/r)^3 below
        double gcX = 0.0; // Northward in geocentric spherical coordinates
        double gcY = 0.0; // Eastward in geocentric spherical coordinates
        double gcZ = 0.0; // Downward (-radial) in geocentric spherical coordinates

        for (int n = 1; n <= MAX_DEGREE; n++) {
            ratioPow *= aOverR; // Now (a / r)^(n + 2)
            for (int m = 0; m <= n; m++) {
                double g = G_COEFF[n][m] + yearsSinceBase * DELTA_G[n][m];
                double h = H_COEFF[n][m] + yearsSinceBase * DELTA_H[n][m];
                double harmonic = g * cosMLon[m] + h * sinMLon[m];
                double dHarmonicDLon = m * (-g * sinMLon[m] + h * cosMLon[m]);
                // B = -grad(V): X' = -(1/r) dV/d(phi'), with dP/d(phi') = -dP/d(theta)
                double dpDPhi = -dpDTheta[n][m];

                gcX -= ratioPow * harmonic * dpDPhi;
                gcY -= ratioPow * dHarmonicDLon * p[n][m] / cosPhi;
                gcZ -= (n + 1.0) * ratioPow * harmonic * p[n][m];
            }
        }

        // Rotate from geocentric (gcLatRad) to geodetic (gdLatRad) local tangent plane.
        double psi = gcLatRad - gdLatRad;
        double cosPsi = Math.cos(psi);
        double sinPsi = Math.sin(psi);
        mX = (float) (gcX * cosPsi - gcZ * sinPsi);
        mY = (float) gcY;
        mZ = (float) (gcX * sinPsi + gcZ * cosPsi);
    }

    public float getX() {
        return mX;
    }

    public float getY() {
        return mY;
    }

    public float getZ() {
        return mZ;
    }

    public float getDeclination() {
        return (float) Math.toDegrees(Math.atan2(mY, mX));
    }

    public float getInclination() {
        return (float) Math.toDegrees(Math.atan2(mZ, getHorizontalStrength()));
    }

    public float getHorizontalStrength() {
        return (float) Math.hypot(mX, mY);
    }

    public float getFieldStrength() {
        return (float) Math.sqrt(mX * mX + mY * mY + mZ * mZ);
    }
}
