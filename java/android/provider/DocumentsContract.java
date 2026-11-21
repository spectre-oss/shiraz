// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Parcel;
import android.os.Parcelable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Document URIs and local provider calls. These helpers do not issue access grants. */
public final class DocumentsContract {
    private DocumentsContract() {
    }

    public static final String PROVIDER_INTERFACE = "android.content.action.DOCUMENTS_PROVIDER";
    public static final String EXTRA_INITIAL_URI = "android.provider.extra.INITIAL_URI";
    public static final String EXTRA_EXCLUDE_SELF = "android.provider.extra.EXCLUDE_SELF";
    public static final String EXTRA_ORIENTATION = "android.provider.extra.ORIENTATION";
    public static final String EXTRA_PROMPT = "android.provider.extra.PROMPT";
    public static final String ACTION_DOCUMENT_SETTINGS = "android.provider.action.DOCUMENT_SETTINGS";

    public static final String EXTRA_LOADING = "loading";
    public static final String EXTRA_INFO = "info";
    public static final String EXTRA_ERROR = "error";

    public static final String METADATA_TYPES = "android:documentMetadataTypes";
    public static final String METADATA_EXIF = "android:documentExif";
    public static final String METADATA_TREE_COUNT = "android:metadataTreeCount";
    public static final String METADATA_TREE_SIZE = "android:metadataTreeSize";

    public static final String QUERY_ARG_DISPLAY_NAME = "android:query-arg-display-name";
    public static final String QUERY_ARG_MIME_TYPES = "android:query-arg-mime-types";
    public static final String QUERY_ARG_FILE_SIZE_OVER = "android:query-arg-file-size-over";
    public static final String QUERY_ARG_LAST_MODIFIED_AFTER = "android:query-arg-last-modified-after";
    public static final String QUERY_ARG_EXCLUDE_MEDIA = "android:query-arg-exclude-media";

    static final String EXTRA_URI = "uri";
    static final String EXTRA_TARGET_URI = "targetUri";
    static final String EXTRA_PARENT_URI = "parentUri";
    static final String EXTRA_RESULT = "result";
    static final String EXTRA_OPTIONS = "options";

    static final String METHOD_CREATE_DOCUMENT = "android:createDocument";
    static final String METHOD_RENAME_DOCUMENT = "android:renameDocument";
    static final String METHOD_DELETE_DOCUMENT = "android:deleteDocument";
    static final String METHOD_COPY_DOCUMENT = "android:copyDocument";
    static final String METHOD_MOVE_DOCUMENT = "android:moveDocument";
    static final String METHOD_IS_CHILD_DOCUMENT = "android:isChildDocument";
    static final String METHOD_REMOVE_DOCUMENT = "android:removeDocument";
    static final String METHOD_EJECT_ROOT = "android:ejectRoot";
    static final String METHOD_FIND_DOCUMENT_PATH = "android:findDocumentPath";
    static final String METHOD_CREATE_WEB_LINK_INTENT = "android:createWebLinkIntent";
    static final String METHOD_GET_DOCUMENT_METADATA = "android:getDocumentMetadata";

    public static final class Document {
        private Document() {
        }

        public static final String COLUMN_DOCUMENT_ID = "document_id";
        public static final String COLUMN_MIME_TYPE = "mime_type";
        public static final String COLUMN_DISPLAY_NAME = "_display_name";
        public static final String COLUMN_SUMMARY = "summary";
        public static final String COLUMN_LAST_MODIFIED = "last_modified";
        public static final String COLUMN_ICON = "icon";
        public static final String COLUMN_FLAGS = "flags";
        public static final String COLUMN_SIZE = "_size";

        public static final String MIME_TYPE_DIR = "vnd.android.document/directory";

