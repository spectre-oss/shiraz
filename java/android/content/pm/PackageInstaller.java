// SPDX-License-Identifier: GPL-3.0-only
package android.content.pm;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
/** Local APK sessions backed by Shiraz's AppImage builder. */
public final class PackageInstaller {
 public static final int STATUS_PENDING_USER_ACTION=-1,STATUS_SUCCESS=0,STATUS_FAILURE=1,STATUS_FAILURE_BLOCKED=2,STATUS_FAILURE_ABORTED=3,STATUS_FAILURE_INVALID=4,STATUS_FAILURE_CONFLICT=5,STATUS_FAILURE_STORAGE=6,STATUS_FAILURE_INCOMPATIBLE=7;
 public static final String EXTRA_STATUS="android.content.pm.extra.STATUS",EXTRA_STATUS_MESSAGE="android.content.pm.extra.STATUS_MESSAGE",EXTRA_SESSION_ID="android.content.pm.extra.SESSION_ID",EXTRA_PACKAGE_NAME="android.content.pm.extra.PACKAGE_NAME";
 private final Context context;private int next=1;private final Map<Integer,Session> sessions=new LinkedHashMap<>();
 public PackageInstaller(Context context){this.context=context;}
 public synchronized int createSession(SessionParams params) throws IOException {
  if(params==null||params.mode!=SessionParams.MODE_FULL_INSTALL)throw new IOException("Only full APK installation is supported");
  if(sessions.size()>=16)throw new IOException("Installation session limit");
  int id=next++;Session session=new Session(id,params);sessions.put(id,session);return id;
 }
 public synchronized Session openSession(int id) throws IOException {Session s=sessions.get(id);if(s==null)throw new IOException("Unknown installation session");return s;}
 public synchronized SessionInfo getSessionInfo(int id){Session s=sessions.get(id);return s==null?null:s.info();}
 public synchronized List<SessionInfo> getMySessions(){ArrayList<SessionInfo> result=new ArrayList<>();for(Session s:sessions.values())result.add(s.info());return result;}
 public List<SessionInfo> getAllSessions(){return getMySessions();}
 public synchronized void abandonSession(int id){Session s=sessions.get(id);if(s!=null){s.abandon();sessions.remove(id);}}
 public abstract static class SessionCallback {public abstract void onCreated(int id);public abstract void onBadgingChanged(int id);public abstract void onActiveChanged(int id,boolean active);public abstract void onProgressChanged(int id,float progress);public abstract void onFinished(int id,boolean success);}
 private final Map<SessionCallback,Handler> callbacks=new LinkedHashMap<>();
 public void registerSessionCallback(SessionCallback callback){registerSessionCallback(callback,new Handler(Looper.getMainLooper()));}
 public synchronized void registerSessionCallback(SessionCallback callback,Handler handler){callbacks.put(callback,handler);}
 public synchronized void unregisterSessionCallback(SessionCallback callback){callbacks.remove(callback);}
 public static class SessionParams {
  public static final int MODE_FULL_INSTALL=1,MODE_INHERIT_EXISTING=2;
  final int mode;int installReason;String packageName;CharSequence label;android.graphics.Bitmap icon;long size=-1;
  public SessionParams(int mode){this.mode=mode;}
  public void setInstallReason(int value){if(value<0||value>4)throw new IllegalArgumentException("Install reason");installReason=value;}
  public void setAppPackageName(String value){packageName=value;}public void setSize(long value){if(value<0||value>1024L*1024*1024)throw new IllegalArgumentException("APK size limit");size=value;}
  public void setInstallLocation(int value){if(value<0||value>2)throw new IllegalArgumentException("Install location");}
  public void setAppLabel(CharSequence label){this.label=label;}public void setAppIcon(android.graphics.Bitmap icon){this.icon=icon;}
 }
 public static class SessionInfo {private final int id;private final String pkg;private final float progress;private final boolean active;
  SessionInfo(int id,String pkg,float progress,boolean active){this.id=id;this.pkg=pkg;this.progress=progress;this.active=active;}
  public int getSessionId(){return id;}public String getAppPackageName(){return pkg;}public float getProgress(){return progress;}public boolean isActive(){return active;}public boolean isSealed(){return active;}
 }
 public final class Session implements Closeable {
  private final int id;private final SessionParams params;private final File dir;private File apk;private SessionOutput output;private volatile boolean committed,abandoned;private volatile float progress;
  Session(int id,SessionParams params)throws IOException {this.id=id;this.params=new SessionParams(params.mode);this.params.packageName=params.packageName;this.params.size=params.size;this.params.label=params.label;this.params.icon=params.icon;this.params.installReason=params.installReason;dir=java.nio.file.Files.createTempDirectory(context.getCacheDir().toPath(),"install-").toFile();}
  synchronized SessionInfo info(){return new SessionInfo(id,params.packageName,progress,committed);}
  public synchronized OutputStream openWrite(String name,long offset,long length)throws IOException {
   if(committed||abandoned||offset!=0||apk!=null)throw new IOException("Only one base APK per session is supported");
   if(name==null||name.isEmpty()||name.contains("/")||length>1024L*1024*1024)throw new IOException("Invalid APK stream");
   apk=new File(dir,"base.apk");output=new SessionOutput(apk,length);return output;
  }
  public void fsync(OutputStream stream)throws IOException {if(stream!=output)throw new IOException("Foreign session stream");stream.flush();output.file.getFD().sync();}
  public void setStagingProgress(float value){progress=Math.max(0,Math.min(1,value));}public void addProgress(float value){setStagingProgress(progress+value);}
  public void commit(final IntentSender target){
   synchronized(this){if(committed||abandoned||apk==null||output==null||!output.closed)throw new IllegalStateException("Invalid installation session");if(target==null)throw new NullPointerException("status receiver");committed=true;}
   new Thread(new Runnable(){public void run(){int status=STATUS_SUCCESS;String message="Installed";String pkg=params.packageName;
    try{pkg=shiraz.runtime.HostAppInstaller.install(context,apk,params.packageName);progress=1;}
    catch(Exception failure){status=STATUS_FAILURE;message=failure.getMessage();System.err.println("SHIRAZ_INSTALL_FAILED "+failure);}
    Intent result=new Intent().putExtra(EXTRA_STATUS,status).putExtra(EXTRA_STATUS_MESSAGE,message).putExtra(EXTRA_SESSION_ID,id).putExtra(EXTRA_PACKAGE_NAME,pkg);
    try{target.sendIntent(context,0,result,null,null);}catch(IntentSender.SendIntentException e){System.err.println("SHIRAZ_INSTALL_RESULT_FAILED "+e);}
    synchronized(PackageInstaller.this){sessions.remove(id);for(Map.Entry<SessionCallback,Handler> c:callbacks.entrySet()){final boolean ok=status==STATUS_SUCCESS;final SessionCallback callback=c.getKey();c.getValue().post(new Runnable(){public void run(){callback.onFinished(id,ok);}});}}
    cleanup();
   }},"Shiraz-install-"+id).start();
  }
  private final class SessionOutput extends OutputStream {
   final FileOutputStream file;final long expected;long bytes;boolean closed;
   SessionOutput(File apk,long expected)throws IOException{file=new FileOutputStream(apk);this.expected=expected;}
   public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
   public synchronized void write(byte[] data,int offset,int length)throws IOException{if(closed||abandoned)throw new IOException("Session stream closed");if(length<0||bytes+length>1024L*1024*1024||(expected>=0&&bytes+length>expected))throw new IOException("APK stream size limit");file.write(data,offset,length);bytes+=length;}
   public void flush()throws IOException{file.flush();}
   public synchronized void close()throws IOException{if(!closed){closed=true;file.close();if(expected>=0&&bytes!=expected){abandoned=true;throw new IOException("Truncated APK stream");}}}
  }
  private void cleanup(){if(output!=null)try{output.close();}catch(IOException ignored){}if(apk!=null)apk.delete();dir.delete();}
  public synchronized void abandon(){if(committed)throw new IllegalStateException("Installation already committed");abandoned=true;cleanup();}
  public void close(){}
 }
}
