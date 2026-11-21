// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import android.os.Bundle;
import java.io.IOException;

public final class MifareUltralight extends BasicTagTechnology {
    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_ULTRALIGHT = 1;
    public static final int TYPE_ULTRALIGHT_C = 2;

    public static final int PAGE_SIZE = 4;

    /** @hide */
    public static final String EXTRA_IS_UL_C = "isulc";

    private static final int MAX_PAGE_COUNT = 256;

    private final int mType;
    private int mTimeout = 618;

    private MifareUltralight(Tag tag) {
        super(tag, Tag.TECH_MIFARE_ULTRALIGHT);
        NfcA a = NfcA.get(tag);
        int type = TYPE_UNKNOWN;
        byte[] id = tag.getId();
        if (a != null && a.getSak() == 0x00 && id.length > 0 && id[0] == 0x04) {
            Bundle extras = tag.getTechExtras(Tag.TECH_MIFARE_ULTRALIGHT);
            if (extras != null && extras.getBoolean(EXTRA_IS_UL_C, false)) {
                type = TYPE_ULTRALIGHT_C;
            } else {
                type = TYPE_ULTRALIGHT;
            }
        }
        mType = type;
    }

    public static MifareUltralight get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_MIFARE_ULTRALIGHT)) {
            return null;
        }
        return new MifareUltralight(tag);
    }

    public int getType() {
        return mType;
    }

    private static void validatePageIndex(int pageIndex) {
        if (pageIndex < 0 || pageIndex >= MAX_PAGE_COUNT) {
            throw new IndexOutOfBoundsException("page out of bounds: " + pageIndex);
        }
    }

    public byte[] readPages(int pageOffset) throws IOException {
        validatePageIndex(pageOffset);
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public void writePage(int pageOffset, byte[] data) throws IOException {
        validatePageIndex(pageOffset);
        if (data == null) {
            throw new NullPointerException("data cannot be null");
        }
        if (data.length != PAGE_SIZE) {
            throw new IllegalArgumentException("must write 4-bytes");
        }
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
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
