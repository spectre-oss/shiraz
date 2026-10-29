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
  final android.content.res.Resources resources=android.content.res.Resources.shirazOpen(args[0],360,640);
  try{
   java.util.Properties facts=new java.util.Properties();facts.setProperty("package",info.packageName);facts.setProperty("sourceDir",args[0]);facts.setProperty("dataDir","unused");facts.setProperty("icon",Integer.toUnsignedString(info.applicationInfo.icon));
   shiraz.runtime.HostPackageManager manager=new shiraz.runtime.HostPackageManager(facts,new android.content.ContextWrapper(null){
    public String getPackageName(){return "org.shiraz.fixture";}
    public android.content.res.Resources getResources(){return resources;}
   });
   android.graphics.drawable.Drawable icon=manager.getApplicationIcon(info.packageName);
   check(icon instanceof android.graphics.drawable.BitmapDrawable&&icon.getIntrinsicWidth()>0,"declared application bitmap");
   check(info.applicationInfo.loadIcon(manager).getIntrinsicWidth()==icon.getIntrinsicWidth(),"loadIcon uses application resource");
   check(manager.getApplicationIcon(info.applicationInfo)!=icon,"independent drawable instances");
   try{manager.getApplicationIcon("missing.package");throw new AssertionError("unknown icon package");}catch(PackageManager.NameNotFoundException expected){}
   ApplicationInfo noIcon=new ApplicationInfo();noIcon.packageName=info.packageName;
   check(manager.getApplicationIcon(noIcon)!=null,"framework default icon");
  }finally{resources.shirazClose();}
  System.out.println("ARCHIVE_PACKAGE_METADATA_OK");
 }
}
