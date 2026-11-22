// SPDX-License-Identifier: GPL-3.0-only
package android.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.provider.ProviderProperties;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import shiraz.runtime.DarwinLocation;
import shiraz.runtime.TestLocationConfig;

/** Optional mock GPS source, test provider registry, and macOS CoreLocation bridge. */
public class LocationManager {
    public static final String NETWORK_PROVIDER = "network";
    public static final String GPS_PROVIDER = "gps";
    public static final String PASSIVE_PROVIDER = "passive";
    public static final String FUSED_PROVIDER = "fused";

    public static final String KEY_PROXIMITY_ENTERING = "entering";
    @Deprecated
    public static final String KEY_STATUS_CHANGED = "status";
    public static final String KEY_PROVIDER_ENABLED = "providerEnabled";
    public static final String KEY_LOCATION_CHANGED = "location";
    public static final String KEY_LOCATIONS = "locations";
    public static final String KEY_FLUSH_COMPLETE = "flushComplete";

    public static final String PROVIDERS_CHANGED_ACTION = "android.location.PROVIDERS_CHANGED";
    public static final String EXTRA_PROVIDER_NAME = "android.location.extra.PROVIDER_NAME";
    public static final String EXTRA_PROVIDER_ENABLED = "android.location.extra.PROVIDER_ENABLED";
    public static final String MODE_CHANGED_ACTION = "android.location.MODE_CHANGED";
    public static final String EXTRA_LOCATION_ENABLED = "android.location.extra.LOCATION_ENABLED";
    public static final String ACTION_GNSS_CAPABILITIES_CHANGED =
            "android.location.action.GNSS_CAPABILITIES_CHANGED";
    public static final String EXTRA_GNSS_CAPABILITIES =
            "android.location.extra.GNSS_CAPABILITIES";

    private static final ProviderProperties DEFAULT_GPS_PROPERTIES =
            new ProviderProperties.Builder()
                    .setHasSatelliteRequirement(true)
                    .setHasAltitudeSupport(true)
                    .setHasSpeedSupport(true)
                    .setHasBearingSupport(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_HIGH)
                    .setAccuracy(ProviderProperties.ACCURACY_FINE)
                    .build();

    private static final ProviderProperties DEFAULT_COARSE_PROPERTIES =
            new ProviderProperties.Builder()
                    .setHasNetworkRequirement(true)
                    .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                    .setAccuracy(ProviderProperties.ACCURACY_COARSE)
                    .build();

    private static final boolean TRACE = "1".equals(System.getenv("SHIRAZ_TRACE_LOCATION"));

    private final Context context;
    private final boolean darwinLive;
    private long darwinHandle;
    private boolean darwinFineActive;
    private TestLocationConfig fix;
    private Location last;
    private boolean foreground = true;
    private boolean foregroundService;
    private boolean closed;

    private final IdentityHashMap<LocationListener, Subscription> subscriptions =
            new IdentityHashMap<LocationListener, Subscription>();
    private final Map<PendingIntent, PendingSubscription> pendingSubscriptions =
            new HashMap<PendingIntent, PendingSubscription>();
    private final LinkedHashMap<String, TestProviderState> testProviders =
            new LinkedHashMap<String, TestProviderState>();
    private final Map<PendingIntent, ProximityAlert> proximityAlerts =
            new HashMap<PendingIntent, ProximityAlert>();

    private static final class TestProviderState {
        final String name;
        ProviderProperties properties;
        boolean enabled;
        Location location;
        int status = LocationProvider.AVAILABLE;
        Bundle statusExtras;
        long statusUpdateTime;

        TestProviderState(String name, ProviderProperties properties) {
            this.name = name;
            this.properties = properties;
        }
    }

    private static final class ProximityAlert {
        final double latitude;
        final double longitude;
        final float radius;
        final long expirationRealtimeMs;
        final PendingIntent intent;
        Boolean inside;

