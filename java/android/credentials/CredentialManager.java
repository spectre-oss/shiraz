// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.OutcomeReceiver;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** System-style credential manager supporting in-process description registration and async callbacks. */
public final class CredentialManager {
    private final Context mContext;
    private final Set<CredentialDescription> mRegisteredDescriptions =
            new LinkedHashSet<CredentialDescription>();

    public CredentialManager() {
        this.mContext = null;
    }

    public CredentialManager(Context context) {
        this.mContext = Objects.requireNonNull(context, "context must not be null");
    }

    public void getCredential(
            Context context,
            GetCredentialRequest request,
            CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            final OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        final AtomicBoolean canceled = attachCancellation(cancellationSignal);
        if (canceled.get()) {
            return;
        }
        callbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (canceled.get()) {
                            return;
                        }
                        callback.onError(
                                new GetCredentialException(
                                        GetCredentialException.TYPE_NO_CREDENTIAL,
                                        "No credential provider available"));
                    }
                });
    }

    public void getCredential(
            Context context,
            PrepareGetCredentialResponse.PendingGetCredentialHandle pendingGetCredentialHandle,
            CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            final OutcomeReceiver<GetCredentialResponse, GetCredentialException> callback) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(
                pendingGetCredentialHandle, "pendingGetCredentialHandle must not be null");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        final AtomicBoolean canceled = attachCancellation(cancellationSignal);
        if (canceled.get()) {
            return;
        }
        callbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (canceled.get()) {
                            return;
                        }
                        callback.onError(
                                new GetCredentialException(
                                        GetCredentialException.TYPE_NO_CREDENTIAL,
                                        "No credential provider available"));
                    }
                });
    }

    public void prepareGetCredential(
            final GetCredentialRequest request,
            CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            final OutcomeReceiver<PrepareGetCredentialResponse, GetCredentialException> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        final AtomicBoolean canceled = attachCancellation(cancellationSignal);
        if (canceled.get()) {
            return;
        }
        final Set<String> matchingTypes = findMatchingDescriptionTypes(request);
        final PrepareGetCredentialResponse response =
                new PrepareGetCredentialResponse(matchingTypes, false, false, request);
        callbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (canceled.get()) {
                            return;
                        }
                        callback.onResult(response);
                    }
                });
    }

    public void createCredential(
            Context context,
            CreateCredentialRequest request,
            CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            final OutcomeReceiver<CreateCredentialResponse, CreateCredentialException> callback) {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        final AtomicBoolean canceled = attachCancellation(cancellationSignal);
        if (canceled.get()) {
            return;
        }
        callbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (canceled.get()) {
                            return;
                        }
                        callback.onError(
                                new CreateCredentialException(
                                        CreateCredentialException.TYPE_NO_CREATE_OPTIONS,
                                        "No create credential provider available"));
                    }
                });
    }

    public void clearCredentialState(
            ClearCredentialStateRequest request,
            CancellationSignal cancellationSignal,
            Executor callbackExecutor,
            final OutcomeReceiver<Void, ClearCredentialStateException> callback) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callbackExecutor, "callbackExecutor must not be null");
        Objects.requireNonNull(callback, "callback must not be null");
        final AtomicBoolean canceled = attachCancellation(cancellationSignal);
        if (canceled.get()) {
            return;
        }
        synchronized (mRegisteredDescriptions) {
            mRegisteredDescriptions.clear();
        }
        callbackExecutor.execute(
                new Runnable() {
                    @Override
                    public void run() {
                        if (canceled.get()) {
                            return;
                        }
                        callback.onResult(null);
                    }
                });
    }

    public boolean isEnabledCredentialProviderService(ComponentName componentName) {
        Objects.requireNonNull(componentName, "componentName must not be null");
        return false;
    }

    public void registerCredentialDescription(RegisterCredentialDescriptionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        synchronized (mRegisteredDescriptions) {
            for (CredentialDescription desc : request.getCredentialDescriptions()) {
                mRegisteredDescriptions.remove(desc);
                mRegisteredDescriptions.add(desc);
            }
        }
    }

    public void unregisterCredentialDescription(UnregisterCredentialDescriptionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        synchronized (mRegisteredDescriptions) {
            mRegisteredDescriptions.removeAll(request.getCredentialDescriptions());
        }
    }

    private Set<String> findMatchingDescriptionTypes(GetCredentialRequest request) {
        LinkedHashSet<String> matched = new LinkedHashSet<String>();
        List<CredentialOption> options = request.getCredentialOptions();
        synchronized (mRegisteredDescriptions) {
            for (CredentialOption option : options) {
                Bundle retrieval = option.getCredentialRetrievalData();
                ArrayList<String> requestedKeys =
                        retrieval.getStringArrayList(CredentialOption.SUPPORTED_ELEMENT_KEYS);
                for (CredentialDescription desc : mRegisteredDescriptions) {
                    if (!option.getType().equals(desc.getType())) {
                        continue;
                    }
                    if (desc.getCredentialEntries().isEmpty()) {
                        continue;
                    }
                    if (requestedKeys == null
                            || desc.getSupportedElementKeys().containsAll(requestedKeys)) {
                        matched.add(option.getType());
                    }
                }
            }
        }
        return matched;
    }

    private static AtomicBoolean attachCancellation(CancellationSignal signal) {
        final AtomicBoolean canceled = new AtomicBoolean(false);
        if (signal != null) {
            if (signal.isCanceled()) {
                canceled.set(true);
                return canceled;
            }
            signal.setOnCancelListener(
                    new CancellationSignal.OnCancelListener() {
                        @Override
                        public void onCancel() {
                            canceled.set(true);
                        }
                    });
            if (signal.isCanceled()) {
                canceled.set(true);
            }
        }
        return canceled;
    }
}
