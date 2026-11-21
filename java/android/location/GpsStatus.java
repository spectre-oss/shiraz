// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import java.util.ArrayList;
import java.util.Collections;

@Deprecated
public final class GpsStatus {
    public static final int GPS_EVENT_STARTED = 1;
    public static final int GPS_EVENT_STOPPED = 2;
    public static final int GPS_EVENT_FIRST_FIX = 3;
    public static final int GPS_EVENT_SATELLITE_STATUS = 4;

    private static final int NUM_SATELLITES = 255;

    @Deprecated
    public interface Listener {
        void onGpsStatusChanged(int event);
    }

    @Deprecated
    public interface NmeaListener {
        void onNmeaReceived(long timestamp, String nmea);
    }

    private int mTimeToFirstFix;
    private final ArrayList<GpsSatellite> mSatellites = new ArrayList<GpsSatellite>();

    GpsStatus() {
    }

    public static GpsStatus create(GnssStatus gnssStatus, int timeToFirstFix) {
        GpsStatus status = new GpsStatus();
        status.setStatus(gnssStatus, timeToFirstFix);
        return status;
    }

    static GpsStatus createEmpty() {
        return new GpsStatus();
    }

    void setStatus(GnssStatus status, int timeToFirstFix) {
        mTimeToFirstFix = timeToFirstFix;
        mSatellites.clear();
        if (status == null) {
            return;
        }
        int count = status.getSatelliteCount();
        for (int i = 0; i < count; i++) {
            int constellation = status.getConstellationType(i);
            int prn = status.getSvid(i);
            if (constellation == GnssStatus.CONSTELLATION_GLONASS) {
                prn += 64;
            } else if (constellation == GnssStatus.CONSTELLATION_BEIDOU) {
                prn += 200;
            } else if (constellation != GnssStatus.CONSTELLATION_GPS
                    && constellation != GnssStatus.CONSTELLATION_SBAS
                    && constellation != GnssStatus.CONSTELLATION_QZSS) {
                continue;
            }
            if (prn > 0 && prn <= NUM_SATELLITES) {
                GpsSatellite satellite = new GpsSatellite(prn);
                satellite.mSnr = status.getCn0DbHz(i);
                satellite.mElevation = status.getElevationDegrees(i);
                satellite.mAzimuth = status.getAzimuthDegrees(i);
                satellite.mHasEphemeris = status.hasEphemerisData(i);
                satellite.mHasAlmanac = status.hasAlmanacData(i);
                satellite.mUsedInFix = status.usedInFix(i);
                mSatellites.add(satellite);
            }
        }
    }

    public int getTimeToFirstFix() {
        return mTimeToFirstFix;
    }

    public Iterable<GpsSatellite> getSatellites() {
        return Collections.unmodifiableList(mSatellites);
    }

    public int getMaxSatellites() {
        return NUM_SATELLITES;
    }
}
