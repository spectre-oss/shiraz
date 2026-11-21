// SPDX-License-Identifier: GPL-3.0-only
package android.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentSender;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Path;
import android.provider.DocumentsContract.Root;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Base class for a document provider backed by roots and hierarchical documents. */
public abstract class DocumentsProvider extends ContentProvider {
    private String mAuthority;

    public DocumentsProvider() {
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        if (info != null) {
            mAuthority = info.authority;
        }
        super.attachInfo(context, info);
    }

    public boolean isChildDocument(String parentDocumentId, String documentId) {
        return false;
    }

    private void enforceTree(Uri documentUri) {
        if (documentUri != null && DocumentsContract.isTreeUri(documentUri)) {
            String parent = DocumentsContract.getTreeDocumentId(documentUri);
            String child = DocumentsContract.getDocumentId(documentUri);
            if (Objects.equals(parent, child)) {
                return;
            }
            if (!isChildDocument(parent, child)) {
                throw new SecurityException(
                        "Document " + child + " is not a descendant of " + parent);
            }
        }
    }

    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Create not supported");
    }

    public String renameDocument(String documentId, String displayName)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Rename not supported");
    }

    public void deleteDocument(String documentId) throws FileNotFoundException {
        throw new UnsupportedOperationException("Delete not supported");
    }

    public String copyDocument(String sourceDocumentId, String targetParentDocumentId)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Copy not supported");
    }

    public String moveDocument(
            String sourceDocumentId,
            String sourceParentDocumentId,
            String targetParentDocumentId)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Move not supported");
    }

    public void removeDocument(String documentId, String parentDocumentId)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Remove not supported");
    }

    public Path findDocumentPath(String parentDocumentId, String childDocumentId)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("findDocumentPath not supported");
    }

    public IntentSender createWebLinkIntent(String documentId, Bundle options)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("createWebLink is not supported.");
    }

    public abstract Cursor queryRoots(String[] projection) throws FileNotFoundException;

    public Cursor queryRecentDocuments(String rootId, String[] projection)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Recent not supported");
    }

    public Cursor queryRecentDocuments(
            String rootId,
            String[] projection,
            Bundle queryArgs,
            CancellationSignal signal)
            throws FileNotFoundException {
        return queryRecentDocuments(rootId, projection);
    }

    public abstract Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException;

    public abstract Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException;

    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, Bundle queryArgs)
            throws FileNotFoundException {
        String sortOrder = queryArgs != null
                ? queryArgs.getString("android:query-arg-sql-sort-order")
                : null;
        return queryChildDocuments(parentDocumentId, projection, sortOrder);
    }

    public Cursor querySearchDocuments(String rootId, String query, String[] projection)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Search not supported");
    }

    public Cursor querySearchDocuments(String rootId, String[] projection, Bundle queryArgs)
            throws FileNotFoundException {
        String query = queryArgs != null
                ? queryArgs.getString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, "")
                : "";
        return querySearchDocuments(rootId, query, projection);
    }

    public void ejectRoot(String rootId) {
        throw new UnsupportedOperationException("Eject not supported");
    }

    public Bundle getDocumentMetadata(String documentId) throws FileNotFoundException {
        throw new UnsupportedOperationException("Metadata not supported");
    }

    public String getDocumentType(String documentId) throws FileNotFoundException {
        Cursor cursor = queryDocument(documentId, null);
        if (cursor == null) {
            return null;
        }
        try {
            if (cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE);
                if (idx >= 0) {
                    return cursor.getString(idx);
                }
            }
            return null;
        } finally {
            cursor.close();
        }
    }

    public abstract ParcelFileDescriptor openDocument(
            String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException;

    public AssetFileDescriptor openDocumentThumbnail(
            String documentId, Point sizeHint, CancellationSignal signal)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Thumbnails not supported");
    }

    public AssetFileDescriptor openTypedDocument(
            String documentId,
            String mimeTypeFilter,
            Bundle opts,
            CancellationSignal signal)
            throws FileNotFoundException {
        throw new FileNotFoundException("The requested MIME type is not supported.");
    }

    @Override
    public final Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder) {
        return query(uri, projection, selection, selectionArgs, sortOrder, null);
    }

    @Override
    public Cursor query(
            Uri uri,
            String[] projection,
            String selection,
            String[] selectionArgs,
            String sortOrder,
            CancellationSignal cancellationSignal) {
        Bundle queryArgs = null;
        if (sortOrder != null) {
            queryArgs = new Bundle();
            queryArgs.putString("android:query-arg-sql-sort-order", sortOrder);
        }
        return query(uri, projection, queryArgs, cancellationSignal);
    }

    public final Cursor query(
            Uri uri,
            String[] projection,
            Bundle queryArgs,
            CancellationSignal cancellationSignal) {
        if (uri == null) {
            throw new NullPointerException("uri");
        }
        if (cancellationSignal != null) {
            cancellationSignal.throwIfCanceled();
        }
        List<String> parts = uri.getPathSegments();
        try {
            if (parts.size() == 1 && "root".equals(parts.get(0))) {
                return queryRoots(projection);
            }
            if (parts.size() == 3 && "root".equals(parts.get(0)) && "recent".equals(parts.get(2))) {
                return queryRecentDocuments(
                        DocumentsContract.getRootId(uri), projection, queryArgs, cancellationSignal);
            }
            if (parts.size() == 3 && "root".equals(parts.get(0)) && "search".equals(parts.get(2))) {
                String query = DocumentsContract.getSearchDocumentsQuery(uri);
                if (query != null) {
                    return querySearchDocuments(DocumentsContract.getRootId(uri), query, projection);
                }
                return querySearchDocuments(DocumentsContract.getRootId(uri), projection, queryArgs);
            }
            if ((parts.size() == 2 && "document".equals(parts.get(0)))
                    || (parts.size() == 4 && "tree".equals(parts.get(0)) && "document".equals(parts.get(2)))) {
                enforceTree(uri);
                return queryDocument(DocumentsContract.getDocumentId(uri), projection);
            }
            if ((parts.size() == 3 && "document".equals(parts.get(0)) && "children".equals(parts.get(2)))
                    || (parts.size() == 5 && "tree".equals(parts.get(0))
                            && "document".equals(parts.get(2)) && "children".equals(parts.get(4)))) {
                enforceTree(uri);
                if (queryArgs != null) {
                    return queryChildDocuments(DocumentsContract.getDocumentId(uri), projection, queryArgs);
                }
                return queryChildDocuments(DocumentsContract.getDocumentId(uri), projection, (String) null);
            }
            throw new UnsupportedOperationException("Unsupported Uri " + uri);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Failed during query for " + uri, e);
        }
    }

    @Override
    public final String getType(Uri uri) {
        if (uri == null) return null;
        try {
            List<String> parts = uri.getPathSegments();
            if (parts.size() == 2 && "root".equals(parts.get(0))) {
                return Root.MIME_TYPE_ITEM;
            }
            if ((parts.size() == 2 && "document".equals(parts.get(0)))
                    || (parts.size() == 4 && "tree".equals(parts.get(0)) && "document".equals(parts.get(2)))) {
                enforceTree(uri);
                return getDocumentType(DocumentsContract.getDocumentId(uri));
            }
            return null;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public final String getTypeAnonymous(Uri uri) {
        if (uri == null) return null;
        try {
            List<String> parts = uri.getPathSegments();
            if (parts.size() == 2 && "root".equals(parts.get(0))) {
                return Root.MIME_TYPE_ITEM;
            }
            if ((parts.size() == 2 && "document".equals(parts.get(0)))
                    || (parts.size() == 4 && "tree".equals(parts.get(0)) && "document".equals(parts.get(2)))) {
                return getDocumentType(DocumentsContract.getDocumentId(uri));
            }
            return null;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    public Uri canonicalize(Uri uri) {
        if (uri != null && DocumentsContract.isTreeUri(uri)) {
            List<String> parts = uri.getPathSegments();
            if (parts.size() == 4 && "document".equals(parts.get(2))) {
                enforceTree(uri);
                return DocumentsContract.buildDocumentUri(
                        uri.getAuthority(), DocumentsContract.getDocumentId(uri));
            }
        }
        return null;
    }

    @Override
    public final Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("Insert not supported");
    }

    @Override
    public final int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Delete not supported");
    }

    @Override
    public final int update(
            Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("Update not supported");
    }

    private Uri buildDerivedUri(Uri scopeUri, String newDocumentId) {
        String authority = scopeUri.getAuthority() != null ? scopeUri.getAuthority() : mAuthority;
        if (DocumentsContract.isTreeUri(scopeUri)) {
            return DocumentsContract.buildDocumentUriUsingTree(scopeUri, newDocumentId);
        }
        return DocumentsContract.buildDocumentUri(authority, newDocumentId);
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (method == null || !method.startsWith("android:")) {
            return super.call(method, arg, extras);
        }
        try {
            return callUnchecked(method, arg, extras);
        } catch (FileNotFoundException e) {
            throw new IllegalStateException("Failed call " + method, e);
        }
    }

    private Bundle callUnchecked(String method, String arg, Bundle extras)
            throws FileNotFoundException {
        Bundle out = new Bundle();
        if (DocumentsContract.METHOD_EJECT_ROOT.equals(method)) {
            Uri rootUri = extras.getParcelable(DocumentsContract.EXTRA_URI);
            ejectRoot(DocumentsContract.getRootId(rootUri));
            return out;
        }

        Uri documentUri = extras.getParcelable(DocumentsContract.EXTRA_URI);
        enforceTree(documentUri);
        String documentId = DocumentsContract.getDocumentId(documentUri);

        if (DocumentsContract.METHOD_IS_CHILD_DOCUMENT.equals(method)) {
            Uri childUri = extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI);
            String childAuthority = childUri.getAuthority();
            String childId = DocumentsContract.getDocumentId(childUri);
            boolean result = Objects.equals(documentUri.getAuthority(), childAuthority)
                    && isChildDocument(documentId, childId);
            out.putBoolean(DocumentsContract.EXTRA_RESULT, result);
        } else if (DocumentsContract.METHOD_CREATE_DOCUMENT.equals(method)) {
            String mimeType = extras.getString(Document.COLUMN_MIME_TYPE);
            String displayName = extras.getString(Document.COLUMN_DISPLAY_NAME);
            String newDocumentId = createDocument(documentId, mimeType, displayName);
            out.putParcelable(DocumentsContract.EXTRA_URI, buildDerivedUri(documentUri, newDocumentId));
        } else if (DocumentsContract.METHOD_RENAME_DOCUMENT.equals(method)) {
            String displayName = extras.getString(Document.COLUMN_DISPLAY_NAME);
            String newDocumentId = renameDocument(documentId, displayName);
            if (newDocumentId != null) {
                Uri newDocumentUri = buildDerivedUri(documentUri, newDocumentId);
                out.putParcelable(DocumentsContract.EXTRA_URI, newDocumentUri);
            }
        } else if (DocumentsContract.METHOD_DELETE_DOCUMENT.equals(method)) {
            deleteDocument(documentId);
        } else if (DocumentsContract.METHOD_COPY_DOCUMENT.equals(method)) {
            Uri targetUri = extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI);
            enforceTree(targetUri);
            String targetId = DocumentsContract.getDocumentId(targetUri);
            String newDocumentId = copyDocument(documentId, targetId);
            if (newDocumentId != null) {
                out.putParcelable(DocumentsContract.EXTRA_URI, buildDerivedUri(targetUri, newDocumentId));
            }
        } else if (DocumentsContract.METHOD_MOVE_DOCUMENT.equals(method)) {
            Uri parentSourceUri = extras.getParcelable(DocumentsContract.EXTRA_PARENT_URI);
            enforceTree(parentSourceUri);
            String parentSourceId = DocumentsContract.getDocumentId(parentSourceUri);
            Uri targetUri = extras.getParcelable(DocumentsContract.EXTRA_TARGET_URI);
            enforceTree(targetUri);
            String targetId = DocumentsContract.getDocumentId(targetUri);
            String newDocumentId = moveDocument(documentId, parentSourceId, targetId);
            if (newDocumentId != null) {
                out.putParcelable(DocumentsContract.EXTRA_URI, buildDerivedUri(targetUri, newDocumentId));
            }
        } else if (DocumentsContract.METHOD_REMOVE_DOCUMENT.equals(method)) {
            Uri parentSourceUri = extras.getParcelable(DocumentsContract.EXTRA_PARENT_URI);
            enforceTree(parentSourceUri);
            String parentSourceId = DocumentsContract.getDocumentId(parentSourceUri);
            removeDocument(documentId, parentSourceId);
        } else if (DocumentsContract.METHOD_FIND_DOCUMENT_PATH.equals(method)) {
            boolean isTreeUri = DocumentsContract.isTreeUri(documentUri);
            String parentDocumentId = isTreeUri ? DocumentsContract.getTreeDocumentId(documentUri) : null;
            Path path = findDocumentPath(parentDocumentId, documentId);
            if (isTreeUri && path != null) {
                List<String> segments = path.getPath();
                int idx = segments.indexOf(parentDocumentId);
                List<String> trimmed = idx >= 0
                        ? new ArrayList<String>(segments.subList(idx, segments.size()))
                        : segments;
                path = new Path(null, trimmed.isEmpty()
                        ? Collections.singletonList(documentId)
                        : trimmed);
            }
            out.putParcelable(DocumentsContract.EXTRA_RESULT, path);
        } else if (DocumentsContract.METHOD_GET_DOCUMENT_METADATA.equals(method)) {
            return getDocumentMetadata(documentId);
        } else if (DocumentsContract.METHOD_CREATE_WEB_LINK_INTENT.equals(method)) {
            Bundle options = extras.getBundle(DocumentsContract.EXTRA_OPTIONS);
            IntentSender intentSender = createWebLinkIntent(documentId, options);
            out.putParcelable(DocumentsContract.EXTRA_RESULT, intentSender);
        } else {
            throw new UnsupportedOperationException("Method not supported " + method);
        }
        return out;
    }

    public final void revokeDocumentPermission(String documentId) {
        if (documentId == null) {
            throw new NullPointerException("documentId");
        }
    }

    @Override
    public final ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        return openFile(uri, mode, null);
    }

    @Override
    public final ParcelFileDescriptor openFile(
            Uri uri, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        enforceTree(uri);
        return openDocument(DocumentsContract.getDocumentId(uri), mode, signal);
    }

    @Override
    public final AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        return openAssetFile(uri, mode, null);
    }

    @Override
    public final AssetFileDescriptor openAssetFile(
            Uri uri, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        enforceTree(uri);
        ParcelFileDescriptor fd = openDocument(DocumentsContract.getDocumentId(uri), mode, signal);
        return fd != null
                ? new AssetFileDescriptor(fd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
                : null;
    }

    public final AssetFileDescriptor openTypedAssetFile(
            Uri uri, String mimeTypeFilter, Bundle opts)
            throws FileNotFoundException {
        return openTypedAssetFile(uri, mimeTypeFilter, opts, null);
    }

    public final AssetFileDescriptor openTypedAssetFile(
            Uri uri,
            String mimeTypeFilter,
            Bundle opts,
            CancellationSignal signal)
            throws FileNotFoundException {
        enforceTree(uri);
        String documentId = DocumentsContract.getDocumentId(uri);
        if (opts != null && opts.containsKey("android.content.extra.SIZE")) {
            Point sizeHint = opts.getParcelable("android.content.extra.SIZE");
            return openDocumentThumbnail(documentId, sizeHint, signal);
        }
        if ("*/*".equals(mimeTypeFilter)) {
            return openAssetFile(uri, "r", signal);
        }
        String baseType = getType(uri);
        if (baseType != null && mimeTypeMatches(mimeTypeFilter, baseType)) {
            return openAssetFile(uri, "r", signal);
        }
        return openTypedDocument(documentId, mimeTypeFilter, opts, signal);
    }

    public String[] getDocumentStreamTypes(String documentId, String mimeTypeFilter) {
        Cursor cursor = null;
        try {
            cursor = queryDocument(documentId, null);
            if (cursor != null && cursor.moveToFirst()) {
                int mimeIdx = cursor.getColumnIndex(Document.COLUMN_MIME_TYPE);
                String mimeType = mimeIdx >= 0 ? cursor.getString(mimeIdx) : null;
                int flagsIdx = cursor.getColumnIndex(Document.COLUMN_FLAGS);
                int flags = flagsIdx >= 0 ? cursor.getInt(flagsIdx) : 0;
                if ((flags & Document.FLAG_VIRTUAL_DOCUMENT) == 0 && mimeType != null
                        && mimeTypeMatches(mimeTypeFilter, mimeType)) {
                    return new String[] { mimeType };
                }
            }
        } catch (FileNotFoundException e) {
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        enforceTree(uri);
        return getDocumentStreamTypes(DocumentsContract.getDocumentId(uri), mimeTypeFilter);
    }

    private static boolean mimeTypeMatches(String filter, String test) {
        if (test == null) return false;
        if (filter == null || "*/*".equals(filter) || filter.equals(test)) return true;
        int slash = filter.indexOf('/');
        if (slash > 0 && "/*".equals(filter.substring(slash))) {
            return test.regionMatches(0, filter, 0, slash + 1);
        }
        return false;
    }
}
