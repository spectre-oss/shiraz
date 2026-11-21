// SPDX-License-Identifier: GPL-3.0-only

package android.provider;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.view.Surface;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Objects;

public abstract class CloudMediaProvider extends ContentProvider {
    private static final String METHOD_GET_MEDIA_COLLECTION_INFO =
            "android:getMediaCollectionInfo";
    private static final String URI_PATH_MEDIA = "media";
    private static final String URI_PATH_DELETED_MEDIA = "deleted_media";
    private static final String URI_PATH_ALBUM = "album";
    private static final String URI_PATH_MEDIA_COLLECTION_INFO = "media_collection_info";

    public CloudMediaProvider() {
    }

    @Override
    public final void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
    }

    public abstract Bundle onGetMediaCollectionInfo(Bundle extras);

    public abstract Cursor onQueryMedia(Bundle extras);

    public abstract Cursor onQueryDeletedMedia(Bundle extras);

    public Cursor onQueryAlbums(Bundle extras) {
        throw new UnsupportedOperationException("queryAlbums not supported");
    }

    public abstract AssetFileDescriptor onOpenPreview(
            String mediaId, Point size, Bundle extras, CancellationSignal signal)
            throws FileNotFoundException;

    public abstract ParcelFileDescriptor onOpenMedia(
            String mediaId, Bundle extras, CancellationSignal signal)
            throws FileNotFoundException;

    public CloudMediaSurfaceController onCreateCloudMediaSurfaceController(
            Bundle config, CloudMediaSurfaceStateChangedCallback callback) {
        return null;
    }

    @Override
    public final Bundle call(String method, String arg, Bundle extras) {
        if (METHOD_GET_MEDIA_COLLECTION_INFO.equals(method)) {
            return onGetMediaCollectionInfo(extras != null ? extras : new Bundle());
        }
        return super.call(method, arg, extras);
    }

    @Override
    public final ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return openFile(uri, mode, null);
    }

    @Override
    public final ParcelFileDescriptor openFile(
            Uri uri, String mode, CancellationSignal signal) throws FileNotFoundException {
        Objects.requireNonNull(uri, "uri");
        return onOpenMedia(extractMediaId(uri), new Bundle(), signal);
    }

    public final AssetFileDescriptor openTypedAssetFile(
            Uri uri, String mimeTypeFilter, Bundle opts) throws FileNotFoundException {
        return openTypedAssetFile(uri, mimeTypeFilter, opts, null);
    }

    public final AssetFileDescriptor openTypedAssetFile(
            Uri uri, String mimeTypeFilter, Bundle opts, CancellationSignal signal)
            throws FileNotFoundException {
        Objects.requireNonNull(uri, "uri");
        Bundle extras = opts != null ? new Bundle(opts) : new Bundle();
        Point size = extras.getParcelable("android.content.extra.SIZE");
        if (size == null) {
            size = new Point(256, 256);
        }
        return onOpenPreview(extractMediaId(uri), size, extras, signal);
    }

    private static String extractMediaId(Uri uri) throws FileNotFoundException {
        String last = uri.getLastPathSegment();
        if (last == null || last.isEmpty()) {
            throw new FileNotFoundException("Missing media ID in URI: " + uri);
        }
        return last;
    }

    public final Cursor query(
            Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal) {
        Objects.requireNonNull(uri, "uri");
        Bundle extras = queryArgs != null ? queryArgs : new Bundle();
        List<String> segments = uri.getPathSegments();
        String first = segments.isEmpty() ? "" : segments.get(0);
        if (URI_PATH_MEDIA.equals(first)) {
            return onQueryMedia(extras);
        }
        if (URI_PATH_DELETED_MEDIA.equals(first)) {
            return onQueryDeletedMedia(extras);
        }
        if (URI_PATH_ALBUM.equals(first)) {
            return onQueryAlbums(extras);
        }
        throw new UnsupportedOperationException("Unsupported URI: " + uri);
    }

    @Override
    public final String getType(Uri uri) {
        throw new UnsupportedOperationException("getType not supported");
    }

    public final Uri canonicalize(Uri uri) {
        throw new UnsupportedOperationException("canonicalize not supported");
    }

    @Override
    public final Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return query(uri, projection, new Bundle(), (CancellationSignal) null);
    }

    @Override
    public final Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder,
            CancellationSignal cancellationSignal) {
        return query(uri, projection, new Bundle(), cancellationSignal);
    }

    @Override
    public final Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert not supported");
    }

    @Override
    public final int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete not supported");
    }

    @Override
    public final int update(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("update not supported");
    }

    public abstract static class CloudMediaSurfaceController {
        public CloudMediaSurfaceController() {
        }

        public abstract void onPlayerCreate();

        public abstract void onPlayerRelease();

        public abstract void onSurfaceCreated(int surfaceId, Surface surface, String mediaId);

        public abstract void onSurfaceChanged(int surfaceId, int format, int width, int height);

        public abstract void onSurfaceDestroyed(int surfaceId);

        public abstract void onMediaPlay(int surfaceId);

        public abstract void onMediaPause(int surfaceId);

        public abstract void onMediaSeekTo(int surfaceId, long timestampMillis);

        public abstract void onConfigChange(Bundle config);

        public abstract void onDestroy();
    }

    public static final class CloudMediaSurfaceStateChangedCallback {
        public static final int PLAYBACK_STATE_BUFFERING = 1;
        public static final int PLAYBACK_STATE_READY = 2;
        public static final int PLAYBACK_STATE_STARTED = 3;
        public static final int PLAYBACK_STATE_PAUSED = 4;
        public static final int PLAYBACK_STATE_COMPLETED = 5;
        public static final int PLAYBACK_STATE_ERROR_RETRIABLE_FAILURE = 6;
        public static final int PLAYBACK_STATE_ERROR_PERMANENT_FAILURE = 7;
        public static final int PLAYBACK_STATE_MEDIA_SIZE_CHANGED = 8;

        private int mLastSurfaceId = -1;
        private int mLastPlaybackState = 0;
        private Bundle mLastPlaybackStateInfo;

        CloudMediaSurfaceStateChangedCallback() {
        }

        public void setPlaybackState(
                int surfaceId, int playbackState, Bundle playbackStateInfo) {
            mLastSurfaceId = surfaceId;
            mLastPlaybackState = playbackState;
            mLastPlaybackStateInfo = playbackStateInfo != null
                    ? new Bundle(playbackStateInfo) : null;
        }

        int getLastSurfaceId() {
            return mLastSurfaceId;
        }

        int getLastPlaybackState() {
            return mLastPlaybackState;
        }

        Bundle getLastPlaybackStateInfo() {
            return mLastPlaybackStateInfo;
        }
    }
}
