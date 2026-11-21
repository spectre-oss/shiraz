// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import android.os.Bundle;
import java.io.IOException;

public final class Ndef extends BasicTagTechnology {
    public static final String NFC_FORUM_TYPE_1 = "org.nfcforum.ndef.type1";
    public static final String NFC_FORUM_TYPE_2 = "org.nfcforum.ndef.type2";
    public static final String NFC_FORUM_TYPE_3 = "org.nfcforum.ndef.type3";
    public static final String NFC_FORUM_TYPE_4 = "org.nfcforum.ndef.type4";
    public static final String MIFARE_CLASSIC = "com.nxp.ndef.mifareclassic";

    /** @hide */
    public static final String EXTRA_NDEF_MSG = "ndefmsg";
    /** @hide */
    public static final String EXTRA_NDEF_MAXLENGTH = "ndefmaxlength";
    /** @hide */
    public static final String EXTRA_NDEF_CARDSTATE = "ndefcardstate";
    /** @hide */
    public static final String EXTRA_NDEF_TYPE = "ndeftype";

    private static final int NDEF_MODE_READ_ONLY = 1;
    private static final int NDEF_MODE_READ_WRITE = 2;

    private final int mMaxNdefSize;
    private final int mCardState;
    private final NdefMessage mNdefMsg;
    private final int mNdefType;

    private Ndef(Tag tag) {
        super(tag, Tag.TECH_NDEF);
        Bundle extras = tag.getTechExtras(Tag.TECH_NDEF);
        if (extras != null) {
            mMaxNdefSize = extras.getInt(EXTRA_NDEF_MAXLENGTH, 0);
            mCardState = extras.getInt(EXTRA_NDEF_CARDSTATE, NDEF_MODE_READ_ONLY);
            mNdefMsg = extras.getParcelable(EXTRA_NDEF_MSG);
            mNdefType = extras.getInt(EXTRA_NDEF_TYPE, -1);
        } else {
            mMaxNdefSize = 0;
            mCardState = NDEF_MODE_READ_ONLY;
            mNdefMsg = null;
            mNdefType = -1;
        }
    }

    public static Ndef get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_NDEF)) {
            return null;
        }
        return new Ndef(tag);
    }

    public NdefMessage getCachedNdefMessage() {
        return mNdefMsg;
    }

    public String getType() {
        switch (mNdefType) {
            case 1:
                return NFC_FORUM_TYPE_1;
            case 2:
                return NFC_FORUM_TYPE_2;
            case 3:
                return NFC_FORUM_TYPE_3;
            case 4:
                return NFC_FORUM_TYPE_4;
            case 101:
                return MIFARE_CLASSIC;
            default:
                return "android.ndef.unknown";
        }
    }

    public int getMaxSize() {
        return mMaxNdefSize;
    }

    public boolean isWritable() {
        return mCardState == NDEF_MODE_READ_WRITE;
    }

    public NdefMessage getNdefMessage() throws FormatException, IOException {
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public void writeNdefMessage(NdefMessage msg) throws FormatException, IOException {
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public boolean canMakeReadOnly() {
        return mCardState == NDEF_MODE_READ_WRITE && (mNdefType == 1 || mNdefType == 2);
    }

    public boolean makeReadOnly() throws IOException {
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
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
