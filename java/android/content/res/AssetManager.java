// SPDX-License-Identifier: GPL-3.0-only
package android.content.res;
import android.os.ParcelFileDescriptor;
import java.io.*;
import shiraz.runtime.NativeFd;

/** APK asset access; descriptor regions remain valid independently of the resource manager. */
public final class AssetManager implements AutoCloseable {
    public static final int ACCESS_UNKNOWN = 0, ACCESS_RANDOM = 1, ACCESS_STREAMING = 2, ACCESS_BUFFER = 3;
    private final Resources resources;
    private boolean closed;
    AssetManager(Resources resources) { this.resources = resources; }
    private void check(String name) {
        if (closed) throw new IllegalStateException("Asset manager is closed");
        if (name == null) throw new NullPointerException("Asset name");
        if (name.isEmpty() || name.length() > 65536 || name.charAt(0) == '/' || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0)
            throw new IllegalArgumentException("Invalid asset name");
        for (String part : name.split("/", -1)) if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IllegalArgumentException("Invalid asset path component");
    }
    /** Immediate app-APK asset children, including directories inferred from ZIP paths. */
    public synchronized String[] list(String path) throws IOException {
        if(closed)throw new IllegalStateException("Asset manager is closed");
        if(path==null)throw new NullPointerException("Asset directory");
        // Directory callers (including Expo's font discovery) may supply a
        // trailing separator. Strip one; traversal and empty inner components
        // still go through the same strict validation as file names.
        if(path.length()>1&&path.endsWith("/"))path=path.substring(0,path.length()-1);
        if(!path.isEmpty())check(path);
        String prefix="assets/"+(path.isEmpty()?"":path+"/");
        java.util.TreeSet<String> children=new java.util.TreeSet<String>(new java.util.Comparator<String>(){
            public int compare(String a,String b){byte[] x=a.getBytes(java.nio.charset.StandardCharsets.UTF_8),y=b.getBytes(java.nio.charset.StandardCharsets.UTF_8);for(int i=0;i<Math.min(x.length,y.length);i++){int difference=(x[i]&255)-(y[i]&255);if(difference!=0)return difference;}return Integer.compare(x.length,y.length);}
        });
        try(java.util.zip.ZipFile archive=new java.util.zip.ZipFile(resources.shirazAssetArchive())){
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries=archive.entries();int count=0;
            while(entries.hasMoreElements()){
                if(++count>1000000)throw new IOException("Asset ZIP entry limit");
                String name=entries.nextElement().getName();if(!name.startsWith(prefix))continue;
                String rest=name.substring(prefix.length());int slash=rest.indexOf('/');String child=slash<0?rest:rest.substring(0,slash);
                if(child.isEmpty())continue;
                if(child.equals(".")||child.equals("..")||child.indexOf('\\')>=0||child.indexOf('\0')>=0||child.length()>65536)throw new IOException("Invalid asset ZIP entry");
                children.add(child);if(children.size()>65536)throw new IOException("Asset directory entry limit");
            }
        }
        return children.toArray(new String[children.size()]);
    }
    public synchronized InputStream openNonAsset(int cookie,String name,int mode) throws IOException {
        check(name);if(cookie<0)throw new IllegalArgumentException("Asset cookie");
        try{return new ByteArrayInputStream(resources.shirazReadNonAsset(cookie,name));}
        catch(Resources.NotFoundException failure){throw new FileNotFoundException("Missing packaged file: "+name);}
    }
    public InputStream openNonAsset(String name) throws IOException {return openNonAsset(0,name,ACCESS_STREAMING);}
    public synchronized InputStream open(String name) throws IOException { return open(name, ACCESS_STREAMING); }
    public synchronized InputStream open(String name, int mode) throws IOException {
        check(name);
        if (mode < 0 || mode > 3) throw new IllegalArgumentException("Unknown asset access mode");
        return new AssetInputStream(resources.shirazOpenAssetStream(name));
    }
    public synchronized AssetFileDescriptor openFd(String name) throws IOException {
        check(name); long[] region = resources.shirazOpenAssetFd(name); int raw = (int)region[0];
        ParcelFileDescriptor descriptor = null;
        try {
            descriptor = ParcelFileDescriptor.adoptFd(raw); raw = -1;
            return new AssetFileDescriptor(descriptor, region[1], region[2]);
        } catch (Throwable failure) {
            if (descriptor != null) try { descriptor.close(); } catch (IOException error) { failure.addSuppressed(error); }
            throw failure;
        } finally { if (raw >= 0) NativeFd.closeRawQuietly(raw); }
    }
    @Override public synchronized void close() { closed = true; }
    private static native int nativeRead(long handle,byte[] target,int offset,int count)throws IOException;
    private static native long nativeSeek(long handle,long position)throws IOException;
    private static native void nativeClose(long handle);
    public static final class AssetInputStream extends InputStream {
        private long handle,position,marked;
        private final long length;
        private final byte[] single=new byte[1];
        private AssetInputStream(long[] opened){handle=opened[0];length=opened[1];}
        private void checkOpen()throws IOException{if(handle==0)throw new IOException("Asset stream is closed");}
        @Override public synchronized int read()throws IOException{return read(single,0,1)==-1?-1:single[0]&255;}
        @Override public int read(byte[] target)throws IOException{return read(target,0,target.length);}
        @Override public synchronized int read(byte[] target,int offset,int count)throws IOException{
            checkOpen();if(target==null)throw new NullPointerException("target");
            if(offset<0||count<0||offset>target.length-count)throw new IndexOutOfBoundsException();
            if(count==0)return 0;
            int actual=nativeRead(handle,target,offset,count);if(actual>0)position+=actual;return actual;
        }
        @Override public synchronized long skip(long count)throws IOException{
            checkOpen();long next=position+Math.min(Math.max(0,count),length-position),old=position;
            position=nativeSeek(handle,next);return position-old;
        }
        @Override public synchronized int available()throws IOException{checkOpen();return (int)Math.min(Integer.MAX_VALUE,length-position);}
        @Override public synchronized void mark(int limit){marked=position;}
        @Override public synchronized void reset()throws IOException{checkOpen();position=nativeSeek(handle,marked);}
        @Override public boolean markSupported(){return true;}
        @Override public synchronized void close(){if(handle!=0){nativeClose(handle);handle=0;}}
        @Override protected void finalize()throws Throwable{try{close();}finally{super.finalize();}}
    }
}
