// SPDX-License-Identifier: GPL-3.0-only
package android.security.identity;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CredentialDataRequest {
    private final Map<String, Collection<String>> mDeviceSignedEntriesToRequest;
    private final Map<String, Collection<String>> mIssuerSignedEntriesToRequest;
    private final boolean mAllowUsingExhaustedKeys;
    private final boolean mAllowUsingExpiredKeys;
    private final boolean mIncrementUseCount;
    private final byte[] mRequestMessage;
    private final byte[] mReaderSignature;

    CredentialDataRequest(Map<String, Collection<String>> deviceEntries,
            Map<String, Collection<String>> issuerEntries,
            boolean allowExhausted, boolean allowExpired, boolean incrementUseCount,
            byte[] requestMessage, byte[] readerSignature) {
        mDeviceSignedEntriesToRequest = deviceEntries != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(deviceEntries))
                : Collections.<String, Collection<String>>emptyMap();
        mIssuerSignedEntriesToRequest = issuerEntries != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(issuerEntries))
                : Collections.<String, Collection<String>>emptyMap();
        mAllowUsingExhaustedKeys = allowExhausted;
        mAllowUsingExpiredKeys = allowExpired;
        mIncrementUseCount = incrementUseCount;
        mRequestMessage = requestMessage != null ? requestMessage.clone() : null;
        mReaderSignature = readerSignature != null ? readerSignature.clone() : null;
    }

    public Map<String, Collection<String>> getDeviceSignedEntriesToRequest() {
        return mDeviceSignedEntriesToRequest;
    }

    public Map<String, Collection<String>> getIssuerSignedEntriesToRequest() {
        return mIssuerSignedEntriesToRequest;
    }

    public boolean isAllowUsingExhaustedKeys() {
        return mAllowUsingExhaustedKeys;
    }

    public boolean isAllowUsingExpiredKeys() {
        return mAllowUsingExpiredKeys;
    }

    public boolean isIncrementUseCount() {
        return mIncrementUseCount;
    }

    public byte[] getRequestMessage() {
        return mRequestMessage != null ? mRequestMessage.clone() : null;
    }

    public byte[] getReaderSignature() {
        return mReaderSignature != null ? mReaderSignature.clone() : null;
    }

    public static final class Builder {
        private Map<String, Collection<String>> mDeviceEntries;
        private Map<String, Collection<String>> mIssuerEntries;
        private boolean mAllowUsingExhaustedKeys = true;
        private boolean mAllowUsingExpiredKeys = false;
        private boolean mIncrementUseCount = true;
        private byte[] mRequestMessage;
        private byte[] mReaderSignature;

        public Builder() {
        }

        public Builder setDeviceSignedEntriesToRequest(
                Map<String, Collection<String>> entriesToRequest) {
            mDeviceEntries = entriesToRequest;
            return this;
        }

        public Builder setIssuerSignedEntriesToRequest(
                Map<String, Collection<String>> entriesToRequest) {
            mIssuerEntries = entriesToRequest;
            return this;
        }

        public Builder setAllowUsingExhaustedKeys(boolean allowUsingExhaustedKeys) {
            mAllowUsingExhaustedKeys = allowUsingExhaustedKeys;
            return this;
        }

        public Builder setAllowUsingExpiredKeys(boolean allowUsingExpiredKeys) {
            mAllowUsingExpiredKeys = allowUsingExpiredKeys;
            return this;
        }

        public Builder setIncrementUseCount(boolean incrementUseCount) {
            mIncrementUseCount = incrementUseCount;
            return this;
        }

        public Builder setRequestMessage(byte[] requestMessage) {
            mRequestMessage = requestMessage != null ? requestMessage.clone() : null;
            return this;
        }

        public Builder setReaderSignature(byte[] readerSignature) {
            mReaderSignature = readerSignature != null ? readerSignature.clone() : null;
            return this;
        }

        public CredentialDataRequest build() {
            return new CredentialDataRequest(mDeviceEntries, mIssuerEntries,
                    mAllowUsingExhaustedKeys, mAllowUsingExpiredKeys, mIncrementUseCount,
                    mRequestMessage, mReaderSignature);
        }
    }
}
