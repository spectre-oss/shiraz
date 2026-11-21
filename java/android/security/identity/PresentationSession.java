// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;

public abstract class PresentationSession {
    protected PresentationSession() {
    }

    public abstract KeyPair getEphemeralKeyPair();
    public abstract void setReaderEphemeralPublicKey(PublicKey readerEphemeralPublicKey)
            throws InvalidKeyException;
    public abstract void setSessionTranscript(byte[] sessionTranscript);
    public abstract CredentialDataResult getCredentialData(String credentialName,
            CredentialDataRequest request) throws NoAuthenticationKeyAvailableException,
            InvalidReaderSignatureException, InvalidRequestMessageException,
            EphemeralPublicKeyNotFoundException;
}
