// SPDX-License-Identifier: GPL-3.0-only
package android.nfc.tech;

import android.nfc.Tag;
import java.io.IOException;

abstract class BasicTagTechnology implements TagTechnology {
    final Tag mTag;
    final int mSelectedTechnology;
    boolean mIsConnected;

    BasicTagTechnology(Tag tag, int tech) {
        if (tag == null) {
            throw new NullPointerException("tag");
        }
        mTag = tag;
        mSelectedTechnology = tech;
    }

    @Override
    public Tag getTag() {
        return mTag;
    }

    void checkConnected() {
        if (mTag.getConnectedTechnology() != mSelectedTechnology || !mIsConnected) {
            throw new IllegalStateException("Call connect() first!");
        }
    }

    @Override
    public boolean isConnected() {
        return mIsConnected && mTag.getConnectedTechnology() == mSelectedTechnology;
    }

    @Override
    public void connect() throws IOException {
        if (mTag.getConnectedTechnology() != -1
                && mTag.getConnectedTechnology() != mSelectedTechnology) {
            throw new IllegalStateException("Call close() first on other technology!");
        }
        throw new IOException("No NFC RF controller attached to host");
    }

    @Override
    public void close() throws IOException {
        try {
            mIsConnected = false;
        } finally {
            mTag.setTechnologyDisconnected();
        }
    }

    byte[] transceiveInternal(byte[] data) throws IOException {
        if (data == null) {
            throw new NullPointerException("transceive data is null");
        }
        checkConnected();
        throw new IOException("No NFC RF controller attached to host");
    }
}
