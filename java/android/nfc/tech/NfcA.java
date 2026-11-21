// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import android.os.Bundle;
import java.io.IOException;

public final class NfcA extends BasicTagTechnology {
    /** @hide */
    public static final String EXTRA_SAK = "sak";
    /** @hide */
    public static final String EXTRA_ATQA = "atqa";

    private final short mSak;
    private final byte[] mAtqa;
    private int mTimeout = 618;

    private NfcA(Tag tag) {
        super(tag, Tag.TECH_NFC_A);
        short sak = 0;
        if (tag.hasTech(Tag.TECH_MIFARE_CLASSIC)) {
            Bundle mcExtras = tag.getTechExtras(Tag.TECH_MIFARE_CLASSIC);
            if (mcExtras != null) {
                sak = mcExtras.getShort(EXTRA_SAK, (short) 0);
            }
        }
        Bundle extras = tag.getTechExtras(Tag.TECH_NFC_A);
        if (extras != null) {
            sak |= extras.getShort(EXTRA_SAK, (short) 0);
            byte[] atqa = extras.getByteArray(EXTRA_ATQA);
            mAtqa = atqa != null ? atqa.clone() : new byte[2];
        } else {
            mAtqa = new byte[2];
        }
        mSak = sak;
    }

    public static NfcA get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_NFC_A)) {
            return null;
        }
        return new NfcA(tag);
    }

    public byte[] getAtqa() {
        return mAtqa.clone();
    }

    public short getSak() {
        return mSak;
    }

    public byte[] transceive(byte[] data) throws IOException {
        return transceiveInternal(data);
    }

    public int getMaxTransceiveLength() {
        return 253;
    }

    public void setTimeout(int timeout) {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout must be >= 0");
        }
        mTimeout = timeout;
    }

    public int getTimeout() {
        return mTimeout;
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
