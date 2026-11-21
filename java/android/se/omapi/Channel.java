// SPDX-License-Identifier: GPL-3.0-only
package android.se.omapi;

import java.io.IOException;

public final class Channel implements java.nio.channels.Channel {
    private final Session mSession;
    private final boolean mBasicChannel;
    private final byte[] mSelectResponse;
    private volatile boolean mOpen;

    Channel(Session session, boolean basicChannel, byte[] selectResponse) {
        mSession = session;
        mBasicChannel = basicChannel;
        mSelectResponse = selectResponse != null ? selectResponse.clone() : null;
        mOpen = true;
    }

    @Override
    public void close() {
        mOpen = false;
    }

    @Override
    public boolean isOpen() {
        return mOpen && !mSession.isClosed();
    }

    public boolean isBasicChannel() {
        if (!mSession.getReader().getSEService().isConnected()) {
            throw new IllegalStateException("service not connected to system");
        }
        return mBasicChannel;
    }

    public byte[] transmit(byte[] command) throws IOException {
        if (command == null) {
            throw new NullPointerException("command must not be null");
        }
        if (!mSession.getReader().getSEService().isConnected()) {
            throw new IllegalStateException("service not connected to system");
        }
        if (!isOpen()) {
            throw new IllegalStateException("channel is closed");
        }
        throw new IOException("No hardware Secure Element channel open");
    }

    public Session getSession() {
        return mSession;
    }

    public byte[] getSelectResponse() {
        return mSelectResponse != null ? mSelectResponse.clone() : null;
    }

    public boolean selectNext() throws IOException {
        if (!mSession.getReader().getSEService().isConnected()) {
            throw new IllegalStateException("service not connected to system");
        }
        if (!isOpen()) {
            throw new IllegalStateException("channel is closed");
        }
        throw new IOException("No hardware Secure Element channel open");
    }
}
