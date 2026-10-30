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
   check(manager.getApplicationInfo(info.packageName,0).splitSourceDirs==null,"base-only package has no splits");
   facts.setProperty("split.count","2");
   facts.setProperty("split.0.name","config.en");facts.setProperty("split.0.sourceDir","/fixtures/language.apk");
   facts.setProperty("split.1.name","config.x86_64");facts.setProperty("split.1.sourceDir","/fixtures/native.apk");
   shiraz.runtime.HostPackageManager splitManager=new shiraz.runtime.HostPackageManager(facts,new android.content.ContextWrapper(null));
   PackageInfo split=splitManager.getPackageInfo(info.packageName,0);
   check(java.util.Arrays.equals(split.splitNames,new String[]{"config.en","config.x86_64"}),"split package names");
   check(java.util.Arrays.equals(split.applicationInfo.splitSourceDirs,new String[]{"/fixtures/language.apk","/fixtures/native.apk"}),"split paths preserve metadata order");
   split.splitNames[0]="changed";split.applicationInfo.splitSourceDirs[0]="changed";
   check("config.en".equals(split.applicationInfo.splitNames[0])&&"/fixtures/language.apk".equals(split.applicationInfo.splitPublicSourceDirs[0]),"split arrays do not alias");
   check("/fixtures/language.apk".equals(splitManager.getApplicationInfo(info.packageName,0).splitSourceDirs[0]),"caller cannot alter stored split metadata");
   facts.setProperty("split.1.name","config.en");
   try{new shiraz.runtime.HostPackageManager(facts,new android.content.ContextWrapper(null)).getApplicationInfo(info.packageName,0);throw new AssertionError("duplicate split accepted");}catch(IllegalArgumentException expected){}
   facts.setProperty("split.count","257");
   try{new shiraz.runtime.HostPackageManager(facts,new android.content.ContextWrapper(null)).getApplicationInfo(info.packageName,0);throw new AssertionError("unbounded split count");}catch(IllegalArgumentException expected){}
   android.graphics.drawable.Drawable loaded=resources.getDrawable(resources.getIdentifier("adaptive_fixture","drawable","org.shiraz.fixture"));
   check(loaded instanceof android.graphics.drawable.AdaptiveIconDrawable,"adaptive XML class");
   android.graphics.drawable.AdaptiveIconDrawable adaptive=(android.graphics.drawable.AdaptiveIconDrawable)loaded;
   check(adaptive.getMonochrome()!=null,"monochrome metadata retained separately");
   adaptive.setBounds(0,0,40,40);
   check(adaptive.getForeground().getBounds().equals(new android.graphics.Rect(-10,-10,50,50)),"adaptive layer overscan");
   android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(40,40,android.graphics.Bitmap.Config.ARGB_8888);
   android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);
   try{adaptive.draw(canvas);check(bitmap.getPixel(20,20)==0xff0000ff,"foreground composition");check(bitmap.getPixel(0,0)==0,"icon mask clips corners");
    adaptive.setBounds(5,5,25,25);bitmap.eraseColor(0);adaptive.draw(canvas);check(bitmap.getPixel(15,15)==0xff0000ff&&bitmap.getPixel(30,30)==0,"mask follows bounds");
   }finally{canvas.setBitmap(null);bitmap.recycle();}
  }finally{resources.shirazClose();}
  System.out.println("ARCHIVE_PACKAGE_METADATA_OK");
 }
}
