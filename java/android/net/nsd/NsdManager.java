// SPDX-License-Identifier: GPL-3.0-only
package android.net.nsd;

import android.net.Network;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

public final class NsdManager {
    public static final String ACTION_NSD_STATE_CHANGED = "android.net.nsd.STATE_CHANGED";
    public static final String EXTRA_NSD_STATE = "nsd_state";
    public static final int NSD_STATE_DISABLED = 1;
    public static final int NSD_STATE_ENABLED = 2;
    public static final int PROTOCOL_DNS_SD = 1;

    public static final int FAILURE_INTERNAL_ERROR = 0;
    public static final int FAILURE_ALREADY_ACTIVE = 3;
    public static final int FAILURE_MAX_LIMIT = 4;
    public static final int FAILURE_OPERATION_NOT_RUNNING = 5;
    public static final int FAILURE_BAD_PARAMETERS = 6;

    private final Map<RegistrationListener, RegEntry> mRegistrations =
            new ConcurrentHashMap<RegistrationListener, RegEntry>();
    private final Map<DiscoveryListener, DiscEntry> mDiscoveries =
            new ConcurrentHashMap<DiscoveryListener, DiscEntry>();
    private final Map<ServiceInfoCallback, Executor> mInfoCallbacks =
            new ConcurrentHashMap<ServiceInfoCallback, Executor>();

    NsdManager() {}

    public void registerService(
            NsdServiceInfo serviceInfo, int protocolType, RegistrationListener listener) {
        registerService(serviceInfo, protocolType, defaultExecutor(), listener);
    }

