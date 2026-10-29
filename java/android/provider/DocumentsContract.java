// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.net.Uri;
import android.content.ContentResolver;
import android.os.Bundle;
import java.io.FileNotFoundException;
import java.util.List;

/** Document URIs and local provider calls. These helpers do not issue access grants. */
public final class DocumentsContract {
    private DocumentsContract() { }

    public static final String EXTRA_URI = "uri", EXTRA_TARGET_URI = "targetUri", EXTRA_RESULT = "result";
    public static final class Document {
        private Document() { }
        public static final String COLUMN_DOCUMENT_ID = "document_id", COLUMN_DISPLAY_NAME = "_display_name",
                COLUMN_MIME_TYPE = "mime_type", COLUMN_SIZE = "_size", COLUMN_LAST_MODIFIED = "last_modified",
                COLUMN_FLAGS = "flags", COLUMN_SUMMARY = "summary", COLUMN_ICON = "icon";
        public static final String MIME_TYPE_DIR = "vnd.android.document/directory";
        public static final int FLAG_SUPPORTS_THUMBNAIL = 1, FLAG_SUPPORTS_WRITE = 2,
                FLAG_SUPPORTS_DELETE = 4, FLAG_DIR_SUPPORTS_CREATE = 8,
                FLAG_SUPPORTS_RENAME = 64, FLAG_SUPPORTS_COPY = 128,
                FLAG_SUPPORTS_MOVE = 256, FLAG_VIRTUAL_DOCUMENT = 512,
                FLAG_SUPPORTS_REMOVE = 1024;
    }

    private static Bundle request(Uri uri) {
        if (uri == null) throw new NullPointerException("document URI");
        Bundle input = new Bundle(); input.putParcelable(EXTRA_URI, uri); return input;
    }

    public static Uri createDocument(ContentResolver resolver, Uri parent, String mimeType, String displayName)
            throws FileNotFoundException {
        Bundle input = request(parent);
        input.putString(Document.COLUMN_MIME_TYPE, mimeType);
        input.putString(Document.COLUMN_DISPLAY_NAME, displayName);
        Bundle output = resolver.call(parent, "android:createDocument", null, input);
        return output == null ? null : (Uri)output.getParcelable(EXTRA_URI);
    }

    public static Uri renameDocument(ContentResolver resolver, Uri document, String displayName)
            throws FileNotFoundException {
        Bundle input = request(document); input.putString(Document.COLUMN_DISPLAY_NAME, displayName);
        Bundle output = resolver.call(document, "android:renameDocument", null, input);
        if (output == null) return null;
        Uri renamed = output.getParcelable(EXTRA_URI); return renamed == null ? document : renamed;
    }

    public static boolean deleteDocument(ContentResolver resolver, Uri document) throws FileNotFoundException {
        // A provider with only the base no-op call implementation cannot confirm deletion.
        return resolver.call(document, "android:deleteDocument", null, request(document)) != null;
    }

    public static boolean isChildDocument(ContentResolver resolver, Uri parent, Uri child) throws FileNotFoundException {
        if (child == null) throw new NullPointerException("child URI");
        Bundle input = request(parent); input.putParcelable(EXTRA_TARGET_URI, child);
        Bundle output = resolver.call(parent, "android:isChildDocument", null, input);
        return output != null && output.getBoolean(EXTRA_RESULT, false);
    }

    private static Uri.Builder base(String authority) {
        return new Uri.Builder().scheme("content").authority(authority);
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

    public static Uri buildSearchDocumentsUri(String authority, String rootId, String query) {
        return buildRootUri(authority, rootId).buildUpon().appendPath("search")
                .appendQueryParameter("query", query).build();
    }

    public static Uri buildDocumentUri(String authority, String documentId) {
        return base(authority).appendPath("document").appendPath(documentId).build();
    }

    public static Uri buildTreeDocumentUri(String authority, String documentId) {
        return base(authority).appendPath("tree").appendPath(documentId).build();
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

    public static boolean isTreeUri(Uri uri) {
        List<String> parts = uri.getPathSegments();
        return parts.size() >= 2 && "tree".equals(parts.get(0));
    }

    public static String getTreeDocumentId(Uri uri) {
        List<String> parts = uri.getPathSegments();
        if (parts.size() >= 2 && "tree".equals(parts.get(0))) return parts.get(1);
        throw new IllegalArgumentException("Not a document tree URI: " + uri);
    }

    public static String getDocumentId(Uri uri) {
        List<String> parts = uri.getPathSegments();
        if (parts.size() >= 2 && "document".equals(parts.get(0))) return parts.get(1);
        if (parts.size() >= 4 && "tree".equals(parts.get(0)) && "document".equals(parts.get(2))) return parts.get(3);
        throw new IllegalArgumentException("Not a document URI: " + uri);
    }

    public static String getRootId(Uri uri) {
        List<String> parts = uri.getPathSegments();
        if (parts.size() >= 2 && "root".equals(parts.get(0))) return parts.get(1);
        throw new IllegalArgumentException("Not a root URI: " + uri);
    }

    public static String getSearchDocumentsQuery(Uri uri) {
        return uri.getQueryParameter("query");
    }
}
