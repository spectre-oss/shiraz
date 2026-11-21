// SPDX-License-Identifier: GPL-3.0-only
package android.net.eap;

public final class EapAkaInfo extends EapInfo {
    private final byte[] mReauthId;

    EapAkaInfo(byte[] reauthId) {
        super(EapSessionConfig.EapMethodConfig.EAP_TYPE_AKA);
        this.mReauthId = reauthId == null ? null : reauthId.clone();
    }

    public byte[] getReauthId() {
        return mReauthId == null ? null : mReauthId.clone();
    }

    public static final class Builder {
        private byte[] mReauthId;

        public Builder() {}

        public Builder setReauthId(byte[] reauthId) {
            this.mReauthId = reauthId == null ? null : reauthId.clone();
            return this;
        }

        public EapAkaInfo build() {
            return new EapAkaInfo(mReauthId);
        }
    }
}
