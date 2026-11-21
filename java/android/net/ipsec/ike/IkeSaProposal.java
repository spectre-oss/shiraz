// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IkeSaProposal extends SaProposal {
    private final List<Integer> mPseudorandomFunctions;

    IkeSaProposal(
            List<Pair<Integer, Integer>> encryptionAlgos,
            List<Integer> prfs,
            List<Integer> integrityAlgos,
            List<Integer> dhGroups) {
        super(encryptionAlgos, integrityAlgos, dhGroups);
        this.mPseudorandomFunctions =
                Collections.unmodifiableList(new ArrayList<Integer>(prfs));
    }

    public static Set<Integer> getSupportedEncryptionAlgorithms() {
        Set<Integer> set = new LinkedHashSet<Integer>();
        set.add(Integer.valueOf(ENCRYPTION_ALGORITHM_3DES));
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

    public static Set<Integer> getSupportedPseudorandomFunctions() {
        Set<Integer> set = new LinkedHashSet<Integer>();
        set.add(Integer.valueOf(PSEUDORANDOM_FUNCTION_HMAC_SHA1));
        set.add(Integer.valueOf(PSEUDORANDOM_FUNCTION_AES128_XCBC));
        set.add(Integer.valueOf(PSEUDORANDOM_FUNCTION_SHA2_256));
        set.add(Integer.valueOf(PSEUDORANDOM_FUNCTION_SHA2_384));
        set.add(Integer.valueOf(PSEUDORANDOM_FUNCTION_SHA2_512));
        set.add(Integer.valueOf(PSEUDORANDOM_FUNCTION_AES128_CMAC));
        return Collections.unmodifiableSet(set);
    }

    public List<Integer> getPseudorandomFunctions() {
        return mPseudorandomFunctions;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Integer.valueOf(super.hashCode()), mPseudorandomFunctions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof IkeSaProposal) || !super.equals(o)) {
            return false;
        }
        return Objects.equals(mPseudorandomFunctions, ((IkeSaProposal) o).mPseudorandomFunctions);
    }

    public static final class Builder {
        private final List<Pair<Integer, Integer>> mEncryptionAlgos =
                new ArrayList<Pair<Integer, Integer>>();
        private final List<Integer> mIntegrityAlgos = new ArrayList<Integer>();
        private final List<Integer> mDhGroups = new ArrayList<Integer>();
        private final List<Integer> mPrfs = new ArrayList<Integer>();

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
            if (dhGroup == DH_GROUP_NONE) {
                throw new IllegalArgumentException("IKE SA proposal cannot use DH_GROUP_NONE");
            }
            Integer boxed = Integer.valueOf(dhGroup);
            if (!mDhGroups.contains(boxed)) {
                mDhGroups.add(boxed);
            }
            return this;
        }

        public Builder addPseudorandomFunction(int algorithm) {
            Integer boxed = Integer.valueOf(algorithm);
            if (!mPrfs.contains(boxed)) {
                mPrfs.add(boxed);
            }
            return this;
        }

        public IkeSaProposal build() {
            if (mEncryptionAlgos.isEmpty()) {
                throw new IllegalArgumentException("Encryption algorithm must be specified");
            }
            if (mPrfs.isEmpty()) {
                throw new IllegalArgumentException("PRF must be specified for IKE SA");
            }
            if (mDhGroups.isEmpty()) {
                throw new IllegalArgumentException("DH group must be specified for IKE SA");
            }
            return new IkeSaProposal(mEncryptionAlgos, mPrfs, mIntegrityAlgos, mDhGroups);
        }
    }
}
