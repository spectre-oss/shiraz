// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.net.Uri;

/** Media catalog URIs; host media indexing is not implemented. */
public final class MediaStore {
    private MediaStore() {}

    public static final String AUTHORITY = "media";
    public static final Uri AUTHORITY_URI = Uri.parse("content://media");

    public static Uri getMediaScannerUri() { return Uri.parse("content://media/none/media_scanner"); }

    public static final class Images {
        private Images() {}
        public static final class Media {
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/images/media");
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/images/media");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/image";
            public static final String DEFAULT_SORT_ORDER = "bucket_display_name";
            private Media() {}
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/images/media");
            }
        }
        public static final class Thumbnails {
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/images/thumbnails");
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/images/thumbnails");
            public static final String DEFAULT_SORT_ORDER = "image_id ASC";
            private Thumbnails() {}
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/images/thumbnails");
            }
        }
    }

    public static final class Video {
        private Video() {}
        public static final class Media {
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/video/media");
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/video/media");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/video";
            public static final String DEFAULT_SORT_ORDER = "title";
            private Media() {}
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/video/media");
            }
        }
        public static final class Thumbnails {
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/video/thumbnails");
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/video/thumbnails");
            public static final String DEFAULT_SORT_ORDER = "video_id ASC";
            private Thumbnails() {}
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/video/thumbnails");
            }
        }
    }

    public static final class Audio {
        private Audio() {}
        public static final class Media {
            public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/audio/media");
            public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/audio/media");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/audio";
            public static final String DEFAULT_SORT_ORDER = "title_key";
            private Media() {}
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/media");
            }
        }
    }

    public static final class Downloads {
        public static final Uri INTERNAL_CONTENT_URI = Uri.parse("content://media/internal/downloads");
        public static final Uri EXTERNAL_CONTENT_URI = Uri.parse("content://media/external/downloads");
        private Downloads() {}
        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://media/" + volumeName + "/downloads");
        }
    }

    public static final class Files {
        private Files() {}
        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://media/" + volumeName + "/file");
        }
    }

    public static final class MediaColumns {
        public static final String DATA = "_data", SIZE = "_size", DISPLAY_NAME = "_display_name", TITLE = "title",
            DATE_ADDED = "date_added", DATE_MODIFIED = "date_modified", MIME_TYPE = "mime_type", WIDTH = "width", HEIGHT = "height";
        private MediaColumns() {}
    }
}
