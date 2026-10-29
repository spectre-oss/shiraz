// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.Files;

/** File-backed provider fixture; this is not a host picker or a grant implementation. */
public final class DocumentsProviderCallSmoke {
    private static final class Provider extends ContentProvider {
        final File root;
        Provider(File root) { this.root = root; }
        public boolean onCreate() { return true; }
        public String getType(Uri uri) { return "application/json"; }
        public android.database.Cursor query(Uri u,String[] p,String s,String[] a,String o) { throw new UnsupportedOperationException(); }
        public Uri insert(Uri u,ContentValues v) { throw new UnsupportedOperationException(); }
        public int delete(Uri u,String s,String[] a) { throw new UnsupportedOperationException(); }
        public int update(Uri u,ContentValues v,String s,String[] a) { throw new UnsupportedOperationException(); }
        private File file(String name) {
            if (name == null || name.isEmpty() || name.equals(".") || name.equals("..") || name.indexOf('/') >= 0)
                throw new IllegalArgumentException("Invalid fixture name");
            return new File(root, name);
        }
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            return ParcelFileDescriptor.open(file(DocumentsContract.getDocumentId(uri)),
                    mode.equals("r") ? ParcelFileDescriptor.MODE_READ_ONLY : ParcelFileDescriptor.MODE_READ_WRITE);
        }
        public Bundle call(String method, String arg, Bundle input) {
            if (method.equals("echo")) { Bundle out = new Bundle(); out.putString("arg", arg); return out; }
            if (method.equals("deny")) throw new SecurityException("Fixture denied request");
            if (method.equals("unsupported")) return null;
            Uri uri = input.getParcelable("uri");
            Bundle out = new Bundle();
            try {
                if (method.equals("android:createDocument")) {
                    check(DocumentsContract.getDocumentId(uri).equals("root"), "Wrong parent");
                    check(input.getString("mime_type").equals("application/json"), "Missing MIME type");
                    String name = input.getString("_display_name");
                    if (!file(name).createNewFile()) throw new IllegalStateException("Already exists");
                    out.putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(uri, name));
                } else if (method.equals("android:renameDocument")) {
                    String name = input.getString("_display_name");
                    if (!file(DocumentsContract.getDocumentId(uri)).renameTo(file(name))) throw new IllegalStateException("Rename failed");
                    out.putParcelable("uri", DocumentsContract.buildDocumentUriUsingTree(uri, name));
                } else if (method.equals("android:deleteDocument")) {
                    if (!file(DocumentsContract.getDocumentId(uri)).delete()) throw new IllegalStateException("Delete failed");
                } else if (method.equals("android:isChildDocument")) {
                    Uri child = input.getParcelable("targetUri");
                    out.putBoolean("result", uri.getAuthority().equals(child.getAuthority()) && file(DocumentsContract.getDocumentId(child)).isFile());
                } else throw new UnsupportedOperationException(method);
                return out;
            } catch (IOException error) { throw new IllegalStateException(error); }
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("shiraz-documents-").toFile();
        Context context = new ContextWrapper(null); ContentResolver resolver = new ContentResolver(context);
        ProviderInfo info = new ProviderInfo(); info.authority = "fixture.documents";
        new Provider(root).attachInfo(context, info);
        Uri parent = DocumentsContract.buildDocumentUriUsingTree(DocumentsContract.buildTreeDocumentUri(info.authority, "root"), "root");
        try {
            Uri created = DocumentsContract.createDocument(resolver, parent, "application/json", "export حافظ.json");
            check(created != null && DocumentsContract.getTreeDocumentId(created).equals("root"), "Created tree identity");
            try (OutputStream stream = resolver.openOutputStream(created)) { stream.write(new byte[]{123,125}); }
            check(java.util.Arrays.equals(Files.readAllBytes(new File(root, "export حافظ.json").toPath()), new byte[]{123,125}), "Created document bytes");
            check(DocumentsContract.isChildDocument(resolver, parent, created), "Existing child not found");
            Uri renamed = DocumentsContract.renameDocument(resolver, created, "renamed.json");
            check(renamed != null && !new File(root, "export حافظ.json").exists(), "Rename did not move file");
            try (InputStream stream = resolver.openInputStream(renamed)) { check(stream.read()==123 && stream.read()==125 && stream.read()==-1, "Rename lost content"); }
            check(DocumentsContract.deleteDocument(resolver, renamed) && !new File(root, "renamed.json").exists(), "Delete did not remove file");
            check(!DocumentsContract.isChildDocument(resolver, parent, renamed), "Deleted child still present");
            check("value".equals(resolver.call(info.authority, "echo", "value", null).getString("arg")), "Authority call arguments");
            try (ContentProviderClient client = resolver.acquireContentProviderClient(parent)) {
                check("client".equals(client.call("echo", "client", null).getString("arg")), "Client call routing");
            }
            check(resolver.call(parent,"unsupported",null,null)==null, "Null provider response changed");
            try { resolver.call(parent,"deny",null,null); throw new AssertionError("Denied call succeeded"); } catch (SecurityException expected) { }
            try { resolver.call("missing.documents","echo",null,null); throw new AssertionError("Missing provider succeeded"); } catch (IllegalArgumentException expected) { }
            try { resolver.call(Uri.fromFile(root),"echo",null,null); throw new AssertionError("File URI call succeeded"); } catch (IllegalArgumentException expected) { }
            System.out.println("DOCUMENT_PROVIDER_CREATE_WRITE_RENAME_DELETE_OK");
        } finally { for (File file : root.listFiles()) file.delete(); root.delete(); }
    }
}
