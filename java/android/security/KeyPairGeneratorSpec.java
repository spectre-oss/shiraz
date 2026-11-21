// SPDX-License-Identifier: GPL-3.0-only
package android.security;

import android.content.Context;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Date;
import java.util.Objects;
import javax.security.auth.x500.X500Principal;

@Deprecated
public final class KeyPairGeneratorSpec implements AlgorithmParameterSpec {
    private final Context mContext;
    private final String mKeystoreAlias;
    private final String mKeyType;
    private final int mKeySize;
    private final AlgorithmParameterSpec mSpec;
    private final X500Principal mSubjectDN;
    private final BigInteger mSerialNumber;
    private final Date mStartDate;
    private final Date mEndDate;
    private final boolean mEncryptionRequired;

    KeyPairGeneratorSpec(Context context, String keyStoreAlias, String keyType, int keySize,
            AlgorithmParameterSpec spec, X500Principal subjectDN, BigInteger serialNumber,
            Date startDate, Date endDate, boolean encryptionRequired) {
        mContext = context;
        mKeystoreAlias = keyStoreAlias;
        mKeyType = keyType;
        mKeySize = keySize;
        mSpec = spec;
        mSubjectDN = subjectDN;
        mSerialNumber = serialNumber;
        mStartDate = startDate != null ? new Date(startDate.getTime()) : null;
        mEndDate = endDate != null ? new Date(endDate.getTime()) : null;
        mEncryptionRequired = encryptionRequired;
    }

    public Context getContext() {
        return mContext;
    }

    public String getKeystoreAlias() {
        return mKeystoreAlias;
    }

    public String getKeyType() {
        return mKeyType;
    }

    public int getKeySize() {
        return mKeySize;
    }

    public AlgorithmParameterSpec getAlgorithmParameterSpec() {
        return mSpec;
    }

    public X500Principal getSubjectDN() {
        return mSubjectDN;
    }

    public BigInteger getSerialNumber() {
        return mSerialNumber;
    }

    public Date getStartDate() {
        return mStartDate != null ? new Date(mStartDate.getTime()) : null;
    }

    public Date getEndDate() {
        return mEndDate != null ? new Date(mEndDate.getTime()) : null;
    }

    @Deprecated
    public boolean isEncryptionRequired() {
        return mEncryptionRequired;
    }

    @Deprecated
    public static final class Builder {
        private final Context mContext;
        private String mKeystoreAlias;
        private String mKeyType = "RSA";
        private int mKeySize = -1;
        private AlgorithmParameterSpec mSpec;
        private X500Principal mSubjectDN;
        private BigInteger mSerialNumber;
        private Date mStartDate;
        private Date mEndDate;
        private boolean mEncryptionRequired;

        public Builder(Context context) {
            mContext = Objects.requireNonNull(context, "context == null");
        }

        public Builder setAlias(String alias) {
            if (alias == null || alias.isEmpty()) {
                throw new IllegalArgumentException("alias must not be empty");
            }
            mKeystoreAlias = alias;
            return this;
        }

        public Builder setKeyType(String keyType) throws NoSuchAlgorithmException {
            if (!KeyChain.isKeyAlgorithmSupported(keyType)) {
                throw new NoSuchAlgorithmException("Unsupported key type: " + keyType);
            }
            mKeyType = keyType;
            return this;
        }

        public Builder setKeySize(int keySize) {
            if (keySize < 0) {
                throw new IllegalArgumentException("keySize < 0");
            }
            mKeySize = keySize;
            return this;
        }

        public Builder setAlgorithmParameterSpec(AlgorithmParameterSpec spec) {
            mSpec = Objects.requireNonNull(spec, "spec == null");
            return this;
        }

        public Builder setSubject(X500Principal subject) {
            mSubjectDN = Objects.requireNonNull(subject, "subject == null");
            return this;
        }

        public Builder setSerialNumber(BigInteger serialNumber) {
            mSerialNumber = Objects.requireNonNull(serialNumber, "serialNumber == null");
            return this;
        }

        public Builder setStartDate(Date startDate) {
            mStartDate = Objects.requireNonNull(startDate, "startDate == null");
            return this;
        }

        public Builder setEndDate(Date endDate) {
            mEndDate = Objects.requireNonNull(endDate, "endDate == null");
            return this;
        }

        @Deprecated
        public Builder setEncryptionRequired() {
            mEncryptionRequired = true;
            return this;
        }

        public KeyPairGeneratorSpec build() {
            if (mKeystoreAlias == null || mSubjectDN == null || mSerialNumber == null
                    || mStartDate == null || mEndDate == null) {
                throw new IllegalArgumentException(
                        "alias, subject, serialNumber, startDate, and endDate must be set");
            }
            if (mEndDate.before(mStartDate)) {
                throw new IllegalArgumentException("endDate must be after startDate");
            }
            return new KeyPairGeneratorSpec(mContext, mKeystoreAlias, mKeyType, mKeySize,
                    mSpec, mSubjectDN, mSerialNumber, mStartDate, mEndDate, mEncryptionRequired);
        }
    }
}
