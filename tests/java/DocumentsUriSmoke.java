// SPDX-License-Identifier: GPL-3.0-only
import android.net.Uri;
import android.provider.DocumentsContract;

public final class DocumentsUriSmoke {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        String authority = "org.shiraz.fixture.documents";
        String root = "primary:Reports/2026";
        String child = "primary:Reports/2026/حافظ + 100%.json";
        Uri tree = DocumentsContract.buildTreeDocumentUri(authority, root);
        check(tree.toString().equals("content://" + authority + "/tree/primary%3AReports%2F2026"), "Tree ID must be one escaped segment");
        Uri document = DocumentsContract.buildDocumentUriUsingTree(tree, child);
        check(document.getPathSegments().size() == 4, "Embedded slash became a path boundary");
        check(DocumentsContract.getTreeDocumentId(document).equals(root), "Lost grant tree identity");
        check(DocumentsContract.getDocumentId(document).equals(child), "Unicode/percent/plus ID roundtrip");
        check(DocumentsContract.isTreeUri(tree) && DocumentsContract.isTreeUri(document), "Tree recognition");
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(document, root);
        check(children.getPathSegments().size() == 5 && "children".equals(children.getLastPathSegment()), "Child query shape");
        check(DocumentsContract.getDocumentId(children).equals(root), "Child query parent identity");
        check(DocumentsContract.getTreeDocumentId(children).equals(root), "Child query lost grant scope");
        Uri single = DocumentsContract.buildDocumentUri(authority, child);
        check(!DocumentsContract.isTreeUri(single) && DocumentsContract.getDocumentId(single).equals(child), "Single document shape");
        check(DocumentsContract.getDocumentId(DocumentsContract.buildChildDocumentsUri(authority, child)).equals(child), "Single child query");
        check(DocumentsContract.getRootId(DocumentsContract.buildRootUri(authority, root)).equals(root), "Root ID");
        check(DocumentsContract.buildRootsUri(authority).getPath().equals("/root"), "Root list URI");
        check(DocumentsContract.buildRecentDocumentsUri(authority, root).getLastPathSegment().equals("recent"), "Recent URI");
        Uri search = DocumentsContract.buildSearchDocumentsUri(authority, root, "a+b & حافظ");
        check(DocumentsContract.getRootId(search).equals(root) && DocumentsContract.getSearchDocumentsQuery(search).equals("a+b & حافظ"), "Search encoding");
        for (String invalid : new String[]{"content://x", "content://x/tree", "content://x/tree/id", "content://x/tree/id/other/child", "content://x/root/id"}) {
            try { DocumentsContract.getDocumentId(Uri.parse(invalid)); throw new AssertionError("Invalid document accepted: " + invalid); }
            catch (IllegalArgumentException expected) { }
        }
        try { DocumentsContract.getTreeDocumentId(single); throw new AssertionError("Single document treated as tree"); }
        catch (IllegalArgumentException expected) { }
        try { DocumentsContract.getRootId(tree); throw new AssertionError("Tree treated as root"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("DOCUMENT_URI_IDENTITIES_OK");
    }
}
