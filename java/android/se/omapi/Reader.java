// SPDX-License-Identifier: GPL-3.0-only
package android.se.omapi;

import java.io.IOException;
import java.util.ArrayList;

public final class Reader {
    private final SEService mService;
    private final String mName;
    private final ArrayList<Session> mSessions = new ArrayList<Session>();

    Reader(SEService service, String name) {
        mService = service;
        mName = name;
    }

    public String getName() {
        return mName;
    }

    public Session openSession() throws IOException {
        if (!mService.isConnected()) {
            throw new IllegalStateException("service is not connected");
        }
        throw new IOException("Secure Element is not present on host reader " + mName);
    }

    public boolean isSecureElementPresent() {
        if (!mService.isConnected()) {
            throw new IllegalStateException("service is not connected");
        }
        return false;
    }

    public SEService getSEService() {
        return mService;
    }

    public void closeSessions() {
        if (!mService.isConnected()) {
            return;
        }
        closeSessionsInternal();
    }

    void closeSessionsInternal() {
        synchronized (mSessions) {
            for (int i = 0; i < mSessions.size(); i++) {
                mSessions.get(i).close();
            }
            mSessions.clear();
        }
    }
}
