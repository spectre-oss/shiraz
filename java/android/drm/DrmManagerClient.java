// SPDX-License-Identifier: GPL-3.0-only
package android.drm;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Client interface to the Android DRM framework supporting rights management and listeners. */
@Deprecated
public class DrmManagerClient implements AutoCloseable {
    public static final int ERROR_NONE = 0;
    public static final int ERROR_UNKNOWN = -2000;

    private static final AtomicInteger NEXT_UNIQUE_ID = new AtomicInteger(1);

    private final int mUniqueId;
    private final WeakReference<Context> mContext;
    private final AtomicBoolean mClosed = new AtomicBoolean(false);
    private final ConcurrentHashMap<String, DrmRights> mSavedRights =
            new ConcurrentHashMap<String, DrmRights>();
    private final Handler mEventHandler;

    private OnInfoListener mOnInfoListener;
    private OnEventListener mOnEventListener;
    private OnErrorListener mOnErrorListener;

    public interface OnInfoListener {
        public void onInfo(DrmManagerClient client, DrmInfoEvent event);
    }

    public interface OnEventListener {
        public void onEvent(DrmManagerClient client, DrmEvent event);
    }

    public interface OnErrorListener {
        public void onError(DrmManagerClient client, DrmErrorEvent event);
    }

    public DrmManagerClient(Context context) {
        this.mUniqueId = NEXT_UNIQUE_ID.getAndIncrement();
        this.mContext = new WeakReference<Context>(context);
        Looper looper = Looper.myLooper();
        if (looper == null) {
            looper = Looper.getMainLooper();
        }
        this.mEventHandler = looper != null ? new Handler(looper) : null;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    @Override
    public void close() {
        if (mClosed.compareAndSet(false, true)) {
            synchronized (this) {
                mOnInfoListener = null;
                mOnEventListener = null;
                mOnErrorListener = null;
            }
            mSavedRights.clear();
        }
    }

    @Deprecated
    public void release() {
        close();
    }

    public synchronized void setOnInfoListener(OnInfoListener infoListener) {
        mOnInfoListener = infoListener;
    }

    public synchronized void setOnEventListener(OnEventListener eventListener) {
        mOnEventListener = eventListener;
    }

    public synchronized void setOnErrorListener(OnErrorListener errorListener) {
        mOnErrorListener = errorListener;
    }

    public String[] getAvailableDrmEngines() {
        return new String[0];
    }

    public Collection<DrmSupportInfo> getAvailableDrmSupportInfo() {
        return new ArrayList<DrmSupportInfo>();
    }

    public ContentValues getConstraints(String path, int action) {
        if (path == null || path.isEmpty() || !DrmStore.Action.isValid(action)) {
            throw new IllegalArgumentException(
                    "Given usage or path is invalid/null");
        }
        return null;
    }

    public ContentValues getMetadata(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Given path is invalid/null");
        }
        return null;
    }

    public ContentValues getConstraints(Uri uri, int action) {
        if (uri == null || Uri.EMPTY.equals(uri)) {
            throw new IllegalArgumentException("Uri should be non null and non empty");
        }
        return getConstraints(convertUriToPath(uri), action);
    }

    public ContentValues getMetadata(Uri uri) {
        if (uri == null || Uri.EMPTY.equals(uri)) {
            throw new IllegalArgumentException("Uri should be non null and non empty");
        }
        return getMetadata(convertUriToPath(uri));
    }

    public int saveRights(DrmRights drmRights, String rightsPath, String contentPath)
            throws IOException {
        if (drmRights == null || !drmRights.isValid()) {
            throw new IllegalArgumentException("Given drmRights Parameter Could Not Be Null");
        }
        if (rightsPath != null && !rightsPath.isEmpty()) {
            DrmUtils.writeToFile(rightsPath, drmRights.getData());
        }
        if (contentPath != null && !contentPath.isEmpty()) {
            mSavedRights.put(contentPath, drmRights);
        }
        dispatchInfo(
                new DrmInfoEvent(
                        mUniqueId, DrmInfoEvent.TYPE_RIGHTS_INSTALLED, "Rights installed"));
        return ERROR_NONE;
    }

    public boolean canHandle(String path, String mimeType) {
        if ((path == null || path.isEmpty()) && (mimeType == null || mimeType.isEmpty())) {
            throw new IllegalArgumentException("Path or the mimetype should be non null");
        }
        return false;
    }

    public boolean canHandle(Uri uri, String mimeType) {
        if ((uri == null || Uri.EMPTY.equals(uri))
                && (mimeType == null || mimeType.isEmpty())) {
            throw new IllegalArgumentException(
                    "Uri or the mimetype should be non null");
        }
        return canHandle(convertUriToPath(uri), mimeType);
    }

    public int processDrmInfo(DrmInfo drmInfo) {
        if (drmInfo == null || !drmInfo.isValid()) {
            throw new IllegalArgumentException("Given drmInfo is invalid/null");
        }
        dispatchError(
                new DrmErrorEvent(
                        mUniqueId,
                        DrmErrorEvent.TYPE_PROCESS_DRM_INFO_FAILED,
                        "No DRM engine available for " + drmInfo.getMimeType()));
        return ERROR_UNKNOWN;
    }

    public DrmInfo acquireDrmInfo(DrmInfoRequest drmInfoRequest) {
        if (drmInfoRequest == null || !drmInfoRequest.isValid()) {
            throw new IllegalArgumentException("Given drmInfoRequest is invalid/null");
        }
        dispatchError(
                new DrmErrorEvent(
                        mUniqueId,
                        DrmErrorEvent.TYPE_ACQUIRE_DRM_INFO_FAILED,
                        "No DRM engine available for " + drmInfoRequest.getMimeType()));
        return null;
    }

