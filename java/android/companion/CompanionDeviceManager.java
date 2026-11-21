// SPDX-License-Identifier: GPL-3.0-only
package android.companion;

import android.content.ComponentName;
import android.content.IntentSender;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

public final class CompanionDeviceManager {
    public static final String EXTRA_DEVICE = "android.companion.extra.DEVICE";
    public static final String EXTRA_ASSOCIATION = "android.companion.extra.ASSOCIATION";
    public static final int FLAG_CALL_METADATA = 1;
    public static final int RESULT_OK = -1;
    public static final int RESULT_CANCELED = 0;
    public static final int RESULT_USER_REJECTED = 1;
    public static final int RESULT_DISCOVERY_TIMEOUT = 2;
    public static final int RESULT_INTERNAL_ERROR = 3;

    public abstract static class Callback {
        public Callback() {
        }

        @Deprecated
        public void onDeviceFound(IntentSender chooserLauncher) {
        }

        public void onAssociationPending(IntentSender intentSender) {
            onDeviceFound(intentSender);
        }

        public void onAssociationCreated(AssociationInfo associationInfo) {
        }

        public abstract void onFailure(CharSequence error);
    }

    private final List<AssociationInfo> mAssociations = new ArrayList<>();

    public CompanionDeviceManager() {
    }

    public void associate(AssociationRequest request, final Callback callback, Handler handler) {
        Objects.requireNonNull(request, "Request cannot be null");
        Objects.requireNonNull(callback, "Callback cannot be null");
        final Handler target = handler != null ? handler : new Handler(Looper.getMainLooper());
        target.post(new Runnable() {
            @Override
            public void run() {
                callback.onFailure("Companion device discovery is unavailable on this host");
            }
        });
    }

    public void associate(AssociationRequest request, Executor executor, final Callback callback) {
        Objects.requireNonNull(request, "Request cannot be null");
        Objects.requireNonNull(executor, "Executor cannot be null");
        Objects.requireNonNull(callback, "Callback cannot be null");
        executor.execute(new Runnable() {
            @Override
            public void run() {
                callback.onFailure("Companion device discovery is unavailable on this host");
            }
        });
    }

    public IntentSender buildAssociationCancellationIntent() {
        return null;
    }

    public void enableSystemDataSyncForTypes(int associationId, int flags) {
    }

    public void disableSystemDataSyncForTypes(int associationId, int flags) {
    }

    @Deprecated
    public List<String> getAssociations() {
        return Collections.emptyList();
    }

    public List<AssociationInfo> getMyAssociations() {
        return Collections.unmodifiableList(new ArrayList<>(mAssociations));
    }

    @Deprecated
    public void disassociate(String deviceMacAddress) {
    }

    public void disassociate(int associationId) {
    }

    public void requestNotificationAccess(ComponentName component) {
        Objects.requireNonNull(component, "component must not be null");
    }

    public boolean hasNotificationAccess(ComponentName component) {
        Objects.requireNonNull(component, "component must not be null");
        return false;
    }

    public void startObservingDevicePresence(String deviceAddress)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("Device is not associated: " + deviceAddress);
    }

    public void stopObservingDevicePresence(String deviceAddress)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("Device is not associated: " + deviceAddress);
    }

    public void attachSystemDataTransport(int associationId, InputStream in, OutputStream out)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("Association not found: " + associationId);
    }

    public void detachSystemDataTransport(int associationId)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("Association not found: " + associationId);
    }

    public IntentSender buildPermissionTransferUserConsentIntent(int associationId)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("Association not found: " + associationId);
    }

    public boolean isPermissionTransferUserConsented(int associationId) {
        return false;
    }

    public void startSystemDataTransfer(int associationId, Executor executor,
            OutcomeReceiver<Void, CompanionException> result)
            throws DeviceNotAssociatedException {
        throw new DeviceNotAssociatedException("Association not found: " + associationId);
    }
}
