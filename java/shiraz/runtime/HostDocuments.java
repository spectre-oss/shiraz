// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;

import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** App-scoped primary-volume document provider. URI grants are issued only by folder selection. */
public final class HostDocuments extends ContentProvider {
    public static final String AUTHORITY = "shiraz.documents";
    private static final Map<String,HostDocuments> stores = new HashMap<String,HostDocuments>();
    private final File volume, state;
    private final Map<String,Grant> grants = new LinkedHashMap<String,Grant>();
    private static final class Grant {
        final File directory; int session, persisted; long time;
        Grant(File directory, int session, int persisted, long time) {
            this.directory=directory; this.session=session; this.persisted=persisted; this.time=time;
        }
    }
    public static synchronized HostDocuments forContext(Context context) {
        try {
            File files=context.getFilesDir().getCanonicalFile();
            String key=files.getPath(); HostDocuments result=stores.get(key);
            if(result==null){result=new HostDocuments(android.os.Environment.getExternalStorageDirectory(),new File(files,"document-grants.properties"));stores.put(key,result);}
            return result;
        } catch(IOException error){throw new IllegalStateException("Cannot resolve document storage",error);}
    }
    public HostDocuments(File volume, File state) throws IOException {
        this.volume=volume.getCanonicalFile(); this.state=state.getCanonicalFile();
        if(!this.volume.isDirectory())throw new IOException("Document volume unavailable");
        if(state.isFile()){
            if(state.length()>65536)throw new IOException("Document grant state exceeds limit");
            Properties values=new Properties();try(InputStream input=new FileInputStream(state)){values.load(input);}
            for(String key:values.stringPropertyNames())if(key.endsWith(".path")){
                if(grants.size()>=256)throw new IOException("Too many document grants");
                String id=key.substring(0,key.length()-5);
                if(!id.matches("[a-f0-9-]{36}"))throw new IOException("Invalid document grant identity");
                try {
                    int modes=Integer.parseInt(values.getProperty(id+".modes"));
                    long time=Long.parseLong(values.getProperty(id+".time"));
                    if(modes<1||modes>3)throw new IllegalArgumentException();
                    File directory=within(this.volume,new File(this.volume,values.getProperty(key)));
                    grants.put(id,new Grant(directory,0,modes,time));
                }catch(RuntimeException error){throw new IOException("Invalid document grant record",error);}
            }
        }
    }
    public File volume(){return volume;}
    private static File within(File root,File target)throws IOException {
        File canonical=target.getCanonicalFile();
        if(!canonical.toPath().startsWith(root.toPath()))throw new SecurityException("Document escapes selected directory");
        return canonical;
    }
    private static void modes(int value){if(value<1||value>3)throw new IllegalArgumentException("Invalid URI modes");}
    private static Uri tree(String id){return DocumentsContract.buildTreeDocumentUri(AUTHORITY,id+":");}
    public synchronized Uri select(File directory)throws IOException {
        File selected=within(volume,directory);
        if(!selected.isDirectory()||!selected.canRead())throw new IOException("Selected folder unavailable");
        if(grants.size()>=256)throw new IOException("Too many selected folders");
        String id=UUID.randomUUID().toString();grants.put(id,new Grant(selected,selected.canWrite()?3:1,0,0));return tree(id);
    }
    private String treeId(Uri uri){
        if(uri==null||!"content".equals(uri.getScheme())||!AUTHORITY.equals(uri.getAuthority()))throw new SecurityException("Not a host document URI");
        String root=DocumentsContract.getTreeDocumentId(uri);
        if(!root.matches("[a-f0-9-]{36}:"))throw new SecurityException("Invalid selected tree identity");
        return root.substring(0,root.length()-1);
    }
    private Grant grant(Uri uri,int requested){
        String id=treeId(uri);Grant grant=grants.get(id);
        if(grant==null||((grant.session|grant.persisted)&requested)!=requested)throw new SecurityException("No document URI grant");
        return grant;
    }
    public synchronized void take(Uri uri,int requested){
        modes(requested);Grant grant=grant(uri,requested);int previous=grant.persisted;long oldTime=grant.time;
        grant.persisted|=requested;grant.time=System.currentTimeMillis();
        try{save();}catch(IOException error){grant.persisted=previous;grant.time=oldTime;throw new IllegalStateException("Cannot persist document grant",error);}
    }
    public synchronized void release(Uri uri,int requested){
        modes(requested);Grant grant=grants.get(treeId(uri));
        if(grant==null||(grant.persisted&requested)!=requested)throw new SecurityException("No persisted document grant");
        int previous=grant.persisted;grant.persisted&=~requested;
        try{save();}catch(IOException error){grant.persisted=previous;throw new IllegalStateException("Cannot release document grant",error);}
    }
    public synchronized List<UriPermission> persisted(){
        List<UriPermission> result=new ArrayList<UriPermission>();
        for(Map.Entry<String,Grant> entry:grants.entrySet()){Grant grant=entry.getValue();if(grant.persisted!=0)result.add(new UriPermission(tree(entry.getKey()),grant.persisted,grant.time));}
        return result;
    }
    private void save()throws IOException {
        Properties values=new Properties();
        for(Map.Entry<String,Grant> entry:grants.entrySet()){
            Grant grant=entry.getValue();if(grant.persisted==0)continue;String id=entry.getKey();
            values.setProperty(id+".path",volume.toPath().relativize(grant.directory.toPath()).toString());
            values.setProperty(id+".modes",Integer.toString(grant.persisted));values.setProperty(id+".time",Long.toString(grant.time));
        }
        File temporary=File.createTempFile("document-grants-",".tmp",state.getParentFile());
        try {
            try(FileOutputStream output=new FileOutputStream(temporary)){values.store(output,"Shiraz selected document trees");output.getFD().sync();}
            if(temporary.length()>65536)throw new IOException("Document grant state exceeds limit");
            Files.move(temporary.toPath(),state.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        }finally{temporary.delete();}
    }
    private File document(Uri uri,int requested)throws IOException {
        Grant grant=grant(uri,requested);String tree=treeId(uri),id=DocumentsContract.getDocumentId(uri);
        if(!id.startsWith(tree+":"))throw new SecurityException("Document belongs to another tree");
        String relative=id.substring(tree.length()+1);
        if(new File(relative).isAbsolute()||relative.indexOf('\0')>=0)throw new SecurityException("Invalid document path");
        return within(volume,within(grant.directory,new File(grant.directory,relative)));
    }
    private Uri documentUri(Uri scope,File file)throws IOException {
        Grant grant=grant(scope,0);File target=within(grant.directory,file);
        return DocumentsContract.buildDocumentUriUsingTree(scope,treeId(scope)+":"+grant.directory.toPath().relativize(target.toPath()));
    }
    public synchronized boolean permitted(Uri uri,int requested){
        try {modes(requested);grant(uri,requested);if(uri.getPathSegments().size()>2)document(uri,requested);return true;}
        catch(IOException|IllegalArgumentException|SecurityException error){return false;}
    }
    public boolean onCreate(){return true;}
    private static String mime(File file){
        if(file.isDirectory())return DocumentsContract.Document.MIME_TYPE_DIR;
        try{String type=Files.probeContentType(file.toPath());if(type!=null)return type;}catch(IOException ignored){}
        return "application/octet-stream";
    }
    public synchronized String getType(Uri uri){try{return mime(document(uri,1));}catch(IOException error){return null;}}
    private static final String[] COLUMNS={"document_id","_display_name","mime_type","flags","_size","last_modified"};
    private void row(MatrixCursor cursor,String[] columns,Uri scope,File file)throws IOException {
        Uri uri=documentUri(scope,file);Object[] values=new Object[columns.length];
        int flags=file.canWrite()? (file.isDirectory()?8:2):0;
        if(file.canWrite()&&!file.equals(grant(scope,1).directory))flags|=4|64;
        for(int i=0;i<columns.length;i++){
            String column=columns[i];
            if(column.equals("document_id"))values[i]=DocumentsContract.getDocumentId(uri);
            else if(column.equals("_display_name"))values[i]=file.getName();
            else if(column.equals("mime_type"))values[i]=mime(file);
            else if(column.equals("flags"))values[i]=flags;
            else if(column.equals("_size"))values[i]=file.isFile()?file.length():0L;
            else if(column.equals("last_modified"))values[i]=file.lastModified();
        }
        cursor.addRow(values);
    }
    public synchronized Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
        if(selection!=null||args!=null||order!=null)throw new UnsupportedOperationException("Document query filtering");
        String[] columns=projection==null?COLUMNS:projection;MatrixCursor cursor=new MatrixCursor(columns);
        try {
            File parent=document(uri,1);
            if("children".equals(uri.getLastPathSegment())){
                File[] children=parent.listFiles();if(children==null)throw new IOException("Cannot list document directory");
                if(children.length>4096)throw new IOException("Document directory exceeds row limit");
                Arrays.sort(children);
                for(File child:children){try{row(cursor,columns,uri,within(parent,child));}catch(SecurityException outside){/* Never expose escaping links. */}}
            }else if(parent.exists())row(cursor,columns,uri,parent);
            return cursor;
        }catch(IOException|RuntimeException error){cursor.close();throw new IllegalArgumentException("Document query failed",error);}
    }
    public synchronized ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        int requested=mode.equals("r")?1:mode.equals("rw")||mode.equals("rwt")?3:2;
        int flags;
        if(mode.equals("r"))flags=ParcelFileDescriptor.MODE_READ_ONLY;
        else if(mode.equals("w")||mode.equals("wt"))flags=ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_TRUNCATE;
        else if(mode.equals("wa"))flags=ParcelFileDescriptor.MODE_WRITE_ONLY|ParcelFileDescriptor.MODE_APPEND;
        else if(mode.equals("rw")||mode.equals("rwt"))flags=ParcelFileDescriptor.MODE_READ_WRITE|(mode.equals("rwt")?ParcelFileDescriptor.MODE_TRUNCATE:0);
        else throw new IllegalArgumentException("Invalid document mode");
        try{File file=document(uri,requested);if(!file.isFile())throw new FileNotFoundException("Document is not a file");return ParcelFileDescriptor.open(file,flags);}
        catch(IOException error){FileNotFoundException failure=new FileNotFoundException("Document unavailable");failure.initCause(error);throw failure;}
    }
    private static String name(Bundle input){String name=input.getString("_display_name");if(name==null||name.isEmpty()||name.equals(".")||name.equals("..")||name.indexOf('/')>=0||name.indexOf('\0')>=0)throw new IllegalArgumentException("Invalid document name");return name;}
    public synchronized Bundle call(String method,String arg,Bundle input){
        Uri uri=input==null?null:(Uri)input.getParcelable("uri");Bundle output=new Bundle();
        try {
            if("android:createDocument".equals(method)){
                File parent=document(uri,2);if(!parent.isDirectory())throw new IOException("Parent is not a directory");
                File target=within(parent,new File(parent,name(input)));
                boolean created=DocumentsContract.Document.MIME_TYPE_DIR.equals(input.getString("mime_type"))?target.mkdir():target.createNewFile();
                if(!created)throw new IOException("Document already exists or cannot be created");output.putParcelable("uri",documentUri(uri,target));
            }else if("android:renameDocument".equals(method)){
                File file=document(uri,2);if(file.equals(grant(uri,2).directory))throw new SecurityException("Cannot rename a selected tree root");
                File target=within(grant(uri,2).directory,new File(file.getParentFile(),name(input)));
                if(target.exists())throw new IOException("Document already exists");Files.move(file.toPath(),target.toPath());output.putParcelable("uri",documentUri(uri,target));
            }else if("android:deleteDocument".equals(method)){
                File file=document(uri,2);if(file.equals(grant(uri,2).directory))throw new SecurityException("Cannot delete a selected tree root");
                Files.delete(file.toPath());
            }else if("android:isChildDocument".equals(method)){
                File parent=document(uri,1),child=document((Uri)input.getParcelable("targetUri"),1);
                output.putBoolean("result",!parent.equals(child)&&child.toPath().startsWith(parent.toPath())&&child.exists());
            }else throw new UnsupportedOperationException("Unsupported document operation: "+method);
            return output;
        }catch(IOException error){throw new IllegalStateException("Document operation failed",error);}
    }
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Use createDocument");}
    public int delete(Uri uri,String where,String[] args){throw new UnsupportedOperationException("Use deleteDocument");}
    public int update(Uri uri,ContentValues values,String where,String[] args){throw new UnsupportedOperationException("Use renameDocument");}
}
