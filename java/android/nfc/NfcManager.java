// SPDX-License-Identifier: GPL-3.0-only
package android.nfc;

import android.content.Context;

/** Service facade on hosts without an NFC controller. */
public final class NfcManager {
    public NfcManager(Context context) {
        if (context == null) {
            throw new NullPointerException("context");
        }
    }

    public NfcAdapter getDefaultAdapter() {
        return null;
    }
}
