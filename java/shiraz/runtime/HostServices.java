// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
/** Explicit manifest-declared started services on the application main queue. */
public final class HostServices implements AutoCloseable {
 private final Context context;private final Application app;private final String pkg;private final Properties metadata;private final Handler main;
 private final HashMap<String,Integer> declarations=new HashMap<String,Integer>();private final LinkedHashMap<ComponentName,Record> active=new LinkedHashMap<ComponentName,Record>();private final ArrayList<Record> records=new ArrayList<Record>();private final LinkedHashSet<Command> pending=new LinkedHashSet<Command>();private long queuedBytes;private boolean closed;
 private final IdentityHashMap<ServiceConnection,Boolean> deadBindings=new IdentityHashMap<ServiceConnection,Boolean>();
 private final IdentityHashMap<ServiceConnection,Boolean> unavailableBindings=new IdentityHashMap<ServiceConnection,Boolean>();
 private final HashSet<ComponentName> deadServices=new HashSet<ComponentName>();
 /** Bind explicit local or supported separate-process services; external APKs have no service registry. */
 public synchronized boolean bindUnavailable(Intent intent,ServiceConnection connection,int flags){
  live();if(intent==null||connection==null)throw new IllegalArgumentException("Service intent and connection are required");
  if((flags&~(Context.BIND_AUTO_CREATE|Context.BIND_WAIVE_PRIORITY|Context.BIND_IMPORTANT))!=0)throw new UnsupportedOperationException("Service binding flags");
  ComponentName name=intent.getComponent();String packageName=intent.getPackage();
  if(name==null&&packageName==null)throw new IllegalArgumentException("Service binding requires a component or package");
  if(name==null&&pkg.equals(packageName))throw new UnsupportedOperationException("Local service filter resolution is not implemented");
  if(name!=null&&pkg.equals(name.getPackageName())&&(packageName==null||pkg.equals(packageName))){
   Integer index=declarations.get(name.getClassName());
   if(index!=null&&flag("service."+index+".enabled",true)){
    if(flag("service."+index+".isolated",false))throw new UnsupportedOperationException("Isolated service permissions are not implemented");
    ComponentName target=new ComponentName(pkg,name.getClassName());return bind(target,intent,connection);
   }
  }
  if(!unavailableBindings.containsKey(connection)&&unavailableBindings.size()>=256)throw new IllegalStateException("Service connection limit");
  unavailableBindings.put(connection,Boolean.TRUE);return false;
 }
 private boolean bind(ComponentName name,Intent intent,final ServiceConnection connection){
  final Intent snapshot=intent.shirazBroadcastCopy();
  Record found=active.get(name);boolean fresh=found==null;
  if(fresh){if(records.size()>=64)throw new IllegalStateException("Service instance limit");found=new Record(name);
   String process=metadata.getProperty("service."+declarations.get(name.getClassName())+".process",pkg);if(process.startsWith(":"))process=pkg+process;
   String current=Application.getProcessName();if(current==null)current=pkg;
   if(!process.equals(current)){if(!ServiceProcess.available())throw new UnsupportedOperationException("Separate service launcher unavailable");found.process=process;
    for(Record other:records)if(process.equals(other.process)&&!name.equals(other.name))throw new UnsupportedOperationException("Multiple components in one service process are not implemented");}
   records.add(found);active.put(name,found);}
  final Record record=found;for(ServiceConnection existing:record.bound)if(existing==connection)return true;
  record.bound.add(connection);record.bindIntent=snapshot;
  try{enqueue(new Command(record,snapshot.shirazBroadcastBytes()){void execute(){connect(record,snapshot,connection);}});}catch(RuntimeException error){record.bound.remove(connection);if(fresh){active.remove(name);records.remove(record);}throw error;}
  return true;
 }
 private void create(Record record) throws ReflectiveOperationException {
  Service service=context.getClassLoader().loadClass(record.name.getClassName()).asSubclass(Service.class).getConstructor().newInstance();record.service=service;service.shirazAttach(context,app,this);service.onCreate();record.created=true;app.registerComponentCallbacks(service);
 }
 private void connect(Record record,Intent intent,ServiceConnection connection){
  synchronized(this){if(record.destroyed||!record.bound.contains(connection))return;}
  if(record.process!=null){connectRemote(record,intent,connection);return;}
  synchronized(this){if(deadServices.contains(record.name)){if(record.bound.remove(connection))deadBindings.put(connection,Boolean.TRUE);if(!record.started&&record.bound.isEmpty()){record.destroyed=true;if(active.get(record.name)==record)active.remove(record.name);records.remove(record);}connection.onBindingDied(record.name);return;}}
  try{if(record.service==null)create(record);if(!record.binderReady){record.binder=record.service.onBind(intent);record.binderReady=true;}}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot create service "+record.name,error);}
  catch(LinkageError|UnsupportedOperationException error){
   System.err.println("W/HostServices: service "+record.name+" cannot run on this runtime and its binding is reported as dead");error.printStackTrace();
   boolean notify;synchronized(this){deadServices.add(record.name);notify=record.bound.remove(connection);if(notify)deadBindings.put(connection,Boolean.TRUE);if(!record.started&&record.bound.isEmpty())record.destroyed=true;}
   if(record.destroyed)synchronized(this){if(active.get(record.name)==record)active.remove(record.name);records.remove(record);}
   if(notify)connection.onBindingDied(record.name);return;
  }
  if(record.binder!=null)connection.onServiceConnected(record.name,record.binder);
 }
 private void connectRemote(final Record record,final Intent intent,ServiceConnection connection){
  synchronized(this){if(record.binderReady){if(record.binder!=null)connection.onServiceConnected(record.name,record.binder);else connection.onNullBinding(record.name);return;}if(record.remoteStarting)return;record.remoteStarting=true;}
  Thread start=new Thread(new Runnable(){public void run(){
   ServiceProcess child=null;Throwable failure=null;
   try{child=ServiceProcess.start(context,record.name.getClassName(),record.process,intent);}catch(Exception|LinkageError error){failure=error;}
   final ServiceProcess result=child;final Throwable error=failure;
   synchronized(HostServices.this){record.remoteStarting=false;if(closed||record.destroyed){if(result!=null)result.close();return;}record.remote=result;}
   if(error!=null){System.err.println("SHIRAZ_SERVICE_START_FAILED component="+record.name);error.printStackTrace();}
   if(!main.post(new Runnable(){public void run(){
    synchronized(HostServices.this){if(closed||record.destroyed){if(result!=null)result.close();return;}
     if(error!=null){remoteDied(record,false);return;}record.binder=result.binder;record.binderReady=true;
     try{result.transport.remoteRoot().linkToDeath(new IBinder.DeathRecipient(){public void binderDied(){main.post(new Runnable(){public void run(){remoteDied(record,true);}});}},0);}
     catch(RemoteException dead){remoteDied(record,false);return;}
     for(ServiceConnection bound:new ArrayList<ServiceConnection>(record.bound))if(record.bound.contains(bound)){if(record.binder!=null)bound.onServiceConnected(record.name,record.binder);else bound.onNullBinding(record.name);}
    }
   }})){if(result!=null)result.close();}
  }},"shiraz-service-start");start.setDaemon(true);start.start();
 }
 private synchronized void remoteDied(Record record,boolean connected){
  if(closed||record.destroyed)return;record.destroyed=true;active.remove(record.name);records.remove(record);
  ArrayList<ServiceConnection> callbacks=new ArrayList<ServiceConnection>(record.bound);record.bound.clear();
  for(ServiceConnection connection:callbacks)deadBindings.put(connection,Boolean.TRUE);
  if(record.remote!=null)record.remote.close();
  for(ServiceConnection connection:callbacks){if(connected)connection.onServiceDisconnected(record.name);else connection.onBindingDied(record.name);}
 }
 private void disconnect(Record record,Intent intent){
  synchronized(this){if(record.destroyed||!record.bound.isEmpty())return;}
  if(record.service!=null)record.service.onUnbind(intent);
  record.binder=null;record.binderReady=false;
  boolean keep;synchronized(this){keep=record.started||!record.bound.isEmpty();}
  if(!keep)destroy(record);
 }
 public synchronized void unbindUnavailable(ServiceConnection connection){
  live();if(connection==null)throw new IllegalArgumentException("Service connection was not registered");
  if(unavailableBindings.remove(connection)!=null||deadBindings.remove(connection)!=null)return;
  for(final Record record:records)for(ServiceConnection existing:record.bound)if(existing==connection){
   record.bound.remove(existing);if(!record.bound.isEmpty())return;
   final Intent intent=record.bindIntent;enqueue(new Command(record,0){void execute(){disconnect(record,intent);}});return;
  }
  throw new IllegalArgumentException("Service connection was not registered");
 }
 private static final class Record {String process;ServiceProcess remote;boolean remoteStarting;final ComponentName name;volatile Service service;int lastId,foregroundId,retainedNotificationId;Runnable foregroundDeadline;boolean created,destroyed,started,binderReady;IBinder binder;Intent bindIntent;final ArrayList<ServiceConnection> bound=new ArrayList<ServiceConnection>();Record(ComponentName name){this.name=name;}}
 public HostServices(Context context,Application app,Properties metadata){this.context=context;this.app=app;this.pkg=context.getPackageName();this.metadata=new Properties();this.metadata.putAll(metadata);main=new Handler(Looper.getMainLooper());int count=Integer.parseInt(metadata.getProperty("service.count","0"));if(count<0||count>256)throw new IllegalArgumentException("Service declaration limit");for(int i=0;i<count;i++){String name=metadata.getProperty("service."+i+".name");if(name==null||name.length()>1024||!name.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")||declarations.put(name,i)!=null)throw new IllegalArgumentException("Invalid service declaration");}}
 private boolean flag(String key,boolean fallback){String value=metadata.getProperty(key);if(value!=null&&!value.equals("true")&&!value.equals("false")&&!value.equals("1")&&!value.equals("0")){String reference=metadata.getProperty(key+"Res");if(reference!=null&&!reference.isEmpty())return context.getResources().getBoolean((int)Long.parseLong(reference));}return bool(value,fallback);}
 private void live(){if(closed)throw new IllegalStateException("Service session closed");}
 private static boolean bool(String value,boolean fallback){if(value==null)return fallback;if(value.equals("true")||value.equals("1"))return true;if(value.equals("false")||value.equals("0"))return false;throw new UnsupportedOperationException("Resource-based service flags are not implemented");}
 private ComponentName resolve(Intent intent){if(intent==null)throw new NullPointerException("intent");ComponentName name=intent.getComponent();if(name==null)throw new UnsupportedOperationException("Implicit service resolution is not implemented");if(!name.getPackageName().equals(pkg)||intent.getPackage()!=null&&!intent.getPackage().equals(pkg))throw new UnsupportedOperationException("External service processes are not implemented");Integer index=declarations.get(name.getClassName());if(index==null)return null;String prefix="service."+index+".";if(!flag(prefix+"enabled",true))return null;String process=metadata.getProperty(prefix+"process",pkg);if(!process.equals(pkg)||flag(prefix+"isolated",false))throw new UnsupportedOperationException("Separate service processes are not implemented");return name;}
 private abstract class Command implements Runnable {final Record record;final int bytes;Command(Record record,int bytes){this.record=record;this.bytes=bytes;}public final void run(){try{synchronized(HostServices.this){if(closed||!pending.contains(this))return;}execute();}finally{synchronized(HostServices.this){if(pending.remove(this))queuedBytes-=bytes;}}}abstract void execute();}
 private void enqueue(Command command){if(pending.size()>=1024||queuedBytes+command.bytes>16*1024*1024L)throw new IllegalStateException("Service command queue limit");pending.add(command);queuedBytes+=command.bytes;try{if(!main.postAtTime(command,this,SystemClock.uptimeMillis()))throw new IllegalStateException("Service main queue stopped");}catch(RuntimeException error){pending.remove(command);queuedBytes-=command.bytes;throw error;}}
 private NotificationManager notifications(){return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);}
 public synchronized ComponentName startForeground(Intent intent){
  ComponentName name=start(intent);if(name==null)return null;final Record record=active.get(name);
  if(record.foregroundId==0&&record.foregroundDeadline==null){record.foregroundDeadline=new Runnable(){public void run(){synchronized(HostServices.this){if(!closed&&!record.destroyed&&record.foregroundId==0)throw new IllegalStateException("Service did not call startForeground within five seconds: "+record.name);}}};main.postDelayed(record.foregroundDeadline,5000);}
  return name;
 }
 private Record attached(Service service){live();for(Record record:records)if(record.service==service&&!record.destroyed)return record;throw new IllegalStateException("Service is not active");}
 public synchronized void promote(Service service,int id,Notification notification){
  Record record=attached(service);if(id==0||notification==null)throw new IllegalArgumentException("Foreground notification ID/value");
  if(notification.icon==0&&notification.getSmallIcon()==null)throw new IllegalArgumentException("Foreground notification requires an icon");
  Notification copy=notification.clone();copy.flags|=Notification.FLAG_FOREGROUND_SERVICE|Notification.FLAG_ONGOING_EVENT;
  notifications().notify(id,copy);if(record.retainedNotificationId!=0&&record.retainedNotificationId!=id)notifications().cancel(record.retainedNotificationId);record.retainedNotificationId=0;if(record.foregroundId!=0&&record.foregroundId!=id)notifications().cancel(record.foregroundId);record.foregroundId=id;
  if(record.foregroundDeadline!=null){main.removeCallbacks(record.foregroundDeadline);record.foregroundDeadline=null;}
 }
 public synchronized void demote(Service service,int flags){
  if((flags&~3)!=0)throw new IllegalArgumentException("Foreground stop flags");Record record=null;for(Record value:records)if(value.service==service){record=value;break;}if(record==null)return;
  if(record.foregroundId!=0){if((flags&Service.STOP_FOREGROUND_REMOVE)!=0)notifications().cancel(record.foregroundId);else {notifications().shirazDetachForeground(record.foregroundId);if((flags&Service.STOP_FOREGROUND_DETACH)==0)record.retainedNotificationId=record.foregroundId;}record.foregroundId=0;}
 }
 public synchronized ComponentName start(Intent intent){live();final ComponentName name=resolve(intent);if(name==null)return null;final Intent snapshot=intent.shirazBroadcastCopy();Record found=active.get(name);boolean fresh=found==null;if(fresh){if(records.size()>=64)throw new IllegalStateException("Service instance limit");found=new Record(name);records.add(found);active.put(name,found);}final Record record=found;if(record.lastId==Integer.MAX_VALUE)throw new IllegalStateException("Service start ID exhausted");final int id=record.lastId+1;
  try{enqueue(new Command(record,snapshot.shirazBroadcastBytes()){void execute(){deliver(record,snapshot,id);}});record.lastId=id;record.started=true;}catch(RuntimeException error){if(fresh){active.remove(name);records.remove(record);}throw error;}return name;
 }
 private void deliver(Record record,Intent intent,int id){if(record.destroyed)return;try{if(record.service==null)create(record);int mode=record.service.onStartCommand(intent,0,id);if(mode<0||mode>3)throw new UnsupportedOperationException("Unsupported service restart mode");}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot create service "+record.name,error);}}
 private boolean stop(Record record){record.started=false;if(!record.bound.isEmpty())return true;enqueue(new Command(record,0){void execute(){destroy(record);}});if(active.get(record.name)==record)active.remove(record.name);return true;}
 public synchronized boolean stop(Intent intent){live();ComponentName name=resolve(intent);Record record=name==null?null:active.get(name);return record!=null&&stop(record);}
 public synchronized boolean stopSelf(Service service,int id){live();for(Record record:active.values())if(record.service==service){if(id>=0&&id!=record.lastId)return false;return stop(record);}return false;}
 private void destroy(Record record){synchronized(this){if(record.destroyed)return;record.destroyed=true;if(record.remote!=null)record.remote.close();if(record.foregroundDeadline!=null)main.removeCallbacks(record.foregroundDeadline);if(record.foregroundId!=0){notifications().cancel(record.foregroundId);record.foregroundId=0;}if(record.retainedNotificationId!=0)notifications().cancel(record.retainedNotificationId);if(active.get(record.name)==record)active.remove(record.name);records.remove(record);}if(record.created){app.unregisterComponentCallbacks(record.service);record.service.onDestroy();}}
 public void close(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Service teardown requires main Looper");Record[] snapshot;synchronized(this){if(closed)return;closed=true;unavailableBindings.clear();deadBindings.clear();main.removeCallbacksAndMessages(this);pending.clear();queuedBytes=0;snapshot=records.toArray(new Record[records.size()]);}Throwable first=null;for(Record record:snapshot)try{destroy(record);}catch(RuntimeException|Error error){if(first==null)first=error;}if(first instanceof Error)throw (Error)first;if(first!=null)throw (RuntimeException)first;}
}
