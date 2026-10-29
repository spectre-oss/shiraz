// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.net.Uri;
import java.util.List;

/** Document URI construction and parsing. These helpers do not issue access grants. */
public final class DocumentsContract {
    private DocumentsContract() { }

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
