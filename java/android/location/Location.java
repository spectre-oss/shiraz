// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Printer;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Objects;
import java.util.StringTokenizer;

/** Location value carrier. Creating a value does not obtain a host/GPS position. */
public class Location implements Parcelable {
    public static final int FORMAT_DEGREES = 0;
    public static final int FORMAT_MINUTES = 1;
    public static final int FORMAT_SECONDS = 2;

    private static final int ALTITUDE = 1;
    private static final int SPEED = 2;
    private static final int BEARING = 4;
    private static final int ACCURACY = 8;
    private static final int MOCK = 16;
    private static final int VERTICAL = 32;
    private static final int SPEED_ACCURACY = 64;
    private static final int BEARING_ACCURACY = 128;
    private static final int ELAPSED_REALTIME_UNCERTAINTY = 256;
    private static final int MSL_ALTITUDE = 512;
    private static final int MSL_ALTITUDE_ACCURACY = 1024;

    private String provider;
    private long time;
    private long elapsed;
    private double elapsedUncertainty;
    private double latitude;
    private double longitude;
    private double altitude;
    private float speed;
    private float bearing;
    private float accuracy;
    private float verticalAccuracy;
    private float speedAccuracy;
    private float bearingAccuracy;
    private double mslAltitude;
    private float mslAltitudeAccuracy;
    private int fields;
    private Bundle extras;

    public Location(String provider) {
        this.provider = provider;
    }

    public Location(Location source) {
        set(source);
    }

    public void set(Location source) {
        if (source == null) {
            throw new NullPointerException("location");
        }
        provider = source.provider;
        time = source.time;
        elapsed = source.elapsed;
        elapsedUncertainty = source.elapsedUncertainty;
        latitude = source.latitude;
        longitude = source.longitude;
        altitude = source.altitude;
        speed = source.speed;
        bearing = source.bearing;
        accuracy = source.accuracy;
        verticalAccuracy = source.verticalAccuracy;
        speedAccuracy = source.speedAccuracy;
        bearingAccuracy = source.bearingAccuracy;
        mslAltitude = source.mslAltitude;
        mslAltitudeAccuracy = source.mslAltitudeAccuracy;
        fields = source.fields;
        extras = source.extras == null ? null : new Bundle(source.extras);
    }

    public void reset() {
        provider = null;
        time = 0L;
        elapsed = 0L;
        elapsedUncertainty = 0.0;
        latitude = 0.0;
        longitude = 0.0;
        altitude = 0.0;
        speed = 0.0f;
        bearing = 0.0f;
        accuracy = 0.0f;
        verticalAccuracy = 0.0f;
        speedAccuracy = 0.0f;
        bearingAccuracy = 0.0f;
        mslAltitude = 0.0;
        mslAltitudeAccuracy = 0.0f;
        fields = 0;
        extras = null;
    }

    public float distanceTo(Location dest) {
        float[] results = new float[1];
        computeDistanceAndBearing(latitude, longitude, dest.latitude, dest.longitude, results);
        return results[0];
    }

    public float bearingTo(Location dest) {
        float[] results = new float[2];
        computeDistanceAndBearing(latitude, longitude, dest.latitude, dest.longitude, results);
        return results[1];
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String value) {
        provider = value;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long value) {
        time = value;
    }

    public long getElapsedRealtimeNanos() {
        return elapsed;
    }

    public void setElapsedRealtimeNanos(long value) {
        elapsed = value;
    }

    public long getElapsedRealtimeMillis() {
        return elapsed / 1000000L;
    }

    public long getElapsedRealtimeAgeMillis() {
        long ref;
        try {
            ref = SystemClock.elapsedRealtime();
        } catch (Throwable ignored) {
            ref = System.nanoTime() / 1000000L;
        }
        return getElapsedRealtimeAgeMillis(ref);
    }

    public long getElapsedRealtimeAgeMillis(long referenceRealtimeMs) {
        return referenceRealtimeMs - getElapsedRealtimeMillis();
    }

    public double getElapsedRealtimeUncertaintyNanos() {
        return elapsedUncertainty;
    }

    public void setElapsedRealtimeUncertaintyNanos(double value) {
        elapsedUncertainty = value;
        fields |= ELAPSED_REALTIME_UNCERTAINTY;
    }

    public boolean hasElapsedRealtimeUncertaintyNanos() {
        return (fields & ELAPSED_REALTIME_UNCERTAINTY) != 0;
    }