        public static final int FLAG_SUPPORTS_THUMBNAIL = 1;
        public static final int FLAG_SUPPORTS_WRITE = 2;
        public static final int FLAG_SUPPORTS_DELETE = 4;
        public static final int FLAG_DIR_SUPPORTS_CREATE = 8;
        public static final int FLAG_DIR_PREFERS_GRID = 16;
        public static final int FLAG_DIR_PREFERS_LAST_MODIFIED = 32;
        public static final int FLAG_SUPPORTS_RENAME = 64;
        public static final int FLAG_SUPPORTS_COPY = 128;
        public static final int FLAG_SUPPORTS_MOVE = 256;
        public static final int FLAG_VIRTUAL_DOCUMENT = 512;
        public static final int FLAG_SUPPORTS_REMOVE = 1024;
        public static final int FLAG_SUPPORTS_SETTINGS = 2048;
        public static final int FLAG_WEB_LINKABLE = 4096;
        public static final int FLAG_PARTIAL = 8192;
        public static final int FLAG_SUPPORTS_METADATA = 16384;
        public static final int FLAG_DIR_BLOCKS_OPEN_DOCUMENT_TREE = 32768;
    }

    public static final class Root {
        private Root() {
        }

        public static final String COLUMN_ROOT_ID = "root_id";
        public static final String COLUMN_FLAGS = "flags";
        public static final String COLUMN_ICON = "icon";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_SUMMARY = "summary";
        public static final String COLUMN_DOCUMENT_ID = "document_id";
        public static final String COLUMN_AVAILABLE_BYTES = "available_bytes";
        public static final String COLUMN_CAPACITY_BYTES = "capacity_bytes";
        public static final String COLUMN_MIME_TYPES = "mime_types";
        public static final String COLUMN_QUERY_ARGS = "query_args";

        public static final String MIME_TYPE_ITEM = "vnd.android.document/root";

        public static final int FLAG_SUPPORTS_CREATE = 1;
        public static final int FLAG_LOCAL_ONLY = 2;
        public static final int FLAG_SUPPORTS_RECENTS = 4;
        public static final int FLAG_SUPPORTS_SEARCH = 8;
        public static final int FLAG_SUPPORTS_IS_CHILD = 16;
        public static final int FLAG_SUPPORTS_EJECT = 32;
        public static final int FLAG_EMPTY = 64;
    }

    public static final class Path implements Parcelable {
        private final String mRootId;
        private final List<String> mPath;

        public Path(String rootId, List<String> path) {
            if (path == null) {
                throw new NullPointerException("path");
            }
            if (path.isEmpty()) {
                throw new IllegalArgumentException("path must not be empty");
            }
            for (int i = 0; i < path.size(); i++) {
                if (path.get(i) == null) {
                    throw new NullPointerException("path element " + i + " must not be null");
                }
            }
            mRootId = rootId;
            mPath = Collections.unmodifiableList(new ArrayList<String>(path));
        }

        public String getRootId() {
            return mRootId;
        }

