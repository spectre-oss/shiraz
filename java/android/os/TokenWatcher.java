// SPDX-License-Identifier: GPL-3.0-only
package android.os;

import android.util.Log;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Monitors a set of {@link IBinder} tokens and dispatches edge-triggered acquire/release notifications. */
public abstract class TokenWatcher {
    private final WeakHashMap<IBinder, Death> tokens = new WeakHashMap<IBinder, Death>();
    private final Handler handler;
    private final String tag;
    private int pendingNotification = -1;
    private volatile boolean acquired = false;

    private final Runnable notificationTask =
            new Runnable() {
                @Override
                public void run() {
                    int value;
                    synchronized (tokens) {
                        value = pendingNotification;
                        pendingNotification = -1;
                    }
                    if (value == 1) {
                        acquired();
                    } else if (value == 0) {
                        released();
                    }
                }
            };

    public TokenWatcher(Handler h, String tag) {
        this.handler = h;
        this.tag = tag != null ? tag : "TokenWatcher";
    }

    public abstract void acquired();

    public abstract void released();

    public void acquire(IBinder token, String tag) {
        synchronized (tokens) {
            if (tokens.containsKey(token)) {
                return;
            }
            int oldSize = tokens.size();
            Death d = new Death(token, tag);
            try {
                token.linkToDeath(d, 0);
            } catch (RemoteException e) {
                return;
            }
            tokens.put(token, d);
            if (oldSize == 0 && !acquired) {
                sendNotificationLocked(true);
                acquired = true;
            }
        }
    }

    public void cleanup(IBinder token, boolean unlink) {
        synchronized (tokens) {
            Death d = tokens.remove(token);
            if (unlink && d != null) {
                d.token.unlinkToDeath(d, 0);
                d.token = null;
            }
            if (tokens.size() == 0 && acquired) {
                sendNotificationLocked(false);
                acquired = false;
            }
        }
    }

    public void release(IBinder token) {
        cleanup(token, true);
    }

    public boolean isAcquired() {
        synchronized (tokens) {
            return acquired;
        }
    }

    public void dump() {
        ArrayList<String> lines = dumpInternal();
        for (int i = 0; i < lines.size(); i++) {
            Log.i(tag, lines.get(i));
        }
    }

    public void dump(PrintWriter pw) {
        ArrayList<String> lines = dumpInternal();
        for (int i = 0; i < lines.size(); i++) {
            pw.println(lines.get(i));
        }
    }

    private ArrayList<String> dumpInternal() {
        ArrayList<String> a = new ArrayList<String>();
        synchronized (tokens) {
            Set<Map.Entry<IBinder, Death>> entries = tokens.entrySet();
            a.add("Token count: " + tokens.size());
            int i = 0;
            for (Map.Entry<IBinder, Death> entry : entries) {
                a.add("[" + i + "] " + entry.getValue().tag + " - " + entry.getKey());
                i++;
            }
        }
        return a;
    }

    private void sendNotificationLocked(boolean on) {
        int value = on ? 1 : 0;
        if (pendingNotification == -1) {
            pendingNotification = value;
            handler.post(notificationTask);
        } else if (pendingNotification != value) {
            pendingNotification = -1;
            handler.removeCallbacks(notificationTask);
        }
    }

    private final class Death implements IBinder.DeathRecipient {
        IBinder token;
        final String tag;

        Death(IBinder token, String tag) {
            this.token = token;
            this.tag = tag;
        }

        @Override
        public void binderDied() {
            cleanup(token, false);
        }
    }
}
