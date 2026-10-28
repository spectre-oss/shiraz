// SPDX-License-Identifier: GPL-3.0-only
import android.content.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.content.res.AssetFileDescriptor;
import java.io.*;
public class ContentFileSmoke {
 public static void main(String[] args)throws Exception{
  ContentResolver resolver=new ContentResolver(new ContextWrapper(null));
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
   System.out.println("CONTENT_FILE_URI_IO_OK");
  }finally{file.delete();}
 }
}
