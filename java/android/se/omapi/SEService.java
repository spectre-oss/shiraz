// SPDX-License-Identifier: GPL-3.0-only
package android.se.omapi;

import android.content.Context;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

public final class SEService {
    public static final String ACTION_SECURE_ELEMENT_STATE_CHANGED =
            "android.se.omapi.action.SECURE_ELEMENT_STATE_CHANGED";
    public static final String EXTRA_READER_NAME =
            "android.se.omapi.extra.READER_NAME";
    public static final String EXTRA_READER_STATE =
            "android.se.omapi.extra.READER_STATE";

    private final Context mContext;
    private final Map<Integer, Reader> mUiccReaders = new HashMap<Integer, Reader>();
    private volatile boolean mConnected;

    public interface OnConnectedListener {
        void onConnected();
    }

    public SEService(
            Context context, Executor executor, final OnConnectedListener listener) {
        if (context == null) {
            throw new NullPointerException("context must not be null");
        }
        if (executor == null) {
            throw new NullPointerException("executor must not be null");
        }
        if (listener == null) {
            throw new NullPointerException("listener must not be null");
        }
        mContext = context;
        mConnected = true;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (mConnected) {
                    listener.onConnected();
                }
            }
        });
    }

    public boolean isConnected() {
        return mConnected;
    }

    public Reader[] getReaders() {
        if (!mConnected) {
            throw new IllegalStateException("service not connected to system");
        }
        return new Reader[0];
    }

    public Reader getUiccReader(int slotNumber) {
        if (slotNumber < 1) {
            throw new IllegalArgumentException("slotNumber should be larger than 0");
        }
        if (!mConnected) {
            throw new IllegalStateException("service not connected to system");
        }
        synchronized (mUiccReaders) {
            Reader reader = mUiccReaders.get(Integer.valueOf(slotNumber));
            if (reader == null) {
                reader = new Reader(this, "SIM" + slotNumber);
                mUiccReaders.put(Integer.valueOf(slotNumber), reader);
            }
            return reader;
        }
    }

    public void shutdown() {
        synchronized (mUiccReaders) {
            for (Reader reader : mUiccReaders.values()) {
                reader.closeSessionsInternal();
            }
            mConnected = false;
        }
    }

    public String getVersion() {
        return "3.3";
    }
}
