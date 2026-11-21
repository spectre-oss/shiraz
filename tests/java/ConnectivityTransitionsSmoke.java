// SPDX-License-Identifier: GPL-3.0-only
package android.net;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
public final class ConnectivityTransitionsSmoke {
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 static ConnectivityMonitor.Snapshot snapshot(boolean validated,String domain){
  NetworkCapabilities caps=NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_WIFI);
  if(validated)caps.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  LinkProperties links=new LinkProperties();links.setDomains(domain);return new ConnectivityMonitor.Snapshot(caps,links);
 }
 static class Source implements ConnectivityMonitor.Source {
  volatile ConnectivityMonitor.Snapshot value=snapshot(false,"one");volatile boolean fail;
  public ConnectivityMonitor.Snapshot read(){if(fail)throw new IllegalStateException("observation unavailable");return value;}
 }
 public static void main(String[] args)throws Exception{
  Source source=new Source();ConnectivityMonitor monitor=new ConnectivityMonitor(source,false);
  List<String> events=new ArrayList<String>();
  ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){
   public void onAvailable(Network n){events.add("available");}
   public void onLost(Network n){events.add("lost");}
   public void onCapabilitiesChanged(Network n,NetworkCapabilities c){events.add("caps");c.addCapability(99);}
   public void onLinkPropertiesChanged(Network n,LinkProperties p){events.add("links");p.setDomains("mutated");}
  };
  monitor.register(callback,null,null);check(events.equals(Arrays.asList("available","caps","links")),"initial ordering");
  events.clear();monitor.poll();check(events.isEmpty(),"unchanged or callback-mutated snapshot redelivered");
  source.value=snapshot(true,"one");monitor.poll();check(events.equals(Arrays.asList("caps")),"capability change");
  events.clear();source.value=snapshot(true,"two");monitor.poll();check(events.equals(Arrays.asList("links")),"resolver change");
  events.clear();source.fail=true;monitor.poll();check(events.isEmpty(),"unknown observation emitted disconnect");source.fail=false;
  source.value=null;monitor.poll();monitor.poll();check(events.equals(Arrays.asList("lost")),"disconnect exactly once");
  events.clear();source.value=snapshot(false,"one");monitor.poll();check(events.equals(Arrays.asList("available","caps","links")),"reconnect");monitor.unregister(callback);
  events.clear();NetworkRequest filter=new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
  monitor.register(callback,null,filter);check(events.isEmpty(),"unmatched initial");source.value=snapshot(true,"one");monitor.poll();
  check(events.equals(Arrays.asList("available","caps","links")),"became matching");events.clear();source.value=snapshot(false,"one");monitor.poll();check(events.equals(Arrays.asList("lost")),"ceased matching");monitor.unregister(callback);
  ConnectivityManager.NetworkCallback reentrant=new ConnectivityManager.NetworkCallback(){
   public void onAvailable(Network n){monitor.unregister(this);}
   public void onCapabilitiesChanged(Network n,NetworkCapabilities c){throw new AssertionError("delivery after unregister");}
  };monitor.register(reentrant,null,null);check(monitor.size()==0,"reentrant unregister");
  ConnectivityManager.NetworkCallback broken=new ConnectivityManager.NetworkCallback(){public void onAvailable(Network n){throw new IllegalStateException("callback failed");}};
  try{monitor.register(broken,null,null);throw new AssertionError("callback failure hidden");}catch(IllegalStateException expected){}check(monitor.size()==0,"callback failure leaked registration");
  ConnectivityMonitor limited=new ConnectivityMonitor(source,false);
  for(int i=0;i<100;i++)limited.register(new ConnectivityManager.NetworkCallback(),null,null);
  try{limited.register(new ConnectivityManager.NetworkCallback(),null,null);throw new AssertionError("unbounded registrations");}catch(IllegalStateException expected){}
  limited.close();check(limited.size()==0,"bulk close");
  source.fail=true;events.clear();monitor.register(callback,null,null);
  check(monitor.size()==1&&events.isEmpty(),"unknown initial observation discarded subscription");
  monitor.poll();check(events.isEmpty(),"unknown observation emitted a callback");
  source.fail=false;source.value=snapshot(false,"recovered");monitor.poll();
  check(events.equals(Arrays.asList("available","caps","links")),"initial observation failure did not recover");
  monitor.unregister(callback);
  Looper.prepare();Looper loop=Looper.myLooper();Handler handler=new Handler(loop);
  events.clear();monitor.register(callback,handler,null);monitor.unregister(callback);loop.shirazDispatchDue(100);check(events.isEmpty(),"queued unregister");
  monitor.register(callback,handler,null);for(int i=0;i<1000;i++){source.value=snapshot(false,"domain"+i);monitor.poll();}
  loop.shirazDispatchDue(100);check(events.equals(Arrays.asList("available","caps","links")),"queued snapshots not coalesced");
  events.clear();source.value=null;monitor.poll();monitor.close();loop.shirazDispatchDue(100);check(events.isEmpty()&&monitor.size()==0,"close invalidates queue");
  ConnectivityMonitor abandoned=new ConnectivityMonitor(source,false);
  abandoned.register(callback,handler,null);check(abandoned.size()==1,"pending registration missing");
  loop.quit();source.fail=true;abandoned.poll();source.fail=false;
  check(abandoned.size()==0,"quit after enqueue leaked registration during unknown observation");
  abandoned.close();
  ConnectivityMonitor stopped=new ConnectivityMonitor(source,false);
  try{stopped.register(callback,handler,null);throw new AssertionError("stopped handler");}catch(IllegalStateException expected){}check(stopped.size()==0,"failed registration leaked");stopped.close();
  Source live=new Source();ConnectivityMonitor polling=new ConnectivityMonitor(live,true);CountDownLatch lost=new CountDownLatch(1);
  CountDownLatch available=new CountDownLatch(1);Thread[] deliveryThread=new Thread[2];
  ConnectivityManager.NetworkCallback watch=new ConnectivityManager.NetworkCallback(){
   public void onAvailable(Network n){deliveryThread[0]=Thread.currentThread();available.countDown();}
   public void onLost(Network n){deliveryThread[1]=Thread.currentThread();lost.countDown();}
  };
  live.fail=true;polling.register(watch,null,null);
  check(available.getCount()==1&&polling.size()==1,"initial unknown reported available or lost registration");
  live.fail=false;check(available.await(4,TimeUnit.SECONDS),"automatic observation recovery missing");
  check(deliveryThread[0]!=Thread.currentThread(),"default callback ran on registering thread");
  live.value=null;check(lost.await(4,TimeUnit.SECONDS),"background polling did not deliver loss");
  check(deliveryThread[0]==deliveryThread[1],"default callback switched threads");
  polling.unregister(watch);check(polling.size()==0,"polling cleanup");
  deliveryThread[0].join(2000);check(!deliveryThread[0].isAlive(),"default callback thread leaked");
  live.value=snapshot(false,"restarted");CountDownLatch closed=new CountDownLatch(1);Thread[] replacement=new Thread[1];
  polling.register(new ConnectivityManager.NetworkCallback(){
   public void onAvailable(Network n){replacement[0]=Thread.currentThread();polling.close();closed.countDown();}
   public void onCapabilitiesChanged(Network n,NetworkCapabilities c){throw new AssertionError("callback after reentrant close");}
  },null,null);
  check(closed.await(4,TimeUnit.SECONDS),"replacement callback missing");
  check(replacement[0]!=Thread.currentThread(),"initial callback ran on registering thread");
  check(replacement[0]!=deliveryThread[0],"retired callback thread reused");
  replacement[0].join(2000);check(!replacement[0].isAlive()&&polling.size()==0,"reentrant close leaked worker");
  Thread registering=Thread.currentThread();CountDownLatch reading=new CountDownLatch(1),release=new CountDownLatch(1);
  Thread[] reader=new Thread[1];java.util.concurrent.atomic.AtomicInteger late=new java.util.concurrent.atomic.AtomicInteger();
  ConnectivityMonitor slow=new ConnectivityMonitor(new ConnectivityMonitor.Source(){public ConnectivityMonitor.Snapshot read(){
   check(Thread.currentThread()!=registering,"observation ran on registering thread");reader[0]=Thread.currentThread();reading.countDown();
   try{release.await(4,TimeUnit.SECONDS);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}
   return snapshot(false,"late");
  }},true);
  slow.register(new ConnectivityManager.NetworkCallback(){public void onAvailable(Network n){late.incrementAndGet();}},null,null);
  check(reading.await(4,TimeUnit.SECONDS),"background observation did not start");slow.close();release.countDown();
  reader[0].join(2000);check(!reader[0].isAlive()&&late.get()==0,"close during observation leaked worker or delivered stale callback");
  HandlerThread blockedThread=new HandlerThread("fixture-blocked-network-callback");blockedThread.start();
  CountDownLatch callbackEntered=new CountDownLatch(1),callbackRelease=new CountDownLatch(1),polled=new CountDownLatch(1);
  Source blockedSource=new Source();ConnectivityMonitor blocked=new ConnectivityMonitor(blockedSource,false);
  CountDownLatch latestDelivered=new CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger linkEvents=new java.util.concurrent.atomic.AtomicInteger();
  blocked.register(new ConnectivityManager.NetworkCallback(){public void onAvailable(Network n){
   callbackEntered.countDown();try{callbackRelease.await(5,TimeUnit.SECONDS);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}
  }
   public void onLinkPropertiesChanged(Network n,LinkProperties p){linkEvents.incrementAndGet();if("latest".equals(p.getDomains()))latestDelivered.countDown();}
  },new Handler(blockedThread.getLooper()),null);
  check(callbackEntered.await(4,TimeUnit.SECONDS),"blocking callback did not start");
  blockedSource.value=snapshot(false,"latest");
  Thread observer=new Thread(new Runnable(){public void run(){for(int i=0;i<1000;i++)blocked.poll();polled.countDown();}});
  observer.start();boolean independent;
  try{independent=polled.await(1,TimeUnit.SECONDS);callbackRelease.countDown();
   if(independent)check(latestDelivered.await(4,TimeUnit.SECONDS)&&linkEvents.get()==2,"latest coalesced snapshot lost or duplicated");
  }finally{callbackRelease.countDown();blocked.close();blockedThread.quit();}
  observer.join(2000);blockedThread.join(2000);
  check(independent,"slow callback blocked network observation");
  check(!observer.isAlive()&&!blockedThread.isAlive(),"slow callback cleanup leaked threads");
  System.out.println("CONNECTIVITY_TRANSITIONS_OK");
 }
}
