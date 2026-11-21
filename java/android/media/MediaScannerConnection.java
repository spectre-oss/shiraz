// SPDX-License-Identifier: GPL-3.0-only
package android.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.File;

public class MediaScannerConnection implements ServiceConnection {
    public interface OnScanCompletedListener {
        void onScanCompleted(String path, Uri uri);
    }

    public interface MediaScannerConnectionClient extends OnScanCompletedListener {
        void onMediaScannerConnected();
    }

    private final Context mContext;
    private final MediaScannerConnectionClient mClient;
    private boolean mConnected;

    public MediaScannerConnection(Context context, MediaScannerConnectionClient client) {
        mContext = context;
        mClient = client;
    }

    public synchronized void connect() {
        if (!mConnected) {
            mConnected = true;
            if (mClient != null) {
                Looper looper = Looper.myLooper();
                if (looper == null) {
                    looper = Looper.getMainLooper();
                }
                if (looper != null) {
                    new Handler(looper).post(new Runnable() {
                        @Override
                        public void run() {
                            if (isConnected()) {
                                mClient.onMediaScannerConnected();
                            }
                        }
                    });
                } else {
                    mClient.onMediaScannerConnected();
                }
            }
        }
    }

    public synchronized void disconnect() {
        mConnected = false;
    }

    public synchronized boolean isConnected() {
        return mConnected;
    }

    public void scanFile(String path, String mimeType) {
        synchronized (this) {
            if (!mConnected) {
                throw new IllegalStateException("not connected to MediaScannerService");
            }
        }
        Uri uri = resolveScannedUri(path);
        if (mClient != null) {
            mClient.onScanCompleted(path, uri);
        }
    }

    public static void scanFile(Context context, String[] paths, String[] mimeTypes,
            final OnScanCompletedListener callback) {
        if (paths == null) {
            return;
        }
        for (int i = 0; i < paths.length; i++) {
            final String path = paths[i];
            final Uri uri = resolveScannedUri(path);
            if (callback != null) {
                callback.onScanCompleted(path, uri);
            }
        }
    }

    private static Uri resolveScannedUri(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        File f = new File(path);
        return f.exists() ? Uri.fromFile(f) : Uri.parse("file://" + path);
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        synchronized (this) {
            mConnected = true;
        }
        if (mClient != null) {
            mClient.onMediaScannerConnected();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        synchronized (this) {
            mConnected = false;
        }
    }
}
