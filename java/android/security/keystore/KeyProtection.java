// SPDX-License-Identifier: GPL-3.0-only
package android.security.keystore;

import java.security.KeyStore;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

public final class KeyProtection implements KeyStore.ProtectionParameter {
    private final Date mKeyValidityStart;
    private final Date mKeyValidityForOriginationEnd;
    private final Date mKeyValidityForConsumptionEnd;
    private final int mPurposes;
    private final String[] mEncryptionPaddings;
    private final String[] mSignaturePaddings;
    private final String[] mDigests;
    private final Set<String> mMgf1Digests;
    private final String[] mBlockModes;
    private final boolean mRandomizedEncryptionRequired;
    private final boolean mUserAuthenticationRequired;
    private final int mUserAuthenticationType;
    private final int mUserAuthenticationValidityDurationSeconds;
    private final boolean mUserPresenceRequired;
    private final boolean mUserAuthenticationValidWhileOnBody;
    private final boolean mInvalidatedByBiometricEnrollment;
    private final boolean mUserConfirmationRequired;
    private final boolean mUnlockedDeviceRequired;
    private final boolean mIsStrongBoxBacked;
    private final int mMaxUsageCount;

    KeyProtection(Date keyValidityStart, Date keyValidityForOriginationEnd,
            Date keyValidityForConsumptionEnd, int purposes, String[] encryptionPaddings,
            String[] signaturePaddings, String[] digests, Set<String> mgf1Digests,
            String[] blockModes, boolean randomizedEncryptionRequired,
            boolean userAuthenticationRequired, int userAuthenticationType,
            int userAuthenticationValidityDurationSeconds, boolean userPresenceRequired,
            boolean userAuthenticationValidWhileOnBody, boolean invalidatedByBiometricEnrollment,
            boolean userConfirmationRequired, boolean unlockedDeviceRequired,
            boolean isStrongBoxBacked, int maxUsageCount) {
        mKeyValidityStart = cloneDate(keyValidityStart);
        mKeyValidityForOriginationEnd = cloneDate(keyValidityForOriginationEnd);
        mKeyValidityForConsumptionEnd = cloneDate(keyValidityForConsumptionEnd);
        mPurposes = purposes;
        mEncryptionPaddings = encryptionPaddings != null ? encryptionPaddings.clone() : new String[0];
        mSignaturePaddings = signaturePaddings != null ? signaturePaddings.clone() : new String[0];
        mDigests = digests != null ? digests.clone() : null;
        mMgf1Digests = mgf1Digests != null
                ? Collections.unmodifiableSet(new HashSet<>(mgf1Digests))
                : Collections.<String>emptySet();
        mBlockModes = blockModes != null ? blockModes.clone() : new String[0];
        mRandomizedEncryptionRequired = randomizedEncryptionRequired;
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticationType = userAuthenticationType;
        mUserAuthenticationValidityDurationSeconds = userAuthenticationValidityDurationSeconds;
        mUserPresenceRequired = userPresenceRequired;
        mUserAuthenticationValidWhileOnBody = userAuthenticationValidWhileOnBody;
        mInvalidatedByBiometricEnrollment = invalidatedByBiometricEnrollment;
        mUserConfirmationRequired = userConfirmationRequired;
        mUnlockedDeviceRequired = unlockedDeviceRequired;
        mIsStrongBoxBacked = isStrongBoxBacked;
        mMaxUsageCount = maxUsageCount;
    }

    public Date getKeyValidityStart() {
        return cloneDate(mKeyValidityStart);
    }

    public Date getKeyValidityForConsumptionEnd() {
        return cloneDate(mKeyValidityForConsumptionEnd);
    }

    public Date getKeyValidityForOriginationEnd() {
        return cloneDate(mKeyValidityForOriginationEnd);
    }

    public int getPurposes() {
        return mPurposes;
    }

    public String[] getEncryptionPaddings() {
        return mEncryptionPaddings.clone();
    }

    public String[] getSignaturePaddings() {
        return mSignaturePaddings.clone();
    }

    public String[] getDigests() {
        if (mDigests == null) {
            throw new IllegalStateException("Digests not specified");
        }
        return mDigests.clone();
    }

    public boolean isDigestsSpecified() {
        return mDigests != null;
    }

    public Set<String> getMgf1Digests() {
        if (mMgf1Digests.isEmpty()) {
            throw new IllegalStateException("Mask generation function (MGF) not specified");
        }
        return new HashSet<>(mMgf1Digests);
    }

    public boolean isMgf1DigestsSpecified() {
        return !mMgf1Digests.isEmpty();
    }

    public String[] getBlockModes() {
        return mBlockModes.clone();
    }

    public boolean isRandomizedEncryptionRequired() {
        return mRandomizedEncryptionRequired;
    }

    public boolean isUserAuthenticationRequired() {
        return mUserAuthenticationRequired;
    }

    public boolean isUserConfirmationRequired() {
        return mUserConfirmationRequired;
    }

    public int getUserAuthenticationType() {
        return mUserAuthenticationType;
    }

    public int getUserAuthenticationValidityDurationSeconds() {
        return mUserAuthenticationValidityDurationSeconds;
    }

    public boolean isUserPresenceRequired() {
        return mUserPresenceRequired;
    }

    public boolean isUserAuthenticationValidWhileOnBody() {
        return mUserAuthenticationValidWhileOnBody;
    }

    public boolean isInvalidatedByBiometricEnrollment() {
        return mInvalidatedByBiometricEnrollment;
    }

    public boolean isUnlockedDeviceRequired() {
        return mUnlockedDeviceRequired;
    }

    public boolean isStrongBoxBacked() {
        return mIsStrongBoxBacked;
    }

