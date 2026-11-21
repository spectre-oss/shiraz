// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.content.*;
import android.os.*;
import java.util.*;

/** Local dynamic and explicit manifest receivers; no fabricated host/system events. */
public final class HostBroadcasts implements AutoCloseable {
    private final String pkg;
    private Context manifestContext;
    private final Handler main;
    private final long timeout;
    private final ArrayList<Record> records = new ArrayList<Record>();
    private final LinkedHashSet<Delivery> pending = new LinkedHashSet<Delivery>();
    private final LinkedHashSet<ExternalDelivery> external = new LinkedHashSet<ExternalDelivery>();
    private int filters, filterBytes, queuedBytes;
    private boolean closed;
    private static final class Registration {
        final IntentFilter filter; final String senderPermission, token; final int flags;
        Registration(IntentFilter filter,String senderPermission,int flags,String token){this.filter=filter;this.senderPermission=senderPermission;this.flags=flags;this.token=token;}
    }
    private static long receiverSequence;
    private static synchronized String nextReceiverIdentity(){
        if(receiverSequence==Long.MAX_VALUE)throw new IllegalStateException("Receiver identity space exhausted");
        String value=Long.toHexString(++receiverSequence);
        return "00000000000000000000000000000000".substring(value.length())+value;
    }
    private static final class Record {
        final Context context; final BroadcastReceiver receiver; final Handler handler; String receiverClass;
        final String identity=nextReceiverIdentity(); ServiceBrokerClient broker;
        final ArrayList<Registration> filters = new ArrayList<Registration>(); boolean active = true;
        Record(Context context, BroadcastReceiver receiver, Handler handler) { this.context = context; this.receiver = receiver; this.handler = handler; }
    }
    public HostBroadcasts(String pkg) { this(pkg, 10000); }
    public HostBroadcasts(String pkg, long timeout) {
        this.pkg = Objects.requireNonNull(pkg); main = new Handler(Looper.getMainLooper());
        if (timeout < 1 || timeout > 10000) throw new IllegalArgumentException("Broadcast timeout"); this.timeout = timeout;
    }
    public HostBroadcasts(Context context) { this(context,10000); }
    public HostBroadcasts(Context context,long timeout) {
        this(Objects.requireNonNull(context).getPackageName(),timeout);
        manifestContext=context.getApplicationContext();if(manifestContext==null)manifestContext=context;
    }
    private static final class ReceiverContext extends ContextWrapper {
        ReceiverContext(Context base){super(base);}
        @Override public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,String permission,Handler handler,int flags){
            if(receiver!=null)throw new ReceiverCallNotAllowedException("Manifest receiver cannot register another receiver");
            return getBaseContext().registerReceiver(null,filter,permission,handler,flags);
        }
        @Override public boolean bindService(Intent intent,ServiceConnection connection,int flags){
            throw new ReceiverCallNotAllowedException("Manifest receiver cannot bind a service");
        }
    }
    private Record manifestRecord(ComponentName component) {
        if(manifestContext==null)throw new UnsupportedOperationException("Manifest receiver context unavailable");
        final android.content.pm.ActivityInfo info;
        try{info=manifestContext.getPackageManager().getReceiverInfo(component,0);}
        catch(android.content.pm.PackageManager.NameNotFoundException absent){return null;}
        if(!info.isEnabled())return null;
        String process=android.app.Application.getProcessName();if(process==null)process=pkg;
        if(info.processName!=null&&!process.equals(info.processName))throw new UnsupportedOperationException("Separate receiver processes are not implemented");
        Record record=new Record(new ReceiverContext(manifestContext),null,main);record.receiverClass=info.name;return record;
    }
    private void live() { if (closed) throw new IllegalStateException("Broadcast session is closed"); }
    public synchronized Intent register(Context context, BroadcastReceiver receiver, IntentFilter filter, String permission, Handler handler, int flags) {
        live(); Objects.requireNonNull(context); Objects.requireNonNull(filter);
        if(!pkg.equals(context.getPackageName()))throw new IllegalArgumentException("Receiver context package mismatch");
        validatePermission(permission);
        if ((flags & ~7) != 0 || (flags & 6) == 6 || (flags & 5) == 5) throw new IllegalArgumentException("Invalid receiver export flags");
        // No sticky producer exists in this session. A query returns no existing sticky value.
        if (receiver == null) return null;
        Handler target = handler == null ? main : handler;
        Record record = null;
        for (Record candidate : records) if (candidate.context == context && candidate.receiver == receiver) { record = candidate; break; }
        if (record == null && records.size() >= 256 || filters >= 1024) throw new IllegalStateException("Receiver registration limit exceeded");
        if (record != null && record.handler.getLooper() != target.getLooper()) throw new IllegalArgumentException("Receiver already registered on another looper");
        IntentFilter snapshot = new IntentFilter(filter);
        if (snapshot.shirazByteSize() > 8 * 1024 * 1024 - filterBytes) throw new IllegalStateException("Registered filter byte limit exceeded");
        boolean fresh=record==null;if(fresh)record=new Record(context,receiver,target);
        ServiceBrokerClient broker=RuntimeBroker.client();String token=null;
        if(record.broker!=null&&record.broker!=broker)throw new IllegalStateException("Receiver broker changed");
        if(broker!=null){
            try{token=broker.registerDynamicReceiver(record.identity,snapshot,permission,flags);}
            catch(java.io.IOException error){throw new IllegalStateException("Receiver registration failed",error);}
            record.broker=broker;
        }
        if(fresh)records.add(record);
        record.filters.add(new Registration(snapshot,permission,flags,token)); ++filters; filterBytes += snapshot.shirazByteSize(); return null;
    }
    public void unregister(Context context, BroadcastReceiver receiver) {
        Objects.requireNonNull(receiver); Record found = null; ArrayList<Delivery> canceled = new ArrayList<Delivery>();
        synchronized (this) {
            for (Record record : records) if (record.context == context && record.receiver == receiver) { found = record; break; }
            if (found == null) throw new IllegalArgumentException("Receiver is not registered by this context");
            if(!closed&&found.broker!=null){
                try{found.broker.unregisterDynamicReceiver(found.identity);}
                catch(java.io.IOException error){throw new IllegalStateException("Receiver unregister failed",error);}
            }
            found.active = false; records.remove(found);
            if(closed)return; // Service teardown may unregister asynchronously after host shutdown.
            filters -= found.filters.size();
            for (Registration registration : found.filters) filterBytes -= registration.filter.shirazByteSize();
            for (Delivery delivery : pending) if (delivery.record == found && !delivery.started) canceled.add(delivery);
        }
        for (Delivery delivery : canceled) delivery.cancel();
    }
    private boolean requiresRemoteProcess(ComponentName component){
        if(!pkg.equals(component.getPackageName()))return true;
        if(manifestContext==null)return false;
        final android.content.pm.ActivityInfo info;
        try{info=manifestContext.getPackageManager().getReceiverInfo(component,0);}
        catch(android.content.pm.PackageManager.NameNotFoundException absent){return false;}
        if(!info.isEnabled())return false;
        String process=android.app.Application.getProcessName();if(process==null)process=pkg;
        return info.processName!=null&&!process.equals(info.processName);
    }
    private static void validatePermission(String permission){
        if(permission!=null&&(permission.length()>1024||!permission.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")))throw new IllegalArgumentException("Invalid broadcast permission");
    }
    private boolean packageHasPermission(Context context,String permission){
        if(permission==null)return true;
        if(context==null||!pkg.equals(context.getPackageName()))return false;
        ServiceBrokerClient broker=RuntimeBroker.client();
        if(broker!=null){
            try{return broker.checkPermission(permission,"package",pkg)==android.content.pm.PackageManager.PERMISSION_GRANTED;}
            catch(java.io.IOException error){throw new IllegalStateException("Broadcast permission query failed",error);}
        }
        return context.checkPermission(permission,android.os.Process.myPid(),android.os.Process.myUid())==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    public void send(Intent source, String permission) {
        Objects.requireNonNull(source);
        validatePermission(permission);
        if ((source.getFlags() & Intent.FLAG_RECEIVER_REPLACE_PENDING) != 0) throw new UnsupportedOperationException("Replacing queued broadcasts is not implemented");
        Intent snapshot = source.shirazBroadcastCopy(); ArrayList<Record> matched = new ArrayList<Record>();
        ArrayList<ComponentName> remote = new ArrayList<ComponentName>();
        if(snapshot.getComponent()==null&&snapshot.getAction()!=null&&RuntimeBroker.client()!=null)sendDynamic(snapshot,permission);
        if(snapshot.getComponent()!=null&&RuntimeBroker.client()!=null&&requiresRemoteProcess(snapshot.getComponent())){
            if(pkg.equals(snapshot.getComponent().getPackageName())&&!packageHasPermission(manifestContext,permission))return;
            if((snapshot.getFlags()&Intent.FLAG_RECEIVER_REGISTERED_ONLY)==0)sendExternal(snapshot,permission);
            return;
        }
        if(snapshot.getComponent()==null&&snapshot.getPackage()!=null&&!pkg.equals(snapshot.getPackage())&&RuntimeBroker.client()!=null){
            if((snapshot.getFlags()&Intent.FLAG_RECEIVER_REGISTERED_ONLY)!=0)return;
            synchronized(this){
                live();final java.util.List<ServiceBrokerClient.Resolution> targets;
                try{targets=RuntimeBroker.client().queryReceivers(snapshot,permission);}catch(java.io.IOException error){throw new IllegalStateException("Receiver discovery failed",error);}
                if(external.size()+targets.size()>4||(long)snapshot.shirazBroadcastBytes()*targets.size()>16*1024*1024L-queuedBytes)throw new IllegalStateException("External broadcast fanout limit");
                for(ServiceBrokerClient.Resolution target:targets)sendExternal(snapshot,permission,new ComponentName(target.packageName,target.component),target.apkSha256);
            }
            return;
        }
        synchronized (this) {
            live(); if (snapshot.getPackage() != null && !snapshot.getPackage().equals(pkg)) return;
            // Only this APK is loaded. An explicit component in another package has no recipient.
            ComponentName component=snapshot.getComponent();
            boolean registeredOnly=(snapshot.getFlags()&Intent.FLAG_RECEIVER_REGISTERED_ONLY)!=0;
            boolean manifestAllowed=packageHasPermission(manifestContext,permission);
            if(component!=null){if(!component.getPackageName().equals(pkg)||registeredOnly||!manifestAllowed)return;Record record=manifestRecord(component);if(record!=null)matched.add(record);}
            final IdentityHashMap<Record,Integer> rank = new IdentityHashMap<Record,Integer>();
            if(manifestAllowed&&!registeredOnly&&component==null&&pkg.equals(snapshot.getPackage())&&manifestContext!=null){
                for(android.content.pm.ResolveInfo info:manifestContext.getPackageManager().queryBroadcastReceivers(snapshot,0)){
                    ComponentName target=new ComponentName(pkg,info.activityInfo.name);
                    if(RuntimeBroker.client()!=null&&requiresRemoteProcess(target)){remote.add(target);continue;}
                    Record record=manifestRecord(target);
                    if(record!=null){matched.add(record);rank.put(record,info.priority);}
                }
            }
            for (Record record : records) {
                if(component!=null)break;
                if(manifestContext!=null?!manifestAllowed:!packageHasPermission(record.context,permission))continue;
                boolean accepts = false; int priority = Integer.MIN_VALUE;
                for (Registration registration : record.filters) {
                    IntentFilter filter=registration.filter;
                    if(filter.match(snapshot.getAction(),snapshot.getType(),snapshot.getScheme(),snapshot.getData(),snapshot.getCategories(),"Shiraz")<0)continue;
                    // Local sends originate from this package; remote sends must
                    // use broker-authenticated sender identity before this path.
                    if(!packageHasPermission(record.context,registration.senderPermission))continue;
                    accepts=true;priority=Math.max(priority,filter.getPriority());
                }
                if (accepts) { matched.add(record); rank.put(record, priority); }
            }
            Collections.sort(matched, new Comparator<Record>() { public int compare(Record a, Record b) { return Integer.compare(rank.get(b), rank.get(a)); } });
            long cost = (long)snapshot.shirazBroadcastBytes() * (matched.size()+remote.size());
            if (external.size()+remote.size()>4 || pending.size() + matched.size() > 4096 || cost > 16 * 1024 * 1024L - queuedBytes) throw new IllegalStateException("Broadcast delivery queue exceeds host limit");
            for(ComponentName target:remote)sendExternal(snapshot,permission,target);
            for (Record record : matched) {
                Delivery delivery = new Delivery(record, snapshot); pending.add(delivery); queuedBytes += snapshot.shirazBroadcastBytes();
                try {
                    if (!record.handler.postAtTime(delivery, delivery, SystemClock.uptimeMillis())) throw new IllegalStateException("Receiver looper has stopped");
                } catch (Throwable error) { delivery.fail(error); throw error; }
            }
        }
    }
    /** Host-admitted explicit manifest delivery; sender authorization belongs to the broker. */
    public synchronized java.util.concurrent.Future<Void> receiveAdmitted(Intent source){return receiveAdmitted(source,Objects.requireNonNull(source).getComponent());}
    synchronized java.util.concurrent.Future<Void> receiveAdmitted(Intent source,ComponentName component){
        live();Intent intent=Objects.requireNonNull(source).shirazBroadcastCopy();
        if(component==null||!pkg.equals(component.getPackageName())||intent.getComponent()!=null&&!component.equals(intent.getComponent())||intent.getPackage()!=null&&!pkg.equals(intent.getPackage()))
            throw new SecurityException("Inbound receiver target mismatch");
        if((intent.getFlags()&(Intent.FLAG_RECEIVER_REGISTERED_ONLY|Intent.FLAG_RECEIVER_REPLACE_PENDING))!=0)
            throw new UnsupportedOperationException("Inbound receiver flags");
        Record record=manifestRecord(component);if(record==null)throw new IllegalArgumentException("Inbound receiver unavailable");
        int cost=intent.shirazBroadcastBytes();
        if(pending.size()>=4096||cost>16*1024*1024-queuedBytes)throw new IllegalStateException("Inbound receiver queue limit");
        Delivery delivery=new Delivery(record,intent);pending.add(delivery);queuedBytes+=cost;
        try{if(!record.handler.postAtTime(delivery,delivery,SystemClock.uptimeMillis()))throw new IllegalStateException("Receiver looper stopped");}
        catch(RuntimeException|Error error){delivery.fail(error);throw error;}
        return delivery.outcome;
    }
    synchronized java.util.concurrent.Future<Void> receiveDynamicAdmitted(Intent source,String token){
        live();Intent intent=Objects.requireNonNull(source).shirazBroadcastCopy();
        if(intent.getComponent()!=null||intent.getPackage()!=null&&!pkg.equals(intent.getPackage()))throw new SecurityException("Dynamic receiver target mismatch");
        if((intent.getFlags()&Intent.FLAG_RECEIVER_REPLACE_PENDING)!=0)throw new UnsupportedOperationException("Replacing dynamic broadcasts");
        for(Record record:records)for(Registration registration:record.filters)if(token.equals(registration.token)){
            IntentFilter filter=registration.filter;
            if(filter.match(intent.getAction(),intent.getType(),intent.getScheme(),intent.getData(),intent.getCategories(),"Shiraz")<0)throw new SecurityException("Dynamic receiver filter mismatch");
            int cost=intent.shirazBroadcastBytes();
            if(pending.size()>=4096||cost>16*1024*1024-queuedBytes)throw new IllegalStateException("Dynamic receiver queue limit");
            Delivery delivery=new Delivery(record,intent);pending.add(delivery);queuedBytes+=cost;
            try{if(!record.handler.postAtTime(delivery,delivery,SystemClock.uptimeMillis()))throw new IllegalStateException("Dynamic receiver looper stopped");}
            catch(RuntimeException|Error error){delivery.fail(error);throw error;}
            return delivery.outcome;
        }
        // Unregister may race a previously authorized delivery; never revive it.
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }
    public synchronized boolean hasWork() { return !closed&&(!pending.isEmpty()||!external.isEmpty()); }
    private synchronized void sendDynamic(Intent intent,String permission){
        live();ServiceBrokerClient broker=RuntimeBroker.client();
        try{
            java.util.List<ServiceBrokerClient.DynamicResolution> targets=broker.queryDynamicReceivers(intent,permission);
            int cost=intent.shirazBroadcastBytes();
            if(external.size()+targets.size()>4||(long)cost*targets.size()>16*1024*1024L-queuedBytes)throw new IllegalStateException("Dynamic broadcast fanout limit");
            if(targets.isEmpty())return;
            Parcel parcel=Parcel.obtain();byte[] payload;
            try{parcel.writeTypedObject(intent,0);payload=parcel.marshall();}finally{parcel.recycle();}
            if(payload.length>4*1024*1024)throw new IllegalArgumentException("Dynamic parcel size limit");
            for(ServiceBrokerClient.DynamicResolution target:targets){
                ServiceBrokerClient.ReceiverConnection connection=broker.openDynamicReceiver(intent,permission,target);
                startExternal(connection,payload,cost,target.packageName+"/dynamic:"+target.receiver);
            }
        }catch(java.io.IOException error){throw new IllegalStateException("Dynamic broadcast admission failed",error);}
    }
    private void sendExternal(Intent intent,String permission){sendExternal(intent,permission,intent.getComponent());}
    private void sendExternal(Intent intent,String permission,ComponentName target){sendExternal(intent,permission,target,null);}
    private synchronized void sendExternal(Intent intent,String permission,ComponentName target,String expectedHash){
        live();int cost=intent.shirazBroadcastBytes();
        if(external.size()>=4||cost>16*1024*1024-queuedBytes)throw new IllegalStateException("External broadcast queue limit");
        Parcel parcel=Parcel.obtain();final byte[] payload;
        try{parcel.writeTypedObject(intent,0);payload=parcel.marshall();}finally{parcel.recycle();}
        if(payload.length>4*1024*1024)throw new IllegalArgumentException("Receiver parcel size limit");
        final ServiceBrokerClient.ReceiverConnection connection;
        try{connection=RuntimeBroker.client().openReceiver(new Intent(intent).setComponent(target),permission,expectedHash);}
        catch(ServiceBrokerClient.ReceiverFilteredException filtered){return;}
        catch(java.io.IOException error){throw new IllegalStateException("Receiver admission failed",error);}
        startExternal(connection,payload,cost,target.flattenToString());
    }
    private void startExternal(ServiceBrokerClient.ReceiverConnection connection,byte[] payload,int cost,String label){
        ExternalDelivery delivery=new ExternalDelivery(connection,payload,cost,label);
        external.add(delivery);queuedBytes+=cost;
        try{Thread thread=new Thread(delivery,"shiraz-receiver-delivery");thread.setDaemon(true);thread.start();}
        catch(RuntimeException|Error error){external.remove(delivery);queuedBytes-=cost;connection.discard();throw error;}
    }
    private final class ExternalDelivery implements Runnable {
        final ServiceBrokerClient.ReceiverConnection connection;final byte[] payload;final int cost;final String component;
        ExternalDelivery(ServiceBrokerClient.ReceiverConnection connection,byte[] payload,int cost,String component){this.connection=connection;this.payload=payload;this.cost=cost;this.component=component;}
        public void run(){
            try{connection.await(payload);System.out.println("SHIRAZ_RECEIVER_DELIVERY_COMPLETE "+component);}
            catch(java.io.IOException|RuntimeException error){System.err.println("SHIRAZ_RECEIVER_DELIVERY_FAILED "+component+" "+error);}
            finally{synchronized(HostBroadcasts.this){external.remove(this);queuedBytes-=cost;if(!closed)main.post(new Runnable(){public void run(){}});}}
        }
    }
    private synchronized void complete(Delivery delivery) {
        if(!delivery.result.shirazFinished()||!delivery.returned&&!delivery.outcome.isDone())return;
        boolean removed=pending.remove(delivery);
        if (removed) queuedBytes -= delivery.intent.shirazBroadcastBytes();
        delivery.record.handler.removeCallbacksAndMessages(delivery); main.removeCallbacksAndMessages(delivery);
        delivery.completeIfReturned();
        // An off-main finish must wake the windowless loop to recheck ownership.
        if(removed&&!closed&&Looper.myLooper()!=main.getLooper())
            if(!main.post(new Runnable(){public void run(){}}))throw new IllegalStateException("Broadcast completion looper stopped");
    }
    private final class Delivery implements Runnable {
        final Record record; final Intent intent; boolean started,returned;
        final java.util.concurrent.CompletableFuture<Void> outcome=new java.util.concurrent.CompletableFuture<Void>();
        final BroadcastReceiver.PendingResult result;
        Delivery(Record record, Intent intent) { this.record = record; this.intent = intent; result = BroadcastReceiver.PendingResult.shirazCreate(new Runnable() { public void run() { complete(Delivery.this); } }); }
        void completeIfReturned(){synchronized(HostBroadcasts.this){if(returned&&result.shirazFinished())outcome.complete(null);}}
        void fail(Throwable error){outcome.completeExceptionally(error);result.shirazCancel();complete(this);}
        void cancel() { fail(new java.util.concurrent.CancellationException("Broadcast delivery canceled")); }
        public void run() {
            synchronized (HostBroadcasts.this) { if (!record.active || closed || result.shirazFinished()) { cancel(); return; } started = true; }
            Runnable deadline = new Runnable() { public void run() {
                synchronized(HostBroadcasts.this){if(result.shirazFinished())return;
                    IllegalStateException error=new IllegalStateException("Asynchronous broadcast receiver did not finish before its deadline");fail(error);throw error;}
            } };
            try {
                if (!main.postAtTime(deadline, this, SystemClock.uptimeMillis() + timeout)) throw new IllegalStateException("Broadcast completion looper has stopped");
                if (result.shirazFinished()) { main.removeCallbacksAndMessages(this); return; }
                BroadcastReceiver receiver=record.receiver;
                if(receiver==null){
                    try{receiver=Class.forName(record.receiverClass).asSubclass(BroadcastReceiver.class).newInstance();}
                    catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot construct manifest receiver",error);}
                }
                Intent delivered=intent.shirazBroadcastCopy();
                // API-27 manifest delivery resolves the component before onReceive;
                // dynamic receivers still observe the original implicit Intent.
                if(record.receiverClass!=null)delivered.setComponent(new ComponentName(pkg,record.receiverClass));
                receiver.shirazDispatch(record.context, delivered, result);
                synchronized(HostBroadcasts.this){returned=true;complete(this);}
            } catch (Throwable error) { fail(error);throw error; }
        }
    }
    @Override public void close() {
        ArrayList<Delivery> canceled;
        synchronized (this) { if (closed) return; closed = true; for (Record record : records) {
            record.active = false; record.filters.clear();
            if(record.broker!=null)try{record.broker.unregisterDynamicReceiver(record.identity);}
            catch(java.io.IOException|RuntimeException error){System.err.println("SHIRAZ_RECEIVER_UNREGISTER_FAILED "+error);}
        } filters = 0; filterBytes = 0; canceled = new ArrayList<Delivery>(pending); }
        for (Delivery delivery : canceled) delivery.cancel();
        synchronized(this){for(ExternalDelivery delivery:external)delivery.connection.cancel();}
    }
}
