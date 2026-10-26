// SPDX-License-Identifier: GPL-3.0-only
package android.location;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

/** Location value carrier. Creating a value does not obtain a host/GPS position. */
public class Location implements Parcelable {
    public static final int FORMAT_DEGREES = 0, FORMAT_MINUTES = 1, FORMAT_SECONDS = 2;
    private static final int ALTITUDE = 1, SPEED = 2, BEARING = 4, ACCURACY = 8, MOCK = 16,
        VERTICAL = 32, SPEED_ACCURACY = 64, BEARING_ACCURACY = 128;
    private String provider;
    private long time, elapsed;
    private double latitude, longitude, altitude;
    private float speed, bearing, accuracy, verticalAccuracy, speedAccuracy, bearingAccuracy;
    private int fields;
    private Bundle extras;
    public Location(String provider) { this.provider = provider; }
    public Location(Location source) { set(source); }
    public void set(Location source) {
        if (source == null) throw new NullPointerException("location");
        provider = source.provider; time = source.time; elapsed = source.elapsed;
        latitude = source.latitude; longitude = source.longitude; altitude = source.altitude;
        speed = source.speed; bearing = source.bearing; accuracy = source.accuracy;
        verticalAccuracy = source.verticalAccuracy; speedAccuracy = source.speedAccuracy; bearingAccuracy = source.bearingAccuracy;
        fields = source.fields; extras = source.extras == null ? null : new Bundle(source.extras);
    }
    public void reset() { set(new Location((String)null)); }
    public String getProvider() { return provider; }
    public void setProvider(String value) { provider = value; }
    public long getTime() { return time; }
    public void setTime(long value) { time = value; }
    public long getElapsedRealtimeNanos() { return elapsed; }
    public void setElapsedRealtimeNanos(long value) { elapsed = value; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double value) { latitude = value; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double value) { longitude = value; }
    public boolean hasAltitude() { return (fields & ALTITUDE) != 0; }
    public double getAltitude() { return altitude; }
    public void setAltitude(double value) { altitude = value; fields |= ALTITUDE; }
    public void removeAltitude() { altitude = 0; fields &= ~ALTITUDE; }
    public boolean hasSpeed() { return (fields & SPEED) != 0; }
    public float getSpeed() { return speed; }
    public void setSpeed(float value) { speed = value; fields |= SPEED; }
    public void removeSpeed() { speed = 0; fields &= ~SPEED; }
    public boolean hasBearing() { return (fields & BEARING) != 0; }
    public float getBearing() { return bearing; }
    public void setBearing(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Bearing must be finite");
        bearing = value % 360; if (bearing < 0) bearing += 360; if (bearing == 0) bearing = 0; fields |= BEARING;
    }
    public void removeBearing() { bearing = 0; fields &= ~BEARING; }
    public boolean hasAccuracy() { return (fields & ACCURACY) != 0; }
    public float getAccuracy() { return accuracy; }
    public void setAccuracy(float value) { accuracy = value; fields |= ACCURACY; }
    public void removeAccuracy() { accuracy = 0; fields &= ~ACCURACY; }
    public boolean hasVerticalAccuracy() { return (fields & VERTICAL) != 0; }
    public float getVerticalAccuracyMeters() { return verticalAccuracy; }
    public void setVerticalAccuracyMeters(float value) { verticalAccuracy = value; fields |= VERTICAL; }
    public void removeVerticalAccuracy() { verticalAccuracy = 0; fields &= ~VERTICAL; }
    public boolean hasSpeedAccuracy() { return (fields & SPEED_ACCURACY) != 0; }
    public float getSpeedAccuracyMetersPerSecond() { return speedAccuracy; }
    public void setSpeedAccuracyMetersPerSecond(float value) { speedAccuracy = value; fields |= SPEED_ACCURACY; }
    public void removeSpeedAccuracy() { speedAccuracy = 0; fields &= ~SPEED_ACCURACY; }
    public boolean hasBearingAccuracy() { return (fields & BEARING_ACCURACY) != 0; }
    public float getBearingAccuracyDegrees() { return bearingAccuracy; }
    public void setBearingAccuracyDegrees(float value) { bearingAccuracy = value; fields |= BEARING_ACCURACY; }
    public void removeBearingAccuracy() { bearingAccuracy = 0; fields &= ~BEARING_ACCURACY; }
    public boolean isFromMockProvider() { return (fields & MOCK) != 0; }
    public boolean isMock() { return isFromMockProvider(); }
    public void setMock(boolean value) { if (value) fields |= MOCK; else fields &= ~MOCK; }
    public Bundle getExtras() { return extras; }
    public void setExtras(Bundle value) { extras = value == null ? null : new Bundle(value); }
    public int describeContents() { return 0; }
    public void writeToParcel(Parcel destination, int flags) { throw new UnsupportedOperationException("Location parcel transport is not implemented"); }
    public static final Parcelable.Creator<Location> CREATOR = new Parcelable.Creator<Location>() {
        public Location createFromParcel(Parcel source) { throw new UnsupportedOperationException("Location parcel decoding is not implemented"); }
        public Location[] newArray(int size) { return new Location[size]; }
    };
}
