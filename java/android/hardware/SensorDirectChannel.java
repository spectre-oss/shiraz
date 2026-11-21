// SPDX-License-Identifier: GPL-3.0-only
package android.hardware;

import java.nio.channels.Channel;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SensorDirectChannel implements Channel {
    public static final int TYPE_MEMORY_FILE = 1;
    public static final int TYPE_HARDWARE_BUFFER = 2;

    public static final int RATE_STOP = 0;
    public static final int RATE_NORMAL = 1;
    public static final int RATE_FAST = 2;
    public static final int RATE_VERY_FAST = 3;

    private final int mType;
    private final AtomicBoolean mClosed = new AtomicBoolean(false);

    SensorDirectChannel(int type) {
        mType = type;
    }

    int getType() {
        return mType;
    }

    @Override
    public boolean isOpen() {
        return !mClosed.get();
    }

    @Override
    public void close() {
        mClosed.set(true);
    }

    public int configure(Sensor sensor, int rateLevel) {
        if (!isOpen() || sensor == null) {
            return 0;
        }
        if (rateLevel < RATE_STOP || rateLevel > RATE_VERY_FAST) {
            throw new IllegalArgumentException("Invalid rate level " + rateLevel);
        }
        return 0;
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
