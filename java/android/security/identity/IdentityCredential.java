// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public abstract class IdentityCredential {
    protected IdentityCredential() {
    }

    @Deprecated
    public abstract KeyPair createEphemeralKeyPair();

    @Deprecated
    public abstract void setReaderEphemeralPublicKey(PublicKey readerEphemeralPublicKey)
            throws InvalidKeyException;

    @Deprecated
    public abstract byte[] encryptMessageToReader(byte[] messagePlaintext);

    @Deprecated
    public abstract byte[] decryptMessageFromReader(byte[] messageCiphertext)
            throws MessageDecryptionException;

    public abstract Collection<X509Certificate> getCredentialKeyCertificateChain();

    @Deprecated
    public abstract void setAllowUsingExhaustedKeys(boolean allowUsingExhaustedKeys);

    @Deprecated
    public void setAllowUsingExpiredKeys(boolean allowUsingExpiredKeys) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    public abstract ResultData getEntries(byte[] requestMessage,
            Map<String, Collection<String>> entriesToRequest, byte[] sessionTranscript,
            byte[] readerSignature) throws SessionTranscriptMismatchException,
            NoAuthenticationKeyAvailableException, InvalidReaderSignatureException,
            EphemeralPublicKeyNotFoundException, InvalidRequestMessageException;

    public abstract void setAvailableAuthenticationKeys(int keyCount, int maxUsesPerKey);

    public abstract Collection<X509Certificate> getAuthKeysNeedingCertification();

    @Deprecated
    public abstract void storeStaticAuthenticationData(X509Certificate authenticationKey,
            byte[] staticAuthData) throws UnknownAuthenticationKeyException;

    public void storeStaticAuthenticationData(X509Certificate authenticationKey,
            Instant expirationDate, byte[] staticAuthData)
            throws UnknownAuthenticationKeyException {
        throw new UnsupportedOperationException();
    }

    public abstract int[] getAuthenticationDataUsageCount();

    public byte[] proveOwnership(byte[] challenge) {
        throw new UnsupportedOperationException();
    }

    public byte[] delete(byte[] challenge) {
        throw new UnsupportedOperationException();
    }

    public byte[] update(PersonalizationData personalizationData) {
        throw new UnsupportedOperationException();
    }

    public void setAvailableAuthenticationKeys(int keyCount, int maxUsesPerKey,
            long minValidTimeMillis) {
        throw new UnsupportedOperationException();
    }

    public List<AuthenticationKeyMetadata> getAuthenticationKeyMetadata() {
        throw new UnsupportedOperationException();
    }
}
