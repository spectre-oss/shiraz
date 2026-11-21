// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import java.io.Closeable;
import java.io.IOException;

public final class MidiInputPort extends MidiReceiver implements Closeable {
    private static final int MAX_PACKET_DATA_SIZE = 1015;

    private final MidiDevice mDevice;
    private final int mPortNumber;
    private final MidiReceiver mTargetReceiver;
    private boolean mIsClosed;

    MidiInputPort(int portNumber) {
        this(null, portNumber, null);
    }

    MidiInputPort(MidiDevice device, int portNumber, MidiReceiver targetReceiver) {
        super(MAX_PACKET_DATA_SIZE);
        mDevice = device;
        mPortNumber = portNumber;
        mTargetReceiver = targetReceiver;
    }

    public int getPortNumber() {
        return mPortNumber;
    }

    @Override
    public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
        if (msg == null) {
            throw new NullPointerException("msg must not be null");
        }
        if (offset < 0 || count < 0 || offset + count > msg.length) {
            throw new IllegalArgumentException("offset or count out of range");
        }
        if (count > MAX_PACKET_DATA_SIZE) {
            throw new IllegalArgumentException("count exceeds max message size");
        }
        synchronized (this) {
            if (mIsClosed) {
                throw new IOException("MidiInputPort is closed");
            }
        }
        if (mTargetReceiver != null) {
            mTargetReceiver.send(msg, offset, count, timestamp);
        }
        if (mDevice != null) {
            mDevice.dispatchFromInputPort(mPortNumber, msg, offset, count, timestamp);
        }
    }

    @Override
    public void onFlush() throws IOException {
        synchronized (this) {
            if (mIsClosed) {
                throw new IOException("MidiInputPort is closed");
            }
        }
        if (mTargetReceiver != null) {
            mTargetReceiver.flush();
        }
        if (mDevice != null) {
            mDevice.dispatchFlushFromInputPort(mPortNumber);
        }
    }

    @Override
    public void close() throws IOException {
        synchronized (this) {
            if (mIsClosed) {
                return;
            }
            mIsClosed = true;
        }
        if (mDevice != null) {
            mDevice.onInputPortClosed(mPortNumber);
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
