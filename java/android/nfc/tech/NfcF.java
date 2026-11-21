// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import android.os.Bundle;
import java.io.IOException;

public final class NfcF extends BasicTagTechnology {
    /** @hide */
    public static final String EXTRA_SC = "systemcode";
    /** @hide */
    public static final String EXTRA_PMM = "pmm";

    private final byte[] mSystemCode;
    private final byte[] mManufacturer;
    private int mTimeout = 618;

    private NfcF(Tag tag) {
        super(tag, Tag.TECH_NFC_F);
        Bundle extras = tag.getTechExtras(Tag.TECH_NFC_F);
        if (extras != null) {
            byte[] sc = extras.getByteArray(EXTRA_SC);
            byte[] pmm = extras.getByteArray(EXTRA_PMM);
            mSystemCode = sc != null ? sc.clone() : new byte[2];
            mManufacturer = pmm != null ? pmm.clone() : new byte[8];
        } else {
            mSystemCode = new byte[2];
            mManufacturer = new byte[8];
        }
    }

    public static NfcF get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_NFC_F)) {
            return null;
        }
        return new NfcF(tag);
    }

    public byte[] getSystemCode() {
        return mSystemCode.clone();
    }

    public byte[] getManufacturer() {
        return mManufacturer.clone();
    }

    public byte[] transceive(byte[] data) throws IOException {
        return transceiveInternal(data);
    }

    public int getMaxTransceiveLength() {
        return 252;
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
