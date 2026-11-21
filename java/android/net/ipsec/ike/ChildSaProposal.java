// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChildSaProposal extends SaProposal {
    ChildSaProposal(
            List<Pair<Integer, Integer>> encryptionAlgos,
            List<Integer> integrityAlgos,
            List<Integer> dhGroups) {
        super(encryptionAlgos, integrityAlgos, dhGroups);
    }

    public static Set<Integer> getSupportedEncryptionAlgorithms() {
        Set<Integer> set = new LinkedHashSet<Integer>();
        set.add(Integer.valueOf(ENCRYPTION_ALGORITHM_AES_CBC));
        set.add(Integer.valueOf(ENCRYPTION_ALGORITHM_AES_CTR));
        set.add(Integer.valueOf(ENCRYPTION_ALGORITHM_AES_GCM_8));
        set.add(Integer.valueOf(ENCRYPTION_ALGORITHM_AES_GCM_12));
        set.add(Integer.valueOf(ENCRYPTION_ALGORITHM_AES_GCM_16));
        set.add(Integer.valueOf(ENCRYPTION_ALGORITHM_CHACHA20_POLY1305));
        return Collections.unmodifiableSet(set);
    }

    public static Set<Integer> getSupportedIntegrityAlgorithms() {
        Set<Integer> set = new LinkedHashSet<Integer>();
        set.add(Integer.valueOf(INTEGRITY_ALGORITHM_NONE));
        set.add(Integer.valueOf(INTEGRITY_ALGORITHM_HMAC_SHA1_96));
        set.add(Integer.valueOf(INTEGRITY_ALGORITHM_AES_XCBC_96));
        set.add(Integer.valueOf(INTEGRITY_ALGORITHM_AES_CMAC_96));
        set.add(Integer.valueOf(INTEGRITY_ALGORITHM_HMAC_SHA2_256_128));
        set.add(Integer.valueOf(INTEGRITY_ALGORITHM_HMAC_SHA2_384_192));
        set.add(Integer.valueOf(INTEGRITY_ALGORITHM_HMAC_SHA2_512_256));
        return Collections.unmodifiableSet(set);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return (o instanceof ChildSaProposal) && super.equals(o);
    }

    public static final class Builder {
        private final List<Pair<Integer, Integer>> mEncryptionAlgos =
                new ArrayList<Pair<Integer, Integer>>();
        private final List<Integer> mIntegrityAlgos = new ArrayList<Integer>();
        private final List<Integer> mDhGroups = new ArrayList<Integer>();

        public Builder() {}

        public Builder addEncryptionAlgorithm(int algorithm, int keyLength) {
            Pair<Integer, Integer> pair =
                    new Pair<Integer, Integer>(
                            Integer.valueOf(algorithm), Integer.valueOf(keyLength));
            if (!mEncryptionAlgos.contains(pair)) {
                mEncryptionAlgos.add(pair);
            }
            return this;
        }

        public Builder addIntegrityAlgorithm(int algorithm) {
            Integer boxed = Integer.valueOf(algorithm);
            if (!mIntegrityAlgos.contains(boxed)) {
                mIntegrityAlgos.add(boxed);
            }
            return this;
        }

        public Builder addDhGroup(int dhGroup) {
            Integer boxed = Integer.valueOf(dhGroup);
            if (!mDhGroups.contains(boxed)) {
                mDhGroups.add(boxed);
            }
            return this;
        }

        public ChildSaProposal build() {
            if (mEncryptionAlgos.isEmpty()) {
                throw new IllegalArgumentException("Encryption algorithm must be specified");
            }
            return new ChildSaProposal(mEncryptionAlgos, mIntegrityAlgos, mDhGroups);
        }
    }
}
