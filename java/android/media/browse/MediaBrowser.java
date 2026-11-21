// SPDX-License-Identifier: GPL-3.0-only
package android.media.browse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.MediaDescription;
import android.media.session.MediaSession;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.service.media.MediaBrowserService;
import java.util.List;

/** Client API for browsing an in-package media service. */
public final class MediaBrowser {
    public static final String EXTRA_PAGE = "android.media.browse.extra.PAGE";
    public static final String EXTRA_PAGE_SIZE = "android.media.browse.extra.PAGE_SIZE";

    private final Context context;
    private final ComponentName service;
    private final ConnectionCallback callback;
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!(binder instanceof MediaBrowserService.BrowserBinder)) {
                callback.onConnectionFailed();
                return;
            }
            platformService = ((MediaBrowserService.BrowserBinder) binder).service();
            root = platformService.onGetRoot(
                    context.getPackageName(), android.os.Process.myUid(), hints);
            if (root == null || platformService.getSessionToken() == null) {
                callback.onConnectionFailed();
                return;
            }
            connected = true;
            callback.onConnected();
        }

        public void onServiceDisconnected(ComponentName name) {
            connected = false;
            callback.onConnectionSuspended();
        }

        public void onBindingDied(ComponentName name) {
            connected = false;
            callback.onConnectionFailed();
        }
    };
    private boolean bound;
    private boolean connected;
    private final Bundle hints;
    private MediaBrowserService platformService;
    private MediaBrowserService.BrowserRoot root;

    public MediaBrowser(
            Context context,
            ComponentName service,
            ConnectionCallback callback,
            Bundle hints) {
        if (context == null || service == null || callback == null) {
            throw new IllegalArgumentException("context, service and callback required");
        }
        this.context = context;
        this.service = service;
        this.callback = callback;
        this.hints = hints == null ? null : new Bundle(hints);
    }

    public void connect() {
        if (bound) {
            return;
        }
        Intent intent = new Intent("android.media.browse.MediaBrowserService").setComponent(service);
        bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            callback.onConnectionFailed();
        }
    }

    public void disconnect() {
        if (bound) {
            context.unbindService(connection);
            bound = false;
        }
        connected = false;
        root = null;
        platformService = null;
    }

    public boolean isConnected() {
        return connected;
    }

    public ComponentName getServiceComponent() {
        return service;
    }

    public String getRoot() {
        if (!connected) {
            throw new IllegalStateException("Media browser is not connected");
        }
        return root.getRootId();
    }

    public Bundle getExtras() {
        if (!connected) {
            throw new IllegalStateException("Media browser is not connected");
        }
        return root.getExtras();
    }

    public MediaSession.Token getSessionToken() {
        if (!connected) {
            throw new IllegalStateException("Media browser is not connected");
        }
        return platformService.getSessionToken();
    }

    public void subscribe(String parentId, SubscriptionCallback callback) {
        if (parentId == null || parentId.isEmpty()) {
            throw new IllegalArgumentException("parentId cannot be empty");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        if (!connected) {
            throw new IllegalStateException("Media browser is not connected");
        }
    }

    public void subscribe(String parentId, Bundle options, SubscriptionCallback callback) {
        if (options == null) {
            throw new IllegalArgumentException("options cannot be null");
        }
        subscribe(parentId, callback);
    }

    public void unsubscribe(String parentId) {
        if (parentId == null || parentId.isEmpty()) {
            throw new IllegalArgumentException("parentId cannot be empty");
        }
        if (!connected) {
            throw new IllegalStateException("Media browser is not connected");
        }
    }

    public void unsubscribe(String parentId, SubscriptionCallback callback) {
        if (callback == null) {
            throw new IllegalArgumentException("callback cannot be null");
        }
        unsubscribe(parentId);
    }

    public void getItem(final String mediaId, final ItemCallback cb) {
        if (mediaId == null || mediaId.isEmpty()) {
            throw new IllegalArgumentException("mediaId cannot be empty.");
        }
        if (cb == null) {
            throw new IllegalArgumentException("cb cannot be null.");
        }
        if (!connected) {
            cb.onError(mediaId);
            return;
        }
        cb.onError(mediaId);
    }

    public static class ConnectionCallback {
        public ConnectionCallback() {
        }

        public void onConnected() {
        }

        public void onConnectionSuspended() {
        }

        public void onConnectionFailed() {
        }
    }

    public abstract static class SubscriptionCallback {
        public SubscriptionCallback() {
        }

        public void onChildrenLoaded(String parentId, List<MediaItem> children) {
        }

        public void onChildrenLoaded(String parentId, List<MediaItem> children, Bundle options) {
        }

        public void onError(String parentId) {
        }

        public void onError(String parentId, Bundle options) {
        }
    }

    public abstract static class ItemCallback {
        public ItemCallback() {
        }

        public void onItemLoaded(MediaItem item) {
        }

        public void onError(String mediaId) {
        }
    }

    public static class MediaItem implements Parcelable {
        public static final int FLAG_BROWSABLE = 1;
        public static final int FLAG_PLAYABLE = 2;

        private final MediaDescription description;
        private final int flags;

        public MediaItem(MediaDescription description, int flags) {
            if (description == null
                    || description.getMediaId() == null
                    || description.getMediaId().isEmpty()) {
                throw new IllegalArgumentException("Media item requires a media ID");
            }
            this.description = description;
            this.flags = flags;
        }

        public MediaDescription getDescription() {
            return description;
        }

        public String getMediaId() {
            return description.getMediaId();
        }

        public int getFlags() {
            return flags;
        }

        public boolean isBrowsable() {
            return (flags & FLAG_BROWSABLE) != 0;
        }

        public boolean isPlayable() {
            return (flags & FLAG_PLAYABLE) != 0;
        }

        public int describeContents() {
            return description.describeContents();
        }

        public void writeToParcel(Parcel output, int parcelFlags) {
            output.writeInt(flags);
            description.writeToParcel(output, parcelFlags);
        }

        public String toString() {
            return "MediaItem{mFlags=" + flags + ", mDescription=" + description + "}";
        }

        public static final Parcelable.Creator<MediaItem> CREATOR =
                new Parcelable.Creator<MediaItem>() {
                    public MediaItem createFromParcel(Parcel input) {
                        int flags = input.readInt();
                        return new MediaItem(
                                MediaDescription.CREATOR.createFromParcel(input), flags);
                    }

                    public MediaItem[] newArray(int size) {
                        return new MediaItem[size];
                    }
                };
    }
}
