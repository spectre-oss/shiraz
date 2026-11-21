// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
/** Main-queue lifecycle for declared local services and registered external bindings. */
public final class HostServices implements AutoCloseable {
 private android.location.LocationManager locations;
 /** Only instantiated local services contribute; remote bindings belong to another process. */
 public synchronized int processImportance(){
  int result=ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED;
  if(!closed)for(Record record:records)if(!record.destroyed&&record.service!=null){
   if(record.foregroundId!=0)return ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
   result=ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE;
  }
  return result;
 }
 /** One process location manager, attached before exposing it to the app. */
 public synchronized void trackLocationManager(android.location.LocationManager manager){
  live();if(manager==null)throw new NullPointerException("location manager");
  if(locations!=null&&locations!=manager)throw new IllegalStateException("Location manager already attached");
  locations=manager;updateLocationForeground();
 }
 private void updateLocationForeground(){
  if(locations==null)return;boolean promoted=false;
  if(!closed)for(Record record:records)if(!record.destroyed&&record.foregroundId!=0&&(Integer.parseInt(metadata.getProperty("targetSdk","1"))<29||(record.foregroundType&android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)!=0)){promoted=true;break;}
  locations.shirazSetForegroundService(promoted);
 }
 public interface StartedListener {void changed(boolean started);default void completed(int startId,int restartMode){}default void completed(int startId,int restartMode,Intent original){completed(startId,restartMode);}default void finished(int startId){}}
 private final Context context;private final Application app;private final String pkg;private final Properties metadata;private final Handler main;
 private final ServiceBrokerClient jobBroker=RuntimeBroker.client();
 private final HashMap<String,Integer> declarations=new HashMap<String,Integer>();private final LinkedHashMap<ComponentName,Record> active=new LinkedHashMap<ComponentName,Record>();private final ArrayList<Record> records=new ArrayList<Record>();private final LinkedHashSet<Command> pending=new LinkedHashSet<Command>();private long queuedBytes;private boolean closed;
 private final IdentityHashMap<ServiceConnection,Boolean> deadBindings=new IdentityHashMap<ServiceConnection,Boolean>();
 private final IdentityHashMap<ServiceConnection,Boolean> unavailableBindings=new IdentityHashMap<ServiceConnection,Boolean>();
 private final HashSet<ComponentName> deadServices=new HashSet<ComponentName>();
 /** Bind declared local services or explicit external services admitted by the host broker. */
 public boolean bindUnavailable(Intent intent,ServiceConnection connection,int flags){return bindUnavailable(intent,connection,flags,context);}
 private static boolean activityCaller(Context caller){
  for(int depth=0;caller!=null&&depth<32;depth++){
   if(caller instanceof Activity)return true;
   if(!(caller instanceof ContextWrapper))return false;
   Context next=((ContextWrapper)caller).getBaseContext();
   if(next==caller)throw new IllegalArgumentException("Cyclic binding context");caller=next;
  }
  if(caller!=null)throw new IllegalArgumentException("Binding context nesting limit");return false;
 }
 private static void bindingFlags(int flags,Context caller,boolean remote){
  // API 27 predates capability inheritance: the newer bit grants nothing.
  int supported=Context.BIND_AUTO_CREATE|Context.BIND_WAIVE_PRIORITY|Context.BIND_IMPORTANT|Context.BIND_ADJUST_WITH_ACTIVITY|Context.BIND_INCLUDE_CAPABILITIES;
  if((flags&~supported)!=0||(flags&Context.BIND_AUTO_CREATE)==0)
   throw new UnsupportedOperationException("Binding currently requires BIND_AUTO_CREATE and supported priority flags: 0x"+Integer.toHexString(flags));
  // Same-process bindings already share the Activity's process importance.
  // An application/service context carries no Activity token to adjust from.
  if(remote&&(flags&Context.BIND_ADJUST_WITH_ACTIVITY)!=0&&activityCaller(caller))
   throw new UnsupportedOperationException("Remote activity binding priority adjustment is not implemented");
 }
 public synchronized boolean bindUnavailable(Intent intent,ServiceConnection connection,int flags,Context caller){
  live();if(intent==null||connection==null)throw new IllegalArgumentException("Service intent and connection are required");
  try{intent=resolvePackageAction(intent);}catch(ServiceBrokerClient.ServiceUnavailableException absent){return unavailable(connection);}
  ComponentName name=intent.getComponent();String packageName=intent.getPackage();
  if(name==null&&packageName==null)throw new IllegalArgumentException("Service binding requires a component or package");
  if(name==null&&pkg.equals(packageName))throw new UnsupportedOperationException("Local service filter resolution is not implemented");
  if(name!=null&&pkg.equals(name.getPackageName())&&(packageName==null||pkg.equals(packageName))){
   Integer index=declarations.get(name.getClassName());
   if(index!=null&&flag("service."+index+".enabled",true)){
    // Flags cannot affect scheduling when the requested service is absent.
    String targetProcess=metadata.getProperty("service."+index+".process",pkg);
    if(targetProcess.startsWith(":"))targetProcess=pkg+targetProcess;
    String currentProcess=Application.getProcessName();if(currentProcess==null)currentProcess=pkg;
    bindingFlags(flags,caller,!targetProcess.equals(currentProcess));

    if(flag("service."+index+".isolated",false))throw new UnsupportedOperationException("Isolated service permissions are not implemented");
    ComponentName target=new ComponentName(pkg,name.getClassName());return bind(target,intent,connection);
   }
  }
  if(name!=null&&!pkg.equals(name.getPackageName())&&(packageName==null||packageName.equals(name.getPackageName()))&&RuntimeBroker.client()!=null){
   bindingFlags(flags,caller,true);
   try{RuntimeBroker.client().resolve(name.getPackageName(),name.getClassName());}
   catch(ServiceBrokerClient.ServiceUnavailableException absent){return unavailable(connection);}
   catch(java.io.IOException error){throw new IllegalStateException("Service broker unavailable",error);}
   return bind(name,intent,connection);
  }
  return unavailable(connection);
 }
 private boolean unavailable(ServiceConnection connection){
  if(!unavailableBindings.containsKey(connection)&&unavailableBindings.size()>=256)throw new IllegalStateException("Service connection limit");
  unavailableBindings.put(connection,Boolean.TRUE);return false;
 }
 private boolean bind(ComponentName name,Intent intent,final ServiceConnection connection){
  final Intent snapshot=intent.shirazBroadcastCopy();
  Record found=active.get(name);
  if(found==null||found.external){
   Record matched=null;
   for(Record candidate:records)if(!candidate.destroyed&&candidate.external&&candidate.name.equals(name)&&candidate.bindIntent!=null&&sameBinding(candidate.bindIntent,snapshot)){matched=candidate;break;}
   if(matched!=null)found=matched;
   else if(found!=null&&found.external&&found.bindIntent!=null&&!sameBinding(found.bindIntent,snapshot))found=null;
  }
  boolean fresh=found==null;
  if(fresh){if(records.size()>=64)throw new IllegalStateException("Service instance limit");found=new Record(name);
   if(!pkg.equals(name.getPackageName()))found.external=true;
   else{String process=metadata.getProperty("service."+declarations.get(name.getClassName())+".process",pkg);if(process.startsWith(":"))process=pkg+process;
   String current=Application.getProcessName();if(current==null)current=pkg;
   if(!process.equals(current)){if(RuntimeBroker.client()!=null)found.external=true;else if(!ServiceProcess.available())throw new UnsupportedOperationException("Separate service launcher unavailable");found.process=process;
    for(Record other:records)if(process.equals(other.process)&&!name.equals(other.name))throw new UnsupportedOperationException("Multiple components in one service process are not implemented");}
   }records.add(found);active.put(name,found);}
  final Record record=found;
  boolean local=!record.external&&record.process==null;
  for(ServiceConnection existing:record.bound)if(existing==connection){
   Intent previous=local?record.bindingClients.get(connection).intent:record.bindIntent;
   if(!sameBinding(previous,snapshot))throw new UnsupportedOperationException("One binding filter per connection and component");
   return true;
  }
  if(!local&&record.bindIntent!=null&&!sameBinding(record.bindIntent,snapshot))throw new UnsupportedOperationException("Multiple unsupervised remote binding filters are not implemented");
  Binding group=null;
  if(local){
   for(Binding value:record.bindings)if(sameBinding(value.intent,snapshot)){group=value;break;}
   if(group==null){if(record.bindings.size()>=64)throw new IllegalStateException("Service binding filter limit");group=new Binding(snapshot);record.bindings.add(group);}
   group.references++;record.bindingClients.put(connection,group);
  }
  record.bound.add(connection);if(record.bindIntent==null)record.bindIntent=snapshot;
  try{enqueue(new Command(record,snapshot.shirazBroadcastBytes()){void execute(){connect(record,snapshot,connection);}});}catch(RuntimeException error){record.bound.remove(connection);Binding removed=record.bindingClients.remove(connection);if(removed!=null&&--removed.references==0&&!removed.ready)record.bindings.remove(removed);if(fresh){active.remove(name);records.remove(record);}throw error;}
  return true;
 }
 private void create(Record record) throws ReflectiveOperationException {
  Service service=context.getClassLoader().loadClass(record.name.getClassName()).asSubclass(Service.class).getConstructor().newInstance();record.service=service;service.shirazAttach(context,app,this);service.onCreate();record.created=true;app.registerComponentCallbacks(service);
 }
 private void connect(Record record,Intent intent,ServiceConnection connection){
  synchronized(this){if(record.destroyed||!record.bound.contains(connection))return;}
  if(record.external||record.process!=null){connectRemote(record,record.bindIntent,connection);return;}
  synchronized(this){if(deadServices.contains(record.name)){if(removeBinding(record,connection))deadBindings.put(connection,Boolean.TRUE);if(!record.workerOwned&&!record.started&&record.bound.isEmpty()&&record.jobs.isEmpty()){record.destroyed=true;if(active.get(record.name)==record)active.remove(record.name);records.remove(record);}connection.onBindingDied(record.name);return;}}
  final Binding binding=record.bindingClients.get(connection);binding.unbound=false;
  try{if(record.service==null)create(record);if(!binding.ready){binding.binder=record.service.onBind(binding.intent.shirazBroadcastCopy());binding.ready=true;}else if(binding.rebind){binding.rebind=false;record.service.onRebind(binding.intent.shirazBroadcastCopy());}}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot create service "+record.name,error);}
  catch(LinkageError|UnsupportedOperationException error){
   System.err.println("W/HostServices: service "+record.name+" cannot run on this runtime and its binding is reported as dead");error.printStackTrace();
   boolean notify;synchronized(this){deadServices.add(record.name);notify=removeBinding(record,connection);if(notify)deadBindings.put(connection,Boolean.TRUE);if(!record.workerOwned&&!record.started&&record.bound.isEmpty()&&record.jobs.isEmpty())record.destroyed=true;}
   if(record.destroyed)synchronized(this){if(active.get(record.name)==record)active.remove(record.name);records.remove(record);}
   if(notify)connection.onBindingDied(record.name);return;
  }
  if(binding.binder!=null)connection.onServiceConnected(record.name,binding.binder);else connection.onNullBinding(record.name);
 }
 private void connectRemote(final Record record,final Intent intent,ServiceConnection connection){
  synchronized(this){if(record.binderReady){if(record.binder!=null)connection.onServiceConnected(record.name,record.binder);else connection.onNullBinding(record.name);return;}if(record.remoteStarting||record.remoteRetry!=null||record.destroyed||record.bound.isEmpty())return;record.remoteStarting=true;}
  Thread start=new Thread(new Runnable(){public void run(){
   ServiceProcess child=null;Throwable failure=null;
   try{child=record.external?ServiceProcess.external(intent):ServiceProcess.start(context,record.name.getClassName(),record.process,intent);}catch(Exception|LinkageError error){failure=error;}
   final ServiceProcess result=child;final Throwable error=failure;
   synchronized(HostServices.this){record.remoteStarting=false;if(closed||record.destroyed){if(result!=null)result.close();return;}record.remote=result;}
   if(error!=null){System.err.println("SHIRAZ_SERVICE_START_FAILED component="+record.name);error.printStackTrace();}
   if(!main.post(new Runnable(){public void run(){
    synchronized(HostServices.this){if(closed||record.destroyed){if(result!=null)result.close();return;}
     if(error!=null){remoteDied(record,false,!(error instanceof SecurityException||error instanceof UnsupportedOperationException));return;}record.binder=result.binder;record.binderReady=true;
     try{result.transport.remoteRoot().linkToDeath(new IBinder.DeathRecipient(){public void binderDied(){main.post(new Runnable(){public void run(){synchronized(HostServices.this){if(record.remote==result)remoteDied(record,true,true);}}});}},0);}
     catch(RemoteException dead){remoteDied(record,false,true);return;}
     for(ServiceConnection bound:new ArrayList<ServiceConnection>(record.bound))if(record.bound.contains(bound)){if(record.binder!=null)bound.onServiceConnected(record.name,record.binder);else bound.onNullBinding(record.name);}
    }
   }})){if(result!=null)result.close();}
  }},"shiraz-service-start");start.setDaemon(true);start.start();
 }
 private synchronized void remoteDied(final Record record,boolean connected,boolean retryable){
  if(closed||record.destroyed)return;
  ServiceProcess previous=record.remote;record.remote=null;record.binder=null;record.binderReady=false;
  if(previous!=null)previous.close();
  boolean recover=record.external&&retryable&&(connected||record.reconnecting)&&record.remoteAttempts<3;
  if(recover){
   record.reconnecting=true;
   if(connected)for(ServiceConnection connection:new ArrayList<ServiceConnection>(record.bound))if(record.bound.contains(connection))connection.onServiceDisconnected(record.name);
   if(record.destroyed||closed)return;
   if(record.bound.isEmpty()){destroy(record);return;}
   final long delay=record.remoteAttempts==0?250:record.remoteAttempts==1?1000:4000;record.remoteAttempts++;
   Command retry=new Command(record,0){void execute(){synchronized(HostServices.this){record.remoteRetry=null;if(record.destroyed||record.bound.isEmpty())return;connectRemote(record,record.bindIntent,record.bound.get(0));}}};
   record.remoteRetry=retry;
   try{enqueue(retry,delay);return;}catch(RuntimeException failure){record.remoteRetry=null;}
  }
  record.destroyed=true;if(active.get(record.name)==record)active.remove(record.name);records.remove(record);
  ArrayList<ServiceConnection> callbacks=new ArrayList<ServiceConnection>(record.bound);record.bound.clear();
  for(ServiceConnection connection:callbacks)deadBindings.put(connection,Boolean.TRUE);
  for(ServiceConnection connection:callbacks){if(connected)connection.onServiceDisconnected(record.name);connection.onBindingDied(record.name);}
 }
 private boolean removeBinding(Record record,ServiceConnection connection){
  boolean removed=record.bound.remove(connection);Binding binding=record.bindingClients.remove(connection);
  if(binding!=null&&--binding.references==0&&!binding.ready)record.bindings.remove(binding);
  return removed;
 }
 private static boolean sameBinding(Intent a,Intent b){
  return Objects.equals(a.getAction(),b.getAction())&&Objects.equals(a.getDataString(),b.getDataString())&&Objects.equals(a.getType(),b.getType())&&Objects.equals(a.getPackage(),b.getPackage())&&Objects.equals(a.getComponent(),b.getComponent())&&Objects.equals(a.getCategories(),b.getCategories());
 }
 private static final class Binding {final Intent intent;IBinder binder;boolean ready,rebind,unbound;int references;Binding(Intent intent){this.intent=intent;}}
 private void disconnectBinding(Record record,Binding binding){
  synchronized(this){if(record.destroyed||binding.references!=0)return;}
  if(record.service!=null&&binding.ready)binding.rebind=record.service.onUnbind(binding.intent.shirazBroadcastCopy());
  binding.unbound=true;
  if(!binding.rebind)record.bindings.remove(binding);
  synchronized(this){if(!record.workerOwned&&!record.started&&record.bound.isEmpty()&&record.jobs.isEmpty()){for(Binding other:record.bindings)if(other.ready&&!other.unbound)return;destroy(record);}}
 }
 private void disconnect(Record record,Intent intent){
  synchronized(this){if(record.destroyed||!record.bound.isEmpty())return;}
  if(record.service!=null&&record.binderReady)record.rebind=record.service.onUnbind(intent);
  if(!record.rebind){record.binder=null;record.binderReady=false;}
  boolean keep;synchronized(this){keep=record.workerOwned||record.started||!record.bound.isEmpty()||!record.jobs.isEmpty();}
  if(!keep)destroy(record);
 }
 public synchronized void unbindUnavailable(ServiceConnection connection){
  live();if(connection==null)throw new IllegalArgumentException("Service connection was not registered");
  ArrayList<Record> matches=new ArrayList<Record>();
  for(Record record:records)for(ServiceConnection existing:record.bound)if(existing==connection){matches.add(record);break;}
  if(matches.isEmpty()&&!unavailableBindings.containsKey(connection)&&!deadBindings.containsKey(connection))throw new IllegalArgumentException("Service connection was not registered");
  ArrayList<Command> scheduled=new ArrayList<Command>();
  try{for(final Record record:matches){
   final Binding binding=record.bindingClients.get(connection);
   if(binding!=null?binding.references==1:record.bound.size()==1){
    final Intent intent=record.bindIntent;Command command=new Command(record,0){void execute(){if(binding!=null)disconnectBinding(record,binding);else disconnect(record,intent);}};
    enqueue(command);scheduled.add(command);
   }
  }}catch(RuntimeException error){for(Command command:scheduled){main.removeCallbacks(command);pending.remove(command);}throw error;}
  // Commit removal only after every last-client lifecycle callback is queued.
  for(Record record:matches)for(int i=0;i<record.bound.size();i++)if(record.bound.get(i)==connection){record.bound.remove(i);Binding binding=record.bindingClients.remove(connection);if(binding!=null)binding.references--;break;}
  unavailableBindings.remove(connection);deadBindings.remove(connection);
 }
 private static final class Record {final ArrayList<Binding> bindings=new ArrayList<Binding>();final IdentityHashMap<ServiceConnection,Binding> bindingClients=new IdentityHashMap<ServiceConnection,Binding>();StartedListener startedListener;String process;ServiceProcess remote;boolean remoteStarting,external,reconnecting;int remoteAttempts;Command remoteRetry;final ComponentName name;volatile Service service;int lastId,finishedId,foregroundId,foregroundType,retainedNotificationId;Runnable foregroundDeadline;boolean created,destroyed,started,binderReady,rebind,workerOwned;IBinder binder;Intent bindIntent;final java.util.HashSet<android.app.job.JobParameters> jobs=new java.util.HashSet<android.app.job.JobParameters>();final ArrayList<ServiceConnection> bound=new ArrayList<ServiceConnection>();Record(ComponentName name){this.name=name;}}
 public HostServices(Context context,Application app,Properties metadata){this.context=context;this.app=app;this.pkg=context.getPackageName();this.metadata=new Properties();this.metadata.putAll(metadata);main=new Handler(Looper.getMainLooper());int count=Integer.parseInt(metadata.getProperty("service.count","0"));if(count<0||count>512)throw new IllegalArgumentException("Service declaration limit");for(int i=0;i<count;i++){String name=metadata.getProperty("service."+i+".name");if(name==null||name.length()>1024||!name.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")||declarations.put(name,i)!=null)throw new IllegalArgumentException("Invalid service declaration");}}
 private boolean flag(String key,boolean fallback){String value=metadata.getProperty(key);if(value!=null&&!value.equals("true")&&!value.equals("false")&&!value.equals("1")&&!value.equals("0")){String reference=metadata.getProperty(key+"Res");if(reference!=null&&!reference.isEmpty())return context.getResources().getBoolean((int)Long.parseLong(reference));}return bool(value,fallback);}
 private void live(){if(closed)throw new IllegalStateException("Service session closed");}
 private static boolean bool(String value,boolean fallback){if(value==null)return fallback;if(value.equals("true")||value.equals("1"))return true;if(value.equals("false")||value.equals("0"))return false;throw new UnsupportedOperationException("Resource-based service flags are not implemented");}
 private ComponentName resolve(Intent intent){if(intent==null)throw new NullPointerException("intent");ComponentName name=intent.getComponent();if(name==null)throw new UnsupportedOperationException("Implicit service resolution is not implemented");if(!name.getPackageName().equals(pkg)||intent.getPackage()!=null&&!intent.getPackage().equals(pkg))throw new UnsupportedOperationException("External service processes are not implemented");Integer index=declarations.get(name.getClassName());if(index==null)return null;String prefix="service."+index+".";if(!flag(prefix+"enabled",true))return null;String process=metadata.getProperty(prefix+"process",pkg);if(process.startsWith(":"))process=pkg+process;String current=Application.getProcessName();if(current==null)current=pkg;if(!process.equals(current)||flag(prefix+"isolated",false))throw new UnsupportedOperationException("Separate service processes are not implemented");return name;}
 private abstract class Command implements Runnable {final Record record;final int bytes;Command(Record record,int bytes){this.record=record;this.bytes=bytes;}public final void run(){try{synchronized(HostServices.this){if(closed||!pending.contains(this))return;}execute();}finally{synchronized(HostServices.this){if(pending.remove(this))queuedBytes-=bytes;}}}abstract void execute();}
 private void enqueue(Command command){enqueue(command,0);}
 private void enqueue(Command command,long delay){if(pending.size()>=1024||queuedBytes+command.bytes>16*1024*1024L)throw new IllegalStateException("Service command queue limit");pending.add(command);queuedBytes+=command.bytes;try{if(!main.postAtTime(command,this,SystemClock.uptimeMillis()+delay))throw new IllegalStateException("Service main queue stopped");}catch(RuntimeException error){pending.remove(command);queuedBytes-=command.bytes;throw error;}}
 private NotificationManager notifications(){return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);}
 public synchronized ComponentName startForeground(Intent intent){
  live();try{intent=resolvePackageAction(intent);}catch(ServiceBrokerClient.ServiceUnavailableException absent){return null;}if(externalStart(intent))return startExternal(intent,true);
  return startLocal(intent,true);
 }
 private Record attached(Service service){live();for(Record record:records)if(record.service==service&&!record.destroyed)return record;throw new IllegalStateException("Service is not active");}
 private void cancelServiceNotification(Record owner,int id){
  if(id==0)return;
  for(Record other:records)if(other!=owner&&!other.destroyed&&(other.foregroundId==id||other.retainedNotificationId==id))return;
  notifications().cancel(id);
 }
 public synchronized int foregroundType(Service service){
  for(Record record:records)if(record.service==service&&!record.destroyed)return record.foregroundType;
  return 0;
 }
 public synchronized void promote(Service service,int id,Notification notification){promote(service,id,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST);}
 public synchronized void promote(Service service,int id,Notification notification,int requestedType){
  Record record=attached(service);
  if(Integer.parseInt(metadata.getProperty("targetSdk","1"))>=28&&context.checkSelfPermission("android.permission.FOREGROUND_SERVICE")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Foreground service requires android.permission.FOREGROUND_SERVICE");
  int declared=Integer.parseInt(metadata.getProperty("service."+declarations.get(record.name.getClassName())+".foregroundServiceType","0"));
  if(declared<0)throw new IllegalArgumentException("Invalid declared foreground service type");
  int type=requestedType==android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST?declared:requestedType;
  if(type<0||(type&~declared)!=0)throw new IllegalArgumentException("Foreground service type is not declared");
  if(type==0&&Integer.parseInt(metadata.getProperty("targetSdk","1"))>=34){
   if(requestedType==android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)throw new android.app.MissingForegroundServiceTypeException("Foreground service requires a declared type");
   throw new android.app.InvalidForegroundServiceTypeException("Foreground service type NONE is not allowed");
  }
  if(Integer.parseInt(metadata.getProperty("targetSdk","1"))>=34){
   ForegroundServicePermissions.enforce(context,type);
   if((type&android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)!=0&&context.checkSelfPermission("android.permission.CAMERA")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Camera foreground service requires camera permission");
   if((type&android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)!=0&&context.checkSelfPermission("android.permission.RECORD_AUDIO")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Microphone foreground service requires recording permission");
   if((type&android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)!=0){
    if(context.checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION")!=android.content.pm.PackageManager.PERMISSION_GRANTED&&context.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("Location foreground service requires coarse or fine location permission");
   }
  }
  // Other type prerequisites and while-in-use admission remain unsupported.
  // Type bookkeeping does not grant location, capture or any other permission.
 if(id==0||notification==null)throw new IllegalArgumentException("Foreground notification ID/value");
  if(notification.icon==0&&notification.getSmallIcon()==null)throw new IllegalArgumentException("Foreground notification requires an icon");
  Notification copy=notification.clone();copy.flags|=Notification.FLAG_FOREGROUND_SERVICE|Notification.FLAG_ONGOING_EVENT;
  notifications().notify(id,copy);if(record.retainedNotificationId!=0&&record.retainedNotificationId!=id)cancelServiceNotification(record,record.retainedNotificationId);record.retainedNotificationId=0;if(record.foregroundId!=0&&record.foregroundId!=id)cancelServiceNotification(record,record.foregroundId);record.foregroundId=id;record.foregroundType=type;updateLocationForeground();
  if(record.foregroundDeadline!=null){main.removeCallbacks(record.foregroundDeadline);record.foregroundDeadline=null;}
 }
 public synchronized void demote(Service service,int flags){
  if((flags&~3)!=0)throw new IllegalArgumentException("Foreground stop flags");Record record=null;for(Record value:records)if(value.service==service){record=value;break;}if(record==null)return;
  int id=record.foregroundId!=0?record.foregroundId:record.retainedNotificationId;
  if(id==0)return;
  if((flags&Service.STOP_FOREGROUND_REMOVE)!=0){cancelServiceNotification(record,id);record.retainedNotificationId=0;}
  else {notifications().shirazDetachForeground(id);record.retainedNotificationId=(flags&Service.STOP_FOREGROUND_DETACH)==0?id:0;}
  record.foregroundId=0;record.foregroundType=0;updateLocationForeground();
 }
 private Intent resolvePackageAction(Intent intent){
  if(intent==null||intent.getComponent()!=null||RuntimeBroker.client()==null)return intent;
  try{return RuntimeBroker.client().explicitServiceIntent(intent);}
  catch(java.io.IOException error){throw new IllegalStateException("Service action discovery unavailable",error);}
 }
 private boolean externalStart(Intent intent){
  if(intent==null||intent.getComponent()==null||RuntimeBroker.client()==null)return false;
  ComponentName name=intent.getComponent();if(!pkg.equals(name.getPackageName()))return true;
  Integer index=declarations.get(name.getClassName());if(index==null)return false;
  String key="service."+index+".";if(!flag(key+"enabled",true))return false;
  if(flag(key+"isolated",false))throw new UnsupportedOperationException("Isolated service permissions are not implemented");
  String process=metadata.getProperty(key+"process",pkg);if(process.startsWith(":"))process=pkg+process;
  String current=Application.getProcessName();if(current==null)current=pkg;return !process.equals(current);
 }
 private ComponentName startExternal(Intent intent,boolean foreground){
  if(intent.getPackage()!=null&&!intent.getPackage().equals(intent.getComponent().getPackageName()))throw new IllegalArgumentException("Service package mismatch");
  try{return RuntimeBroker.client().start(intent,foreground);}catch(ServiceBrokerClient.ServiceUnavailableException absent){return null;}catch(RuntimeException error){throw error;}catch(Exception error){throw new IllegalStateException("Service start delivery failed",error);}
 }
 public synchronized ComponentName start(Intent intent){
  live();try{intent=resolvePackageAction(intent);}catch(ServiceBrokerClient.ServiceUnavailableException absent){return null;}if(externalStart(intent))return startExternal(intent,false);
  return startLocal(intent,false);
 }
 private ComponentName startLocal(Intent intent,boolean foreground){
  final ComponentName name=resolve(intent);if(name==null)return null;
  final Intent snapshot=intent.shirazBroadcastCopy();Record found=active.get(name);boolean fresh=found==null;
  if(fresh){if(records.size()>=64)throw new IllegalStateException("Service instance limit");found=new Record(name);records.add(found);active.put(name,found);}
  final Record record=found;if(record.lastId==Integer.MAX_VALUE)throw new IllegalStateException("Service start ID exhausted");
  final int id=record.lastId+1;final boolean wasStarted=record.started;
  Command command=new Command(record,snapshot.shirazBroadcastBytes()){void execute(){deliver(record,snapshot,id);}};
  Runnable deadline=null;
  if(foreground&&record.foregroundId==0&&record.foregroundDeadline==null)deadline=new Runnable(){public void run(){synchronized(HostServices.this){if(!closed&&!record.destroyed&&record.foregroundId==0)throw new IllegalStateException("Service did not call startForeground within five seconds: "+record.name);}}};
  try{
   // Delivery and promotion timeout must both be admitted before start succeeds.
   if(deadline!=null){if(!main.postDelayed(deadline,5000))throw new IllegalStateException("Service main queue stopped");record.foregroundDeadline=deadline;}
   enqueue(command);record.lastId=id;record.started=true;if(!wasStarted&&record.startedListener!=null)record.startedListener.changed(true);
  }catch(RuntimeException error){
   main.removeCallbacks(command);if(pending.remove(command))queuedBytes-=command.bytes;
   if(deadline!=null){main.removeCallbacks(deadline);if(record.foregroundDeadline==deadline)record.foregroundDeadline=null;}
   record.lastId=id-1;record.started=wasStarted;if(fresh){active.remove(name);records.remove(record);}throw error;
  }
  return name;
 }
 private void deliver(Record record,Intent intent,int id){deliver(record,intent,id,0);}
 private void deliver(Record record,Intent intent,int id,int flags){if(record.destroyed)return;try{if(record.service==null)create(record);Intent original=record.startedListener!=null&&intent!=null?intent.shirazBroadcastCopy():null;int mode=record.service.onStartCommand(intent,flags,id);if(mode<0||mode>3)throw new UnsupportedOperationException("Unsupported service restart mode");if(record.started&&!record.destroyed&&record.startedListener!=null)record.startedListener.completed(id,mode,original);}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot create service "+record.name,error);}}
 /** A dedicated worker owns this service until its control channel closes. */
 synchronized int workerStartId(ComponentName name){
  Record record=active.get(name);
  if(Looper.myLooper()!=Looper.getMainLooper()||record==null||!record.workerOwned||!record.started||record.lastId<=0)throw new IllegalStateException("Current worker start required");
  return record.lastId;
 }
 synchronized void restartStickyWorker(ComponentName name){
  live();if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Service restart requires main Looper");
  final Record record=active.get(name);
  if(record==null||!record.workerOwned||record.started||record.lastId!=0)throw new IllegalStateException("Sticky restart requires a fresh worker service");
  Command command=new Command(record,0){void execute(){deliver(record,null,1);}};
  try{enqueue(command);record.lastId=1;record.started=true;if(record.startedListener!=null)record.startedListener.changed(true);}
  catch(RuntimeException error){main.removeCallbacks(command);pending.remove(command);record.lastId=0;record.started=false;throw error;}
 }
 synchronized void restartRedeliveryWorker(ComponentName name,ServiceRedelivery work){
  live();if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Service restart requires main Looper");
  final Record record=active.get(name);
  if(record==null||!record.workerOwned||record.started||record.lastId!=0)throw new IllegalStateException("Redelivery requires a fresh worker service");
  ArrayList<Command> added=new ArrayList<Command>();
  try{
   for(int i=0;i<work.ids.length;i++){
    final int id=work.ids[i];final Intent intent=work.intents[i];
    Command command=new Command(record,intent.shirazBroadcastBytes()){void execute(){deliver(record,intent,id,Service.START_FLAG_REDELIVERY);}};
    enqueue(command);added.add(command);
   }
   record.lastId=work.ids[work.ids.length-1];record.started=true;
   if(record.startedListener!=null)record.startedListener.changed(true);
  }catch(RuntimeException error){for(Command command:added){main.removeCallbacks(command);if(pending.remove(command))queuedBytes-=command.bytes;}record.lastId=0;record.started=false;throw error;}
 }
 public synchronized Service createWorkerService(ComponentName name){return createWorkerService(name,null);}
 public synchronized Service createWorkerService(ComponentName name,StartedListener listener){
  live();if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Service creation requires main Looper");
  if(resolve(new Intent().setComponent(name))==null)throw new IllegalArgumentException("Worker service unavailable");
  if(active.containsKey(name)||records.size()>=64)throw new IllegalStateException("Worker service already owned or instance limit");
  Record record=new Record(name);record.workerOwned=true;record.startedListener=listener;records.add(record);active.put(name,record);
  try{create(record);return record.service;}catch(ReflectiveOperationException error){destroy(record);throw new IllegalStateException("Cannot create worker service "+name,error);}
  catch(RuntimeException|Error error){destroy(record);throw error;}
 }
 private boolean stop(Record record){
  boolean destroyable=!record.workerOwned&&record.bound.isEmpty()&&record.jobs.isEmpty();
  // Admit destruction before changing lifecycle state: queue rejection leaves the service started.
  if(destroyable)enqueue(new Command(record,0){void execute(){destroy(record);}});
  boolean wasStarted=record.started;record.started=false;
  if(wasStarted&&record.startedListener!=null)record.startedListener.changed(false);
  if(destroyable&&active.get(record.name)==record)active.remove(record.name);
  return true;
 }
 public synchronized boolean stop(Intent intent){live();try{intent=resolvePackageAction(intent);}catch(ServiceBrokerClient.ServiceUnavailableException absent){return false;}if(externalStart(intent)){
  if(intent.getPackage()!=null&&!intent.getPackage().equals(intent.getComponent().getPackageName()))throw new IllegalArgumentException("Service package mismatch");
  try{return RuntimeBroker.client().stop(intent);}catch(ServiceBrokerClient.ServiceUnavailableException absent){return false;}catch(RuntimeException error){throw error;}catch(Exception error){throw new IllegalStateException("Service stop delivery failed",error);}
 }ComponentName name=resolve(intent);Record record=name==null?null:active.get(name);return record!=null&&stop(record);}
 public synchronized boolean stopSelf(Service service,int id){live();for(Record record:active.values())if(record.service==service){if(id>0&&id<=record.lastId&&record.started&&id>record.finishedId){if(record.startedListener!=null)record.startedListener.finished(id);record.finishedId=id;}if(id>=0&&id!=record.lastId)return false;return stop(record);}return false;}
 private void destroy(Record record){synchronized(this){if(record.destroyed)return;record.destroyed=true;record.foregroundType=0;if(record.remoteRetry!=null){main.removeCallbacks(record.remoteRetry);pending.remove(record.remoteRetry);record.remoteRetry=null;}if(record.remote!=null)record.remote.close();if(record.foregroundDeadline!=null)main.removeCallbacks(record.foregroundDeadline);if(record.foregroundId!=0){cancelServiceNotification(record,record.foregroundId);record.foregroundId=0;}if(record.retainedNotificationId!=0)cancelServiceNotification(record,record.retainedNotificationId);if(active.get(record.name)==record)active.remove(record.name);records.remove(record);updateLocationForeground();}if(record.created){app.unregisterComponentCallbacks(record.service);record.service.onDestroy();}}
 public synchronized void validateJob(android.app.job.JobInfo job){
  live();
  if(jobBroker!=null){
   ComponentName target=job.getService();if(target==null||!pkg.equals(target.getPackageName()))throw new IllegalArgumentException("Job service must belong to caller");
   try{jobBroker.resolveJob(pkg,target.getClassName());return;}
   catch(java.io.IOException error){throw new IllegalStateException("Job broker unavailable",error);}
  }
  ComponentName name=resolve(new Intent().setComponent(job.getService()));
  if(name==null)throw new IllegalArgumentException("Job service is unavailable");
  String permission=metadata.getProperty("service."+declarations.get(name.getClassName())+".permission");
  if(!android.app.job.JobService.PERMISSION_BIND.equals(permission))throw new IllegalArgumentException("Job service requires BIND_JOB_SERVICE");
 }
 public synchronized boolean hasJobBroker(){live();return jobBroker!=null;}
 public synchronized void enqueueJob(android.app.job.JobInfo job,android.app.job.JobWorkItem item){
  live();if(jobBroker==null)throw new IllegalStateException("Queued job work requires the host broker");
  try{jobBroker.enqueueJob(job,item,pkg);}catch(java.io.IOException error){throw new IllegalStateException("Job enqueue outcome unavailable",error);}
 }
 public synchronized ServiceBrokerClient.JobMutation persistJob(android.app.job.JobInfo job){
  live();if(jobBroker==null)throw new IllegalStateException("Persistent jobs require the host broker");
  try{return jobBroker.scheduleSharedJob(job,pkg);}catch(java.io.IOException error){throw new IllegalStateException("Job submission outcome unavailable",error);}
 }
 public synchronized boolean cancelPersistedJob(int id){
  live();if(jobBroker==null)return false;
  try{return jobBroker.cancelPersistedJob(id).cleanupPending;}catch(java.io.IOException error){throw new IllegalStateException("Job cancellation outcome unavailable",error);}
 }
 public synchronized void cancelAllSharedJobs(){
  live();if(jobBroker==null)return;
  try{jobBroker.cancelAllSharedJobs();}catch(java.io.IOException error){throw new IllegalStateException("Job cancellation outcome unavailable",error);}
 }
 public synchronized int[] persistedJobIds(){
  live();if(jobBroker==null)return new int[0];
  try{return jobBroker.listPersistedJobIds();}catch(java.io.IOException error){throw new IllegalStateException("Job listing unavailable",error);}
 }
 public synchronized android.app.job.JobInfo persistedJob(int id){
  live();if(jobBroker==null)return null;
  try{return jobBroker.getPersistedJob(id,pkg);}catch(java.io.IOException error){throw new IllegalStateException("Job query unavailable",error);}
 }
 public boolean runJob(android.app.job.JobInfo job,android.app.job.JobParameters params){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Job main thread");
  Record record;
  synchronized(this){validateJob(job);record=active.get(job.getService());if(record==null){if(records.size()>=64)throw new IllegalStateException("Service instance limit");record=new Record(job.getService());records.add(record);active.put(record.name,record);}record.jobs.add(params);}
  try{if(record.service==null)create(record);if(!(record.service instanceof android.app.job.JobService))throw new IllegalArgumentException("Declared job service is not a JobService");return ((android.app.job.JobService)record.service).onStartJob(params);}
  catch(ReflectiveOperationException error){finishJob(params,false);throw new IllegalStateException("Cannot create job service",error);}
  catch(RuntimeException|Error error){finishJob(params,false);throw error;}
 }
 public void finishJob(android.app.job.JobParameters params,boolean stop){finishJob(params,stop?android.app.job.JobParameters.STOP_REASON_SYSTEM_PROCESSING:android.app.job.JobParameters.STOP_REASON_UNDEFINED);}
 public void finishJob(android.app.job.JobParameters params,int stopReason){stopJob(params,stopReason);}
 public boolean stopJob(android.app.job.JobParameters params,int stopReason){
  if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Job main thread");
  Record found=null;synchronized(this){for(Record record:records)if(record.jobs.remove(params)){found=record;break;}}
  if(found==null)return false;
  try{if(stopReason!=android.app.job.JobParameters.STOP_REASON_UNDEFINED&&found.service instanceof android.app.job.JobService){params.shirazStopReason(stopReason);return ((android.app.job.JobService)found.service).onStopJob(params);}return false;}
  finally{if(!found.workerOwned&&!found.started&&found.bound.isEmpty()&&found.jobs.isEmpty())destroy(found);}
 }
 /** Includes queued destruction so the background loop delivers onDestroy before exiting. */
 public synchronized boolean hasWork(){return !closed&&(!records.isEmpty()||!pending.isEmpty());}
 public void close(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Service teardown requires main Looper");Record[] snapshot;synchronized(this){if(closed)return;closed=true;unavailableBindings.clear();deadBindings.clear();main.removeCallbacksAndMessages(this);pending.clear();queuedBytes=0;snapshot=records.toArray(new Record[records.size()]);}Throwable first=null;for(Record record:snapshot)try{destroy(record);}catch(RuntimeException|Error error){if(first==null)first=error;}if(first instanceof Error)throw (Error)first;if(first!=null)throw (RuntimeException)first;}
}