    public void removeElapsedRealtimeUncertaintyNanos() {
        elapsedUncertainty = 0.0;
        fields &= ~ELAPSED_REALTIME_UNCERTAINTY;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double value) {
        latitude = value;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double value) {
        longitude = value;
    }

    public boolean hasAltitude() {
        return (fields & ALTITUDE) != 0;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double value) {
        altitude = value;
        fields |= ALTITUDE;
    }

    public void removeAltitude() {
        altitude = 0.0;
        fields &= ~ALTITUDE;
    }

    public boolean hasSpeed() {
        return (fields & SPEED) != 0;
    }

    public float getSpeed() {
        return speed;
    }

    public void setSpeed(float value) {
        speed = value;
        fields |= SPEED;
    }

    public void removeSpeed() {
        speed = 0.0f;
        fields &= ~SPEED;
    }

    public boolean hasBearing() {
        return (fields & BEARING) != 0;
    }

    public float getBearing() {
        return bearing;
    }

    public void setBearing(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Bearing must be finite");
        }
        bearing = value % 360.0f;
        if (bearing < 0.0f) {
            bearing += 360.0f;
        }
        if (bearing == 0.0f) {
            bearing = 0.0f;
        }
        fields |= BEARING;
    }

    public void removeBearing() {
        bearing = 0.0f;
        fields &= ~BEARING;
    }

    public boolean hasAccuracy() {
        return (fields & ACCURACY) != 0;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float value) {
        accuracy = value;
        fields |= ACCURACY;
    }

    public void removeAccuracy() {
        accuracy = 0.0f;
        fields &= ~ACCURACY;
    }

    public boolean hasVerticalAccuracy() {
        return (fields & VERTICAL) != 0;
    }

    public float getVerticalAccuracyMeters() {
        return verticalAccuracy;
    }

    public void setVerticalAccuracyMeters(float value) {
        verticalAccuracy = value;
        fields |= VERTICAL;
    }

    public void removeVerticalAccuracy() {
        verticalAccuracy = 0.0f;
        fields &= ~VERTICAL;
    }

    public boolean hasSpeedAccuracy() {
        return (fields & SPEED_ACCURACY) != 0;
    }

    public float getSpeedAccuracyMetersPerSecond() {
        return speedAccuracy;
    }

    public void setSpeedAccuracyMetersPerSecond(float value) {
        speedAccuracy = value;
        fields |= SPEED_ACCURACY;
    }

    public void removeSpeedAccuracy() {
        speedAccuracy = 0.0f;
        fields &= ~SPEED_ACCURACY;
    }

    public boolean hasBearingAccuracy() {
        return (fields & BEARING_ACCURACY) != 0;
    }

    public float getBearingAccuracyDegrees() {
        return bearingAccuracy;
    }

    public void setBearingAccuracyDegrees(float value) {
        bearingAccuracy = value;
        fields |= BEARING_ACCURACY;
    }

    public void removeBearingAccuracy() {
        bearingAccuracy = 0.0f;
        fields &= ~BEARING_ACCURACY;
    }

    public double getMslAltitudeMeters() {
        return mslAltitude;
    }

    public void setMslAltitudeMeters(double value) {
        mslAltitude = value;
        fields |= MSL_ALTITUDE;
    }

    public boolean hasMslAltitude() {
        return (fields & MSL_ALTITUDE) != 0;
    }

    public void removeMslAltitude() {
        mslAltitude = 0.0;
        fields &= ~MSL_ALTITUDE;
    }

    public float getMslAltitudeAccuracyMeters() {
        return mslAltitudeAccuracy;
    }

    public void setMslAltitudeAccuracyMeters(float value) {
        mslAltitudeAccuracy = value;
        fields |= MSL_ALTITUDE_ACCURACY;
    }

    public boolean hasMslAltitudeAccuracy() {
        return (fields & MSL_ALTITUDE_ACCURACY) != 0;
    }

    public void removeMslAltitudeAccuracy() {
        mslAltitudeAccuracy = 0.0f;
        fields &= ~MSL_ALTITUDE_ACCURACY;
    }

    @Deprecated
    public boolean isFromMockProvider() {
        return (fields & MOCK) != 0;
    }

    public boolean isMock() {
        return isFromMockProvider();
    }

    public void setMock(boolean value) {
        if (value) {
            fields |= MOCK;
        } else {
            fields &= ~MOCK;
        }
    }

    public Bundle getExtras() {
        return extras;
    }

    public void setExtras(Bundle value) {
        extras = value == null ? null : new Bundle(value);
    }

    public boolean isComplete() {
        return provider != null && hasAccuracy() && time != 0L && elapsed != 0L;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Location)) {
            return false;
        }
        Location l = (Location) o;
        return time == l.time
                && elapsed == l.elapsed
                && fields == l.fields
                && Double.compare(l.latitude, latitude) == 0
                && Double.compare(l.longitude, longitude) == 0
                && Double.compare(l.altitude, altitude) == 0
                && Float.compare(l.speed, speed) == 0
                && Float.compare(l.bearing, bearing) == 0
                && Float.compare(l.accuracy, accuracy) == 0
                && Float.compare(l.verticalAccuracy, verticalAccuracy) == 0
                && Float.compare(l.speedAccuracy, speedAccuracy) == 0
                && Float.compare(l.bearingAccuracy, bearingAccuracy) == 0
                && Double.compare(l.elapsedUncertainty, elapsedUncertainty) == 0
                && Double.compare(l.mslAltitude, mslAltitude) == 0
                && Float.compare(l.mslAltitudeAccuracy, mslAltitudeAccuracy) == 0
                && Objects.equals(provider, l.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, fields, time, elapsed, latitude, longitude);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append("Location[");
        s.append(provider);
        s.append(String.format(Locale.US, " %.6f,%.6f", latitude, longitude));
        if (hasAccuracy()) {
            s.append(String.format(Locale.US, " hAcc=%.0f", accuracy));
        }
        if (hasAltitude()) {
            s.append(String.format(Locale.US, " alt=%.1f", altitude));
        }
        if (hasMslAltitude()) {
            s.append(String.format(Locale.US, " mslAlt=%.1f", mslAltitude));
        }
        if (hasSpeed()) {
            s.append(String.format(Locale.US, " vel=%.1f", speed));
        }
        if (hasBearing()) {
            s.append(String.format(Locale.US, " bear=%.1f", bearing));
        }
        if (isMock()) {
            s.append(" mock");
        }
        s.append(']');
        return s.toString();
    }

    @Deprecated
    public void dump(Printer pw, String prefix) {
        pw.println(prefix + toString());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(provider);
        parcel.writeInt(fields);
        parcel.writeLong(time);
        parcel.writeLong(elapsed);
        if (hasElapsedRealtimeUncertaintyNanos()) {
            parcel.writeDouble(elapsedUncertainty);
        }
        parcel.writeDouble(latitude);
        parcel.writeDouble(longitude);
        if (hasAltitude()) {
            parcel.writeDouble(altitude);
        }
        if (hasSpeed()) {
            parcel.writeFloat(speed);
        }
        if (hasBearing()) {
            parcel.writeFloat(bearing);
        }
        if (hasAccuracy()) {
            parcel.writeFloat(accuracy);
        }
        if (hasVerticalAccuracy()) {
            parcel.writeFloat(verticalAccuracy);
        }
        if (hasSpeedAccuracy()) {
            parcel.writeFloat(speedAccuracy);
        }
        if (hasBearingAccuracy()) {
            parcel.writeFloat(bearingAccuracy);
        }
        if (hasMslAltitude()) {
            parcel.writeDouble(mslAltitude);
        }
        if (hasMslAltitudeAccuracy()) {
            parcel.writeFloat(mslAltitudeAccuracy);
        }
        parcel.writeBundle(extras);
    }

    public static final Parcelable.Creator<Location> CREATOR = new Parcelable.Creator<Location>() {
        @Override
        public Location createFromParcel(Parcel in) {
            Location l = new Location(in.readString());
            l.fields = in.readInt();
            l.time = in.readLong();
            l.elapsed = in.readLong();
            if (l.hasElapsedRealtimeUncertaintyNanos()) {
                l.elapsedUncertainty = in.readDouble();
            }
            l.latitude = in.readDouble();
            l.longitude = in.readDouble();
            if (l.hasAltitude()) {
                l.altitude = in.readDouble();
            }
            if (l.hasSpeed()) {
                l.speed = in.readFloat();
            }
            if (l.hasBearing()) {
                l.bearing = in.readFloat();
            }
            if (l.hasAccuracy()) {
                l.accuracy = in.readFloat();
            }
            if (l.hasVerticalAccuracy()) {
                l.verticalAccuracy = in.readFloat();
            }
            if (l.hasSpeedAccuracy()) {
                l.speedAccuracy = in.readFloat();
            }
            if (l.hasBearingAccuracy()) {
                l.bearingAccuracy = in.readFloat();
            }
            if (l.hasMslAltitude()) {
                l.mslAltitude = in.readDouble();
            }
            if (l.hasMslAltitudeAccuracy()) {
                l.mslAltitudeAccuracy = in.readFloat();
            }
            l.extras = in.readBundle();
            return l;
        }

        @Override
        public Location[] newArray(int size) {
            return new Location[size];
        }
    };

    public static String convert(double coordinate, int outputType) {
        if (coordinate < -180.0 || coordinate > 180.0 || Double.isNaN(coordinate)) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        if (outputType != FORMAT_DEGREES
                && outputType != FORMAT_MINUTES
                && outputType != FORMAT_SECONDS) {
            throw new IllegalArgumentException("outputType=" + outputType);
        }

        StringBuilder sb = new StringBuilder();
        if (coordinate < 0.0) {
            sb.append('-');
            coordinate = -coordinate;
        }

        DecimalFormat df = new DecimalFormat("###.#####",
                DecimalFormatSymbols.getInstance(Locale.US));
        if (outputType == FORMAT_MINUTES || outputType == FORMAT_SECONDS) {
            int degrees = (int) Math.floor(coordinate);
            sb.append(degrees);
            sb.append(':');
            coordinate -= degrees;
            coordinate *= 60.0;
            if (outputType == FORMAT_SECONDS) {
                int minutes = (int) Math.floor(coordinate);
                sb.append(minutes);
                sb.append(':');
                coordinate -= minutes;
                coordinate *= 60.0;
            }
        }
        sb.append(df.format(coordinate));
        return sb.toString();
    }

    public static double convert(String coordinate) {
        if (coordinate == null) {
            throw new NullPointerException("coordinate");
        }

        boolean negative = false;
        if (!coordinate.isEmpty() && coordinate.charAt(0) == '-') {
            coordinate = coordinate.substring(1);
            negative = true;
        }

        StringTokenizer st = new StringTokenizer(coordinate, ":");
        int tokens = st.countTokens();
        if (tokens < 1) {
            throw new IllegalArgumentException("coordinate=" + coordinate);
        }
        try {
            String degrees = st.nextToken();
            double val;
            if (tokens == 1) {
                val = Double.parseDouble(degrees);
                if (val < 0.0 || val > 180.0 || Double.isNaN(val)) {
                    throw new IllegalArgumentException("coordinate=" + coordinate);
                }
                return negative ? -val : val;
            }

            String minutes = st.nextToken();
            int deg = Integer.parseInt(degrees);
            double min;
            double sec = 0.0;
            boolean secPresent = false;

            if (st.hasMoreTokens()) {
                min = Integer.parseInt(minutes);
                String seconds = st.nextToken();
                if (st.hasMoreTokens()) {
                    throw new IllegalArgumentException("coordinate=" + coordinate);
                }
                sec = Double.parseDouble(seconds);
                secPresent = true;
            } else {
                min = Double.parseDouble(minutes);
            }

            boolean isNegative180 = negative && deg == 180 && min == 0.0 && sec == 0.0;
            if (deg < 0.0 || (deg > 179 && !isNegative180 && !(deg == 180 && min == 0.0 && sec == 0.0))) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }
            if (min < 0.0 || min >= 60.0 || (secPresent && min > 59.0) || Double.isNaN(min)) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }
            if (sec < 0.0 || sec >= 60.0 || Double.isNaN(sec)) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }

            val = deg * 3600.0 + min * 60.0 + sec;
            val /= 3600.0;
            if (val > 180.0) {
                throw new IllegalArgumentException("coordinate=" + coordinate);
            }
            return negative ? -val : val;
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("coordinate=" + coordinate, nfe);
        }
    }

    public static void distanceBetween(
            double startLatitude,
            double startLongitude,
            double endLatitude,
            double endLongitude,
            float[] results) {
        if (results == null || results.length < 1) {
            throw new IllegalArgumentException("results is null or has length < 1");
        }
        computeDistanceAndBearing(
                startLatitude, startLongitude, endLatitude, endLongitude, results);
    }

    private static void computeDistanceAndBearing(
            double lat1, double lon1, double lat2, double lon2, float[] results) {
        // WGS-84 Vincenty inverse formula
        final int maxIters = 20;
        lat1 *= Math.PI / 180.0;
        lat2 *= Math.PI / 180.0;
        lon1 *= Math.PI / 180.0;
        lon2 *= Math.PI / 180.0;

        final double a = 6378137.0;
        final double b = 6356752.3142;
        final double f = (a - b) / a;
        final double aSqMinusBSqOverBSq = (a * a - b * b) / (b * b);

        final double l = lon2 - lon1;
        double capA = 0.0;
        final double u1 = Math.atan((1.0 - f) * Math.tan(lat1));
        final double u2 = Math.atan((1.0 - f) * Math.tan(lat2));

        final double cosU1 = Math.cos(u1);
        final double cosU2 = Math.cos(u2);
        final double sinU1 = Math.sin(u1);
        final double sinU2 = Math.sin(u2);
        final double cosU1cosU2 = cosU1 * cosU2;
        final double sinU1sinU2 = sinU1 * sinU2;

        double sigma = 0.0;
        double deltaSigma = 0.0;
        double cosSqAlpha;
        double cos2SM;
        double cosSigma;
        double sinSigma;
        double cosLambda = 0.0;
        double sinLambda = 0.0;

        double lambda = l;
        for (int iter = 0; iter < maxIters; iter++) {
            double lambdaOrig = lambda;
            cosLambda = Math.cos(lambda);
            sinLambda = Math.sin(lambda);
            double t1 = cosU2 * sinLambda;
            double t2 = cosU1 * sinU2 - sinU1 * cosU2 * cosLambda;
            double sinSqSigma = t1 * t1 + t2 * t2;
            sinSigma = Math.sqrt(sinSqSigma);
            cosSigma = sinU1sinU2 + cosU1cosU2 * cosLambda;
            sigma = Math.atan2(sinSigma, cosSigma);
            double sinAlpha = (sinSigma == 0.0) ? 0.0 : cosU1cosU2 * sinLambda / sinSigma;
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha;
            cos2SM = (cosSqAlpha == 0.0) ? 0.0 : cosSigma - 2.0 * sinU1sinU2 / cosSqAlpha;

            double uSquared = cosSqAlpha * aSqMinusBSqOverBSq;
            capA = 1.0 + (uSquared / 16384.0)
                    * (4096.0 + uSquared * (-768.0 + uSquared * (320.0 - 175.0 * uSquared)));
            double capB = (uSquared / 1024.0)
                    * (256.0 + uSquared * (-128.0 + uSquared * (74.0 - 47.0 * uSquared)));
            double capC = (f / 16.0) * cosSqAlpha * (4.0 + f * (4.0 - 3.0 * cosSqAlpha));
            double cos2SMSq = cos2SM * cos2SM;
            deltaSigma = capB * sinSigma * (cos2SM + (capB / 4.0)
                    * (cosSigma * (-1.0 + 2.0 * cos2SMSq) - (capB / 6.0) * cos2SM
                    * (-3.0 + 4.0 * sinSigma * sinSigma) * (-3.0 + 4.0 * cos2SMSq)));

            lambda = l + (1.0 - capC) * f * sinAlpha * (sigma + capC * sinSigma
                    * (cos2SM + capC * cosSigma * (-1.0 + 2.0 * cos2SM * cos2SM)));

            double delta = (lambda - lambdaOrig) / lambda;
            if (Math.abs(delta) < 1.0e-12) {
                break;
            }
        }

        results[0] = (float) (b * capA * (sigma - deltaSigma));
        if (results.length > 1) {
            float initialBearing = (float) Math.atan2(cosU2 * sinLambda,
                    cosU1 * sinU2 - sinU1 * cosU2 * cosLambda);
            initialBearing = (float) (initialBearing * (180.0 / Math.PI));
            results[1] = initialBearing;
            if (results.length > 2) {
                float finalBearing = (float) Math.atan2(cosU1 * sinLambda,
                        -sinU1 * cosU2 + cosU1 * sinU2 * cosLambda);
                finalBearing = (float) (finalBearing * (180.0 / Math.PI));
                results[2] = finalBearing;
            }
        }
    }
}
