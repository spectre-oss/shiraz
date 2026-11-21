// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

import java.security.cert.X509Certificate;
import java.util.Collection;

public abstract class WritableIdentityCredential {
    protected WritableIdentityCredential() {
    }

    public abstract Collection<X509Certificate> getCredentialKeyCertificateChain(byte[] challenge);
    public abstract byte[] personalize(PersonalizationData personalizationData);
}
