// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public abstract class SaProposal {
    public static final int ENCRYPTION_ALGORITHM_3DES = 3;
    public static final int ENCRYPTION_ALGORITHM_AES_CBC = 12;
    public static final int ENCRYPTION_ALGORITHM_AES_CTR = 13;
    public static final int ENCRYPTION_ALGORITHM_AES_GCM_8 = 18;
    public static final int ENCRYPTION_ALGORITHM_AES_GCM_12 = 19;
    public static final int ENCRYPTION_ALGORITHM_AES_GCM_16 = 20;
    public static final int ENCRYPTION_ALGORITHM_CHACHA20_POLY1305 = 28;

    public static final int KEY_LEN_UNUSED = 0;
    public static final int KEY_LEN_AES_128 = 128;
    public static final int KEY_LEN_AES_192 = 192;
    public static final int KEY_LEN_AES_256 = 256;

    public static final int PSEUDORANDOM_FUNCTION_HMAC_SHA1 = 2;
    public static final int PSEUDORANDOM_FUNCTION_AES128_XCBC = 4;
    public static final int PSEUDORANDOM_FUNCTION_SHA2_256 = 5;
    public static final int PSEUDORANDOM_FUNCTION_SHA2_384 = 6;
    public static final int PSEUDORANDOM_FUNCTION_SHA2_512 = 7;
    public static final int PSEUDORANDOM_FUNCTION_AES128_CMAC = 8;

    public static final int INTEGRITY_ALGORITHM_NONE = 0;
    public static final int INTEGRITY_ALGORITHM_HMAC_SHA1_96 = 2;
    public static final int INTEGRITY_ALGORITHM_AES_XCBC_96 = 5;
    public static final int INTEGRITY_ALGORITHM_AES_CMAC_96 = 8;
    public static final int INTEGRITY_ALGORITHM_HMAC_SHA2_256_128 = 12;
    public static final int INTEGRITY_ALGORITHM_HMAC_SHA2_384_192 = 13;
    public static final int INTEGRITY_ALGORITHM_HMAC_SHA2_512_256 = 14;

    public static final int DH_GROUP_NONE = 0;
    public static final int DH_GROUP_1024_BIT_MODP = 2;
    public static final int DH_GROUP_1536_BIT_MODP = 5;
    public static final int DH_GROUP_2048_BIT_MODP = 14;
    public static final int DH_GROUP_3072_BIT_MODP = 15;
    public static final int DH_GROUP_4096_BIT_MODP = 16;
    public static final int DH_GROUP_CURVE_25519 = 31;

    private final List<Pair<Integer, Integer>> mEncryptionAlgorithms;
    private final List<Integer> mIntegrityAlgorithms;
    private final List<Integer> mDhGroups;

    SaProposal(
            List<Pair<Integer, Integer>> encryptionAlgos,
            List<Integer> integrityAlgos,
            List<Integer> dhGroups) {
        this.mEncryptionAlgorithms =
                Collections.unmodifiableList(
                        new ArrayList<Pair<Integer, Integer>>(encryptionAlgos));
        this.mIntegrityAlgorithms =
                Collections.unmodifiableList(new ArrayList<Integer>(integrityAlgos));
        this.mDhGroups = Collections.unmodifiableList(new ArrayList<Integer>(dhGroups));
    }

    public List<Pair<Integer, Integer>> getEncryptionAlgorithms() {
        return mEncryptionAlgorithms;
    }

    public List<Integer> getIntegrityAlgorithms() {
        return mIntegrityAlgorithms;
    }

    public List<Integer> getDhGroups() {
        return mDhGroups;
    }

    public static Set<Integer> getSupportedDhGroups() {
        Set<Integer> set = new LinkedHashSet<Integer>();
        set.add(Integer.valueOf(DH_GROUP_NONE));
        set.add(Integer.valueOf(DH_GROUP_1024_BIT_MODP));
        set.add(Integer.valueOf(DH_GROUP_1536_BIT_MODP));
        set.add(Integer.valueOf(DH_GROUP_2048_BIT_MODP));
        set.add(Integer.valueOf(DH_GROUP_3072_BIT_MODP));
        set.add(Integer.valueOf(DH_GROUP_4096_BIT_MODP));
        set.add(Integer.valueOf(DH_GROUP_CURVE_25519));
        return Collections.unmodifiableSet(set);
    }

    @Override
    public String toString() {
        return "SaProposal{enc="
                + mEncryptionAlgorithms
                + ", int="
                + mIntegrityAlgorithms
                + ", dh="
                + mDhGroups
                + "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEncryptionAlgorithms, mIntegrityAlgorithms, mDhGroups);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SaProposal)) {
            return false;
        }
        SaProposal other = (SaProposal) o;
        return Objects.equals(mEncryptionAlgorithms, other.mEncryptionAlgorithms)
                && Objects.equals(mIntegrityAlgorithms, other.mIntegrityAlgorithms)
                && Objects.equals(mDhGroups, other.mDhGroups);
    }
}