    public int acquireRights(DrmInfoRequest drmInfoRequest) {
        DrmInfo info = acquireDrmInfo(drmInfoRequest);
        if (info == null) {
            return ERROR_UNKNOWN;
        }
        return processDrmInfo(info);
    }

    public int getDrmObjectType(String path, String mimeType) {
        if ((path == null || path.isEmpty()) && (mimeType == null || mimeType.isEmpty())) {
            throw new IllegalArgumentException("Path or the mimetype should be non null");
        }
        return DrmStore.DrmObjectType.UNKNOWN;
    }

    public int getDrmObjectType(Uri uri, String mimeType) {
        if ((uri == null || Uri.EMPTY.equals(uri))
                && (mimeType == null || mimeType.isEmpty())) {
            throw new IllegalArgumentException("Uri or the mimetype should be non null");
        }
        String path = "";
        try {
            path = convertUriToPath(uri);
        } catch (Exception ignored) {
        }
        return getDrmObjectType(path, mimeType);
    }

    public String getOriginalMimeType(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Given path should be non null");
        }
        return null;
    }

    public String getOriginalMimeType(Uri uri) {
        if (uri == null || Uri.EMPTY.equals(uri)) {
            throw new IllegalArgumentException("Given uri is not valid");
        }
        return getOriginalMimeType(convertUriToPath(uri));
    }

    public int checkRightsStatus(String path) {
        return checkRightsStatus(path, DrmStore.Action.DEFAULT);
    }

    public int checkRightsStatus(Uri uri) {
        if (uri == null || Uri.EMPTY.equals(uri)) {
            throw new IllegalArgumentException("Given uri is not valid");
        }
        return checkRightsStatus(convertUriToPath(uri));
    }

    public int checkRightsStatus(String path, int action) {
        if (path == null || path.isEmpty() || !DrmStore.Action.isValid(action)) {
            throw new IllegalArgumentException("Given path or action is not valid");
        }
        if (mSavedRights.containsKey(path)) {
            return DrmStore.RightsStatus.RIGHTS_VALID;
        }
        return DrmStore.RightsStatus.RIGHTS_NOT_ACQUIRED;
    }

    public int checkRightsStatus(Uri uri, int action) {
        if (uri == null || Uri.EMPTY.equals(uri)) {
            throw new IllegalArgumentException("Given uri is not valid");
        }
        return checkRightsStatus(convertUriToPath(uri), action);
    }

    public int removeRights(String path) {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Given path should be non null");
        }
        mSavedRights.remove(path);
        dispatchInfo(
                new DrmInfoEvent(
                        mUniqueId, DrmInfoEvent.TYPE_RIGHTS_REMOVED, "Rights removed"));
        return ERROR_NONE;
    }

    public int removeRights(Uri uri) {
        if (uri == null || Uri.EMPTY.equals(uri)) {
            throw new IllegalArgumentException("Given uri is not valid");
        }
        return removeRights(convertUriToPath(uri));
    }

    public int removeAllRights() {
        mSavedRights.clear();
        dispatchEvent(new DrmEvent(mUniqueId, DrmEvent.TYPE_ALL_RIGHTS_REMOVED, ""));
        return ERROR_NONE;
    }

    public int openConvertSession(String mimeType) {
        if (mimeType == null || mimeType.isEmpty()) {
            throw new IllegalArgumentException("Path or the mimeType should be non null");
        }
        return ERROR_UNKNOWN;
    }

    public DrmConvertedStatus convertData(int convertId, byte[] inputData) {
        if (inputData == null) {
            throw new IllegalArgumentException("Given inputData should be non null");
        }
        return null;
    }

    public DrmConvertedStatus closeConvertSession(int convertId) {
        return null;
    }

    private void dispatchInfo(final DrmInfoEvent event) {
        final OnInfoListener listener;
        synchronized (this) {
            listener = mOnInfoListener;
        }
        if (listener == null) {
            return;
        }
        if (mEventHandler != null) {
            mEventHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onInfo(DrmManagerClient.this, event);
                        }
                    });
        } else {
            listener.onInfo(this, event);
        }
    }

    private void dispatchEvent(final DrmEvent event) {
        final OnEventListener listener;
        synchronized (this) {
            listener = mOnEventListener;
        }
        if (listener == null) {
            return;
        }
        if (mEventHandler != null) {
            mEventHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onEvent(DrmManagerClient.this, event);
                        }
                    });
        } else {
            listener.onEvent(this, event);
        }
    }

    private void dispatchError(final DrmErrorEvent event) {
        final OnErrorListener listener;
        synchronized (this) {
            listener = mOnErrorListener;
        }
        if (listener == null) {
            return;
        }
        if (mEventHandler != null) {
            mEventHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            listener.onError(DrmManagerClient.this, event);
                        }
                    });
        } else {
            listener.onError(this, event);
        }
    }

    private static String convertUriToPath(Uri uri) {
        if (uri == null) {
            return null;
        }
        String scheme = uri.getScheme();
        if (scheme == null || scheme.isEmpty() || "file".equals(scheme)) {
            return uri.getPath();
        }
        if ("http".equals(scheme) || "https".equals(scheme)) {
            return uri.toString();
        }
        if ("content".equals(scheme)) {
            String path = uri.getPath();
            return path != null && !path.isEmpty() ? path : uri.toString();
        }
        throw new IllegalArgumentException("Given Uri could not be found in media store");
    }
}
