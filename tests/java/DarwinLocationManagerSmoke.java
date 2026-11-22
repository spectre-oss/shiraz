// SPDX-License-Identifier: GPL-3.0-only
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import shiraz.runtime.DarwinLocation;
import shiraz.runtime.DarwinLocationBackend;

public final class DarwinLocationManagerSmoke {
    private static final class StubContext extends ContextWrapper {
        boolean fine;
        boolean coarse;

        StubContext(boolean fine, boolean coarse) {
            super(null);
            this.fine = fine;
            this.coarse = coarse;
        }

        @Override
        public int checkCallingOrSelfPermission(String permission) {
            if ("android.permission.ACCESS_FINE_LOCATION".equals(permission)) {
                return fine ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
            }
            if ("android.permission.ACCESS_COARSE_LOCATION".equals(permission)) {
                return coarse
                        ? PackageManager.PERMISSION_GRANTED
                        : PackageManager.PERMISSION_DENIED;
            }
            return PackageManager.PERMISSION_DENIED;
        }
    }

    private static void require(boolean cond, String message) {
        if (!cond) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        final AtomicInteger starts = new AtomicInteger();
        final AtomicInteger stops = new AtomicInteger();
        final AtomicReference<Boolean> lastFine = new AtomicReference<Boolean>();
        final AtomicReference<Double> lastDistance = new AtomicReference<Double>();

        DarwinLocation.setBackendForTest(new DarwinLocationBackend() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public int authorizationStatus() {
                return DarwinLocation.STATUS_AUTHORIZED;
            }

            @Override
            public boolean locationServicesEnabled() {
                return true;
            }

            @Override
            public long start(boolean fineAccuracy, double minDistanceMeters) {
                lastFine.set(Boolean.valueOf(fineAccuracy));
                lastDistance.set(Double.valueOf(minDistanceMeters));
                return starts.incrementAndGet();
            }

            @Override
            public void stop(long handle) {
                if (handle > 0L) {
                    stops.incrementAndGet();
                }
            }

            @Override
            public double[] pollFix(long handle) {
                return new double[] {37.3349, -122.0090, 24.5, 4.2, 1.5, 90.0, 1730000000000.0};
            }

            @Override
            public String lastError(long handle) {
                return "";
            }
        });

        try {
            StubContext coarseOnly = new StubContext(false, true);
            LocationManager coarseManager = LocationManager.shirazForProcess(coarseOnly);
            List<String> providers = coarseManager.getAllProviders();
            require(providers.contains(LocationManager.GPS_PROVIDER), "missing gps");
            require(providers.contains(LocationManager.NETWORK_PROVIDER), "missing network");
            require(providers.contains(LocationManager.FUSED_PROVIDER), "missing fused");
            require(coarseManager.isLocationEnabled(), "location should be enabled");
            require(
                    coarseManager.getProviderProperties(LocationManager.NETWORK_PROVIDER)
                                    .getAccuracy()
                            == ProviderProperties.ACCURACY_COARSE,
                    "network provider should be coarse");

            boolean deniedGps = false;
            try {
                coarseManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            } catch (SecurityException expected) {
                deniedGps = true;
            }
            require(deniedGps, "coarse-only context must not access gps provider");

            Location coarseFix = coarseManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            require(coarseFix != null, "expected coarse fix");
            require(!coarseFix.isMock(), "live darwin fix must not be mock");
            require(Boolean.FALSE.equals(lastFine.get()), "network must request coarse accuracy");
            require(Math.abs(coarseFix.getLatitude() - 37.3349) < 1e-6, "latitude mismatch");
            coarseManager.shirazClose();
            require(stops.get() == 1, "shirazClose must stop CoreLocation session");

            StubContext fineCtx = new StubContext(true, true);
            LocationManager fineManager = LocationManager.shirazForProcess(fineCtx);
            final AtomicReference<Location> delivered = new AtomicReference<Location>();
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    delivered.set(location);
                }
            };
            Executor direct = new Executor() {
                @Override
                public void execute(Runnable command) {
                    command.run();
                }
            };
            fineManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 5.0f, direct, listener);
            require(Boolean.TRUE.equals(lastFine.get()), "gps must request fine accuracy");
            require(Math.abs(lastDistance.get().doubleValue() - 5.0) < 1e-6, "distance mismatch");
            require(delivered.get() != null, "listener must receive CoreLocation fix");
            require(Math.abs(delivered.get().getLongitude() - (-122.0090)) < 1e-6, "lon mismatch");
            fineManager.removeUpdates(listener);
            require(stops.get() == 2, "removeUpdates must stop idle CoreLocation session");
            fineManager.shirazClose();
        } finally {
            DarwinLocation.setBackendForTest(null);
        }

        System.out.println("DARWIN_LOCATION_MANAGER_SMOKE_PASS");
    }
}
