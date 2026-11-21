// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Size;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Media catalog URIs, columns, and content resolver helpers. */
public final class MediaStore {
    public MediaStore() {
    }

    public static final String AUTHORITY = "media";
    public static final Uri AUTHORITY_URI = Uri.parse("content://media");

    public static final String VOLUME_INTERNAL = "internal";
    public static final String VOLUME_EXTERNAL = "external";
    public static final String VOLUME_EXTERNAL_PRIMARY = "external_primary";

    public static final String ACCESS_MEDIA_OWNER_PACKAGE_NAME_PERMISSION =
            "com.android.providers.media.permission.ACCESS_MEDIA_OWNER_PACKAGE_NAME";

    public static final String ACTION_IMAGE_CAPTURE = "android.media.action.IMAGE_CAPTURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE = "android.media.action.IMAGE_CAPTURE_SECURE";
    public static final String ACTION_VIDEO_CAPTURE = "android.media.action.VIDEO_CAPTURE";
    public static final String ACTION_REVIEW = "android.provider.action.REVIEW";
    public static final String ACTION_REVIEW_SECURE = "android.provider.action.REVIEW_SECURE";
    public static final String ACTION_PICK_IMAGES = "android.provider.action.PICK_IMAGES";
    public static final String ACTION_PICK_IMAGES_SETTINGS = "android.provider.action.PICK_IMAGES_SETTINGS";

    public static final String EXTRA_PICK_IMAGES_MAX = "android.provider.extra.PICK_IMAGES_MAX";
    public static final String EXTRA_PICK_IMAGES_LAUNCH_TAB = "android.provider.extra.PICK_IMAGES_LAUNCH_TAB";
    public static final String EXTRA_PICK_IMAGES_IN_ORDER = "android.provider.extra.PICK_IMAGES_IN_ORDER";
    public static final String EXTRA_PICK_IMAGES_ACCENT_COLOR = "android.provider.extra.PICK_IMAGES_ACCENT_COLOR";
    public static final int PICK_IMAGES_TAB_ALBUMS = 0;
    public static final int PICK_IMAGES_TAB_IMAGES = 1;

    public static final String EXTRA_BRIGHTNESS = "android.provider.extra.BRIGHTNESS";
    public static final String EXTRA_ACCEPT_ORIGINAL_MEDIA_FORMAT =
            "android.provider.extra.ACCEPT_ORIGINAL_MEDIA_FORMAT";
    public static final String EXTRA_MEDIA_CAPABILITIES = "android.provider.extra.MEDIA_CAPABILITIES";
    public static final String EXTRA_MEDIA_CAPABILITIES_UID = "android.provider.extra.MEDIA_CAPABILITIES_UID";

    public static final String INTENT_ACTION_MUSIC_PLAYER = "android.intent.action.MUSIC_PLAYER";
    public static final String INTENT_ACTION_MEDIA_SEARCH = "android.intent.action.MEDIA_SEARCH";
    public static final String INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    public static final String INTENT_ACTION_TEXT_OPEN_FROM_SEARCH =
            "android.media.action.TEXT_OPEN_FROM_SEARCH";
    public static final String INTENT_ACTION_VIDEO_PLAY_FROM_SEARCH =
            "android.media.action.VIDEO_PLAY_FROM_SEARCH";
    public static final String INTENT_ACTION_STILL_IMAGE_CAMERA =
            "android.media.action.STILL_IMAGE_CAMERA";
    public static final String INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE =
            "android.media.action.STILL_IMAGE_CAMERA_SECURE";
    public static final String INTENT_ACTION_VIDEO_CAMERA = "android.media.action.VIDEO_CAMERA";

    public static final String META_DATA_STILL_IMAGE_CAMERA_PREWARM_SERVICE =
            "android.media.still_image_camera_preview_service";
    public static final String META_DATA_REVIEW_GALLERY_PREWARM_SERVICE =
            "android.media.review_gallery_prewarm_service";

