// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Parcel;
import android.os.Parcelable;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Cryptographic algorithm name, key material, and truncation length for IPsec transforms. */
public final class IpSecAlgorithm implements Parcelable {
    public static final String CRYPT_AES_CBC = "cbc(aes)";
    public static final String CRYPT_AES_CTR = "rfc3686(ctr(aes))";
    public static final String AUTH_HMAC_MD5 = "hmac(md5)";
    public static final String AUTH_HMAC_SHA1 = "hmac(sha1)";
    public static final String AUTH_HMAC_SHA256 = "hmac(sha256)";
    public static final String AUTH_HMAC_SHA384 = "hmac(sha384)";
    public static final String AUTH_HMAC_SHA512 = "hmac(sha512)";
    public static final String AUTH_AES_XCBC = "xcbc(aes)";
    public static final String AUTH_AES_CMAC = "cmac(aes)";
    public static final String AUTH_CRYPT_AES_GCM = "rfc4106(gcm(aes))";
    public static final String AUTH_CRYPT_CHACHA20_POLY1305 = "rfc7539esp(chacha20,poly1305)";

    private static final Set<String> SUPPORTED =
            Collections.unmodifiableSet(
                    new LinkedHashSet<String>(
                            Arrays.asList(
                                    CRYPT_AES_CBC,
                                    CRYPT_AES_CTR,
                                    AUTH_HMAC_MD5,
                                    AUTH_HMAC_SHA1,
                                    AUTH_HMAC_SHA256,
                                    AUTH_HMAC_SHA384,
                                    AUTH_HMAC_SHA512,
                                    AUTH_AES_XCBC,
                                    AUTH_AES_CMAC,
                                    AUTH_CRYPT_AES_GCM,
                                    AUTH_CRYPT_CHACHA20_POLY1305)));

    private final String mName;
    private final byte[] mKey;
    private final int mTruncLenBits;

    public IpSecAlgorithm(String algorithm, byte[] key) {
        this(algorithm, key, defaultTruncLen(algorithm));
    }

    public IpSecAlgorithm(String algorithm, byte[] key, int truncLenBits) {
        Objects.requireNonNull(algorithm, "algorithm");
        Objects.requireNonNull(key, "key");
        if (!SUPPORTED.contains(algorithm)) {
            throw new IllegalArgumentException("Unknown or unsupported algorithm: " + algorithm);
        }
        int keyBits = key.length * 8;
        validateLengths(algorithm, keyBits, truncLenBits);
        this.mName = algorithm;
        this.mKey = key.clone();
        this.mTruncLenBits = truncLenBits;
    }

    private static int defaultTruncLen(String algorithm) {
        if (CRYPT_AES_CBC.equals(algorithm) || CRYPT_AES_CTR.equals(algorithm)) {
            return 0;
        }
        if (AUTH_HMAC_MD5.equals(algorithm)
                || AUTH_HMAC_SHA1.equals(algorithm)
                || AUTH_AES_XCBC.equals(algorithm)
                || AUTH_AES_CMAC.equals(algorithm)) {
            return 96;
        }
        if (AUTH_HMAC_SHA256.equals(algorithm)
                || AUTH_CRYPT_AES_GCM.equals(algorithm)
                || AUTH_CRYPT_CHACHA20_POLY1305.equals(algorithm)) {
            return 128;
        }
        if (AUTH_HMAC_SHA384.equals(algorithm)) {
            return 192;
        }
        if (AUTH_HMAC_SHA512.equals(algorithm)) {
            return 256;
        }
        return 0;
    }

    private static void validateLengths(String alg, int keyBits, int truncBits) {
        if (CRYPT_AES_CBC.equals(alg)) {
            if ((keyBits != 128 && keyBits != 192 && keyBits != 256) || truncBits != 0) {
                throw new IllegalArgumentException("Invalid AES-CBC key/trunc length");
            }
        } else if (CRYPT_AES_CTR.equals(alg)) {
            if ((keyBits != 160 && keyBits != 224 && keyBits != 288) || truncBits != 0) {
                throw new IllegalArgumentException("Invalid AES-CTR key/trunc length");
            }
        } else if (AUTH_CRYPT_AES_GCM.equals(alg)) {
            if ((keyBits != 160 && keyBits != 224 && keyBits != 288)
                    || (truncBits != 64 && truncBits != 96 && truncBits != 128)) {
                throw new IllegalArgumentException("Invalid AES-GCM key/ICV length");
            }
        } else if (AUTH_CRYPT_CHACHA20_POLY1305.equals(alg)) {
            if (keyBits != 288 || truncBits != 128) {
                throw new IllegalArgumentException("Invalid ChaCha20-Poly1305 key/ICV length");
            }
        } else if (AUTH_HMAC_MD5.equals(alg)) {
            if (keyBits != 128 || truncBits < 96 || truncBits > 128) {
                throw new IllegalArgumentException("Invalid HMAC-MD5 key/trunc length");
            }
        } else if (AUTH_HMAC_SHA1.equals(alg)) {
            if (keyBits != 160 || truncBits < 96 || truncBits > 160) {
                throw new IllegalArgumentException("Invalid HMAC-SHA1 key/trunc length");
            }
        } else if (AUTH_HMAC_SHA256.equals(alg)) {
            if (keyBits != 256 || truncBits < 96 || truncBits > 256) {
                throw new IllegalArgumentException("Invalid HMAC-SHA256 key/trunc length");
            }
        } else if (AUTH_HMAC_SHA384.equals(alg)) {
            if (keyBits != 384 || truncBits < 192 || truncBits > 384) {
                throw new IllegalArgumentException("Invalid HMAC-SHA384 key/trunc length");
            }
        } else if (AUTH_HMAC_SHA512.equals(alg)) {
            if (keyBits != 512 || truncBits < 256 || truncBits > 512) {
                throw new IllegalArgumentException("Invalid HMAC-SHA512 key/trunc length");
            }
        } else if (AUTH_AES_XCBC.equals(alg) || AUTH_AES_CMAC.equals(alg)) {
            if (keyBits != 128 || truncBits != 96) {
                throw new IllegalArgumentException("Invalid AES-XCBC/CMAC key/trunc length");
            }
        }
    }

    public String getName() {
        return mName;
    }

    public byte[] getKey() {
        return mKey.clone();
    }

    public int getTruncationLengthBits() {
        return mTruncLenBits;
    }

    public static Set<String> getSupportedAlgorithms() {
        return SUPPORTED;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(mName);
        out.writeByteArray(mKey);
        out.writeInt(mTruncLenBits);
    }

    @Override
    public String toString() {
        return "{mName=" + mName + ", mTruncLenBits=" + mTruncLenBits + "}";
    }

    public static final Parcelable.Creator<IpSecAlgorithm> CREATOR =
            new Parcelable.Creator<IpSecAlgorithm>() {
                @Override
                public IpSecAlgorithm createFromParcel(Parcel in) {
                    String name = in.readString();
                    byte[] key = in.createByteArray();
                    int trunc = in.readInt();
                    return new IpSecAlgorithm(name, key, trunc);
                }

                @Override
                public IpSecAlgorithm[] newArray(int size) {
                    return new IpSecAlgorithm[size];
                }
            };
}
