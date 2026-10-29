// SPDX-License-Identifier: GPL-3.0-only
import android.app.PendingIntent;
import android.content.*;
import android.content.pm.PackageInstaller;
import android.os.*;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import shiraz.runtime.HostBroadcasts;
public final class InstallerSmoke {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static final class Owner extends ContextWrapper {
  final File cache;final HostBroadcasts broadcasts=new HostBroadcasts("org.shiraz.installerfixture");
  Owner(File cache){super(null);this.cache=cache;}
  public File getCacheDir(){return cache;}public String getPackageName(){return "org.shiraz.installerfixture";}public Context getApplicationContext(){return this;}
  protected HostBroadcasts shirazBroadcasts(){return broadcasts;}
 }
 static byte[] sized(byte[] data){return java.nio.ByteBuffer.allocate(data.length+4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(data.length).put(data).array();}
 static void certificates(File root)throws Exception {
  byte[] certificate={48,3,1,2,3};byte[] signed=sized(join(sized(new byte[0]),sized(sized(certificate))));
  byte[] value=sized(sized(signed));int entrySize=value.length+4;long size=8+entrySize+24;
  java.nio.ByteBuffer apk=java.nio.ByteBuffer.allocate((int)size+8+22).order(java.nio.ByteOrder.LITTLE_ENDIAN);
  apk.putLong(size).putLong(entrySize).putInt(0x7109871a).put(value).putLong(size).putLong(0x20676953204b5041L).putLong(0x3234206b636f6c42L);
  apk.putInt(0x06054b50).putShort((short)0).putShort((short)0).putShort((short)0).putShort((short)0).putInt(0).putInt((int)size+8).putShort((short)0);
  File file=new File(root,"signer.apk");Files.write(file.toPath(),apk.array());
  java.lang.reflect.Method method=Class.forName("shiraz.runtime.ApkCertificates").getDeclaredMethod("read",String.class);method.setAccessible(true);
  android.content.pm.Signature[] result=(android.content.pm.Signature[])method.invoke(null,file.getPath());check(result.length==1&&java.util.Arrays.equals(result[0].toByteArray(),certificate),"signer metadata differs");
  android.content.pm.SigningInfo info=new android.content.pm.SigningInfo(result);result[0]=null;
  check(!info.hasMultipleSigners()&&!info.hasPastSigningCertificates()&&info.getSigningCertificateHistory().length==1,"API-27 signer history");
  android.content.pm.Signature[] copy=info.getApkContentsSigners();copy[0]=null;check(info.getApkContentsSigners()[0]!=null,"signer snapshot isolation");
  android.content.pm.Signature signer=info.getApkContentsSigners()[0];
  android.content.pm.SigningInfo multiple=new android.content.pm.SigningInfo(new android.content.pm.Signature[]{signer,new android.content.pm.Signature(new byte[]{1})});
  check(multiple.hasMultipleSigners()&&multiple.getSigningCertificateHistory()==null,"multiple signer history");
  java.util.Properties facts=new java.util.Properties();facts.setProperty("package","test.signer");facts.setProperty("sourceDir",file.getPath());facts.setProperty("dataDir",root.getPath());
  shiraz.runtime.HostPackageManager manager=new shiraz.runtime.HostPackageManager(facts,new ContextWrapper(null));
  android.content.pm.PackageInfo metadata=manager.getPackageInfo("test.signer",android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
  check(metadata.signatures==null&&metadata.signingInfo.getApkContentsSigners()[0].equals(signer),"modern query returns v2 metadata only when requested");
  check(manager.getPackageInfo("test.signer",0).signingInfo==null,"unrequested signing metadata");
  facts.setProperty("sharedUserId","test.shared");facts.setProperty("sharedUserLabel","2130771969");facts.setProperty("baseRevisionCode","3");facts.setProperty("installLocation","2");
  facts.setProperty("icon","2130771970");facts.setProperty("logo","2130771971");facts.setProperty("banner","2130771972");
  metadata=new shiraz.runtime.HostPackageManager(facts,new ContextWrapper(null)).getPackageInfo("test.signer",0);
  check("test.shared".equals(metadata.sharedUserId)&&metadata.sharedUserLabel==0x7f010001&&metadata.baseRevisionCode==3&&metadata.installLocation==2,"manifest identity fields");
  check(metadata.applicationInfo.icon==0x7f010002&&metadata.applicationInfo.logo==0x7f010003&&metadata.applicationInfo.banner==0x7f010004,"application image resource IDs");
  check(metadata.splitNames==null&&metadata.splitRevisionCodes==null&&!metadata.isApex,"monolithic APK metadata");
  apk.putLong(0,1);Files.write(file.toPath(),apk.array());try{method.invoke(null,file.getPath());throw new AssertionError("corrupt signing size accepted");}catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof IllegalStateException,"wrong malformed signer failure");}file.delete();
 }
 static byte[] join(byte[] a,byte[] b){byte[] out=java.util.Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,out,a.length,b.length);return out;}
 static void html(){
  final java.util.List<String> tags=new java.util.ArrayList<String>();
  android.text.Spanned text=android.text.Html.fromHtml("<Custom>Hello <b>bold</b><a href='https://example.org/?a=1&amp;b=2'>link</a></Custom><script>hidden</script>",0,null,new android.text.Html.TagHandler(){public void handleTag(boolean open,String tag,android.text.Editable output,org.xml.sax.XMLReader reader){tags.add(tag+open);}});
  check(tags.toString().equals("[Customtrue, Customfalse]"),"custom tag callbacks");check(!text.toString().contains("hidden"),"script text leaked");check(text.getSpans(0,text.length(),android.text.style.StyleSpan.class).length==1,"bold span lost");
  android.text.style.URLSpan[] links=text.getSpans(0,text.length(),android.text.style.URLSpan.class);check(links.length==1&&links[0].getURL().equals("https://example.org/?a=1&b=2"),"link attribute decoding");
 }
 public static void main(String[] args)throws Exception {
  if(args.length==2&&"--certificates".equals(args[1])){File root=Files.createTempDirectory(Paths.get(args[0]),"certificates-").toFile();try{certificates(root);}finally{root.delete();}System.out.println("SIGNING_METADATA_OK");return;}
  Looper.prepareMainLooper();File cache=Files.createTempDirectory(Paths.get(args[0]),"installer-smoke-").toFile();Owner owner=new Owner(cache);
  try{
   certificates(cache);html();
   PackageInstaller installer=new PackageInstaller(owner);PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(1);params.setAppPackageName("test.app");params.setSize(3);
   int id=installer.createSession(params);PackageInstaller.Session session=installer.openSession(id);
   OutputStream stream=session.openWrite("base.apk",0,3);stream.write(new byte[]{1,2,3});session.fsync(stream);
   try{stream.write(4);throw new AssertionError("oversized stream accepted");}catch(IOException expected){}
   try{session.commit(null);throw new AssertionError("open stream committed");}catch(IllegalStateException expected){}
   stream.close();installer.abandonSession(id);check(installer.getMySessions().isEmpty(),"abandoned session visible");check(cache.list().length==0,"abandoned file remains");
   id=installer.createSession(params);session=installer.openSession(id);stream=session.openWrite("base.apk",0,3);stream.write(1);try{stream.close();throw new AssertionError("truncated stream accepted");}catch(IOException expected){}installer.abandonSession(id);
   final AtomicInteger status=new AtomicInteger(-1);BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){status.set(intent.getIntExtra("status",-2));}};
   owner.registerReceiver(receiver,new IntentFilter("result"));
   PendingIntent token=PendingIntent.getBroadcast(owner,1,new Intent("result").setPackage(owner.getPackageName()),PendingIntent.FLAG_MUTABLE|PendingIntent.FLAG_ONE_SHOT);
   token.getIntentSender().sendIntent(owner,0,new Intent().putExtra("status",7),null,null);Looper.myLooper().shirazDispatchDue(64);check(status.get()==7,"mutable result not delivered");
   try{token.send();throw new AssertionError("one-shot reused");}catch(PendingIntent.CanceledException expected){}
   PendingIntent immutable=PendingIntent.getBroadcast(owner,2,new Intent("result").putExtra("status",9),PendingIntent.FLAG_IMMUTABLE);
   immutable.getIntentSender().sendIntent(owner,0,new Intent().putExtra("status",8),null,null);Looper.myLooper().shirazDispatchDue(64);check(status.get()==9,"immutable intent overwritten");
   owner.unregisterReceiver(receiver);System.out.println("INSTALLER_SESSION_AND_RESULT_OK");
  }finally{owner.broadcasts.close();cache.delete();Looper.shirazShutdownMain();}
 }
}
