// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.content.Context;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class Geocoder {
    private static final String ERROR_BACKEND_UNAVAILABLE = "Geocoder backend unavailable";

    public interface GeocodeListener {
        void onGeocode(List<Address> addresses);

        default void onError(String errorMessage) {
        }
    }

    private final Context mContext;
    private final Locale mLocale;

    public Geocoder(Context context) {
        this(context, Locale.getDefault());
    }

    public Geocoder(Context context, Locale locale) {
        mContext = Objects.requireNonNull(context, "context must not be null");
        mLocale = Objects.requireNonNull(locale, "locale must not be null");
    }

    public static boolean isPresent() {
        return false;
    }

    @Deprecated
    public List<Address> getFromLocation(double latitude, double longitude, int maxResults)
            throws IOException {
        validateCoordinates(latitude, longitude);
        throw new IOException(ERROR_BACKEND_UNAVAILABLE);
    }

    public void getFromLocation(
            double latitude,
            double longitude,
            int maxResults,
            GeocodeListener listener) {
        validateCoordinates(latitude, longitude);
        Objects.requireNonNull(listener, "listener must not be null");
        listener.onError(ERROR_BACKEND_UNAVAILABLE);
    }

    @Deprecated
    public List<Address> getFromLocationName(String locationName, int maxResults)
            throws IOException {
        if (locationName == null) {
            throw new IllegalArgumentException("locationName == null");
        }
        throw new IOException(ERROR_BACKEND_UNAVAILABLE);
    }

    public void getFromLocationName(
            String locationName,
            int maxResults,
            GeocodeListener listener) {
        if (locationName == null) {
            throw new IllegalArgumentException("locationName == null");
        }
        Objects.requireNonNull(listener, "listener must not be null");
        listener.onError(ERROR_BACKEND_UNAVAILABLE);
    }

    @Deprecated
    public List<Address> getFromLocationName(
            String locationName,
            int maxResults,
            double lowerLeftLatitude,
            double lowerLeftLongitude,
            double upperRightLatitude,
            double upperRightLongitude)
            throws IOException {
        if (locationName == null) {
            throw new IllegalArgumentException("locationName == null");
        }
        validateBoundingBox(
                lowerLeftLatitude, lowerLeftLongitude,
                upperRightLatitude, upperRightLongitude);
        throw new IOException(ERROR_BACKEND_UNAVAILABLE);
    }

    public void getFromLocationName(
            String locationName,
            int maxResults,
            double lowerLeftLatitude,
            double lowerLeftLongitude,
            double upperRightLatitude,
            double upperRightLongitude,
            GeocodeListener listener) {
        if (locationName == null) {
            throw new IllegalArgumentException("locationName == null");
        }
        validateBoundingBox(
                lowerLeftLatitude, lowerLeftLongitude,
                upperRightLatitude, upperRightLongitude);
        Objects.requireNonNull(listener, "listener must not be null");
        listener.onError(ERROR_BACKEND_UNAVAILABLE);
    }

    private static void validateCoordinates(double latitude, double longitude) {
        if (latitude < -90.0 || latitude > 90.0 || Double.isNaN(latitude)) {
            throw new IllegalArgumentException("latitude == " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0 || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("longitude == " + longitude);
        }
    }

    private static void validateBoundingBox(
            double lowerLeftLatitude,
            double lowerLeftLongitude,
            double upperRightLatitude,
            double upperRightLongitude) {
        if (lowerLeftLatitude < -90.0 || lowerLeftLatitude > 90.0
                || Double.isNaN(lowerLeftLatitude)) {
            throw new IllegalArgumentException("lowerLeftLatitude == " + lowerLeftLatitude);
        }
        if (lowerLeftLongitude < -180.0 || lowerLeftLongitude > 180.0
                || Double.isNaN(lowerLeftLongitude)) {
            throw new IllegalArgumentException("lowerLeftLongitude == " + lowerLeftLongitude);
        }
        if (upperRightLatitude < -90.0 || upperRightLatitude > 90.0
                || Double.isNaN(upperRightLatitude)) {
            throw new IllegalArgumentException("upperRightLatitude == " + upperRightLatitude);
        }
        if (upperRightLongitude < -180.0 || upperRightLongitude > 180.0
                || Double.isNaN(upperRightLongitude)) {
            throw new IllegalArgumentException("upperRightLongitude == " + upperRightLongitude);
        }
    }
}
