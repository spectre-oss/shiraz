// SPDX-License-Identifier: GPL-3.0-only
import android.util.AtomicFile;
import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
public final class AtomicFileSmoke {
 private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
 private static void content(AtomicFile file,int... bytes)throws Exception{byte[] expected=new byte[bytes.length];for(int i=0;i<bytes.length;i++)expected[i]=(byte)bytes[i];check(Arrays.equals(file.readFully(),expected),"Atomic contents differ");}
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory(Paths.get("build"),"atomic-smoke-");
  try{
   File base=root.resolve("nested/value").toFile();AtomicFile file=new AtomicFile(base);check(file.getBaseFile().equals(base)&&!file.exists(),"Lookup must not create file");
   FileOutputStream out=file.startWrite();out.write(new byte[]{1,2,3});file.finishWrite(out);content(file,1,2,3);
   out=file.startWrite();out.write(9);file.failWrite(out);content(file,1,2,3);
   out=file.startWrite();out.write(8);out.close();content(new AtomicFile(base),1,2,3);
   out=file.startWrite();out.write(7);out.close();out=new AtomicFile(base).startWrite();out.write(6);file.failWrite(out);content(file,1,2,3);
   out=file.startWrite();out.write(4);file.finishWrite(out);content(file,4);check(!new File(base+".bak").exists(),"Backup removed after commit");
   out=file.startWrite();out.write(5);out.close();file.finishWrite(out);content(file,4); // Failed sync must retain the backup.
   file.finishWrite(null);file.failWrite(null);content(file,4);
   File bad=root.resolve("bad").toFile();Files.write(bad.toPath(),new byte[]{10});Files.createDirectory(Paths.get(bad+".bak"));
   try{new AtomicFile(bad).startWrite();throw new AssertionError("Backup directory accepted");}catch(IOException expected){}
   check(Files.readAllBytes(bad.toPath())[0]==10,"Failed backup must preserve base");
   file.delete();check(!file.exists(),"Delete removes base and backup");
   out=file.startWrite();out.write(3);file.failWrite(out);check(!file.exists(),"Failed first write leaves no file");
   try{file.openRead();throw new AssertionError("Missing file accepted");}catch(FileNotFoundException expected){}
   System.out.println("ATOMIC_FILE_RECOVERY_OK");
  }finally{try(java.util.stream.Stream<Path> entries=Files.walk(root)){entries.sorted(java.util.Comparator.reverseOrder()).forEach(new java.util.function.Consumer<Path>(){public void accept(Path p){try{Files.delete(p);}catch(IOException e){throw new RuntimeException(e);}}});}}
 }
}
