// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.database.Cursor;
import java.io.*;
import java.nio.file.*;
import shiraz.runtime.*;

public final class HostDocumentsSmoke {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void remove(File file)throws IOException{if(!Files.isSymbolicLink(file.toPath())&&file.isDirectory())for(File child:file.listFiles())remove(child);Files.deleteIfExists(file.toPath());}
    public static void main(String[] args)throws Exception{
        File data=Files.createTempDirectory("shiraz-document-store-").toFile();
        final HostStorage storage=new HostStorage(data,"org.shiraz.fixture");
        android.os.Environment.shirazInitialize(storage);
        Context context=new ContextWrapper(null){public File getFilesDir(){return storage.files();}public String getPackageName(){return "org.shiraz.fixture";}};
        ContentResolver resolver=new ContentResolver(context);
        HostDocuments store=HostDocuments.forContext(context);
        try{
            java.util.Properties facts=new java.util.Properties();facts.setProperty("package",context.getPackageName());
            android.content.pm.PackageManager manager=new HostPackageManager(facts,context);
            Intent request=new Intent(Intent.ACTION_GET_CONTENT).setType("application/json");
            check(manager.resolveActivity(request,0)!=null,"File picker not resolvable");
            check(manager.resolveActivity(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),0)!=null,"Folder picker not resolvable");
            check(manager.resolveActivity(request,android.content.pm.PackageManager.MATCH_SYSTEM_ONLY)==null,"Picker claimed Android system package");
            check(manager.resolveActivity(new Intent(request).setPackage("unrelated.package"),0)==null,"Explicit package hijacked");
            check(HostDocumentPicker.accepts(request,"application/json")&&!HostDocumentPicker.accepts(request,"image/png"),"MIME filter");
            request.setType("image/*");check(HostDocumentPicker.accepts(request,"image/png")&&!HostDocumentPicker.accepts(request,"application/json"),"MIME family filter");
            request.putExtra("android.intent.extra.MIME_TYPES",new String[]{"text/plain","application/json"});check(HostDocumentPicker.accepts(request,"application/json")&&!HostDocumentPicker.accepts(request,"image/png"),"MIME alternatives");
            File selected=new File(store.volume(),"Exports");check(selected.mkdir(),"Create selected directory");
            Uri tree=store.select(selected);
            Uri root=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));
            check(store.persisted().isEmpty(),"Selection persisted without request");
            check(context.checkCallingOrSelfUriPermission(root,3)==0,"Selected tree not granted");
            resolver.takePersistableUriPermission(tree,3);
            check(resolver.getPersistedUriPermissions().size()==1,"Grant not persisted");
            resolver.getPersistedUriPermissions().clear();check(resolver.getPersistedUriPermissions().size()==1,"Grant snapshot mutable");
            Uri document=DocumentsContract.createDocument(resolver,root,"application/json","حافظ.json");
            try(OutputStream output=resolver.openOutputStream(document)){output.write(new byte[]{123,125});}
            Uri chosen=store.selectFile(new File(selected,"حافظ.json"));
            Intent payload=new Intent().setData(chosen);check(payload.getClipData()==null,"Single result fabricated clip data");
            ClipData clip=ClipData.newRawUri("selected",chosen);payload.setClipData(clip);
            Intent payloadCopy=new Intent(payload);clip.addItem(new ClipData.Item(root));
            check(payload.getClipData()==clip&&payloadCopy.getClipData().getItemCount()==1&&payloadCopy.getClipData().getItemAt(0).getUri().equals(chosen),"Clip copy lost identity or shared item list");
            check(payload.cloneFilter().getClipData()==null,"Filter copy retained payload");
            android.os.Parcel parcel=android.os.Parcel.obtain();
            try{payload.writeToParcel(parcel,0);throw new AssertionError("Clip parcel silently dropped payload");}catch(UnsupportedOperationException expected){}finally{parcel.recycle();}
            payload.setClipData(null);check(payload.getClipData()==null,"Clip clear failed");
            check(store.permitted(chosen,1)&&!store.permitted(chosen,2),"Selected file grant modes");
            try(InputStream input=resolver.openInputStream(chosen)){check(input.read()==123&&input.read()==125,"Selected file cannot be read");}
            Uri sibling=DocumentsContract.buildDocumentUriUsingTree(chosen,DocumentsContract.getTreeDocumentId(chosen)+"other.json");
            Uri chosenRoot=DocumentsContract.buildDocumentUriUsingTree(chosen,DocumentsContract.getTreeDocumentId(chosen));
            check(!store.permitted(sibling,1)&&!store.permitted(chosenRoot,1),"File selection granted sibling or directory");
            try{resolver.takePersistableUriPermission(chosen,1);throw new AssertionError("Session file grant persisted");}catch(SecurityException expected){}
            try{resolver.openOutputStream(chosen);throw new AssertionError("Read-only selection writable");}catch(SecurityException expected){}
            try(Cursor cursor=resolver.query(document,new String[]{"_display_name","_size"},null,null,null)){
                check(cursor.moveToFirst()&&cursor.getString(0).equals("حافظ.json")&&cursor.getLong(1)==2,"Document metadata");
            }
            Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getDocumentId(root));
            try(Cursor cursor=resolver.query(children,null,null,null,null)){check(cursor.getCount()==1,"Child listing");}
            HostDocuments reopened=new HostDocuments(store.volume(),new File(storage.files(),"document-grants.properties"));
            check(reopened.permitted(document,3)&&reopened.persisted().size()==1,"Restart lost grant");
            check(!reopened.permitted(chosen,1),"Session file grant survived restart");
            Uri escaped=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)+"../outside");
            check(!store.permitted(escaped,1),"Parent traversal granted");
            File outside=new File(data,"outside");Files.write(outside.toPath(),new byte[]{99});
            Files.createSymbolicLink(new File(selected,"escape").toPath(),outside.toPath());
            Uri link=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree)+"escape");
            check(!store.permitted(link,1),"Escaping symlink granted");
            try(Cursor cursor=resolver.query(children,null,null,null,null)){check(cursor.getCount()==1,"Escaping symlink listed");}
            Uri renamed=DocumentsContract.renameDocument(resolver,document,"saved.json");
            try(InputStream input=resolver.openInputStream(renamed)){check(input.read()==123&&input.read()==125,"Renamed data lost");}
            check(DocumentsContract.deleteDocument(resolver,renamed)&&!new File(selected,"saved.json").exists(),"Delete failed");
            try{DocumentsContract.deleteDocument(resolver,root);throw new AssertionError("Selected root deleted");}catch(SecurityException expected){}
            reopened.release(tree,3);
            HostDocuments revoked=new HostDocuments(store.volume(),new File(storage.files(),"document-grants.properties"));
            check(!revoked.permitted(root,1)&&revoked.persisted().isEmpty(),"Revoked grant survived restart");
            try{revoked.take(tree,1);throw new AssertionError("Forged grant persisted");}catch(SecurityException expected){}
            System.out.println("HOST_DOCUMENTS_GRANTS_IO_RESTART_CONFINEMENT_OK");
        }finally{android.os.Environment.shirazShutdown();remove(data);}
    }
}
