// SPDX-License-Identifier: GPL-3.0-only
package android.util;
import java.io.*;

/** Backup-based file replacement. Callers must serialize all reads and writes. */
public class AtomicFile {
 private final File base,backup;
 public AtomicFile(File baseName){if(baseName==null)throw new NullPointerException("baseName");base=baseName;backup=new File(baseName.getPath()+".bak");}
 public File getBaseFile(){return base;}
 public boolean exists(){return base.exists()||backup.exists();}
 public long getLastModifiedTime(){return backup.exists()?backup.lastModified():base.lastModified();}
 private static void remove(File file)throws IOException{if(file.exists()&&!file.delete())throw new IOException("Cannot remove atomic file");}
 private static void move(File from,File to)throws IOException{if(!from.renameTo(to))throw new IOException("Cannot rename atomic file");}
 private void filesOnly()throws IOException{if(base.exists()&&!base.isFile()||backup.exists()&&!backup.isFile())throw new IOException("Atomic file path is not a regular file");}
 public void delete(){try{filesOnly();remove(base);remove(backup);}catch(IOException failure){Log.w("AtomicFile","Cannot delete atomic file",failure);}}
 public FileOutputStream startWrite()throws IOException{
  filesOnly();
  if(base.exists()){if(backup.exists())remove(base);else move(base,backup);}
  File parent=base.getAbsoluteFile().getParentFile();
  if(!parent.isDirectory()&&!parent.mkdirs()&&!parent.isDirectory())throw new IOException("Cannot create atomic file directory");
  return new FileOutputStream(base);
 }
 public void finishWrite(FileOutputStream stream){
  if(stream==null)return;
  try{try{stream.getFD().sync();}finally{stream.close();}remove(backup);}
  catch(IOException failure){Log.w("AtomicFile","Cannot finish atomic write; retaining backup",failure);}
 }
 public void failWrite(FileOutputStream stream){
  if(stream==null)return;
  try{stream.close();}catch(IOException failure){Log.w("AtomicFile","Cannot close failed atomic write",failure);}
  try{filesOnly();remove(base);if(backup.exists())move(backup,base);}catch(IOException failure){Log.w("AtomicFile","Cannot restore atomic backup",failure);}
 }
 public FileInputStream openRead()throws FileNotFoundException{
  try{filesOnly();if(backup.exists()){remove(base);move(backup,base);}return new FileInputStream(base);}
  catch(IOException failure){if(failure instanceof FileNotFoundException)throw (FileNotFoundException)failure;FileNotFoundException error=new FileNotFoundException("Cannot recover atomic file");error.initCause(failure);throw error;}
 }
 public byte[] readFully()throws IOException{
  try(FileInputStream input=openRead();ByteArrayOutputStream output=new ByteArrayOutputStream()){
   byte[] block=new byte[8192];int count;
   while((count=input.read(block))!=-1){if(output.size()>64*1024*1024-count)throw new IOException("Atomic file readFully exceeds 64MiB; use openRead");output.write(block,0,count);}
   return output.toByteArray();
  }
 }
}
