// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.location.Location;
import android.location.LocationListener;
import android.os.Bundle;
final class LocationFixture {
    static void check() {
        Location location = new Location("fixture");
        if (location.hasAltitude() || location.hasSpeed() || location.hasBearing() || location.hasAccuracy()
                || location.hasVerticalAccuracy() || location.hasSpeedAccuracy() || location.hasBearingAccuracy()
                || location.isFromMockProvider() || location.getExtras() != null || location.getTime() != 0 || location.getElapsedRealtimeNanos() != 0)
            throw new AssertionError("Location defaults must not fabricate a fix");
        location.setLatitude(48.2082); location.setLongitude(16.3738); location.setTime(1234567); location.setElapsedRealtimeNanos(9876543);
        location.setAltitude(180); location.setSpeed(3); location.setBearing(-450); location.setAccuracy(4);
        location.setVerticalAccuracyMeters(5); location.setSpeedAccuracyMetersPerSecond(6); location.setBearingAccuracyDegrees(7);
        Bundle extras = new Bundle(); extras.putString("source", "fixture"); location.setExtras(extras); extras.putString("source", "changed");
        if (!"fixture".equals(location.getExtras().getString("source"))) throw new AssertionError("Location extras setter aliases map");
        Location copy = new Location(location);
        location.getExtras().putString("source", "changed");
        if (!"fixture".equals(copy.getProvider()) || copy.getLatitude() != 48.2082 || copy.getLongitude() != 16.3738 || copy.getTime() != 1234567
                || copy.getElapsedRealtimeNanos() != 9876543 || !copy.hasAltitude() || copy.getAltitude() != 180 || !copy.hasSpeed() || copy.getSpeed() != 3
                || !copy.hasBearing() || copy.getBearing() != 270 || !copy.hasAccuracy() || copy.getAccuracy() != 4
                || !copy.hasVerticalAccuracy() || copy.getVerticalAccuracyMeters() != 5 || !copy.hasSpeedAccuracy() || copy.getSpeedAccuracyMetersPerSecond() != 6
                || !copy.hasBearingAccuracy() || copy.getBearingAccuracyDegrees() != 7 || !"fixture".equals(copy.getExtras().getString("source")))
            throw new AssertionError("Location copy fields/presence bits");
        location.removeAltitude(); location.removeSpeed(); location.removeBearing(); location.removeAccuracy();
        location.removeVerticalAccuracy(); location.removeSpeedAccuracy(); location.removeBearingAccuracy();
        if (location.hasAltitude() || location.getAltitude() != 0 || location.hasSpeed() || location.getSpeed() != 0 || location.hasBearing()
                || location.getBearing() != 0 || location.hasAccuracy() || location.hasVerticalAccuracy() || location.hasSpeedAccuracy() || location.hasBearingAccuracy())
            throw new AssertionError("Location optional field removal");
        LocationListener listener = new LocationListener() {
            public void onLocationChanged(Location value) { if (value.getBearing() != 270) throw new AssertionError("Location callback value"); }
            public void onStatusChanged(String provider, int status, Bundle bundle) { }
            public void onProviderEnabled(String provider) { }
            public void onProviderDisabled(String provider) { }
        };
        listener.onLocationChanged(copy);
        location.set(copy); location.reset();
        if (location.getProvider() != null || location.getLatitude() != 0 || location.getLongitude() != 0 || location.getTime() != 0
                || location.getElapsedRealtimeNanos() != 0 || location.hasAltitude() || location.hasAccuracy() || location.getExtras() != null)
            throw new AssertionError("Location reset");
        location.setBearing(Float.MAX_VALUE); if (!(location.getBearing() >= 0 && location.getBearing() < 360)) throw new AssertionError("Bounded bearing normalization");
        System.out.println("SHIRAZ_FIXTURE_LOCATION_VALUES_OK");
    }
}
