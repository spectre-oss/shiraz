// SPDX-License-Identifier: GPL-3.0-only
package android.security.keystore;

import java.security.spec.KeySpec;
import java.util.Date;

public class KeyInfo implements KeySpec {
    private final String mKeystoreAlias;
    private final boolean mInsideSecureHardware;
    private final int mOrigin;
    private final int mKeySize;
    private final Date mKeyValidityStart;
    private final Date mKeyValidityForOriginationEnd;
    private final Date mKeyValidityForConsumptionEnd;
    private final int mPurposes;
    private final String[] mEncryptionPaddings;
    private final String[] mSignaturePaddings;
    private final String[] mDigests;
    private final String[] mBlockModes;
    private final boolean mUserAuthenticationRequired;
    private final int mUserAuthenticationValidityDurationSeconds;
    private final int mUserAuthenticationType;
    private final boolean mUserAuthenticationRequirementEnforcedBySecureHardware;
    private final boolean mUserAuthenticationValidWhileOnBody;
    private final boolean mTrustedUserPresenceRequired;
    private final boolean mInvalidatedByBiometricEnrollment;
    private final boolean mUserConfirmationRequired;
    private final int mSecurityLevel;
    private final int mRemainingUsageCount;

    public KeyInfo(String keystoreKeyAlias, boolean insideSecureHardware, int origin,
            int keySize, Date keyValidityStart, Date keyValidityForOriginationEnd,
            Date keyValidityForConsumptionEnd, int purposes, String[] encryptionPaddings,
            String[] signaturePaddings, String[] digests, String[] blockModes,
            boolean userAuthenticationRequired, int userAuthenticationValidityDurationSeconds,
            int userAuthenticationType,
            boolean userAuthenticationRequirementEnforcedBySecureHardware,
            boolean userAuthenticationValidWhileOnBody, boolean trustedUserPresenceRequired,
            boolean invalidatedByBiometricEnrollment, boolean userConfirmationRequired,
            int securityLevel, int remainingUsageCount) {
        mKeystoreAlias = keystoreKeyAlias;
        mInsideSecureHardware = insideSecureHardware;
        mOrigin = origin;
        mKeySize = keySize;
        mKeyValidityStart = cloneDate(keyValidityStart);
        mKeyValidityForOriginationEnd = cloneDate(keyValidityForOriginationEnd);
        mKeyValidityForConsumptionEnd = cloneDate(keyValidityForConsumptionEnd);
        mPurposes = purposes;
        mEncryptionPaddings = cloneArray(encryptionPaddings);
        mSignaturePaddings = cloneArray(signaturePaddings);
        mDigests = cloneArray(digests);
        mBlockModes = cloneArray(blockModes);
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticationValidityDurationSeconds = userAuthenticationValidityDurationSeconds;
        mUserAuthenticationType = userAuthenticationType;
        mUserAuthenticationRequirementEnforcedBySecureHardware =
                userAuthenticationRequirementEnforcedBySecureHardware;
        mUserAuthenticationValidWhileOnBody = userAuthenticationValidWhileOnBody;
        mTrustedUserPresenceRequired = trustedUserPresenceRequired;
        mInvalidatedByBiometricEnrollment = invalidatedByBiometricEnrollment;
        mUserConfirmationRequired = userConfirmationRequired;
        mSecurityLevel = securityLevel;
        mRemainingUsageCount = remainingUsageCount;
    }

    public String getKeystoreAlias() {
        return mKeystoreAlias;
    }

    @Deprecated
    public boolean isInsideSecureHardware() {
        return mInsideSecureHardware;
    }

    public int getOrigin() {
        return mOrigin;
    }

    public int getKeySize() {
        return mKeySize;
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

    public String[] getBlockModes() {
        return cloneArray(mBlockModes);
    }

    public String[] getEncryptionPaddings() {
        return cloneArray(mEncryptionPaddings);
    }

    public String[] getSignaturePaddings() {
        return cloneArray(mSignaturePaddings);
    }

    public String[] getDigests() {
        return cloneArray(mDigests);
    }

    public boolean isUserAuthenticationRequired() {
        return mUserAuthenticationRequired;
    }

    public boolean isUserConfirmationRequired() {
        return mUserConfirmationRequired;
    }

    public int getUserAuthenticationValidityDurationSeconds() {
        return mUserAuthenticationValidityDurationSeconds;
    }

    public int getUserAuthenticationType() {
        return mUserAuthenticationType;
    }

    public boolean isUserAuthenticationRequirementEnforcedBySecureHardware() {
        return mUserAuthenticationRequirementEnforcedBySecureHardware;
    }

    public boolean isUserAuthenticationValidWhileOnBody() {
        return mUserAuthenticationValidWhileOnBody;
    }

    public boolean isInvalidatedByBiometricEnrollment() {
        return mInvalidatedByBiometricEnrollment;
    }

    public boolean isTrustedUserPresenceRequired() {
        return mTrustedUserPresenceRequired;
    }

    public int getSecurityLevel() {
        return mSecurityLevel;
    }

    public int getRemainingUsageCount() {
        return mRemainingUsageCount;
    }

    private static Date cloneDate(Date d) {
        return d != null ? new Date(d.getTime()) : null;
    }

    private static String[] cloneArray(String[] a) {
        return a != null ? a.clone() : new String[0];
    }
}
