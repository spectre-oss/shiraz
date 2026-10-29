// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.content.res.AssetFileDescriptor;
import java.io.*;
public class ContentFileSmoke {
 private static final class RegionProvider extends ContentProvider {
  final File file;AssetFileDescriptor last;boolean closed;long start=2,length=3;
  RegionProvider(File file){this.file=file;}
  public boolean onCreate(){return true;}
  public String getType(Uri uri){return "application/octet-stream";}
  public android.database.Cursor query(Uri u,String[] p,String s,String[] a,String o){throw new UnsupportedOperationException();}
  public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
  public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
  public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
  public AssetFileDescriptor openAssetFile(Uri uri,String mode)throws FileNotFoundException{
   closed=false;
   last=new AssetFileDescriptor(ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_WRITE),start,length){
    public void close()throws IOException{closed=true;super.close();}
   };return last;
  }
 }
 public static void main(String[] args)throws Exception{
  Context context=new ContextWrapper(null);ContentResolver resolver=new ContentResolver(context);
  File file=File.createTempFile("shiraz-content-", ".txt");Uri uri=Uri.fromFile(file);
  try{
   try(OutputStream output=resolver.openOutputStream(uri,"w")){output.write(new byte[]{1,2});}
   try(OutputStream output=resolver.openOutputStream(uri,"wa")){output.write(3);}
   try(InputStream input=resolver.openInputStream(uri)){if(input.read()!=1||input.read()!=2||input.read()!=3||input.read()!=-1)throw new AssertionError("append/read");}
   try(ParcelFileDescriptor fd=resolver.openFileDescriptor(uri,"rw")){if(fd.getStatSize()!=3)throw new AssertionError("rw preserves size");}
   try(AssetFileDescriptor asset=resolver.openAssetFileDescriptor(uri,"r");InputStream input=asset.createInputStream()){if(input.read()!=1)throw new AssertionError("asset file read");}
   for(int i=0;i<64;i++){try(OutputStream output=resolver.openOutputStream(uri,"wt")){output.write(9);}}
   if(file.length()!=1)throw new AssertionError("truncate");
   try{resolver.openOutputStream(uri,"bad");throw new AssertionError("bad mode");}catch(IllegalArgumentException expected){}
   try{resolver.openOutputStream(Uri.parse("file://remote/tmp/file"));throw new AssertionError("remote authority");}catch(FileNotFoundException expected){}
   RegionProvider provider=new RegionProvider(file);android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="fixture.region";provider.attachInfo(context,info);
   Uri content=Uri.parse("content://fixture.region/item");
   try(FileOutputStream output=new FileOutputStream(file)){output.write(new byte[]{10,11,12,13,14,15,16});}
   OutputStream region=resolver.openOutputStream(content,"rw");
   region.write(20);region.write(new byte[]{21,22,23,24},0,4);region.write(25);region.close();region.close();
   byte[] actual=java.nio.file.Files.readAllBytes(file.toPath());
   if(!java.util.Arrays.equals(actual,new byte[]{10,11,20,21,22,15,16}))throw new AssertionError("Provider region overwritten: "+java.util.Arrays.toString(actual));
   if(!provider.closed||provider.last.getFileDescriptor().valid())throw new AssertionError("Provider asset not closed");
   try{region.write(26);throw new AssertionError("Closed region writable");}catch(IOException expected){}
   provider.start=1;provider.length=0;
   try(OutputStream empty=resolver.openOutputStream(content,"rw")){empty.write(new byte[]{99,98});empty.write(97);}
   if(!java.util.Arrays.equals(actual,java.nio.file.Files.readAllBytes(file.toPath())))throw new AssertionError("Empty region changed file");
   provider.start=0;provider.length=AssetFileDescriptor.UNKNOWN_LENGTH;
   try(OutputStream whole=resolver.openOutputStream(content,"rw")){whole.write(new byte[]{30,31,32,33,34,35,36,37});}
   if(file.length()!=8||!provider.closed)throw new AssertionError("Unbounded output or close");
   System.out.println("CONTENT_FILE_URI_IO_OK");
  }finally{file.delete();}
 }
}
