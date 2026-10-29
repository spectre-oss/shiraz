// SPDX-License-Identifier: GPL-3.0-only
import java.io.*;
import java.util.zip.*;
import android.content.pm.*;
public final class ArchivePackageSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  PackageInfo info=shiraz.runtime.ArchivePackageInfo.read(args[0],0);
  check(info!=null&&"org.shiraz.fixture".equals(info.packageName),"package identity");
  check("1.0".equals(info.versionName),"resource version name");
  check(info.applicationInfo.minSdkVersion==27&&info.applicationInfo.targetSdkVersion==35,"SDK declarations");
  check("org.shiraz.fixture.FixtureApplication".equals(info.applicationInfo.className),"relative class name");
  check(info.applicationInfo.dataDir==null&&info.applicationInfo.uid==-1&&info.firstInstallTime==0,"archive is not installed");
  check(android.os.storage.StorageManager.UUID_DEFAULT.equals(info.applicationInfo.storageUuid),"default storage UUID");
  check(info.applicationInfo.labelRes!=0&&info.applicationInfo.icon!=0,"resource references");
  check(shiraz.runtime.ArchivePackageInfo.read(args[0]+".missing",0)==null,"missing archive");
  File invalid=File.createTempFile("archive-invalid-",".zip");
  try{try(ZipOutputStream zip=new ZipOutputStream(new FileOutputStream(invalid))){zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));zip.write(new byte[]{3,0,8,0});zip.closeEntry();}
   check(shiraz.runtime.ArchivePackageInfo.read(invalid.getPath(),0)==null,"truncated binary manifest");
  }finally{invalid.delete();}
  try{shiraz.runtime.ArchivePackageInfo.read(args[0],PackageManager.GET_ACTIVITIES);throw new AssertionError("unsupported flag silently accepted");}catch(UnsupportedOperationException expected){}
  System.out.println("ARCHIVE_PACKAGE_METADATA_OK");
 }
}
