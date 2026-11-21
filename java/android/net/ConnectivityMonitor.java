// SPDX-License-Identifier: GPL-3.0-only
package android.net;

import android.os.Handler;
import java.util.IdentityHashMap;

/** One default-route observation per second, with at most one queued delivery per registration. */
final class ConnectivityMonitor {
 interface Source { Snapshot read(); }
 static final class Snapshot {
  final NetworkCapabilities capabilities;final LinkProperties links;
  Snapshot(NetworkCapabilities capabilities,LinkProperties links){this.capabilities=new NetworkCapabilities(capabilities);this.links=new LinkProperties(links);}
 }
 private static final Network NETWORK=new Network(100);
 private final Source source;private final boolean automatic;
 private final IdentityHashMap<ConnectivityManager.NetworkCallback,Registration> registrations=new IdentityHashMap<ConnectivityManager.NetworkCallback,Registration>();
 private Thread worker;private android.os.HandlerThread callbacks;private Handler callbackHandler;private boolean closed;
 ConnectivityMonitor(Source source,boolean automatic){this.source=source;this.automatic=automatic;}
 void register(ConnectivityManager.NetworkCallback callback,Handler handler,NetworkRequest request){
  if(callback==null)throw new NullPointerException("callback");
  final Registration registration;
  synchronized(this){
   if(closed)throw new IllegalStateException("Connectivity manager closed");
   if(registrations.containsKey(callback))throw new IllegalArgumentException("NetworkCallback already registered");
   if(registrations.size()>=100)throw new IllegalStateException("Network callback limit");
   if(handler==null&&automatic){
    if(callbacks==null){
     callbacks=new android.os.HandlerThread("shiraz-network-callbacks");callbacks.setDaemon(true);callbacks.start();
     android.os.Looper looper=callbacks.getLooper();
     if(looper==null){callbacks=null;throw new IllegalStateException("Network callback thread failed to start");}
     callbackHandler=new Handler(looper);
    }
    handler=callbackHandler;
   }
   registration=new Registration(callback,handler,request);
   registrations.put(callback,registration);
  }
  // Only the deterministic manual driver observes inline. Production callers
  // subscribe without waiting for native path/resolver observation.
  if(!automatic){
   Snapshot initial=null;boolean observed=false;
   try{initial=source.read();observed=true;}
   catch(RuntimeException unavailable){/* Retry on the next poll. */}
   if(observed)try{registration.offer(initial);}
   catch(RuntimeException error){remove(registration);throw error;}
  }
  synchronized(this){if(automatic&&!registrations.isEmpty()&&worker==null){
   worker=new Thread(new Runnable(){public void run(){observe();}},"shiraz-connectivity");worker.setDaemon(true);worker.start();
  }}
 }
 void unregister(ConnectivityManager.NetworkCallback callback){
  synchronized(this){if(registrations.remove(callback)==null)throw new IllegalArgumentException("NetworkCallback was not registered");stopIfEmpty();}
 }
 private synchronized boolean current(Registration registration){return registrations.get(registration.callback)==registration;}
 private synchronized void remove(Registration registration){if(current(registration)){registrations.remove(registration.callback);stopIfEmpty();}}
 private void stopIfEmpty(){if(registrations.isEmpty()){
  if(worker!=null){worker.interrupt();worker=null;}
  if(callbacks!=null){callbackHandler.getLooper().quit();callbacks=null;callbackHandler=null;}
 }}
 synchronized void close(){closed=true;registrations.clear();stopIfEmpty();}
 synchronized int size(){return registrations.size();}
 void poll(){poll(null);}
 private void poll(Thread expected){
  Registration[] values;
  synchronized(this){if(expected!=null&&worker!=expected)return;values=registrations.values().toArray(new Registration[registrations.size()]);}
  // A queue can accept delivery and then discard it on quit. Reap these even
  // when network observation is unavailable, without generating a lost event.
  for(Registration registration:values)if(registration.handler!=null&&registration.handler.getLooper().shirazIsQuitting())remove(registration);
  final Snapshot snapshot;
  try{snapshot=source.read();}catch(RuntimeException unavailable){return;} // Unknown is not a disconnect event.
  for(Registration registration:values){try{registration.offer(snapshot);}catch(RuntimeException failure){remove(registration);}}
 }
 private void observe(){
  Thread self=Thread.currentThread();
  try{while(true){synchronized(this){if(worker!=self)return;}poll(self);Thread.sleep(1000);}}
  catch(InterruptedException stopped){Thread.currentThread().interrupt();}
  finally{synchronized(this){if(worker==self)worker=null;}}
 }
 private final class Registration {
  final ConnectivityManager.NetworkCallback callback;final Handler handler;final NetworkRequest filter;
  Snapshot pending,last;boolean queued;
  Registration(ConnectivityManager.NetworkCallback callback,Handler handler,NetworkRequest request){this.callback=callback;this.handler=handler;filter=request==null?null:new NetworkRequest(request);}
  synchronized void offer(Snapshot snapshot){
   if(!current(this))return;
   pending=snapshot;
   if(queued)return;
   queued=true;
   Runnable delivery=new Runnable(){public void run(){deliver();}};
   if(handler==null)delivery.run();
   else if(!handler.post(delivery)){queued=false;remove(this);throw new IllegalStateException("Network callback handler has stopped");}
  }
  void deliver(){
   final Snapshot next,previous;
   synchronized(this){
    queued=false;
    if(!current(this))return;
    Snapshot candidate=pending;
    if(candidate!=null&&filter!=null&&!filter.canBeSatisfiedBy(candidate.capabilities))candidate=null;
    next=candidate;previous=last;last=next;
   }
   // The Handler serializes delivery. Never hold the pending-state lock
   // across app code: polling must still coalesce its latest observation.
   try{
    if(next==null){if(previous!=null)callback.onLost(NETWORK);return;}
    if(previous==null)callback.onAvailable(NETWORK);
    if(!current(this))return;
    if(previous==null||!next.capabilities.shirazSame(previous.capabilities))callback.onCapabilitiesChanged(NETWORK,new NetworkCapabilities(next.capabilities));
    if(!current(this))return;
    if(previous==null||!next.links.shirazSame(previous.links))callback.onLinkPropertiesChanged(NETWORK,new LinkProperties(next.links));
   }catch(RuntimeException failure){remove(this);throw failure;}
  }
 }
}