        public List<String> getPath() {
            return mPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Path)) return false;
            Path other = (Path) o;
            return Objects.equals(mRootId, other.mRootId) && Objects.equals(mPath, other.mPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mRootId, mPath);
        }

        @Override
        public String toString() {
            return "DocumentsContract.Path{rootId=" + mRootId + ", path=" + mPath + "}";
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mRootId);
            dest.writeStringArray(mPath.toArray(new String[mPath.size()]));
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<Path> CREATOR = new Parcelable.Creator<Path>() {
            @Override
            public Path createFromParcel(Parcel in) {
                String rootId = in.readString();
                String[] raw = in.createStringArray();
                List<String> path = raw == null ? null : java.util.Arrays.asList(raw);
                return new Path(rootId, path);
            }

            @Override
            public Path[] newArray(int size) {
                return new Path[size];
            }
        };
    }

    private static Bundle request(Uri uri) {
        if (uri == null) throw new NullPointerException("document URI");
        Bundle input = new Bundle();
        input.putParcelable(EXTRA_URI, uri);
        return input;
    }

    private static Bundle callDocumentMethod(ContentResolver resolver, Uri target, String method, Bundle input)
            throws FileNotFoundException {
        try {
            return resolver.call(target, method, null, input);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof FileNotFoundException) {
                throw (FileNotFoundException) e.getCause();
            }
            throw e;
        }
    }

    public static Uri buildRootsUri(String authority) {
        return base(authority).appendPath("root").build();
    }

    public static Uri buildRootUri(String authority, String rootId) {
        return base(authority).appendPath("root").appendPath(rootId).build();
    }

    public static Uri buildRecentDocumentsUri(String authority, String rootId) {
        return buildRootUri(authority, rootId).buildUpon().appendPath("recent").build();
    }

    public static Uri buildTreeDocumentUri(String authority, String documentId) {
        return base(authority).appendPath("tree").appendPath(documentId).build();
    }

    public static Uri buildDocumentUri(String authority, String documentId) {
        return base(authority).appendPath("document").appendPath(documentId).build();
    }

    public static Uri buildDocumentUriUsingTree(Uri treeUri, String documentId) {
        return buildTreeDocumentUri(treeUri.getAuthority(), getTreeDocumentId(treeUri))
                .buildUpon().appendPath("document").appendPath(documentId).build();
    }

    public static Uri buildChildDocumentsUri(String authority, String parentDocumentId) {
        return buildDocumentUri(authority, parentDocumentId).buildUpon().appendPath("children").build();
    }

    public static Uri buildChildDocumentsUriUsingTree(Uri treeUri, String parentDocumentId) {
        return buildDocumentUriUsingTree(treeUri, parentDocumentId).buildUpon().appendPath("children").build();
    }

    public static Uri buildSearchDocumentsUri(String authority, String rootId, String query) {
        return buildRootUri(authority, rootId).buildUpon().appendPath("search")
                .appendQueryParameter("query", query).build();
    }

    private static Uri.Builder base(String authority) {
        return new Uri.Builder().scheme("content").authority(authority);
    }

    private static boolean isDocumentsProvider(Context context, String authority) {
        if (authority == null) return false;
        if (shiraz.runtime.HostDocuments.AUTHORITY.equals(authority)) return true;
        if (context == null) return false;
        try {
            ContentResolver resolver = context.getContentResolver();
            if (resolver == null) {
                resolver = new ContentResolver(context);
            }
            ContentProviderClient client = resolver.acquireContentProviderClient(authority);
            if (client != null) {
                try {
                    if (client.getLocalContentProvider() instanceof DocumentsProvider) {
                        return true;
                    }
                } finally {
                    client.close();
                }
            }
        } catch (RuntimeException ignored) {
        }
        try {
            PackageManager pm = context.getPackageManager();
            if (pm != null) {
                Intent intent = new Intent(PROVIDER_INTERFACE);
                List<ResolveInfo> infos = pm.queryIntentContentProviders(intent, 0);
                if (infos != null) {
                    for (int i = 0; i < infos.size(); i++) {
                        ResolveInfo info = infos.get(i);
                        if (info != null && info.providerInfo != null
                                && authority.equals(info.providerInfo.authority)) {
                            return true;
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    public static boolean isDocumentUri(Context context, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())
                || !isDocumentsProvider(context, uri.getAuthority())) {
            return false;
        }
        List<String> parts = uri.getPathSegments();
        return (parts.size() == 2 && "document".equals(parts.get(0)))
                || (parts.size() == 4 && "tree".equals(parts.get(0)) && "document".equals(parts.get(2)));
    }

    public static boolean isRootsUri(Context context, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())
                || !isDocumentsProvider(context, uri.getAuthority())) {
            return false;
        }
        List<String> parts = uri.getPathSegments();
        return parts.size() == 1 && "root".equals(parts.get(0));
    }

    public static boolean isRootUri(Context context, Uri uri) {
        if (uri == null || !"content".equals(uri.getScheme())
                || !isDocumentsProvider(context, uri.getAuthority())) {
            return false;
        }
        List<String> parts = uri.getPathSegments();
        return parts.size() == 2 && "root".equals(parts.get(0));
    }

    public static boolean isTreeUri(Uri uri) {
        List<String> parts = uri.getPathSegments();
        return parts.size() >= 2 && "tree".equals(parts.get(0));
    }

    public static String getRootId(Uri rootUri) {
        List<String> parts = rootUri.getPathSegments();
        if (parts.size() >= 2 && "root".equals(parts.get(0))) return parts.get(1);
        throw new IllegalArgumentException("Not a root URI: " + rootUri);
    }

    public static String getDocumentId(Uri documentUri) {
        List<String> parts = documentUri.getPathSegments();
        if (parts.size() >= 2 && "document".equals(parts.get(0))) return parts.get(1);
        if (parts.size() >= 4 && "tree".equals(parts.get(0)) && "document".equals(parts.get(2))) return parts.get(3);
        throw new IllegalArgumentException("Not a document URI: " + documentUri);
    }

    public static String getTreeDocumentId(Uri documentUri) {
        List<String> parts = documentUri.getPathSegments();
        if (parts.size() >= 2 && "tree".equals(parts.get(0))) return parts.get(1);
        throw new IllegalArgumentException("Not a document tree URI: " + documentUri);
    }

    public static String getSearchDocumentsQuery(Uri searchDocumentsUri) {
        return searchDocumentsUri.getQueryParameter("query");
    }

    public static Bitmap getDocumentThumbnail(
            ContentResolver content, Uri documentUri, Point size, CancellationSignal signal)
            throws FileNotFoundException {
        if (content == null) throw new NullPointerException("content");
        if (documentUri == null) throw new NullPointerException("documentUri");
        if (size == null) throw new NullPointerException("size");
        if (signal != null) signal.throwIfCanceled();

        AssetFileDescriptor afd = null;
        ContentProviderClient client = content.acquireContentProviderClient(documentUri);
        try {
            if (client != null && client.getLocalContentProvider() instanceof DocumentsProvider) {
                Bundle opts = new Bundle();
                opts.putParcelable("android.content.extra.SIZE", size);
                afd = ((DocumentsProvider) client.getLocalContentProvider())
                        .openTypedAssetFile(documentUri, "image/*", opts, signal);
            } else {
                afd = content.openAssetFileDescriptor(documentUri, "r", signal);
            }
            if (afd == null) return null;
            InputStream in = afd.createInputStream();
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap != null && size.x > 0 && size.y > 0
                        && (bitmap.getWidth() > size.x * 2 || bitmap.getHeight() > size.y * 2)) {
                    return Bitmap.createScaledBitmap(bitmap, size.x, size.y, true);
                }
                return bitmap;
            } finally {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
        } catch (FileNotFoundException e) {
            throw e;
        } catch (IOException e) {
            return null;
        } finally {
            if (afd != null) {
                try {
                    afd.close();
                } catch (IOException ignored) {
                }
            }
            if (client != null) {
                client.close();
            }
        }
    }

    public static Uri createDocument(
            ContentResolver content, Uri parentDocumentUri, String mimeType, String displayName)
            throws FileNotFoundException {
        Bundle input = request(parentDocumentUri);
        input.putString(Document.COLUMN_MIME_TYPE, mimeType);
        input.putString(Document.COLUMN_DISPLAY_NAME, displayName);
        Bundle output = callDocumentMethod(content, parentDocumentUri, METHOD_CREATE_DOCUMENT, input);
        return output == null ? null : (Uri) output.getParcelable(EXTRA_URI);
    }

    public static boolean isChildDocument(
            ContentResolver content, Uri parentDocumentUri, Uri childDocumentUri)
            throws FileNotFoundException {
        if (childDocumentUri == null) throw new NullPointerException("child URI");
        Bundle input = request(parentDocumentUri);
        input.putParcelable(EXTRA_TARGET_URI, childDocumentUri);
        Bundle output = callDocumentMethod(content, parentDocumentUri, METHOD_IS_CHILD_DOCUMENT, input);
        return output != null && output.getBoolean(EXTRA_RESULT, false);
    }

    public static Uri renameDocument(
            ContentResolver content, Uri documentUri, String displayName)
            throws FileNotFoundException {
        Bundle input = request(documentUri);
        input.putString(Document.COLUMN_DISPLAY_NAME, displayName);
        Bundle output = callDocumentMethod(content, documentUri, METHOD_RENAME_DOCUMENT, input);
        if (output == null) return null;
        Uri renamed = output.getParcelable(EXTRA_URI);
        return renamed == null ? documentUri : renamed;
    }

    public static boolean deleteDocument(ContentResolver content, Uri documentUri)
            throws FileNotFoundException {
        return callDocumentMethod(content, documentUri, METHOD_DELETE_DOCUMENT, request(documentUri)) != null;
    }

    public static Uri copyDocument(
            ContentResolver content, Uri sourceDocumentUri, Uri targetParentDocumentUri)
            throws FileNotFoundException {
        if (targetParentDocumentUri == null) throw new NullPointerException("targetParentDocumentUri");
        Bundle input = request(sourceDocumentUri);
        input.putParcelable(EXTRA_TARGET_URI, targetParentDocumentUri);
        Bundle output = callDocumentMethod(content, sourceDocumentUri, METHOD_COPY_DOCUMENT, input);
        return output == null ? null : (Uri) output.getParcelable(EXTRA_URI);
    }

    public static Uri moveDocument(
            ContentResolver content,
            Uri sourceDocumentUri,
            Uri sourceParentDocumentUri,
            Uri targetParentDocumentUri)
            throws FileNotFoundException {
        if (sourceParentDocumentUri == null) throw new NullPointerException("sourceParentDocumentUri");
        if (targetParentDocumentUri == null) throw new NullPointerException("targetParentDocumentUri");
        Bundle input = request(sourceDocumentUri);
        input.putParcelable(EXTRA_PARENT_URI, sourceParentDocumentUri);
        input.putParcelable(EXTRA_TARGET_URI, targetParentDocumentUri);
        Bundle output = callDocumentMethod(content, sourceDocumentUri, METHOD_MOVE_DOCUMENT, input);
        return output == null ? null : (Uri) output.getParcelable(EXTRA_URI);
    }

    public static boolean removeDocument(
            ContentResolver content, Uri documentUri, Uri parentDocumentUri)
            throws FileNotFoundException {
        if (parentDocumentUri == null) throw new NullPointerException("parentDocumentUri");
        Bundle input = request(documentUri);
        input.putParcelable(EXTRA_PARENT_URI, parentDocumentUri);
        return callDocumentMethod(content, documentUri, METHOD_REMOVE_DOCUMENT, input) != null;
    }

    public static void ejectRoot(ContentResolver content, Uri rootUri) {
        try {
            callDocumentMethod(content, rootUri, METHOD_EJECT_ROOT, request(rootUri));
        } catch (FileNotFoundException ignored) {
        }
    }

    public static Bundle getDocumentMetadata(ContentResolver content, Uri documentUri)
            throws FileNotFoundException {
        return callDocumentMethod(content, documentUri, METHOD_GET_DOCUMENT_METADATA, request(documentUri));
    }

    public static Path findDocumentPath(ContentResolver content, Uri treeUri)
            throws FileNotFoundException {
        Bundle output = callDocumentMethod(content, treeUri, METHOD_FIND_DOCUMENT_PATH, request(treeUri));
        return output == null ? null : (Path) output.getParcelable(EXTRA_RESULT);
    }

    public static IntentSender createWebLinkIntent(
            ContentResolver content, Uri uri, Bundle options)
            throws FileNotFoundException {
        Bundle input = request(uri);
        if (options != null) {
            input.putBundle(EXTRA_OPTIONS, options);
        }
        Bundle output = callDocumentMethod(content, uri, METHOD_CREATE_WEB_LINK_INTENT, input);
        return output == null ? null : (IntentSender) output.getParcelable(EXTRA_RESULT);
    }
}
