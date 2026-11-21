// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public abstract class MediaSession2Service extends Service {
    public static final String SERVICE_INTERFACE = "android.media.MediaSession2Service";

    private final Map<String, MediaSession2> mSessions =
            new LinkedHashMap<String, MediaSession2>();
    private final IBinder mBinder = new Binder();

    public MediaSession2Service() {}

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && SERVICE_INTERFACE.equals(intent.getAction())) {
            return mBinder;
        }
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        synchronized (mSessions) {
            mSessions.clear();
        }
    }

    public abstract MediaSession2 onGetSession(MediaSession2.ControllerInfo controllerInfo);

    public abstract MediaNotification onUpdateNotification(MediaSession2 session);

    public final void addSession(MediaSession2 session) {
        Objects.requireNonNull(session, "session");
        synchronized (mSessions) {
            mSessions.put(session.getId(), session);
        }
    }

    public final void removeSession(MediaSession2 session) {
        Objects.requireNonNull(session, "session");
        synchronized (mSessions) {
            mSessions.remove(session.getId());
        }
    }

    public final List<MediaSession2> getSessions() {
        synchronized (mSessions) {
            return new ArrayList<MediaSession2>(mSessions.values());
        }
    }

    public static class MediaNotification {
        private final int mNotificationId;
        private final Notification mNotification;

        public MediaNotification(int notificationId, Notification notification) {
            mNotificationId = notificationId;
            mNotification = Objects.requireNonNull(notification, "notification");
        }

        public int getNotificationId() {
            return mNotificationId;
        }

        public Notification getNotification() {
            return mNotification;
        }
    }
}
