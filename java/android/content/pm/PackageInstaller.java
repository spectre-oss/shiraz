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
  if(next==Integer.MAX_VALUE)throw new IOException("Installation session ID exhausted");
  final int id=next++;Session session=new Session(id,params);sessions.put(id,session);dispatch(new Event(){public void deliver(SessionCallback callback){callback.onCreated(id);}});return id;
 }
 public synchronized Session openSession(int id) throws IOException {Session s=sessions.get(id);if(s==null)throw new IOException("Unknown installation session");return s;}
 public SessionInfo getSessionInfo(int id){Session s;synchronized(this){s=sessions.get(id);}return s==null?null:s.info();}
 public List<SessionInfo> getMySessions(){Session[] snapshot;synchronized(this){snapshot=sessions.values().toArray(new Session[sessions.size()]);}ArrayList<SessionInfo> result=new ArrayList<>();for(Session s:snapshot)result.add(s.info());return result;}
 public List<SessionInfo> getAllSessions(){return getMySessions();}
 public void abandonSession(int id){Session s;synchronized(this){s=sessions.get(id);}if(s!=null)s.abandon();}
 public abstract static class SessionCallback {public abstract void onCreated(int id);public abstract void onBadgingChanged(int id);public abstract void onActiveChanged(int id,boolean active);public abstract void onProgressChanged(int id,float progress);public abstract void onFinished(int id,boolean success);}
 private final Map<SessionCallback,Handler> callbacks=new LinkedHashMap<>();
 public void registerSessionCallback(SessionCallback callback){registerSessionCallback(callback,new Handler(Looper.getMainLooper()));}
 public synchronized void registerSessionCallback(SessionCallback callback,Handler handler){Objects.requireNonNull(callback);Objects.requireNonNull(handler);if(!callbacks.containsKey(callback)&&callbacks.size()>=64)throw new IllegalStateException("Installer callback limit");callbacks.put(callback,handler);}
 public synchronized void unregisterSessionCallback(SessionCallback callback){callbacks.remove(callback);}
 private interface Event{void deliver(SessionCallback callback);}
 private synchronized void dispatch(final Event event){for(Map.Entry<SessionCallback,Handler> entry:callbacks.entrySet()){final SessionCallback callback=entry.getKey();entry.getValue().post(new Runnable(){public void run(){event.deliver(callback);}});}}
 private synchronized void finished(final int id,final boolean success){if(sessions.remove(id)!=null)dispatch(new Event(){public void deliver(SessionCallback callback){callback.onFinished(id,success);}});}
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
  public void setStagingProgress(float value){
   if(!Float.isFinite(value))throw new IllegalArgumentException("Finite installation progress required");
   synchronized(this){if(committed||abandoned)throw new IllegalStateException("Installation session sealed");final float updated=Math.max(0,Math.min(1,value));progress=updated;
   dispatch(new Event(){public void deliver(SessionCallback callback){callback.onProgressChanged(id,updated);}});}
  }
  public void addProgress(float value){setStagingProgress(progress+value);}
  public void commit(final IntentSender target){
   synchronized(this){if(committed||abandoned||apk==null||output==null||!output.closed)throw new IllegalStateException("Invalid installation session");if(target==null)throw new NullPointerException("status receiver");committed=true;}
   new Thread(new Runnable(){public void run(){int status=STATUS_SUCCESS;String message="Installed";String pkg=params.packageName;
    try{pkg=shiraz.runtime.HostAppInstaller.install(context,apk,params.packageName);progress=1;}
    catch(Exception failure){status=STATUS_FAILURE;message=failure.getMessage();System.err.println("SHIRAZ_INSTALL_FAILED "+failure);}
    Intent result=new Intent().putExtra(EXTRA_STATUS,status).putExtra(EXTRA_STATUS_MESSAGE,message).putExtra(EXTRA_SESSION_ID,id).putExtra(EXTRA_PACKAGE_NAME,pkg);
    try{target.sendIntent(context,0,result,null,null);}catch(IntentSender.SendIntentException e){System.err.println("SHIRAZ_INSTALL_RESULT_FAILED "+e);}
    finally{try{finished(id,status==STATUS_SUCCESS);}finally{cleanup();}}
   }},"Shiraz-install-"+id).start();
  }
  private final class SessionOutput extends OutputStream {
   final FileOutputStream file;final long expected;long bytes;boolean closed;
   SessionOutput(File apk,long expected)throws IOException{file=new FileOutputStream(apk);this.expected=expected;}
   public void write(int value)throws IOException{write(new byte[]{(byte)value},0,1);}
   public synchronized void write(byte[] data,int offset,int length)throws IOException{if(closed||abandoned)throw new IOException("Session stream closed");if(length<0||bytes+length>1024L*1024*1024||(expected>=0&&bytes+length>expected))throw new IOException("APK stream size limit");file.write(data,offset,length);bytes+=length;}
   public void flush()throws IOException{file.flush();}
   public void close()throws IOException{
    // Match abandon's session -> stream lock order. Commit must not observe a
    // closed stream before close/length validation has actually completed.
    synchronized(Session.this){synchronized(this){if(closed)return;closed=true;
     try{file.close();if(expected>=0&&bytes!=expected)throw new IOException("Truncated APK stream");}
     catch(IOException failure){abandoned=true;try{cleanup();}finally{finished(id,false);}throw failure;}
    }}
   }
  }
  private void cleanup(){if(output!=null)try{output.close();}catch(IOException ignored){}if(apk!=null)apk.delete();dir.delete();}
  public void abandon(){synchronized(this){if(committed)throw new IllegalStateException("Installation already committed");abandoned=true;cleanup();}finished(id,false);}
  public void close(){}
 }
 public static final int PACKAGE_SOURCE_UNSPECIFIED=0,PACKAGE_SOURCE_OTHER=1,PACKAGE_SOURCE_STORE=2,PACKAGE_SOURCE_LOCAL_FILE=3,PACKAGE_SOURCE_DOWNLOADED_FILE=4;
 public static final class InstallConstraints implements Parcelable {
  public static final InstallConstraints GENTLE_UPDATE=new Builder().setAppNotInteractingRequired().build();
  private final boolean mDeviceIdleRequired,mAppNotForegroundRequired,mAppNotInteractingRequired,mAppNotTopVisibleRequired,mNotInCallRequired;
  InstallConstraints(boolean idle,boolean notFg,boolean notInteracting,boolean notTop,boolean notCall){
   mDeviceIdleRequired=idle;mAppNotForegroundRequired=notFg;mAppNotInteractingRequired=notInteracting;mAppNotTopVisibleRequired=notTop;mNotInCallRequired=notCall;
  }
  InstallConstraints(Parcel in){
   mDeviceIdleRequired=in.readInt()!=0;mAppNotForegroundRequired=in.readInt()!=0;mAppNotInteractingRequired=in.readInt()!=0;mAppNotTopVisibleRequired=in.readInt()!=0;mNotInCallRequired=in.readInt()!=0;
  }
  public boolean isDeviceIdleRequired(){return mDeviceIdleRequired;}
  public boolean isAppNotForegroundRequired(){return mAppNotForegroundRequired;}
  public boolean isAppNotInteractingRequired(){return mAppNotInteractingRequired;}
  public boolean isAppNotTopVisibleRequired(){return mAppNotTopVisibleRequired;}
  public boolean isNotInCallRequired(){return mNotInCallRequired;}
  @Override public boolean equals(Object o){
   if(this==o)return true;if(!(o instanceof InstallConstraints))return false;InstallConstraints c=(InstallConstraints)o;
   return mDeviceIdleRequired==c.mDeviceIdleRequired&&mAppNotForegroundRequired==c.mAppNotForegroundRequired&&mAppNotInteractingRequired==c.mAppNotInteractingRequired&&mAppNotTopVisibleRequired==c.mAppNotTopVisibleRequired&&mNotInCallRequired==c.mNotInCallRequired;
  }
  @Override public int hashCode(){return Objects.hash(Boolean.valueOf(mDeviceIdleRequired),Boolean.valueOf(mAppNotForegroundRequired),Boolean.valueOf(mAppNotInteractingRequired),Boolean.valueOf(mAppNotTopVisibleRequired),Boolean.valueOf(mNotInCallRequired));}
  @Override public void writeToParcel(Parcel dest,int flags){
   dest.writeInt(mDeviceIdleRequired?1:0);dest.writeInt(mAppNotForegroundRequired?1:0);dest.writeInt(mAppNotInteractingRequired?1:0);dest.writeInt(mAppNotTopVisibleRequired?1:0);dest.writeInt(mNotInCallRequired?1:0);
  }
  @Override public int describeContents(){return 0;}
  public static final Creator<InstallConstraints> CREATOR=new Creator<InstallConstraints>(){
   @Override public InstallConstraints createFromParcel(Parcel in){return new InstallConstraints(in);}
   @Override public InstallConstraints[] newArray(int size){return new InstallConstraints[size];}
  };
  public static final class Builder {
   private boolean mIdle,mNotFg,mNotInteracting,mNotTop,mNotCall;
   public Builder(){}
   public Builder setDeviceIdleRequired(){mIdle=true;return this;}
   public Builder setAppNotForegroundRequired(){mNotFg=true;return this;}
   public Builder setAppNotInteractingRequired(){mNotInteracting=true;return this;}
   public Builder setAppNotTopVisibleRequired(){mNotTop=true;return this;}
   public Builder setNotInCallRequired(){mNotCall=true;return this;}
   public InstallConstraints build(){return new InstallConstraints(mIdle,mNotFg,mNotInteracting,mNotTop,mNotCall);}
  }
 }
 public static final class InstallConstraintsResult implements Parcelable {
  private final boolean mAllConstraintsSatisfied;
  public InstallConstraintsResult(boolean satisfied){mAllConstraintsSatisfied=satisfied;}
  InstallConstraintsResult(Parcel in){mAllConstraintsSatisfied=in.readInt()!=0;}
  public boolean areAllConstraintsSatisfied(){return mAllConstraintsSatisfied;}
  @Override public void writeToParcel(Parcel dest,int flags){dest.writeInt(mAllConstraintsSatisfied?1:0);}
  @Override public int describeContents(){return 0;}
  public static final Creator<InstallConstraintsResult> CREATOR=new Creator<InstallConstraintsResult>(){
   @Override public InstallConstraintsResult createFromParcel(Parcel in){return new InstallConstraintsResult(in);}
   @Override public InstallConstraintsResult[] newArray(int size){return new InstallConstraintsResult[size];}
  };
 }
 public static final class PreapprovalDetails implements Parcelable {
  private final android.graphics.Bitmap mIcon;private final CharSequence mLabel;private final android.icu.util.ULocale mLocale;private final String mPackageName;
  PreapprovalDetails(android.graphics.Bitmap icon,CharSequence label,android.icu.util.ULocale locale,String pkg){
   mIcon=icon;mLabel=Objects.requireNonNull(label);mLocale=Objects.requireNonNull(locale);mPackageName=Objects.requireNonNull(pkg);
  }
  PreapprovalDetails(Parcel in){
   mIcon=in.readParcelable(android.graphics.Bitmap.class.getClassLoader());
   mLabel=android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
   String loc=in.readString();mLocale=loc!=null?new android.icu.util.ULocale(loc):android.icu.util.ULocale.US;
   mPackageName=in.readString();
  }
  @Override public void writeToParcel(Parcel dest,int flags){
   dest.writeParcelable(mIcon,flags);android.text.TextUtils.writeToParcel(mLabel,dest,flags);dest.writeString(mLocale!=null?mLocale.toString():null);dest.writeString(mPackageName);
  }
  @Override public int describeContents(){return 0;}
  public android.graphics.Bitmap getIcon(){return mIcon;}
  public CharSequence getLabel(){return mLabel;}
  public android.icu.util.ULocale getLocale(){return mLocale;}
  public String getPackageName(){return mPackageName;}
  @Override public String toString(){return "PreapprovalDetails{packageName="+mPackageName+", label="+mLabel+"}";}
  public static final Creator<PreapprovalDetails> CREATOR=new Creator<PreapprovalDetails>(){
   @Override public PreapprovalDetails createFromParcel(Parcel in){return new PreapprovalDetails(in);}
   @Override public PreapprovalDetails[] newArray(int size){return new PreapprovalDetails[size];}
  };
  public static final class Builder {
   private android.graphics.Bitmap mIcon;private CharSequence mLabel;private android.icu.util.ULocale mLocale;private String mPackageName;
   public Builder(){}
   public Builder setIcon(android.graphics.Bitmap icon){mIcon=icon;return this;}
   public Builder setLabel(CharSequence label){mLabel=label;return this;}
   public Builder setLocale(android.icu.util.ULocale locale){mLocale=locale;return this;}
   public Builder setPackageName(String packageName){mPackageName=packageName;return this;}
   public PreapprovalDetails build(){return new PreapprovalDetails(mIcon,mLabel,mLocale,mPackageName);}
  }
 }
 public static final class UnarchivalState {
  private final int mUnarchiveId;private final int mStatus;private final long mRequiredStorageBytes;private final android.app.PendingIntent mUserActionIntent;
  private UnarchivalState(int id,int status,long bytes,android.app.PendingIntent intent){mUnarchiveId=id;mStatus=status;mRequiredStorageBytes=bytes;mUserActionIntent=intent;}
  public static UnarchivalState createOkState(int unarchiveId){return new UnarchivalState(unarchiveId,0,0L,null);}
  public static UnarchivalState createUserActionRequiredState(int unarchiveId,android.app.PendingIntent userActionIntent){Objects.requireNonNull(userActionIntent);return new UnarchivalState(unarchiveId,1,0L,userActionIntent);}
  public static UnarchivalState createInsufficientStorageState(int unarchiveId,long requiredStorageBytes,android.app.PendingIntent userActionIntent){return new UnarchivalState(unarchiveId,2,requiredStorageBytes,userActionIntent);}
  public static UnarchivalState createNoConnectivityState(int unarchiveId){return new UnarchivalState(unarchiveId,3,0L,null);}
  public static UnarchivalState createGenericErrorState(int unarchiveId){return new UnarchivalState(unarchiveId,100,0L,null);}
 }
}