    public int getMaxUsageCount() {
        return mMaxUsageCount;
    }

    private static Date cloneDate(Date d) {
        return d != null ? new Date(d.getTime()) : null;
    }

    public static final class Builder {
        private final int mPurposes;
        private Date mKeyValidityStart;
        private Date mKeyValidityForOriginationEnd;
        private Date mKeyValidityForConsumptionEnd;
        private String[] mEncryptionPaddings;
        private String[] mSignaturePaddings;
        private String[] mDigests;
        private Set<String> mMgf1Digests;
        private String[] mBlockModes;
        private boolean mRandomizedEncryptionRequired = true;
        private boolean mUserAuthenticationRequired;
        private int mUserAuthenticationValidityDurationSeconds = 0;
        private int mUserAuthenticationType = KeyProperties.AUTH_BIOMETRIC_STRONG;
        private boolean mUserPresenceRequired;
        private boolean mUserAuthenticationValidWhileOnBody;
        private boolean mInvalidatedByBiometricEnrollment = true;
        private boolean mUserConfirmationRequired;
        private boolean mUnlockedDeviceRequired;
        private boolean mIsStrongBoxBacked;
        private int mMaxUsageCount = KeyProperties.UNRESTRICTED_USAGE_COUNT;

        public Builder(int purposes) {
            mPurposes = purposes;
        }

        public Builder setKeyValidityStart(Date startDate) {
            mKeyValidityStart = cloneDate(startDate);
            return this;
        }

        public Builder setKeyValidityEnd(Date endDate) {
            setKeyValidityForOriginationEnd(endDate);
            setKeyValidityForConsumptionEnd(endDate);
            return this;
        }

        public Builder setKeyValidityForOriginationEnd(Date endDate) {
            mKeyValidityForOriginationEnd = cloneDate(endDate);
            return this;
        }

        public Builder setKeyValidityForConsumptionEnd(Date endDate) {
            mKeyValidityForConsumptionEnd = cloneDate(endDate);
            return this;
        }

        public Builder setEncryptionPaddings(String... paddings) {
            mEncryptionPaddings = paddings != null ? paddings.clone() : null;
            return this;
        }

        public Builder setSignaturePaddings(String... paddings) {
            mSignaturePaddings = paddings != null ? paddings.clone() : null;
            return this;
        }

        public Builder setDigests(String... digests) {
            mDigests = digests != null ? digests.clone() : null;
            return this;
        }

        public Builder setMgf1Digests(String... mgf1Digests) {
            mMgf1Digests = mgf1Digests != null
                    ? new HashSet<>(Arrays.asList(mgf1Digests))
                    : Collections.<String>emptySet();
            return this;
        }

        public Builder setBlockModes(String... blockModes) {
            mBlockModes = blockModes != null ? blockModes.clone() : null;
            return this;
        }

        public Builder setRandomizedEncryptionRequired(boolean required) {
            mRandomizedEncryptionRequired = required;
            return this;
        }

        public Builder setUserAuthenticationRequired(boolean required) {
            mUserAuthenticationRequired = required;
            return this;
        }

        public Builder setUserConfirmationRequired(boolean required) {
            mUserConfirmationRequired = required;
            return this;
        }

        @Deprecated
        public Builder setUserAuthenticationValidityDurationSeconds(int seconds) {
            if (seconds < -1) {
                throw new IllegalArgumentException("seconds must be -1 or larger");
            }
            mUserAuthenticationValidityDurationSeconds = seconds == -1 ? 0 : seconds;
            return this;
        }

        public Builder setUserAuthenticationParameters(int timeout, int type) {
            if (timeout < 0) {
                throw new IllegalArgumentException("timeout must be 0 or larger");
            }
            mUserAuthenticationValidityDurationSeconds = timeout;
            mUserAuthenticationType = type;
            return this;
        }

        public Builder setUserPresenceRequired(boolean required) {
            mUserPresenceRequired = required;
            return this;
        }

        public Builder setUserAuthenticationValidWhileOnBody(boolean remainsValid) {
            mUserAuthenticationValidWhileOnBody = remainsValid;
            return this;
        }

        public Builder setInvalidatedByBiometricEnrollment(boolean invalidateKey) {
            mInvalidatedByBiometricEnrollment = invalidateKey;
            return this;
        }

        public Builder setUnlockedDeviceRequired(boolean unlockedDeviceRequired) {
            mUnlockedDeviceRequired = unlockedDeviceRequired;
            return this;
        }

        public Builder setIsStrongBoxBacked(boolean isStrongBoxBacked) {
            mIsStrongBoxBacked = isStrongBoxBacked;
            return this;
        }

        public Builder setMaxUsageCount(int maxUsageCount) {
            if (maxUsageCount == KeyProperties.UNRESTRICTED_USAGE_COUNT || maxUsageCount > 0) {
                mMaxUsageCount = maxUsageCount;
                return this;
            }
            throw new IllegalArgumentException("maxUsageCount is not valid");
        }

        public KeyProtection build() {
            return new KeyProtection(mKeyValidityStart, mKeyValidityForOriginationEnd,
                    mKeyValidityForConsumptionEnd, mPurposes, mEncryptionPaddings,
                    mSignaturePaddings, mDigests, mMgf1Digests, mBlockModes,
                    mRandomizedEncryptionRequired, mUserAuthenticationRequired,
                    mUserAuthenticationType, mUserAuthenticationValidityDurationSeconds,
                    mUserPresenceRequired, mUserAuthenticationValidWhileOnBody,
                    mInvalidatedByBiometricEnrollment, mUserConfirmationRequired,
                    mUnlockedDeviceRequired, mIsStrongBoxBacked, mMaxUsageCount);
        }
    }
}
