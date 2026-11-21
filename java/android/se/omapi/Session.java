// SPDX-License-Identifier: GPL-3.0-only
package android.se.omapi;

import java.io.IOException;
import java.util.ArrayList;

public final class Session {
    private final Reader mReader;
    private final byte[] mAtr;
    private final ArrayList<Channel> mChannels = new ArrayList<Channel>();
    private volatile boolean mClosed;

    Session(Reader reader, byte[] atr) {
        mReader = reader;
        mAtr = atr != null ? atr.clone() : null;
    }

    public Reader getReader() {
        return mReader;
    }

    public byte[] getATR() {
        return mAtr != null ? mAtr.clone() : null;
    }

    public void close() {
        closeChannels();
        mClosed = true;
    }

    public boolean isClosed() {
        return mClosed || !mReader.getSEService().isConnected();
    }

    public void closeChannels() {
        synchronized (mChannels) {
            for (int i = 0; i < mChannels.size(); i++) {
                mChannels.get(i).close();
            }
            mChannels.clear();
        }
    }

    private void ensureOpen() throws IOException {
        if (!mReader.getSEService().isConnected()) {
            throw new IllegalStateException("service not connected to system");
        }
        if (mClosed) {
            throw new IllegalStateException("Session is closed");
        }
        throw new IOException("No hardware Secure Element attached to " + mReader.getName());
    }

    public Channel openBasicChannel(byte[] aid, byte p2) throws IOException {
        ensureOpen();
        return null;
    }

    public Channel openBasicChannel(byte[] aid) throws IOException {
        return openBasicChannel(aid, (byte) 0x00);
    }

    public Channel openLogicalChannel(byte[] aid, byte p2) throws IOException {
        ensureOpen();
        return null;
    }

    public Channel openLogicalChannel(byte[] aid) throws IOException {
        return openLogicalChannel(aid, (byte) 0x00);
    }
}
