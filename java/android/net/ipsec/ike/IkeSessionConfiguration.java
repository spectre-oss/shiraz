// SPDX-License-Identifier: GPL-3.0-only
package android.net.ipsec.ike;

import android.net.eap.EapInfo;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class IkeSessionConfiguration {
    public static final int EXTENSION_TYPE_FRAGMENTATION = 1;
    public static final int EXTENSION_TYPE_MOBIKE = 2;

    private final String mRemoteApplicationVersion;
    private final IkeSessionConnectionInfo mIkeConnInfo;
    private final List<byte[]> mRemoteVendorIds;
    private final Set<Integer> mEnabledExtensions;
    private final EapInfo mEapInfo;

    IkeSessionConfiguration(
            IkeSessionConnectionInfo ikeConnInfo,
            List<byte[]> remoteVendorIds,
            Set<Integer> enabledExtensions,
            String remoteAppVersion,
            EapInfo eapInfo) {
        this.mIkeConnInfo = ikeConnInfo;
        List<byte[]> copiedIds = new ArrayList<byte[]>(remoteVendorIds.size());
        for (int i = 0; i < remoteVendorIds.size(); i++) {
            copiedIds.add(remoteVendorIds.get(i).clone());
        }
        this.mRemoteVendorIds = Collections.unmodifiableList(copiedIds);
        this.mEnabledExtensions =
                Collections.unmodifiableSet(new LinkedHashSet<Integer>(enabledExtensions));
        this.mRemoteApplicationVersion = remoteAppVersion == null ? "" : remoteAppVersion;
        this.mEapInfo = eapInfo;
    }

    public String getRemoteApplicationVersion() {
        return mRemoteApplicationVersion;
    }

    public List<byte[]> getRemoteVendorIds() {
        return mRemoteVendorIds;
    }

    public boolean isIkeExtensionEnabled(int extensionType) {
        return mEnabledExtensions.contains(Integer.valueOf(extensionType));
    }

    public IkeSessionConnectionInfo getIkeSessionConnectionInfo() {
        return mIkeConnInfo;
    }

    public EapInfo getEapInfo() {
        return mEapInfo;
    }

    public static final class Builder {
        private final IkeSessionConnectionInfo mIkeConnInfo;
        private final List<byte[]> mRemoteVendorIds = new ArrayList<byte[]>();
        private final Set<Integer> mEnabledExtensions = new LinkedHashSet<Integer>();
        private String mRemoteApplicationVersion = "";
        private EapInfo mEapInfo;

        public Builder(IkeSessionConnectionInfo ikeConnInfo) {
            this.mIkeConnInfo = Objects.requireNonNull(ikeConnInfo, "ikeConnInfo was null");
        }

        public Builder addRemoteVendorId(byte[] remoteVendorId) {
            Objects.requireNonNull(remoteVendorId, "remoteVendorId was null");
            mRemoteVendorIds.add(remoteVendorId.clone());
            return this;
        }

        public Builder clearRemoteVendorIds() {
            mRemoteVendorIds.clear();
            return this;
        }

        public Builder setRemoteApplicationVersion(String remoteApplicationVersion) {
            this.mRemoteApplicationVersion =
                    Objects.requireNonNull(
                            remoteApplicationVersion, "remoteApplicationVersion was null");
            return this;
        }

        public Builder clearRemoteApplicationVersion() {
            this.mRemoteApplicationVersion = "";
            return this;
        }

        public Builder addIkeExtension(int extensionType) {
            if (extensionType != EXTENSION_TYPE_FRAGMENTATION
                    && extensionType != EXTENSION_TYPE_MOBIKE) {
                throw new IllegalArgumentException("Invalid extensionType: " + extensionType);
            }
            mEnabledExtensions.add(Integer.valueOf(extensionType));
            return this;
        }

        public Builder clearIkeExtensions() {
            mEnabledExtensions.clear();
            return this;
        }

        public Builder setEapInfo(EapInfo eapInfo) {
            this.mEapInfo = eapInfo;
            return this;
        }

        public IkeSessionConfiguration build() {
            return new IkeSessionConfiguration(
                    mIkeConnInfo,
                    mRemoteVendorIds,
                    mEnabledExtensions,
                    mRemoteApplicationVersion,
                    mEapInfo);
        }
    }
}
