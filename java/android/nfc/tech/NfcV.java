// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import android.os.Bundle;
import java.io.IOException;

public final class NfcV extends BasicTagTechnology {
    /** @hide */
    public static final String EXTRA_RESP_FLAGS = "respflags";
    /** @hide */
    public static final String EXTRA_DSFID = "dsfid";

    private final byte mRespFlags;
    private final byte mDsfId;

    private NfcV(Tag tag) {
        super(tag, Tag.TECH_NFC_V);
        Bundle extras = tag.getTechExtras(Tag.TECH_NFC_V);
        if (extras != null) {
            mRespFlags = extras.getByte(EXTRA_RESP_FLAGS, (byte) 0);
            mDsfId = extras.getByte(EXTRA_DSFID, (byte) 0);
        } else {
            mRespFlags = 0;
            mDsfId = 0;
        }
    }

    public static NfcV get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_NFC_V)) {
            return null;
        }
        return new NfcV(tag);
    }

    public byte getResponseFlags() {
        return mRespFlags;
    }

    public byte getDsfId() {
        return mDsfId;
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
