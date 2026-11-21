// SPDX-License-Identifier: GPL-3.0-only
package android.credentials;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Prefetched candidate metadata returned from {@link CredentialManager#prepareGetCredential}. */
public final class PrepareGetCredentialResponse {
    private final Set<String> mCredentialResultTypes;
    private final boolean mHasAuthenticationResults;
    private final boolean mHasRemoteResults;
    private final PendingGetCredentialHandle mPendingGetCredentialHandle;

    PrepareGetCredentialResponse(
            Set<String> credentialResultTypes,
            boolean hasAuthenticationResults,
            boolean hasRemoteResults,
            GetCredentialRequest request) {
        this.mCredentialResultTypes =
                credentialResultTypes == null
                        ? Collections.<String>emptySet()
                        : Collections.unmodifiableSet(
                                new LinkedHashSet<String>(credentialResultTypes));
        this.mHasAuthenticationResults = hasAuthenticationResults;
        this.mHasRemoteResults = hasRemoteResults;
        this.mPendingGetCredentialHandle = new PendingGetCredentialHandle(request);
    }

    public boolean hasCredentialResults(String credentialType) {
        Objects.requireNonNull(credentialType, "credentialType must not be null");
        return mCredentialResultTypes.contains(credentialType);
    }

    public boolean hasAuthenticationResults() {
        return mHasAuthenticationResults;
    }

    public boolean hasRemoteResults() {
        return mHasRemoteResults;
    }

    public PendingGetCredentialHandle getPendingGetCredentialHandle() {
        return mPendingGetCredentialHandle;
    }

    public static final class PendingGetCredentialHandle {
        private final GetCredentialRequest mRequest;

        PendingGetCredentialHandle(GetCredentialRequest request) {
            this.mRequest = request;
        }

        GetCredentialRequest getRequest() {
            return mRequest;
        }
    }
}
