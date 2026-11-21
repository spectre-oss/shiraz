// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MidiOutputPort extends MidiSender implements Closeable {
    private final MidiDevice mDevice;
    private final int mPortNumber;
    private final CopyOnWriteArrayList<MidiReceiver> mReceivers =
            new CopyOnWriteArrayList<MidiReceiver>();
    private boolean mIsClosed;

    MidiOutputPort(int portNumber) {
        this(null, portNumber);
    }

    MidiOutputPort(MidiDevice device, int portNumber) {
        mDevice = device;
        mPortNumber = portNumber;
    }

    public int getPortNumber() {
        return mPortNumber;
    }

    @Override
    public void onConnect(MidiReceiver receiver) {
        if (receiver == null) {
            throw new NullPointerException("receiver must not be null");
        }
        synchronized (mReceivers) {
            if (mIsClosed) {
                return;
            }
            mReceivers.add(receiver);
        }
    }

    @Override
    public void onDisconnect(MidiReceiver receiver) {
        if (receiver == null) {
            throw new NullPointerException("receiver must not be null");
        }
        synchronized (mReceivers) {
            mReceivers.remove(receiver);
        }
    }

    void dispatchSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
        if (mIsClosed) {
            return;
        }
        for (MidiReceiver receiver : mReceivers) {
            receiver.send(msg, offset, count, timestamp);
        }
    }

    void dispatchFlush() throws IOException {
        if (mIsClosed) {
            return;
        }
        for (MidiReceiver receiver : mReceivers) {
            receiver.flush();
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (mReceivers) {
            if (mIsClosed) {
                return;
            }
            mIsClosed = true;
            mReceivers.clear();
        }
        if (mDevice != null) {
            mDevice.onOutputPortClosed(mPortNumber);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}
