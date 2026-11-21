// SPDX-License-Identifier: GPL-3.0-only
package android.media.midi;

import java.io.IOException;

public abstract class MidiReceiver {
    private final int mMaxMessageSize;

    public MidiReceiver() {
        mMaxMessageSize = Integer.MAX_VALUE;
    }

    public MidiReceiver(int maxMessageSize) {
        mMaxMessageSize = maxMessageSize > 0 ? maxMessageSize : Integer.MAX_VALUE;
    }

    public abstract void onSend(byte[] msg, int offset, int count, long timestamp)
            throws IOException;

    public void flush() throws IOException {
        onFlush();
    }

    public void onFlush() throws IOException {
    }

    public final int getMaxMessageSize() {
        return mMaxMessageSize;
    }

    public void send(byte[] msg, int offset, int count) throws IOException {
        send(msg, offset, count, 0L);
    }

    public void send(byte[] msg, int offset, int count, long timestamp) throws IOException {
        if (msg == null) {
            throw new NullPointerException("msg must not be null");
        }
        if (offset < 0 || count < 0 || offset + count > msg.length) {
            throw new IndexOutOfBoundsException(
                    "offset=" + offset + " count=" + count + " length=" + msg.length);
        }
        int messageSize = getMaxMessageSize();
        if (messageSize <= 0) {
            messageSize = Integer.MAX_VALUE;
        }
        while (count > 0) {
            int length = (count > messageSize) ? messageSize : count;
            onSend(msg, offset, length, timestamp);
            offset += length;
            count -= length;
        }
    }
}