    public static final String EXTRA_MEDIA_ARTIST = "android.intent.extra.artist";
    public static final String EXTRA_MEDIA_ALBUM = "android.intent.extra.album";
    public static final String EXTRA_MEDIA_TITLE = "android.intent.extra.title";
    public static final String EXTRA_MEDIA_GENRE = "android.intent.extra.genre";
    public static final String EXTRA_MEDIA_PLAYLIST = "android.intent.extra.playlist";
    public static final String EXTRA_MEDIA_RADIO_CHANNEL = "android.intent.extra.radio_channel";
    public static final String EXTRA_MEDIA_FOCUS = "android.intent.extra.focus";
    public static final String EXTRA_SCREEN_ORIENTATION = "android.intent.extra.screenOrientation";
    public static final String EXTRA_FULL_SCREEN = "android.intent.extra.fullScreen";
    public static final String EXTRA_SHOW_ACTION_ICONS = "android.intent.extra.showActionIcons";
    public static final String EXTRA_FINISH_ON_COMPLETION = "android.intent.extra.finishOnCompletion";
    public static final String EXTRA_OUTPUT = "output";
    public static final String EXTRA_VIDEO_QUALITY = "android.intent.extra.videoQuality";
    public static final String EXTRA_SIZE_LIMIT = "android.intent.extra.sizeLimit";
    public static final String EXTRA_DURATION_LIMIT = "android.intent.extra.durationLimit";

    public static final String MEDIA_SCANNER_VOLUME = "volume";
    public static final String MEDIA_IGNORE_FILENAME = ".nomedia";
    public static final String UNKNOWN_STRING = "<unknown>";

    public static final int MATCH_DEFAULT = 0;
    public static final int MATCH_INCLUDE = 1;
    public static final int MATCH_EXCLUDE = 2;
    public static final int MATCH_ONLY = 3;

    public static final String QUERY_ARG_MATCH_PENDING = "android:query-arg-match-pending";
    public static final String QUERY_ARG_MATCH_TRASHED = "android:query-arg-match-trashed";
    public static final String QUERY_ARG_MATCH_FAVORITE = "android:query-arg-match-favorite";
    public static final String QUERY_ARG_RELATED_URI = "android:query-arg-related-uri";
    public static final String QUERY_ARG_INCLUDE_RECENTLY_UNMOUNTED_VOLUMES =
            "android:query-arg-recently-unmounted-volumes";
    public static final String QUERY_ARG_LATEST_SELECTION_ONLY =
            "android:query-arg-latest-selection-only";

    private static final String PARAM_INCLUDE_PENDING = "includePending";
    private static final String PARAM_REQUIRE_ORIGINAL = "requireOriginal";

    public static int getPickImagesMaxLimit() {
        return 100;
    }

    public static Uri setIncludePending(Uri uri) {
        if (uri == null) throw new NullPointerException("uri");
        return uri.buildUpon().appendQueryParameter(PARAM_INCLUDE_PENDING, "1").build();
    }

    public static Uri setRequireOriginal(Uri uri) {
        if (uri == null) throw new NullPointerException("uri");
        return uri.buildUpon().appendQueryParameter(PARAM_REQUIRE_ORIGINAL, "1").build();
    }

    public static boolean getRequireOriginal(Uri uri) {
        if (uri == null) throw new NullPointerException("uri");
        return "1".equals(uri.getQueryParameter(PARAM_REQUIRE_ORIGINAL));
    }

    public static ParcelFileDescriptor getOriginalMediaFormatFileDescriptor(
            Context context, ParcelFileDescriptor fileDescriptor) throws IOException {
        if (fileDescriptor == null) throw new NullPointerException("fileDescriptor");
        return fileDescriptor.dup();
    }

