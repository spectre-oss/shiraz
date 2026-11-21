// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import android.os.Bundle;
import java.io.IOException;

public final class NfcB extends BasicTagTechnology {
    /** @hide */
    public static final String EXTRA_APPDATA = "appdata";
    /** @hide */
    public static final String EXTRA_PROTINFO = "protinfo";

    private final byte[] mAppData;
    private final byte[] mProtInfo;

    private NfcB(Tag tag) {
        super(tag, Tag.TECH_NFC_B);
        Bundle extras = tag.getTechExtras(Tag.TECH_NFC_B);
        if (extras != null) {
            byte[] app = extras.getByteArray(EXTRA_APPDATA);
            byte[] prot = extras.getByteArray(EXTRA_PROTINFO);
            mAppData = app != null ? app.clone() : new byte[4];
            mProtInfo = prot != null ? prot.clone() : new byte[3];
        } else {
            mAppData = new byte[4];
            mProtInfo = new byte[3];
        }
    }

    public static NfcB get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_NFC_B)) {
            return null;
        }
        return new NfcB(tag);
    }

    public byte[] getApplicationData() {
        return mAppData.clone();
    }

    public byte[] getProtocolInfo() {
        return mProtInfo.clone();
    }

    public byte[] transceive(byte[] data) throws IOException {
        return transceiveInternal(data);
    }

    public int getMaxTransceiveLength() {
        return 253;
    }

    @Override
    public boolean isConnected() {
        return super.isConnected();
    }

    @Override
    public Tag getTag() {
        return super.getTag();
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    @Override
    public void connect() throws IOException {
        super.connect();
    }
}
