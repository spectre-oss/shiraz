// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import android.os.Bundle;
import java.io.IOException;

public final class NfcBarcode extends BasicTagTechnology {
    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_KOVIO = 1;

    /** @hide */
    public static final String EXTRA_BARCODE_TYPE = "barcodetype";

    private final int mType;

    private NfcBarcode(Tag tag) {
        super(tag, Tag.TECH_NFC_BARCODE);
        Bundle extras = tag.getTechExtras(Tag.TECH_NFC_BARCODE);
        if (extras != null) {
            mType = extras.getInt(EXTRA_BARCODE_TYPE, TYPE_KOVIO);
        } else {
            mType = TYPE_KOVIO;
        }
    }

    public static NfcBarcode get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_NFC_BARCODE)) {
            return null;
        }
        return new NfcBarcode(tag);
    }

    public int getType() {
        return mType;
    }

    public byte[] getBarcode() {
        if (mType == TYPE_KOVIO) {
            return mTag.getId();
        }
        return null;
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
