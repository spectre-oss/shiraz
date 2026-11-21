// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.Tag;
import java.io.IOException;

public final class NdefFormatable extends BasicTagTechnology {
    private NdefFormatable(Tag tag) {
        super(tag, Tag.TECH_NDEF_FORMATABLE);
    }

    public static NdefFormatable get(Tag tag) {
        if (tag == null || !tag.hasTech(Tag.TECH_NDEF_FORMATABLE)) {
            return null;
        }
        return new NdefFormatable(tag);
    }

    public void format(NdefMessage firstMessage) throws FormatException, IOException {
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }

    public void formatReadOnly(NdefMessage firstMessage) throws FormatException, IOException {
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