        ProximityAlert(
                double latitude,
                double longitude,
                float radius,
                long expirationRealtimeMs,
                PendingIntent intent) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.radius = radius;
            this.expirationRealtimeMs = expirationRealtimeMs;
            this.intent = intent;
        }
    }

    private LocationManager(Context context, TestLocationConfig fix, boolean allowDarwinLive) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        this.context = context;
        this.fix = fix;
        this.darwinLive =
                allowDarwinLive
                        && fix == null
                        && DarwinLocation.isAvailable()
                        && DarwinLocation.locationServicesEnabled();
        if (fix != null) {
            last = sample();
        }
    }

    public static LocationManager shirazForProcess(Context context) {
        return new LocationManager(context, TestLocationConfig.fromEnvironment(), true);
    }

    public static LocationManager shirazForTest(Context context, TestLocationConfig fix) {
        return new LocationManager(context, fix, false);
    }

    private static boolean isDarwinProvider(String provider) {
        return GPS_PROVIDER.equals(provider)
                || NETWORK_PROVIDER.equals(provider)
                || FUSED_PROVIDER.equals(provider);
    }

    private boolean hasDarwinProvider(String provider) {
        return darwinLive && isDarwinProvider(provider);
    }

    private void ensureDarwinSession(String provider, float minDistance) {
        if (!hasDarwinProvider(provider)) {
            return;
        }
        boolean wantFine = !NETWORK_PROVIDER.equals(provider);
        if (darwinHandle != 0L && (!wantFine || darwinFineActive)) {
            return;
        }
        if (darwinHandle != 0L) {
            DarwinLocation.stop(darwinHandle);
            darwinHandle = 0L;
        }
        long handle = DarwinLocation.start(wantFine, Math.max(0.0f, minDistance));
        if (handle != 0L) {
            darwinHandle = handle;
            darwinFineActive = wantFine;
        }
    }

    private void stopDarwinIfIdle() {
        if (darwinHandle != 0L && subscriptions.isEmpty() && pendingSubscriptions.isEmpty()) {
            DarwinLocation.stop(darwinHandle);
            darwinHandle = 0L;
            darwinFineActive = false;
        }
    }

    private static void provider(String value) {
        if (value == null) {
            throw new IllegalArgumentException("provider is null");
        }
    }

    public synchronized List<String> getAllProviders() {
        ArrayList<String> providers = new ArrayList<String>();
        if (closed) {
            return providers;
        }
        if ((fix != null || darwinLive) && !testProviders.containsKey(GPS_PROVIDER)) {
            providers.add(GPS_PROVIDER);
        }
        if (darwinLive) {
            if (!testProviders.containsKey(NETWORK_PROVIDER)) {
                providers.add(NETWORK_PROVIDER);
            }
            if (!testProviders.containsKey(FUSED_PROVIDER)) {
                providers.add(FUSED_PROVIDER);
            }
        }
        providers.addAll(testProviders.keySet());
        return providers;
    }

    public synchronized List<String> getProviders(boolean enabledOnly) {
        if (!enabledOnly) {
            return getAllProviders();
        }
        ArrayList<String> result = new ArrayList<String>();
        if (closed) {
            return result;
        }
        if ((fix != null || darwinLive) && !testProviders.containsKey(GPS_PROVIDER)) {
            result.add(GPS_PROVIDER);
        }
        if (darwinLive) {
            if (!testProviders.containsKey(NETWORK_PROVIDER)) {
                result.add(NETWORK_PROVIDER);
            }
            if (!testProviders.containsKey(FUSED_PROVIDER)) {
                result.add(FUSED_PROVIDER);
            }
        }
        for (TestProviderState tp : testProviders.values()) {
            if (tp.enabled) {
                result.add(tp.name);
            }
        }
        return result;
    }

    public synchronized List<String> getProviders(Criteria criteria, boolean enabledOnly) {
        if (criteria == null) {
            throw new IllegalArgumentException("criteria is null");
        }
        List<String> candidates = getProviders(enabledOnly);
        ArrayList<String> matching = new ArrayList<String>();
        for (int i = 0; i < candidates.size(); i++) {
            String name = candidates.get(i);
            LocationProvider lp = getProvider(name);
            if (lp != null && lp.meetsCriteria(criteria)) {
                matching.add(name);
            }
        }
        return matching;
    }

    public synchronized String getBestProvider(Criteria criteria, boolean enabledOnly) {
        if (criteria == null) {
            throw new IllegalArgumentException("criteria is null");
        }
        List<String> providers = getProviders(criteria, enabledOnly);
        if (!providers.isEmpty()) {
            return providers.get(0);
        }
        Criteria copy = new Criteria(criteria);
        int power = copy.getPowerRequirement();
        while (providers.isEmpty() && power != Criteria.NO_REQUIREMENT) {
            power = (power == Criteria.POWER_LOW)
                    ? Criteria.POWER_MEDIUM
                    : (power == Criteria.POWER_MEDIUM ? Criteria.POWER_HIGH : Criteria.NO_REQUIREMENT);
            copy.setPowerRequirement(power);
            providers = getProviders(copy, enabledOnly);
        }
        int accuracy = copy.getAccuracy();
        while (providers.isEmpty() && accuracy != Criteria.NO_REQUIREMENT) {
            accuracy = (accuracy == Criteria.ACCURACY_FINE)
                    ? Criteria.ACCURACY_COARSE
                    : Criteria.NO_REQUIREMENT;
            copy.setAccuracy(accuracy);
            providers = getProviders(copy, enabledOnly);
        }
        return providers.isEmpty() ? null : providers.get(0);
    }

    public synchronized boolean hasProvider(String provider) {
        provider(provider);
        if (closed) {
            return false;
        }
        return testProviders.containsKey(provider)
                || (fix != null && GPS_PROVIDER.equals(provider))
                || hasDarwinProvider(provider);
    }

    @Deprecated
    public synchronized LocationProvider getProvider(String name) {
        provider(name);
        if (!hasProvider(name)) {
            return null;
        }
        return new LocationProvider(name, getProviderProperties(name));
    }

    public synchronized ProviderProperties getProviderProperties(String provider) {
        provider(provider);
        if (closed) {
            throw new IllegalStateException("Location service is closed");
        }
        TestProviderState tp = testProviders.get(provider);
        if (tp != null) {
            return tp.properties;
        }
        if (fix != null && GPS_PROVIDER.equals(provider)) {
            return DEFAULT_GPS_PROPERTIES;
        }
        if (hasDarwinProvider(provider)) {
            return NETWORK_PROVIDER.equals(provider)
                    ? DEFAULT_COARSE_PROPERTIES
                    : DEFAULT_GPS_PROPERTIES;
        }
        throw new IllegalArgumentException("Provider \"" + provider + "\" does not exist");
    }

    public synchronized boolean isProviderEnabled(String provider) {
        provider(provider);
        if (closed) {
            return false;
        }
        TestProviderState tp = testProviders.get(provider);
        if (tp != null) {
            return tp.enabled;
        }
        return (GPS_PROVIDER.equals(provider) && fix != null) || hasDarwinProvider(provider);
    }

    public synchronized boolean isLocationEnabled() {
        if (closed) {
            return false;
        }
        if (fix != null || darwinLive) {
            return true;
        }
        for (TestProviderState tp : testProviders.values()) {
            if (tp.enabled) {
                return true;
            }
        }
        return false;
    }

    private boolean permitted() {
        return context.checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean permittedForProvider(String providerName) {
        if (permitted()) {
            return true;
        }
        TestProviderState tp = testProviders.get(providerName);
        if (tp != null && tp.properties.getAccuracy() == ProviderProperties.ACCURACY_COARSE) {
            return context.checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                    == PackageManager.PERMISSION_GRANTED;
        }
        if (hasDarwinProvider(providerName)
                && (NETWORK_PROVIDER.equals(providerName) || FUSED_PROVIDER.equals(providerName))) {
            return context.checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                    == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }

    private void requireBackend(String provider) {
        if (!permittedForProvider(provider)) {
            throw new SecurityException("Location permission required for provider: " + provider);
        }
        if (closed) {
            throw new IllegalStateException("Location service is closed");
        }
        if (testProviders.containsKey(provider) || hasDarwinProvider(provider)) {
            return;
        }
        if (fix == null) {
            throw new UnsupportedOperationException("No configured location backend");
        }
        if (!GPS_PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("Unknown location provider: " + provider);
        }
    }

    private String resolveCriteriaProvider(Criteria criteria) {
        if (criteria == null) {
            throw new IllegalArgumentException("criteria is null");
        }
        if (!permitted()
                && context.checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                        != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Location permission required");
        }
        if (closed) {
            throw new IllegalStateException("Location service is closed");
        }
        if (fix == null && !darwinLive && testProviders.isEmpty()) {
            throw new UnsupportedOperationException("No configured location backend");
        }
        String best = getBestProvider(criteria, false);
        if (best == null) {
            throw new IllegalArgumentException("No provider matches criteria: " + criteria);
        }
        return best;
    }

    private static long safeElapsedRealtimeNanos() {
        try {
            return SystemClock.elapsedRealtimeNanos();
        } catch (Throwable ignored) {
            return System.nanoTime();
        }
    }

    private static long safeElapsedRealtimeMillis() {
        try {
            return SystemClock.elapsedRealtime();
        } catch (Throwable ignored) {
            return System.nanoTime() / 1_000_000L;
        }
    }

    private Location sample() {
        Location l = new Location(GPS_PROVIDER);
        l.setLatitude(fix.latitude);
        l.setLongitude(fix.longitude);
        l.setAccuracy(fix.accuracy);
        l.setMock(true);
        l.setTime(System.currentTimeMillis());
        l.setElapsedRealtimeNanos(safeElapsedRealtimeNanos());
        return l;
    }

    private Location sampleForProvider(String providerName) {
        TestProviderState tp = testProviders.get(providerName);
        if (tp != null) {
            if (!tp.enabled || tp.location == null) {
                return null;
            }
            return new Location(tp.location);
        }
        if (GPS_PROVIDER.equals(providerName) && fix != null) {
            last = sample();
            return new Location(last);
        }
        if (hasDarwinProvider(providerName)) {
            ensureDarwinSession(providerName, 0.0f);
            Location polled = DarwinLocation.toLocation(providerName, DarwinLocation.pollFix(darwinHandle));
            if (polled != null) {
                last = polled;
                return new Location(polled);
            }
            return last != null ? new Location(last) : null;
        }
        return null;
    }

    private static void trace(String action, Location l) {
        if (TRACE) {
            System.out.println("SHIRAZ_TEST_LOCATION " + action
                    + " latitude=" + l.getLatitude()
                    + " longitude=" + l.getLongitude()
                    + " mock=true elapsed_ns=" + l.getElapsedRealtimeNanos());
        }
    }

    public synchronized Location getLastKnownLocation(String provider) {
        provider(provider);
        requireBackend(provider);
        TestProviderState tp = testProviders.get(provider);
        if (tp != null) {
            if (!tp.enabled || tp.location == null) {
                return null;
            }
            return new Location(tp.location);
        }
        if (hasDarwinProvider(provider)) {
            return sampleForProvider(provider);
        }
        Location result = new Location(last);
        trace("last-known", result);
        return result;
    }

    public void getCurrentLocation(
            String provider,
            CancellationSignal cancellationSignal,
            Executor executor,
            Consumer<Location> consumer) {
        getCurrentLocation(
                provider,
                new LocationRequest.Builder(0L).build(),
                cancellationSignal,
                executor,
                consumer);
    }

    public void getCurrentLocation(
            String provider,
            LocationRequest locationRequest,
            CancellationSignal cancellationSignal,
            final Executor executor,
            final Consumer<Location> consumer) {
        provider(provider);
        if (locationRequest == null) {
            throw new IllegalArgumentException("locationRequest is null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor is null");
        }
        if (consumer == null) {
            throw new IllegalArgumentException("consumer is null");
        }
        synchronized (this) {
            requireBackend(provider);
        }
        if (cancellationSignal != null && cancellationSignal.isCanceled()) {
            return;
        }
        Looper looper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
        if (looper == null) {
            final Location immediate;
            synchronized (this) {
                immediate = deliveryAllowed() ? sampleForProvider(provider) : null;
            }
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    if (cancellationSignal == null || !cancellationSignal.isCanceled()) {
                        consumer.accept(immediate);
                    }
                }
            });
            return;
        }
        final LocationListener adapter = new LocationListener() {
            @Override
            public void onLocationChanged(final Location location) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (cancellationSignal == null || !cancellationSignal.isCanceled()) {
                            consumer.accept(location);
                        }
                    }
                });
            }
        };
        if (cancellationSignal != null) {
            cancellationSignal.setOnCancelListener(new CancellationSignal.OnCancelListener() {
                @Override
                public void onCancel() {
                    removeUpdates(adapter);
                }
            });
        }
        synchronized (this) {
            if (cancellationSignal != null && cancellationSignal.isCanceled()) {
                return;
            }
            request(provider, 0L, 0.0f, adapter, looper, null, 1, locationRequest.getDurationMillis());
        }
    }

    public void requestLocationUpdates(
            String provider, long minTimeMs, float minDistanceM, LocationListener listener) {
        requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, (Looper) null);
    }

    public synchronized void requestLocationUpdates(
            String provider,
            long minTimeMs,
            float minDistanceM,
            LocationListener listener,
            Looper looper) {
        request(provider, minTimeMs, minDistanceM, listener, looper, null, Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    public synchronized void requestLocationUpdates(
            String provider,
            long minTimeMs,
            float minDistanceM,
            Executor executor,
            LocationListener listener) {
        if (executor == null) {
            throw new IllegalArgumentException("executor is null");
        }
        request(provider, minTimeMs, minDistanceM, listener, resolveExecutorLooper(), executor,
                Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    @Deprecated
    public synchronized void requestLocationUpdates(
            long minTimeMs,
            float minDistanceM,
            Criteria criteria,
            LocationListener listener,
            Looper looper) {
        String resolved = resolveCriteriaProvider(criteria);
        request(resolved, minTimeMs, minDistanceM, listener, looper, null, Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    @Deprecated
    public synchronized void requestLocationUpdates(
            long minTimeMs,
            float minDistanceM,
            Criteria criteria,
            Executor executor,
            LocationListener listener) {
        if (executor == null) {
            throw new IllegalArgumentException("executor is null");
        }
        String resolved = resolveCriteriaProvider(criteria);
        request(resolved, minTimeMs, minDistanceM, listener, resolveExecutorLooper(), executor,
                Integer.MAX_VALUE, Long.MAX_VALUE);
    }

    public synchronized void requestLocationUpdates(
            String provider,
            long minTimeMs,
            float minDistanceM,
            PendingIntent pendingIntent) {
        requestPending(provider, minTimeMs, minDistanceM, pendingIntent, Integer.MAX_VALUE);
    }

    @Deprecated
    public synchronized void requestLocationUpdates(
            long minTimeMs,
            float minDistanceM,
            Criteria criteria,
            PendingIntent pendingIntent) {
        String resolved = resolveCriteriaProvider(criteria);
        requestPending(resolved, minTimeMs, minDistanceM, pendingIntent, Integer.MAX_VALUE);
    }

    public synchronized void requestLocationUpdates(
            String provider,
            LocationRequest locationRequest,
            Executor executor,
            LocationListener listener) {
        if (locationRequest == null) {
            throw new IllegalArgumentException("locationRequest is null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor is null");
        }
        long interval = locationRequest.getIntervalMillis() == LocationRequest.PASSIVE_INTERVAL
                ? Long.MAX_VALUE
                : locationRequest.getIntervalMillis();
        request(provider,
                interval,
                locationRequest.getMinUpdateDistanceMeters(),
                listener,
                resolveExecutorLooper(),
                executor,
                locationRequest.getMaxUpdates(),
                locationRequest.getDurationMillis());
    }

    public synchronized void requestLocationUpdates(
            String provider,
            LocationRequest locationRequest,
            PendingIntent pendingIntent) {
        if (locationRequest == null) {
            throw new IllegalArgumentException("locationRequest is null");
        }
        long interval = locationRequest.getIntervalMillis() == LocationRequest.PASSIVE_INTERVAL
                ? Long.MAX_VALUE
                : locationRequest.getIntervalMillis();
        requestPending(
                provider,
                interval,
                locationRequest.getMinUpdateDistanceMeters(),
                pendingIntent,
                locationRequest.getMaxUpdates());
    }

    @Deprecated
    public synchronized void requestSingleUpdate(
            String provider, LocationListener listener, Looper looper) {
        request(provider, 0L, 0.0f, listener, looper, null, 1, Long.MAX_VALUE);
    }

    @Deprecated
    public synchronized void requestSingleUpdate(
            Criteria criteria, LocationListener listener, Looper looper) {
        String resolved = resolveCriteriaProvider(criteria);
        request(resolved, 0L, 0.0f, listener, looper, null, 1, Long.MAX_VALUE);
    }

    @Deprecated
    public synchronized void requestSingleUpdate(String provider, PendingIntent pendingIntent) {
        requestPending(provider, 0L, 0.0f, pendingIntent, 1);
    }

    @Deprecated
    public synchronized void requestSingleUpdate(Criteria criteria, PendingIntent pendingIntent) {
        String resolved = resolveCriteriaProvider(criteria);
        requestPending(resolved, 0L, 0.0f, pendingIntent, 1);
    }

    private static Looper resolveExecutorLooper() {
        Looper current = Looper.myLooper();
        return current != null ? current : Looper.getMainLooper();
    }

    private void request(
            String provider,
            long minTime,
            float minDistance,
            LocationListener listener,
            Looper looper,
            Executor executor,
            int maxUpdates,
            long durationMillis) {
        provider(provider);
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
        if (!Float.isFinite(minDistance)) {
            throw new IllegalArgumentException("Non-finite minimum distance");
        }
        requireBackend(provider);
        Looper targetLooper = looper == null ? Looper.myLooper() : looper;
        Handler handler = targetLooper != null ? new Handler(targetLooper) : null;
        if (handler == null && executor == null) {
            throw new IllegalStateException("Calling thread has no Looper");
        }
        removeUpdates(listener);
        long effectiveInterval = minTime == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1000L, minTime);
        Subscription subscription = new Subscription(
                provider,
                listener,
                handler,
                executor,
                effectiveInterval,
                Math.max(0.0f, minDistance),
                maxUpdates,
                durationMillis);
        if (TRACE) {
            System.out.println("SHIRAZ_TEST_LOCATION subscribe interval_ms=" + subscription.interval
                    + " min_distance_m=" + subscription.minDistance
                    + " listener=" + ((Object) listener).getClass().getName());
        }
        subscriptions.put(listener, subscription);
        ensureDarwinSession(provider, minDistance);
        if (deliveryAllowed()) {
            if (handler != null) {
                if (effectiveInterval != Long.MAX_VALUE && !handler.post(subscription)) {
                    subscriptions.remove(listener);
                }
            } else if (effectiveInterval != Long.MAX_VALUE) {
                subscription.run();
            }
        }
    }

    private void requestPending(
            String provider,
            long minTime,
            float minDistance,
            PendingIntent pendingIntent,
            int maxUpdates) {
        provider(provider);
        if (pendingIntent == null) {
            throw new IllegalArgumentException("pendingIntent is null");
        }
        if (!Float.isFinite(minDistance)) {
            throw new IllegalArgumentException("Non-finite minimum distance");
        }
        requireBackend(provider);
        removeUpdates(pendingIntent);
        Looper targetLooper = resolveExecutorLooper();
        Handler handler = targetLooper != null ? new Handler(targetLooper) : null;
        long effectiveInterval = minTime == Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(1000L, minTime);
        PendingSubscription sub = new PendingSubscription(
                provider,
                pendingIntent,
                handler,
                effectiveInterval,
                Math.max(0.0f, minDistance),
                maxUpdates);
        pendingSubscriptions.put(pendingIntent, sub);
        if (deliveryAllowed() && effectiveInterval != Long.MAX_VALUE) {
            if (handler != null) {
                if (!handler.post(sub)) {
                    pendingSubscriptions.remove(pendingIntent);
                }
            } else {
                sub.run();
            }
        }
    }

    public void requestFlush(String provider, final LocationListener listener, final int requestCode) {
        provider(provider);
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
        final Subscription sub;
        synchronized (this) {
            sub = subscriptions.get(listener);
            if (sub == null || !sub.providerName.equals(provider)) {
                throw new IllegalArgumentException("Unregistered listener cannot be flushed");
            }
        }
        Runnable flushTask = new Runnable() {
            @Override
            public void run() {
                listener.onFlushComplete(requestCode);
            }
        };
        if (sub.executor != null) {
            sub.executor.execute(flushTask);
        } else if (sub.handler != null) {
            sub.handler.post(flushTask);
        } else {
            flushTask.run();
        }
    }

    public void requestFlush(String provider, PendingIntent pendingIntent, int requestCode) {
        provider(provider);
        if (pendingIntent == null) {
            throw new IllegalArgumentException("pendingIntent is null");
        }
        synchronized (this) {
            PendingSubscription sub = pendingSubscriptions.get(pendingIntent);
            if (sub == null || !sub.providerName.equals(provider)) {
                throw new IllegalArgumentException("Unregistered PendingIntent cannot be flushed");
            }
        }
        Intent flushIntent = new Intent();
        flushIntent.putExtra(KEY_FLUSH_COMPLETE, requestCode);
        try {
            pendingIntent.shirazSend(flushIntent);
        } catch (PendingIntent.CanceledException e) {
            removeUpdates(pendingIntent);
        }
    }

    public synchronized void removeUpdates(LocationListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
        Subscription subscription = subscriptions.remove(listener);
        if (subscription != null && subscription.handler != null) {
            subscription.handler.removeCallbacks(subscription);
        }
        stopDarwinIfIdle();
    }

    public synchronized void removeUpdates(PendingIntent pendingIntent) {
        if (pendingIntent == null) {
            throw new IllegalArgumentException("pendingIntent is null");
        }
        PendingSubscription sub = pendingSubscriptions.remove(pendingIntent);
        if (sub != null && sub.handler != null) {
            sub.handler.removeCallbacks(sub);
        }
        stopDarwinIfIdle();
    }

    public void addTestProvider(
            String name,
            boolean requiresNetwork,
            boolean requiresSatellite,
            boolean requiresCell,
            boolean hasMonetaryCost,
            boolean supportsAltitude,
            boolean supportsSpeed,
            boolean supportsBearing,
            int powerRequirement,
            int accuracy) {
        int mappedPower = powerRequirement;
        if (mappedPower == Criteria.NO_REQUIREMENT) {
            mappedPower = ProviderProperties.POWER_USAGE_LOW;
        }
        int mappedAccuracy = accuracy;
        if (mappedAccuracy == Criteria.NO_REQUIREMENT) {
            mappedAccuracy = ProviderProperties.ACCURACY_FINE;
        }
        ProviderProperties properties = new ProviderProperties.Builder()
                .setHasNetworkRequirement(requiresNetwork)
                .setHasSatelliteRequirement(requiresSatellite)
                .setHasCellRequirement(requiresCell)
                .setHasMonetaryCost(hasMonetaryCost)
                .setHasAltitudeSupport(supportsAltitude)
                .setHasSpeedSupport(supportsSpeed)
                .setHasBearingSupport(supportsBearing)
                .setPowerUsage(mappedPower)
                .setAccuracy(mappedAccuracy)
                .build();
        addTestProvider(name, properties, Collections.<String>emptySet());
    }

    public void addTestProvider(String name, ProviderProperties properties) {
        addTestProvider(name, properties, Collections.<String>emptySet());
    }

    public synchronized void addTestProvider(
            String name, ProviderProperties properties, Set<String> extraAttributionTags) {
        provider(name);
        if (properties == null) {
            throw new IllegalArgumentException("properties is null");
        }
        if (closed) {
            throw new IllegalStateException("Location service is closed");
        }
        TestProviderState existing = testProviders.get(name);
        if (existing != null) {
            existing.properties = properties;
        } else {
            testProviders.put(name, new TestProviderState(name, properties));
        }
    }

    public synchronized void removeTestProvider(String provider) {
        provider(provider);
        if (closed) {
            throw new IllegalStateException("Location service is closed");
        }
        testProviders.remove(provider);
    }

    private TestProviderState requireTestProvider(String provider) {
        provider(provider);
        if (closed) {
            throw new IllegalStateException("Location service is closed");
        }
        TestProviderState tp = testProviders.get(provider);
        if (tp == null) {
            throw new IllegalArgumentException("Provider \"" + provider + "\" is not a test provider");
        }
        return tp;
    }

    public void setTestProviderLocation(String provider, Location loc) {
        if (loc == null) {
            throw new IllegalArgumentException("location is null");
        }
        ArrayList<Subscription> toNotify = new ArrayList<Subscription>();
        ArrayList<PendingSubscription> pendingToNotify = new ArrayList<PendingSubscription>();
        Location stored;
        synchronized (this) {
            TestProviderState tp = requireTestProvider(provider);
            stored = new Location(loc);
            stored.setProvider(provider);
            stored.setMock(true);
            if (stored.getTime() == 0L) {
                stored.setTime(System.currentTimeMillis());
            }
            if (stored.getElapsedRealtimeNanos() == 0L) {
                stored.setElapsedRealtimeNanos(safeElapsedRealtimeNanos());
            }
            tp.location = stored;
            if (tp.enabled && deliveryAllowed()) {
                for (Subscription s : subscriptions.values()) {
                    if (provider.equals(s.providerName) || PASSIVE_PROVIDER.equals(s.providerName)) {
                        toNotify.add(s);
                    }
                }
                for (PendingSubscription ps : pendingSubscriptions.values()) {
                    if (provider.equals(ps.providerName) || PASSIVE_PROVIDER.equals(ps.providerName)) {
                        pendingToNotify.add(ps);
                    }
                }
            }
        }
        evaluateProximityAlerts(stored);
        for (int i = 0; i < toNotify.size(); i++) {
            Subscription s = toNotify.get(i);
            if (s.handler != null) {
                s.handler.removeCallbacks(s);
                s.handler.post(s);
            } else {
                s.run();
            }
        }
        for (int i = 0; i < pendingToNotify.size(); i++) {
            PendingSubscription ps = pendingToNotify.get(i);
            if (ps.handler != null) {
                ps.handler.removeCallbacks(ps);
                ps.handler.post(ps);
            } else {
                ps.run();
            }
        }
    }

    @Deprecated
    public synchronized void clearTestProviderLocation(String provider) {
        TestProviderState tp = requireTestProvider(provider);
        tp.location = null;
    }

    public void setTestProviderEnabled(final String provider, final boolean enabled) {
        ArrayList<Subscription> matching = new ArrayList<Subscription>();
        synchronized (this) {
            TestProviderState tp = requireTestProvider(provider);
            if (tp.enabled == enabled) {
                return;
            }
            tp.enabled = enabled;
            for (Subscription s : subscriptions.values()) {
                if (provider.equals(s.providerName)) {
                    matching.add(s);
                }
            }
        }
        for (int i = 0; i < matching.size(); i++) {
            final Subscription s = matching.get(i);
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    if (enabled) {
                        s.listener.onProviderEnabled(provider);
                    } else {
                        s.listener.onProviderDisabled(provider);
                    }
                }
            };
            if (s.executor != null) {
                s.executor.execute(r);
            } else if (s.handler != null) {
                s.handler.post(r);
            }
        }
    }

    @Deprecated
    public void clearTestProviderEnabled(String provider) {
        setTestProviderEnabled(provider, false);
    }

    @Deprecated
    public synchronized void setTestProviderStatus(
            String provider, int status, Bundle extras, long updateTime) {
        TestProviderState tp = requireTestProvider(provider);
        tp.status = status;
        tp.statusExtras = extras != null ? new Bundle(extras) : null;
        tp.statusUpdateTime = updateTime;
    }

    @Deprecated
    public synchronized void clearTestProviderStatus(String provider) {
        TestProviderState tp = requireTestProvider(provider);
        tp.status = LocationProvider.AVAILABLE;
        tp.statusExtras = null;
        tp.statusUpdateTime = 0L;
    }

    public synchronized void addProximityAlert(
            double latitude,
            double longitude,
            float radius,
            long expiration,
            PendingIntent intent) {
        if (Double.isNaN(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Invalid latitude: " + latitude);
        }
        if (Double.isNaN(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Invalid longitude: " + longitude);
        }
        if (Float.isNaN(radius) || radius < 0.0f) {
            throw new IllegalArgumentException("Invalid radius: " + radius);
        }
        if (intent == null) {
            throw new IllegalArgumentException("intent is null");
        }
        if (!permitted()) {
            throw new SecurityException("addProximityAlert requires ACCESS_FINE_LOCATION");
        }
        if (closed) {
            throw new IllegalStateException("Location service is closed");
        }
        long now = safeElapsedRealtimeMillis();
        long exp = expiration < 0L ? Long.MAX_VALUE : now + expiration;
        proximityAlerts.put(intent, new ProximityAlert(latitude, longitude, radius, exp, intent));
    }

    public synchronized void removeProximityAlert(PendingIntent intent) {
        if (intent == null) {
            throw new IllegalArgumentException("intent is null");
        }
        proximityAlerts.remove(intent);
    }

    private void evaluateProximityAlerts(Location loc) {
        if (loc == null) {
            return;
        }
        ArrayList<ProximityAlert> toFire = new ArrayList<ProximityAlert>();
        ArrayList<Boolean> enteringFlags = new ArrayList<Boolean>();
        long now = safeElapsedRealtimeMillis();
        synchronized (this) {
            if (proximityAlerts.isEmpty()) {
                return;
            }
            ArrayList<PendingIntent> expired = null;
            float[] distOut = new float[1];
            for (Map.Entry<PendingIntent, ProximityAlert> entry : proximityAlerts.entrySet()) {
                ProximityAlert alert = entry.getValue();
                if (now >= alert.expirationRealtimeMs) {
                    if (expired == null) {
                        expired = new ArrayList<PendingIntent>();
                    }
                    expired.add(entry.getKey());
                    continue;
                }
                Location.distanceBetween(
                        loc.getLatitude(), loc.getLongitude(),
                        alert.latitude, alert.longitude, distOut);
                boolean isInside = distOut[0] <= alert.radius;
                if (alert.inside == null) {
                    if (isInside) {
                        alert.inside = Boolean.TRUE;
                        toFire.add(alert);
                        enteringFlags.add(Boolean.TRUE);
                    } else {
                        alert.inside = Boolean.FALSE;
                    }
                } else if (alert.inside.booleanValue() != isInside) {
                    alert.inside = Boolean.valueOf(isInside);
                    toFire.add(alert);
                    enteringFlags.add(Boolean.valueOf(isInside));
                }
            }
            if (expired != null) {
                for (int i = 0; i < expired.size(); i++) {
                    proximityAlerts.remove(expired.get(i));
                }
            }
        }
        for (int i = 0; i < toFire.size(); i++) {
            ProximityAlert alert = toFire.get(i);
            Intent proxIntent = new Intent();
            proxIntent.putExtra(KEY_PROXIMITY_ENTERING, enteringFlags.get(i).booleanValue());
            try {
                alert.intent.shirazSend(proxIntent);
            } catch (PendingIntent.CanceledException e) {
                removeProximityAlert(alert.intent);
            }
        }
    }

    /** Host lifecycle pauses delivery without discarding registrations. */
    private boolean deliveryAllowed() {
        return foreground || foregroundService;
    }

    public synchronized void shirazSetForeground(boolean value) {
        boolean before = deliveryAllowed();
        foreground = value;
        reschedule(before);
    }

    /** Host-owned foreground service lifetime; this never grants location permission. */
    public synchronized void shirazSetForegroundService(boolean value) {
        boolean before = deliveryAllowed();
        foregroundService = value;
        reschedule(before);
    }

    private void reschedule(boolean before) {
        if (closed || before == deliveryAllowed()) {
            return;
        }
        for (Subscription s : subscriptions.values()) {
            if (s.handler != null) {
                s.handler.removeCallbacks(s);
                if (deliveryAllowed() && s.interval != Long.MAX_VALUE) {
                    s.handler.post(s);
                }
            }
        }
        for (PendingSubscription ps : pendingSubscriptions.values()) {
            if (ps.handler != null) {
                ps.handler.removeCallbacks(ps);
                if (deliveryAllowed() && ps.interval != Long.MAX_VALUE) {
                    ps.handler.post(ps);
                }
            }
        }
    }

    public synchronized void shirazSetTestLocation(TestLocationConfig value) {
        if (value == null) {
            throw new NullPointerException("fix");
        }
        if (fix == null || closed) {
            throw new IllegalStateException("Test backend is not active");
        }
        fix = value;
        last = sample();
    }

    public synchronized void shirazClose() {
        if (closed) {
            return;
        }
        closed = true;
        for (Subscription s : subscriptions.values()) {
            if (s.handler != null) {
                s.handler.removeCallbacks(s);
            }
        }
        subscriptions.clear();
        for (PendingSubscription ps : pendingSubscriptions.values()) {
            if (ps.handler != null) {
                ps.handler.removeCallbacks(ps);
            }
        }
        pendingSubscriptions.clear();
        if (darwinHandle != 0L) {
            DarwinLocation.stop(darwinHandle);
            darwinHandle = 0L;
            darwinFineActive = false;
        }
        proximityAlerts.clear();
        testProviders.clear();
        last = null;
    }

    public GnssCapabilities getGnssCapabilities() {
        return new GnssCapabilities.Builder().build();
    }

    public int getGnssYearOfHardware() {
        return 0;
    }

    public String getGnssHardwareModelName() {
        return null;
    }

    public List<GnssAntennaInfo> getGnssAntennaInfos() {
        return null;
    }

    @Deprecated
    public synchronized GpsStatus getGpsStatus(GpsStatus status) {
        if (!permitted()) {
            throw new SecurityException("getGpsStatus requires ACCESS_FINE_LOCATION");
        }
        if (status == null) {
            return GpsStatus.createEmpty();
        }
        status.setStatus(null, 0);
        return status;
    }

    @Deprecated
    public synchronized boolean addGpsStatusListener(GpsStatus.Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    @Deprecated
    public void removeGpsStatusListener(GpsStatus.Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is null");
        }
    }

    @Deprecated
    public boolean registerGnssStatusCallback(GnssStatus.Callback callback) {
        return registerGnssStatusCallback(callback, null);
    }

    // Simulated coordinates provide no satellite measurements or assistance injection.
    public synchronized boolean registerGnssStatusCallback(
            GnssStatus.Callback callback, Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public synchronized boolean registerGnssStatusCallback(
            Executor executor, GnssStatus.Callback callback) {
        if (executor == null) {
            throw new IllegalArgumentException("executor");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public void unregisterGnssStatusCallback(GnssStatus.Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
    }

    @Deprecated
    public synchronized boolean addNmeaListener(GpsStatus.NmeaListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    @Deprecated
    public void removeNmeaListener(GpsStatus.NmeaListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
    }

    @Deprecated
    public boolean addNmeaListener(OnNmeaMessageListener listener) {
        return addNmeaListener(listener, null);
    }

    public synchronized boolean addNmeaListener(OnNmeaMessageListener listener, Handler handler) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public synchronized boolean addNmeaListener(
            Executor executor, OnNmeaMessageListener listener) {
        if (executor == null) {
            throw new IllegalArgumentException("executor");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public void removeNmeaListener(OnNmeaMessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
    }

    @Deprecated
    public boolean registerGnssMeasurementsCallback(GnssMeasurementsEvent.Callback callback) {
        return registerGnssMeasurementsCallback(callback, null);
    }

    public synchronized boolean registerGnssMeasurementsCallback(
            GnssMeasurementsEvent.Callback callback, Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public synchronized boolean registerGnssMeasurementsCallback(
            Executor executor, GnssMeasurementsEvent.Callback callback) {
        if (executor == null) {
            throw new IllegalArgumentException("executor");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public synchronized boolean registerGnssMeasurementsCallback(
            GnssMeasurementRequest request,
            Executor executor,
            GnssMeasurementsEvent.Callback callback) {
        if (request == null) {
            throw new IllegalArgumentException("request");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public void unregisterGnssMeasurementsCallback(GnssMeasurementsEvent.Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
    }

    public synchronized boolean registerAntennaInfoListener(
            Executor executor, GnssAntennaInfo.Listener listener) {
        if (executor == null) {
            throw new IllegalArgumentException("executor");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public void unregisterAntennaInfoListener(GnssAntennaInfo.Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
    }

    @Deprecated
    public boolean registerGnssNavigationMessageCallback(
            GnssNavigationMessage.Callback callback) {
        return registerGnssNavigationMessageCallback(callback, null);
    }

    public synchronized boolean registerGnssNavigationMessageCallback(
            GnssNavigationMessage.Callback callback, Handler handler) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public synchronized boolean registerGnssNavigationMessageCallback(
            Executor executor, GnssNavigationMessage.Callback callback) {
        if (executor == null) {
            throw new IllegalArgumentException("executor");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
        requireBackend(GPS_PROVIDER);
        return false;
    }

    public void unregisterGnssNavigationMessageCallback(
            GnssNavigationMessage.Callback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback");
        }
    }

    public synchronized boolean sendExtraCommand(String provider, String command, Bundle extras) {
        provider(provider);
        requireBackend(provider);
        return false;
    }

    private static double distance(Location a, Location b) {
        double lat = Math.toRadians(b.getLatitude() - a.getLatitude());
        double lon = Math.toRadians(b.getLongitude() - a.getLongitude());
        double h = Math.sin(lat / 2) * Math.sin(lat / 2)
                + Math.cos(Math.toRadians(a.getLatitude()))
                        * Math.cos(Math.toRadians(b.getLatitude()))
                        * Math.sin(lon / 2) * Math.sin(lon / 2);
        return 6371000 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
    }

    private final class Subscription implements Runnable {
        final String providerName;
        final LocationListener listener;
        final Handler handler;
        final Executor executor;
        final long interval;
        final float minDistance;
        final int maxUpdates;
        final long expirationRealtimeMs;
        int deliveredCount;
        Location delivered;
        long deliveredAt;

        Subscription(
                String providerName,
                LocationListener listener,
                Handler handler,
                Executor executor,
                long interval,
                float distance,
                int maxUpdates,
                long durationMillis) {
            this.providerName = providerName;
            this.listener = listener;
            this.handler = handler;
            this.executor = executor;
            this.interval = interval;
            this.minDistance = distance;
            this.maxUpdates = maxUpdates;
            long now = safeElapsedRealtimeMillis();
            this.expirationRealtimeMs =
                    durationMillis == Long.MAX_VALUE || Long.MAX_VALUE - now < durationMillis
                            ? Long.MAX_VALUE
                            : now + durationMillis;
        }

        @Override
        public void run() {
            Location value = null;
            synchronized (LocationManager.this) {
                if (closed || !deliveryAllowed() || subscriptions.get(listener) != this) {
                    return;
                }
                if (!permittedForProvider(providerName)) {
                    subscriptions.remove(listener);
                    return;
                }
                long now = safeElapsedRealtimeMillis();
                if (now > expirationRealtimeMs) {
                    subscriptions.remove(listener);
                    return;
                }
                Location current = sampleForProvider(providerName);
                if (current != null
                        && (delivered == null
                                || (now - deliveredAt >= interval
                                        && distance(delivered, current) >= minDistance))) {
                    value = new Location(current);
                    delivered = new Location(current);
                    deliveredAt = now;
                    deliveredCount++;
                    // Retire before calling app code, which may register this listener again.
                    if (deliveredCount >= maxUpdates) {
                        subscriptions.remove(listener);
                    }
                }
            }
            // App callbacks may synchronously coordinate with another thread removing this listener.
            if (value != null) {
                final Location callbackValue = value;
                if (executor != null) {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            listener.onLocationChanged(callbackValue);
                        }
                    });
                } else {
                    listener.onLocationChanged(callbackValue);
                }
                trace("callback-completed", delivered);
                evaluateProximityAlerts(callbackValue);
            }
            synchronized (LocationManager.this) {
                if (!closed
                        && deliveryAllowed()
                        && subscriptions.get(listener) == this
                        && handler != null
                        && interval != Long.MAX_VALUE
                        && !handler.hasCallbacks(this)
                        && !handler.postDelayed(this, interval)) {
                    subscriptions.remove(listener);
                }
            }
        }
    }

    private final class PendingSubscription implements Runnable {
        final String providerName;
        final PendingIntent pendingIntent;
        final Handler handler;
        final long interval;
        final float minDistance;
        final int maxUpdates;
        int deliveredCount;
        Location delivered;
        long deliveredAt;

        PendingSubscription(
                String providerName,
                PendingIntent pendingIntent,
                Handler handler,
                long interval,
                float minDistance,
                int maxUpdates) {
            this.providerName = providerName;
            this.pendingIntent = pendingIntent;
            this.handler = handler;
            this.interval = interval;
            this.minDistance = minDistance;
            this.maxUpdates = maxUpdates;
        }

        @Override
        public void run() {
            Location value = null;
            synchronized (LocationManager.this) {
                if (closed || !deliveryAllowed() || pendingSubscriptions.get(pendingIntent) != this) {
                    return;
                }
                if (!permittedForProvider(providerName)) {
                    pendingSubscriptions.remove(pendingIntent);
                    return;
                }
                long now = safeElapsedRealtimeMillis();
                Location current = sampleForProvider(providerName);
                if (current != null
                        && (delivered == null
                                || (now - deliveredAt >= interval
                                        && distance(delivered, current) >= minDistance))) {
                    value = new Location(current);
                    delivered = new Location(current);
                    deliveredAt = now;
                    deliveredCount++;
                    if (deliveredCount >= maxUpdates) {
                        pendingSubscriptions.remove(pendingIntent);
                    }
                }
            }
            if (value != null) {
                Intent locIntent = new Intent();
                locIntent.putExtra(KEY_LOCATION_CHANGED, value);
                try {
                    pendingIntent.shirazSend(locIntent);
                } catch (PendingIntent.CanceledException e) {
                    removeUpdates(pendingIntent);
                    return;
                }
                evaluateProximityAlerts(value);
            }
            synchronized (LocationManager.this) {
                if (!closed
                        && deliveryAllowed()
                        && pendingSubscriptions.get(pendingIntent) == this
                        && handler != null
                        && interval != Long.MAX_VALUE
                        && !handler.hasCallbacks(this)
                        && !handler.postDelayed(this, interval)) {
                    pendingSubscriptions.remove(pendingIntent);
                }
            }
        }
    }
}
