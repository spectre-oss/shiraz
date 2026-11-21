// SPDX-License-Identifier: GPL-3.0-only
package android.security;

import java.security.KeyPair;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AttestedKeyPair {
    private final KeyPair mKeyPair;
    private final List<Certificate> mAttestationRecord;

    public AttestedKeyPair(KeyPair keyPair, List<Certificate> attestationRecord) {
        mKeyPair = keyPair;
        mAttestationRecord = attestationRecord != null
                ? Collections.unmodifiableList(new ArrayList<>(attestationRecord))
                : Collections.<Certificate>emptyList();
    }

    public KeyPair getKeyPair() {
        return mKeyPair;
    }

    public List<Certificate> getAttestationRecord() {
        return mAttestationRecord;
    }
}
