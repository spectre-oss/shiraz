// SPDX-License-Identifier: GPL-3.0-only
package android.app;
import android.content.*;
import android.os.IBinder;
/** Session-owned started services, including foreground notification lifetime. Binding is separate. */
public abstract class Service extends ContextWrapper implements ComponentCallbacks2 {
 public static final int START_STICKY_COMPATIBILITY=0,START_STICKY=1,START_NOT_STICKY=2,START_REDELIVER_INTENT=3,START_CONTINUATION_MASK=15,START_FLAG_REDELIVERY=1,START_FLAG_RETRY=2;
 public static final int STOP_FOREGROUND_LEGACY=0,STOP_FOREGROUND_REMOVE=1,STOP_FOREGROUND_DETACH=2;
 private Application application;private shiraz.runtime.HostServices host;
 public Service(){super(null);}
 public final Application getApplication(){return application;}
 public void onCreate(){}public void onStart(Intent intent,int startId){}
 public int onStartCommand(Intent intent,int flags,int startId){onStart(intent,startId);return getApplicationInfo().targetSdkVersion<5?START_STICKY_COMPATIBILITY:START_STICKY;}
 public void onDestroy(){}public abstract IBinder onBind(Intent intent);public boolean onUnbind(Intent intent){return false;}public void onRebind(Intent intent){}public void onTaskRemoved(Intent rootIntent){}
 public void onConfigurationChanged(android.content.res.Configuration config){}public void onLowMemory(){}public void onTrimMemory(int level){}
 public final void startForeground(int id,Notification notification){requireHost().promote(this,id,notification);}
 public final void stopForeground(boolean remove){stopForeground(remove?STOP_FOREGROUND_REMOVE:STOP_FOREGROUND_LEGACY);}
 public final void stopForeground(int flags){requireHost().demote(this,flags);}
 public final void stopSelf(){stopSelf(-1);}public final void stopSelf(int startId){requireHost().stopSelf(this,startId);}public final boolean stopSelfResult(int startId){return requireHost().stopSelf(this,startId);}
 private shiraz.runtime.HostServices requireHost(){if(host==null)throw new IllegalStateException("Service is not attached to a host");return host;}
 public final void shirazAttach(Context context,Application application,shiraz.runtime.HostServices host){if(this.host!=null||host==null||application==null)throw new IllegalStateException("Invalid service attachment");attachBaseContext(context);this.application=application;this.host=host;}
}
