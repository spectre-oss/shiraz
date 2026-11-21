// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.os.Bundle;
import java.util.List;

public interface LocationListener {
    void onLocationChanged(Location location);

    default void onLocationChanged(List<Location> locations) {
        final int size = locations.size();
        for (int i = 0; i < size; i++) {
            onLocationChanged(locations.get(i));
        }
    }

    default void onFlushComplete(int requestCode) {
    }

    @Deprecated
    default void onStatusChanged(String provider, int status, Bundle extras) {
    }

    default void onProviderEnabled(String provider) {
    }

    default void onProviderDisabled(String provider) {
    }
}