    public static PendingIntent createWriteRequest(ContentResolver resolver, Collection<Uri> uris) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("uris must not be empty");
        throw new UnsupportedOperationException("MediaStore write request dialog is unavailable");
    }

    public static PendingIntent createTrashRequest(
            ContentResolver resolver, Collection<Uri> uris, boolean value) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("uris must not be empty");
        throw new UnsupportedOperationException("MediaStore trash request dialog is unavailable");
    }

    public static PendingIntent createFavoriteRequest(
            ContentResolver resolver, Collection<Uri> uris, boolean value) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("uris must not be empty");
        throw new UnsupportedOperationException("MediaStore favorite request dialog is unavailable");
    }

    public static PendingIntent createDeleteRequest(ContentResolver resolver, Collection<Uri> uris) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (uris == null || uris.isEmpty()) throw new IllegalArgumentException("uris must not be empty");
        throw new UnsupportedOperationException("MediaStore delete request dialog is unavailable");
    }

    public static Set<String> getExternalVolumeNames(Context context) {
        if (context == null) throw new NullPointerException("context");
        Set<String> names = new LinkedHashSet<String>();
        names.add(VOLUME_EXTERNAL_PRIMARY);
        return Collections.unmodifiableSet(names);
    }

    public static Set<String> getRecentExternalVolumeNames(Context context) {
        return getExternalVolumeNames(context);
    }

    public static String getVolumeName(Uri uri) {
        if (uri == null) throw new NullPointerException("uri");
        List<String> segments = uri.getPathSegments();
        if (AUTHORITY.equals(uri.getAuthority()) && segments != null && !segments.isEmpty()) {
            return segments.get(0);
        }
        throw new IllegalArgumentException("Missing volume name: " + uri);
    }

    public static Uri getMediaScannerUri() {
        return Uri.parse("content://media/none/media_scanner");
    }

    public static String getVersion(Context context) {
        return getVersion(context, VOLUME_EXTERNAL_PRIMARY);
    }

    public static String getVersion(Context context, String volumeName) {
        if (context == null) throw new NullPointerException("context");
        if (volumeName == null) throw new NullPointerException("volumeName");
        return "shiraz-1";
    }

    public static long getGeneration(Context context, String volumeName) {
        if (context == null) throw new NullPointerException("context");
        if (volumeName == null) throw new NullPointerException("volumeName");
        return 0L;
    }

    public static Uri getDocumentUri(Context context, Uri mediaUri) {
        if (context == null) throw new NullPointerException("context");
        if (mediaUri == null) throw new NullPointerException("mediaUri");
        return null;
    }

    public static Uri getMediaUri(Context context, Uri documentUri) {
        if (context == null) throw new NullPointerException("context");
        if (documentUri == null) throw new NullPointerException("documentUri");
        return null;
    }

    public static boolean isCurrentSystemGallery(ContentResolver resolver, int uid, String packageName) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (packageName == null) throw new NullPointerException("packageName");
        return false;
    }

    public static Uri getRedactedUri(ContentResolver resolver, Uri uri) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (uri == null) throw new NullPointerException("uri");
        return uri;
    }

    public static List<Uri> getRedactedUri(ContentResolver resolver, List<Uri> uris) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (uris == null) throw new NullPointerException("uris");
        return new ArrayList<Uri>(uris);
    }

    public static boolean canManageMedia(Context context) {
        if (context == null) throw new NullPointerException("context");
        return false;
    }

    public static boolean isCurrentCloudMediaProviderAuthority(
            ContentResolver resolver, String authority) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (authority == null) throw new NullPointerException("authority");
        return false;
    }

    public static boolean isSupportedCloudMediaProviderAuthority(
            ContentResolver resolver, String authority) {
        if (resolver == null) throw new NullPointerException("resolver");
        if (authority == null) throw new NullPointerException("authority");
        return false;
    }

    public static void notifyCloudMediaChangedEvent(
            ContentResolver resolver, String authority, String currentMediaCollectionId)
            throws SecurityException {
        if (resolver == null) throw new NullPointerException("resolver");
        if (authority == null) throw new NullPointerException("authority");
        if (currentMediaCollectionId == null) throw new NullPointerException("currentMediaCollectionId");
    }

    public interface MediaColumns extends BaseColumns {
        String DATA = "_data";
        String SIZE = "_size";
        String DISPLAY_NAME = "_display_name";
        String TITLE = "title";
        String DATE_ADDED = "date_added";
        String DATE_MODIFIED = "date_modified";
        String DATE_TAKEN = "datetaken";
        String MIME_TYPE = "mime_type";
        String IS_DRM = "is_drm";
        String IS_PENDING = "is_pending";
        String IS_TRASHED = "is_trashed";
        String DATE_EXPIRES = "date_expires";
        String WIDTH = "width";
        String HEIGHT = "height";
        String RESOLUTION = "resolution";
        String OWNER_PACKAGE_NAME = "owner_package_name";
        String VOLUME_NAME = "volume_name";
        String RELATIVE_PATH = "relative_path";
        String BUCKET_ID = "bucket_id";
        String BUCKET_DISPLAY_NAME = "bucket_display_name";
        String DOCUMENT_ID = "document_id";
        String INSTANCE_ID = "instance_id";
        String ORIGINAL_DOCUMENT_ID = "original_document_id";
        String ORIENTATION = "orientation";
        String IS_FAVORITE = "is_favorite";
        String IS_DOWNLOAD = "is_download";
        String GENERATION_ADDED = "generation_added";
        String GENERATION_MODIFIED = "generation_modified";
        String XMP = "xmp";
        String CD_TRACK_NUMBER = "cd_track_number";
        String ALBUM = "album";
        String ARTIST = "artist";
        String AUTHOR = "author";
        String COMPOSER = "composer";
        String GENRE = "genre";
        String YEAR = "year";
        String DURATION = "duration";
        String NUM_TRACKS = "num_tracks";
        String WRITER = "writer";
        String ALBUM_ARTIST = "album_artist";
        String DISC_NUMBER = "disc_number";
        String COMPILATION = "compilation";
        String BITRATE = "bitrate";
        String CAPTURE_FRAMERATE = "capture_framerate";
    }

    public static class PickerMediaColumns {
        private PickerMediaColumns() {
        }

        public static final String DISPLAY_NAME = "_display_name";
        public static final String DATA = "_data";
        public static final String MIME_TYPE = "mime_type";
        public static final String DATE_TAKEN = "datetaken";
        public static final String SIZE = "_size";
        public static final String DURATION_MILLIS = "duration";
        public static final String HEIGHT = "height";
        public static final String WIDTH = "width";
        public static final String ORIENTATION = "orientation";
    }

    public static final class Files {
        public Files() {
        }

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://media/" + volumeName + "/file");
        }

        public static Uri getContentUri(String volumeName, long rowId) {
            return ContentUris.withAppendedId(getContentUri(volumeName), rowId);
        }

        public interface FileColumns extends MediaColumns {
            String PARENT = "parent";
            String MIME_TYPE = "mime_type";
            String MEDIA_TYPE = "media_type";
            int MEDIA_TYPE_NONE = 0;
            int MEDIA_TYPE_IMAGE = 1;
            int MEDIA_TYPE_AUDIO = 2;
            int MEDIA_TYPE_VIDEO = 3;
            int MEDIA_TYPE_PLAYLIST = 4;
            int MEDIA_TYPE_SUBTITLE = 5;
            int MEDIA_TYPE_DOCUMENT = 6;
        }
    }

    public interface DownloadColumns extends MediaColumns {
        String DOWNLOAD_URI = "download_uri";
        String REFERER_URI = "referer_uri";
    }

    public static final class Downloads implements DownloadColumns {
        private Downloads() {
        }

        public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
        public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/download";

        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://media/" + volumeName + "/downloads");
        }

        public static Uri getContentUri(String volumeName, long id) {
            return ContentUris.withAppendedId(getContentUri(volumeName), id);
        }
    }

    private static Size thumbnailKindSize(int kind) {
        if (kind == Images.Thumbnails.MINI_KIND) {
            return new Size(512, 384);
        }
        if (kind == Images.Thumbnails.FULL_SCREEN_KIND) {
            return new Size(1024, 786);
        }
        if (kind == Images.Thumbnails.MICRO_KIND) {
            return new Size(96, 96);
        }
        throw new IllegalArgumentException("Unsupported kind: " + kind);
    }

    public static final class Images {
        public Images() {
        }

        public interface ImageColumns extends MediaColumns {
            String DESCRIPTION = "description";
            String PICASA_ID = "picasa_id";
            String IS_PRIVATE = "isprivate";
            String LATITUDE = "latitude";
            String LONGITUDE = "longitude";
            @Deprecated String MINI_THUMB_MAGIC = "mini_thumb_magic";
            String SCENE_CAPTURE_TYPE = "scene_capture_type";
            String EXPOSURE_TIME = "exposure_time";
            String F_NUMBER = "f_number";
            String ISO = "iso";
        }

        public static final class Media implements ImageColumns {
            public Media() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/image";
            public static final String DEFAULT_SORT_ORDER = ImageColumns.BUCKET_DISPLAY_NAME;

            @Deprecated
            public static Cursor query(ContentResolver cr, Uri uri, String[] projection) {
                return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
            }

            @Deprecated
            public static Cursor query(
                    ContentResolver cr, Uri uri, String[] projection, String where, String orderBy) {
                return cr.query(
                        uri,
                        projection,
                        where,
                        null,
                        orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
            }

            @Deprecated
            public static Cursor query(
                    ContentResolver cr,
                    Uri uri,
                    String[] projection,
                    String selection,
                    String[] selectionArgs,
                    String orderBy) {
                return cr.query(
                        uri,
                        projection,
                        selection,
                        selectionArgs,
                        orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
            }

            @Deprecated
            public static Bitmap getBitmap(ContentResolver cr, Uri url)
                    throws FileNotFoundException, IOException {
                InputStream input = cr.openInputStream(url);
                if (input == null) return null;
                try {
                    return BitmapFactory.decodeStream(input);
                } finally {
                    input.close();
                }
            }

            @Deprecated
            public static String insertImage(
                    ContentResolver cr, String imagePath, String name, String description)
                    throws FileNotFoundException {
                FileInputStream stream = new FileInputStream(imagePath);
                try {
                    Bitmap bm = BitmapFactory.decodeFile(imagePath);
                    if (bm == null) return null;
                    try {
                        return insertImage(cr, bm, name, description);
                    } finally {
                        bm.recycle();
                    }
                } finally {
                    try {
                        stream.close();
                    } catch (IOException ignored) {
                    }
                }
            }

            @Deprecated
            public static String insertImage(
                    ContentResolver cr, Bitmap source, String title, String description) {
                ContentValues values = new ContentValues();
                values.put(Media.DISPLAY_NAME, title);
                values.put(Media.DESCRIPTION, description);
                values.put(Media.MIME_TYPE, "image/jpeg");
                Uri url = cr.insert(EXTERNAL_CONTENT_URI, values);
                if (url == null) return null;
                if (source != null) {
                    try {
                        OutputStream imageOut = cr.openOutputStream(url);
                        if (imageOut != null) {
                            try {
                                source.compress(Bitmap.CompressFormat.JPEG, 50, imageOut);
                            } finally {
                                imageOut.close();
                            }
                        }
                    } catch (Exception e) {
                        cr.delete(url, null, null);
                        return null;
                    }
                } else {
                    cr.delete(url, null, null);
                    return null;
                }
                return url.toString();
            }

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/images/media");
            }

            public static Uri getContentUri(String volumeName, long id) {
                return ContentUris.withAppendedId(getContentUri(volumeName), id);
            }
        }

        @Deprecated
        public static class Thumbnails implements BaseColumns {
            public Thumbnails() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String DEFAULT_SORT_ORDER = "image_id ASC";

            public static final String DATA = "_data";
            public static final String IMAGE_ID = "image_id";
            public static final String KIND = "kind";
            public static final int MINI_KIND = 1;
            public static final int FULL_SCREEN_KIND = 2;
            public static final int MICRO_KIND = 3;
            public static final String THUMB_DATA = "thumb_data";
            public static final String WIDTH = "width";
            public static final String HEIGHT = "height";

            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection) {
                return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
            }

            public static final Cursor queryMiniThumbnails(
                    ContentResolver cr, Uri uri, int kind, String[] projection) {
                return cr.query(uri, projection, "kind = " + kind, null, DEFAULT_SORT_ORDER);
            }

            public static final Cursor queryMiniThumbnail(
                    ContentResolver cr, long origId, int kind, String[] projection) {
                return cr.query(
                        EXTERNAL_CONTENT_URI,
                        projection,
                        IMAGE_ID + " = " + origId + " AND " + KIND + " = " + kind,
                        null,
                        null);
            }

            public static void cancelThumbnailRequest(ContentResolver cr, long origId) {
            }

            public static Bitmap getThumbnail(
                    ContentResolver cr, long imageId, int kind, BitmapFactory.Options options) {
                return null;
            }

            public static void cancelThumbnailRequest(ContentResolver cr, long origId, long groupId) {
            }

            public static Bitmap getThumbnail(
                    ContentResolver cr,
                    long imageId,
                    long groupId,
                    int kind,
                    BitmapFactory.Options options) {
                return null;
            }

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/images/thumbnails");
            }

            @Deprecated
            public static Size getKindSize(int kind) {
                return thumbnailKindSize(kind);
            }
        }
    }

    public static final class Audio {
        public Audio() {
        }

        public interface AudioColumns extends MediaColumns {
            String TITLE_KEY = "title_key";
            String BOOKMARK = "bookmark";
            String ARTIST_ID = "artist_id";
            String ARTIST_KEY = "artist_key";
            String ALBUM_ID = "album_id";
            String ALBUM_KEY = "album_key";
            String TRACK = "track";
            String YEAR = "year";
            String IS_MUSIC = "is_music";
            String IS_PODCAST = "is_podcast";
            String IS_RINGTONE = "is_ringtone";
            String IS_ALARM = "is_alarm";
            String IS_NOTIFICATION = "is_notification";
            String IS_AUDIOBOOK = "is_audiobook";
            String IS_RECORDING = "is_recording";
            String GENRE = "genre";
            String GENRE_ID = "genre_id";
            String GENRE_KEY = "genre_key";
            String TITLE_RESOURCE_URI = "title_resource_uri";
        }

        public static String keyFor(String name) {
            if (name == null) return null;
            if (UNKNOWN_STRING.equals(name)) return "\u0001";
            boolean sortFirst = name.startsWith("\u0001");
            String cleaned = name.trim().toLowerCase(Locale.ROOT);
            if (cleaned.startsWith("the ")) cleaned = cleaned.substring(4);
            if (cleaned.startsWith("an ")) cleaned = cleaned.substring(3);
            if (cleaned.startsWith("a ")) cleaned = cleaned.substring(2);
            cleaned = cleaned.replaceAll("[\\[\\]\\(\\)\"'.,?!]", "").trim();
            if (cleaned.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            if (sortFirst) sb.append('\u0001');
            for (int i = 0; i < cleaned.length(); i++) {
                sb.append(cleaned.charAt(i));
            }
            return sb.toString();
        }

        public static final class Media implements AudioColumns {
            public Media() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/audio";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/audio";
            public static final String DEFAULT_SORT_ORDER = TITLE_KEY;
            public static final String RECORD_SOUND_ACTION = "android.provider.MediaStore.RECORD_SOUND";
            public static final String EXTRA_MAX_BYTES = "android.provider.MediaStore.extra.MAX_BYTES";

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/media");
            }

            public static Uri getContentUri(String volumeName, long id) {
                return ContentUris.withAppendedId(getContentUri(volumeName), id);
            }

            @Deprecated
            public static Uri getContentUriForPath(String path) {
                if (path == null) throw new NullPointerException("path");
                File ext = Environment.getExternalStorageDirectory();
                return (ext != null && path.startsWith(ext.getPath()))
                        ? EXTERNAL_CONTENT_URI
                        : INTERNAL_CONTENT_URI;
            }
        }

        public interface GenresColumns {
            String NAME = "name";
        }

        public static final class Genres implements BaseColumns, GenresColumns {
            public Genres() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/genre";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/genre";
            public static final String DEFAULT_SORT_ORDER = NAME;

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/genres");
            }

            public static Uri getContentUriForAudioId(String volumeName, int audioId) {
                return Uri.parse("content://media/" + volumeName + "/audio/media/" + audioId + "/genres");
            }

            public static final class Members implements AudioColumns {
                public Members() {
                }

                public static final String CONTENT_DIRECTORY = "members";
                public static final String DEFAULT_SORT_ORDER = TITLE_KEY;
                public static final String AUDIO_ID = "audio_id";
                public static final String GENRE_ID = "genre_id";

                public static Uri getContentUri(String volumeName, long genreId) {
                    return Uri.parse(
                            "content://media/" + volumeName + "/audio/genres/" + genreId + "/members");
                }
            }
        }

        @Deprecated
        public interface PlaylistsColumns extends MediaColumns {
            String NAME = "name";
            String DATA = "_data";
            String DATE_ADDED = "date_added";
            String DATE_MODIFIED = "date_modified";
        }

        @Deprecated
        public static final class Playlists implements BaseColumns, PlaylistsColumns {
            public Playlists() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/playlist";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/playlist";
            public static final String DEFAULT_SORT_ORDER = NAME;

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/playlists");
            }

            public static final class Members implements AudioColumns {
                public Members() {
                }

                public static final String _ID = "_id";
                public static final String CONTENT_DIRECTORY = "members";
                public static final String AUDIO_ID = "audio_id";
                public static final String PLAYLIST_ID = "playlist_id";
                public static final String PLAY_ORDER = "play_order";
                public static final String DEFAULT_SORT_ORDER = PLAY_ORDER;

                public static Uri getContentUri(String volumeName, long playlistId) {
                    return Uri.parse(
                            "content://media/" + volumeName + "/audio/playlists/" + playlistId + "/members");
                }

                public static boolean moveItem(
                        ContentResolver res, long playlistId, int from, int to) {
                    Uri uri = getContentUri(VOLUME_EXTERNAL, playlistId)
                            .buildUpon()
                            .appendEncodedPath(String.valueOf(from))
                            .appendQueryParameter("move", "true")
                            .build();
                    ContentValues values = new ContentValues();
                    values.put(PLAY_ORDER, to);
                    return res.update(uri, values, null, null) != 0;
                }
            }
        }

        public interface ArtistColumns {
            String ARTIST = "artist";
            String ARTIST_KEY = "artist_key";
            String NUMBER_OF_ALBUMS = "number_of_albums";
            String NUMBER_OF_TRACKS = "number_of_tracks";
        }

        public static final class Artists implements BaseColumns, ArtistColumns {
            public Artists() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/artists";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/artist";
            public static final String DEFAULT_SORT_ORDER = ARTIST_KEY;

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/artists");
            }

            public static final class Albums implements BaseColumns, AlbumColumns {
                public Albums() {
                }

                public static Uri getContentUri(String volumeName, long artistId) {
                    return Uri.parse(
                            "content://media/" + volumeName + "/audio/artists/" + artistId + "/albums");
                }
            }
        }

        public interface AlbumColumns {
            String ALBUM_ID = "album_id";
            String ALBUM = "album";
            String ARTIST = "artist";
            String ARTIST_ID = "artist_id";
            String ARTIST_KEY = "artist_key";
            String NUMBER_OF_SONGS = "numsongs";
            String NUMBER_OF_SONGS_FOR_ARTIST = "numsongs_by_artist";
            String FIRST_YEAR = "minyear";
            String LAST_YEAR = "maxyear";
            String ALBUM_KEY = "album_key";
            @Deprecated String ALBUM_ART = "album_art";
        }

        public static final class Albums implements BaseColumns, AlbumColumns {
            public Albums() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/albums";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/album";
            public static final String DEFAULT_SORT_ORDER = ALBUM_KEY;

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/albums");
            }
        }

        public static final class Radio {
            private Radio() {
            }

            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/radio";
        }
    }

    public static final class Video {
        public Video() {
        }

        public static final String DEFAULT_SORT_ORDER = MediaColumns.DISPLAY_NAME;

        @Deprecated
        public static Cursor query(ContentResolver cr, Uri uri, String[] projection) {
            return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
        }

        public interface VideoColumns extends MediaColumns {
            String DESCRIPTION = "description";
            String IS_PRIVATE = "isprivate";
            String TAGS = "tags";
            String CATEGORY = "category";
            String LANGUAGE = "language";
            String LATITUDE = "latitude";
            String LONGITUDE = "longitude";
            String BOOKMARK = "bookmark";
            @Deprecated String MINI_THUMB_MAGIC = "mini_thumb_magic";
            String COLOR_STANDARD = "color_standard";
            String COLOR_TRANSFER = "color_transfer";
            String COLOR_RANGE = "color_range";
        }

        public static final class Media implements VideoColumns {
            public Media() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/video";
            public static final String DEFAULT_SORT_ORDER = TITLE;

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/video/media");
            }

            public static Uri getContentUri(String volumeName, long id) {
                return ContentUris.withAppendedId(getContentUri(volumeName), id);
            }
        }

        @Deprecated
        public static class Thumbnails implements BaseColumns {
            public Thumbnails() {
            }

            public static final Uri INTERNAL_CONTENT_URI = getContentUri(VOLUME_INTERNAL);
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri(VOLUME_EXTERNAL);
            public static final String DEFAULT_SORT_ORDER = "video_id ASC";

            public static final String DATA = "_data";
            public static final String VIDEO_ID = "video_id";
            public static final String KIND = "kind";
            public static final int MINI_KIND = 1;
            public static final int FULL_SCREEN_KIND = 2;
            public static final int MICRO_KIND = 3;
            public static final String WIDTH = "width";
            public static final String HEIGHT = "height";

            public static void cancelThumbnailRequest(ContentResolver cr, long origId) {
            }

            public static Bitmap getThumbnail(
                    ContentResolver cr, long videoId, int kind, BitmapFactory.Options options) {
                return null;
            }

            public static void cancelThumbnailRequest(ContentResolver cr, long origId, long groupId) {
            }

            public static Bitmap getThumbnail(
                    ContentResolver cr,
                    long videoId,
                    long groupId,
                    int kind,
                    BitmapFactory.Options options) {
                return null;
            }

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/video/thumbnails");
            }

            @Deprecated
            public static Size getKindSize(int kind) {
                return thumbnailKindSize(kind);
            }
        }
    }
}
