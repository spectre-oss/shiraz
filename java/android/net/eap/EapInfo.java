// SPDX-License-Identifier: GPL-3.0-only
package android.net.eap;

public abstract class EapInfo {
    private final int mEapMethodType;

    EapInfo(int eapMethodType) {
        this.mEapMethodType = eapMethodType;
    }

    public final int getEapMethodType() {
        return mEapMethodType;
    }
}
