// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

import java.security.cert.X509Certificate;
import java.util.Objects;

public class AccessControlProfile {
    final AccessControlProfileId mAccessControlProfileId;
    final X509Certificate mReaderCertificate;
    final boolean mUserAuthenticationRequired;
    final long mUserAuthenticationTimeout;

    AccessControlProfile(AccessControlProfileId id, X509Certificate readerCertificate,
            boolean userAuthenticationRequired, long userAuthenticationTimeout) {
        mAccessControlProfileId = id;
        mReaderCertificate = readerCertificate;
        mUserAuthenticationRequired = userAuthenticationRequired;
        mUserAuthenticationTimeout = userAuthenticationTimeout;
    }

    public static final class Builder {
        private final AccessControlProfileId mId;
        private X509Certificate mReaderCertificate;
        private boolean mUserAuthenticationRequired = true;
        private long mUserAuthenticationTimeout = 0;

        public Builder(AccessControlProfileId accessControlProfileId) {
            mId = Objects.requireNonNull(accessControlProfileId, "accessControlProfileId == null");
        }

        public Builder setUserAuthenticationRequired(boolean userAuthenticationRequired) {
            mUserAuthenticationRequired = userAuthenticationRequired;
            return this;
        }

        public Builder setUserAuthenticationTimeout(long userAuthenticationTimeoutMillis) {
            mUserAuthenticationTimeout = userAuthenticationTimeoutMillis;
            return this;
        }

        public Builder setReaderCertificate(X509Certificate readerCertificate) {
            mReaderCertificate = readerCertificate;
            return this;
        }

        public AccessControlProfile build() {
            return new AccessControlProfile(mId, mReaderCertificate,
                    mUserAuthenticationRequired, mUserAuthenticationTimeout);
        }
    }
}