    public void registerService(
            final NsdServiceInfo serviceInfo,
            int protocolType,
            Executor executor,
            final RegistrationListener listener) {
        Objects.requireNonNull(serviceInfo, "serviceInfo");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        if (protocolType != PROTOCOL_DNS_SD
                || serviceInfo.getPort() <= 0
                || isEmpty(serviceInfo.getServiceName())
                || isEmpty(serviceInfo.getServiceType())) {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onRegistrationFailed(serviceInfo, FAILURE_BAD_PARAMETERS);
                        }
                    });
            return;
        }
        if (mRegistrations.putIfAbsent(listener, new RegEntry(serviceInfo, executor)) != null) {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onRegistrationFailed(serviceInfo, FAILURE_ALREADY_ACTIVE);
                        }
                    });
            return;
        }
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onServiceRegistered(serviceInfo);
                    }
                });
        for (Map.Entry<DiscoveryListener, DiscEntry> e : mDiscoveries.entrySet()) {
            final DiscoveryListener dl = e.getKey();
            final DiscEntry de = e.getValue();
            if (de.serviceType.equals(serviceInfo.getServiceType())) {
                de.executor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                dl.onServiceFound(serviceInfo);
                            }
                        });
            }
        }
    }

    public void unregisterService(final RegistrationListener listener) {
        Objects.requireNonNull(listener, "listener");
        final RegEntry removed = mRegistrations.remove(listener);
        if (removed == null) {
            throw new IllegalArgumentException("listener not registered");
        }
        removed.executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onServiceUnregistered(removed.info);
                    }
                });
    }

    public void discoverServices(
            String serviceType, int protocolType, DiscoveryListener listener) {
        discoverServices(serviceType, protocolType, (Network) null, defaultExecutor(), listener);
    }

    public void discoverServices(
            final String serviceType,
            int protocolType,
            Network network,
            Executor executor,
            final DiscoveryListener listener) {
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        if (isEmpty(serviceType) || protocolType != PROTOCOL_DNS_SD) {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onStartDiscoveryFailed(serviceType, FAILURE_BAD_PARAMETERS);
                        }
                    });
            return;
        }
        if (mDiscoveries.putIfAbsent(listener, new DiscEntry(serviceType, executor)) != null) {
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onStartDiscoveryFailed(serviceType, FAILURE_ALREADY_ACTIVE);
                        }
                    });
            return;
        }
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onDiscoveryStarted(serviceType);
                        for (RegEntry reg : mRegistrations.values()) {
                            if (serviceType.equals(reg.info.getServiceType())) {
                                listener.onServiceFound(reg.info);
                            }
                        }
                    }
                });
    }

    public void discoverServices(
            DiscoveryRequest discoveryRequest, Executor executor, DiscoveryListener listener) {
        Objects.requireNonNull(discoveryRequest, "discoveryRequest");
        discoverServices(
                discoveryRequest.getServiceType(),
                PROTOCOL_DNS_SD,
                discoveryRequest.getNetwork(),
                executor,
                listener);
    }

    public void discoverServices(
            String serviceType,
            int protocolType,
            NetworkRequest networkRequest,
            Executor executor,
            DiscoveryListener listener) {
        Objects.requireNonNull(networkRequest, "networkRequest");
        discoverServices(serviceType, protocolType, (Network) null, executor, listener);
    }

    public void stopServiceDiscovery(final DiscoveryListener listener) {
        Objects.requireNonNull(listener, "listener");
        final DiscEntry removed = mDiscoveries.remove(listener);
        if (removed == null) {
            throw new IllegalArgumentException("listener not active");
        }
        removed.executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onDiscoveryStopped(removed.serviceType);
                    }
                });
    }

    @Deprecated
    public void resolveService(NsdServiceInfo serviceInfo, ResolveListener listener) {
        resolveService(serviceInfo, defaultExecutor(), listener);
    }

    @Deprecated
    public void resolveService(
            final NsdServiceInfo serviceInfo,
            Executor executor,
            final ResolveListener listener) {
        Objects.requireNonNull(serviceInfo, "serviceInfo");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        for (RegEntry reg : mRegistrations.values()) {
            if (Objects.equals(serviceInfo.getServiceName(), reg.info.getServiceName())
                    && Objects.equals(serviceInfo.getServiceType(), reg.info.getServiceType())) {
                final NsdServiceInfo found = reg.info;
                executor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                listener.onServiceResolved(found);
                            }
                        });
                return;
            }
        }
        executor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onResolveFailed(serviceInfo, FAILURE_INTERNAL_ERROR);
                    }
                });
    }

    public void stopServiceResolution(final ResolveListener listener) {
        Objects.requireNonNull(listener, "listener");
    }

    public void registerServiceInfoCallback(
            final NsdServiceInfo serviceInfo,
            Executor executor,
            final ServiceInfoCallback listener) {
        Objects.requireNonNull(serviceInfo, "serviceInfo");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(listener, "listener");
        if (mInfoCallbacks.putIfAbsent(listener, executor) != null) {
            throw new IllegalArgumentException("listener already registered");
        }
        for (RegEntry reg : mRegistrations.values()) {
            if (Objects.equals(serviceInfo.getServiceName(), reg.info.getServiceName())
                    && Objects.equals(serviceInfo.getServiceType(), reg.info.getServiceType())) {
                final NsdServiceInfo found = reg.info;
                executor.execute(
                        new Runnable() {
                            @Override
                            public void run() {
                                listener.onServiceUpdated(found);
                            }
                        });
                return;
            }
        }
    }

    public void unregisterServiceInfoCallback(final ServiceInfoCallback listener) {
        Objects.requireNonNull(listener, "listener");
        Executor exec = mInfoCallbacks.remove(listener);
        if (exec == null) {
            throw new IllegalArgumentException("listener not registered");
        }
        exec.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        listener.onServiceInfoCallbackUnregistered();
                    }
                });
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    private static Executor defaultExecutor() {
        final Looper main = Looper.getMainLooper();
        if (main != null) {
            final Handler h = new Handler(main);
            return new Executor() {
                @Override
                public void execute(Runnable command) {
                    h.post(command);
                }
            };
        }
        return new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    private static final class RegEntry {
        final NsdServiceInfo info;
        final Executor executor;

        RegEntry(NsdServiceInfo info, Executor executor) {
            this.info = info;
            this.executor = executor;
        }
    }

    private static final class DiscEntry {
        final String serviceType;
        final Executor executor;

        DiscEntry(String serviceType, Executor executor) {
            this.serviceType = serviceType;
            this.executor = executor;
        }
    }

    public interface DiscoveryListener {
        void onStartDiscoveryFailed(String serviceType, int errorCode);

        void onStopDiscoveryFailed(String serviceType, int errorCode);

        void onDiscoveryStarted(String serviceType);

        void onDiscoveryStopped(String serviceType);

        void onServiceFound(NsdServiceInfo serviceInfo);

        void onServiceLost(NsdServiceInfo serviceInfo);
    }

    public interface RegistrationListener {
        void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode);

        void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode);

        void onServiceRegistered(NsdServiceInfo serviceInfo);

        void onServiceUnregistered(NsdServiceInfo serviceInfo);
    }

    public interface ResolveListener {
        void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode);

        void onServiceResolved(NsdServiceInfo serviceInfo);

        default void onResolutionStopped(NsdServiceInfo serviceInfo) {}

        default void onStopResolutionFailed(NsdServiceInfo serviceInfo, int errorCode) {}
    }

    public interface ServiceInfoCallback {
        void onServiceInfoCallbackRegistrationFailed(int reason);

        void onServiceUpdated(NsdServiceInfo serviceInfo);

        void onServiceLost();

        void onServiceInfoCallbackUnregistered();
    }
}
