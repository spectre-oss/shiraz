// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import android.os.Bundle;
import java.io.IOException;

public final class IsoDep extends BasicTagTechnology {
    /** @hide */
    public static final String EXTRA_HI_LAYER_RESP = "hiresp";
    /** @hide */
    public static final String EXTRA_HIST_BYTES = "histbytes";

    private final byte[] mHiLayerResponse;
    private final byte[] mHistBytes;
    private int mTimeout = 309;

    private IsoDep(Tag tag) {
        super(tag, Tag.TECH_ISO_DEP);
        Bundle extras = tag.getTechExtras(Tag.TECH_ISO_DEP);
        if (extras != null) {
            byte[] hi = extras.getByteArray(EXTRA_HI_LAYER_RESP);
            byte[] hist = extras.getByteArray(EXTRA_HIST_BYTES);
            mHiLayerResponse = hi != null ? hi.clone() : null;
            mHistBytes = hist != null ? hist.clone() : null;
        } else {
            mHiLayerResponse = null;
            mHistBytes = null;
        }
    }

    public static IsoDep get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_ISO_DEP)) {
            return null;
        }
        return new IsoDep(tag);
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

    public byte[] getHistoricalBytes() {
        return mHistBytes != null ? mHistBytes.clone() : null;
    }

    public byte[] getHiLayerResponse() {
        return mHiLayerResponse != null ? mHiLayerResponse.clone() : null;
    }

    public byte[] transceive(byte[] data) throws IOException {
        return transceiveInternal(data);
    }

    public int getMaxTransceiveLength() {
        return 65279;
    }

    public boolean isExtendedLengthApduSupported() {
        return true;
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
