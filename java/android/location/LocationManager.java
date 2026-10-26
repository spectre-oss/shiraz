// SPDX-License-Identifier: GPL-3.0-only
package android.location;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import shiraz.runtime.TestLocationConfig;

/** Optional, explicitly configured mock GPS source. No host positioning backend yet. */
public final class LocationManager {
 public static final String GPS_PROVIDER="gps",NETWORK_PROVIDER="network",PASSIVE_PROVIDER="passive",FUSED_PROVIDER="fused";
 public static final String PROVIDERS_CHANGED_ACTION="android.location.PROVIDERS_CHANGED",MODE_CHANGED_ACTION="android.location.MODE_CHANGED";
 private final Context context;
 private TestLocationConfig fix;
 private Location last;
 private boolean foreground=true,closed;
 private final IdentityHashMap<LocationListener,Subscription> subscriptions=new IdentityHashMap<LocationListener,Subscription>();
 private static final boolean TRACE="1".equals(System.getenv("SHIRAZ_TRACE_LOCATION"));
 private LocationManager(Context context,TestLocationConfig fix){if(context==null)throw new NullPointerException("context");this.context=context;this.fix=fix;if(fix!=null)last=sample();}
 public static LocationManager shirazForProcess(Context context){return new LocationManager(context,TestLocationConfig.fromEnvironment());}
 public static LocationManager shirazForTest(Context context,TestLocationConfig fix){return new LocationManager(context,fix);}
 public synchronized List<String> getAllProviders(){ArrayList<String> providers=new ArrayList<String>();if(fix!=null&&!closed)providers.add(GPS_PROVIDER);return providers;}
 public List<String> getProviders(boolean enabledOnly){return getAllProviders();}
 private static void provider(String value){if(value==null)throw new IllegalArgumentException("provider is null");}
 public synchronized boolean isProviderEnabled(String provider){provider(provider);return GPS_PROVIDER.equals(provider)&&fix!=null&&!closed;}
 public synchronized boolean isLocationEnabled(){return fix!=null&&!closed;}
 private boolean permitted(){return context.checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED;}
 private void requireBackend(String provider){
  if(!permitted())throw new SecurityException("GPS requires fine location permission");
  if(closed)throw new IllegalStateException("Location service is closed");
  if(fix==null)throw new UnsupportedOperationException("No configured location backend");
  if(!GPS_PROVIDER.equals(provider))throw new IllegalArgumentException("Unknown location provider: "+provider);
 }
 private Location sample(){Location l=new Location(GPS_PROVIDER);l.setLatitude(fix.latitude);l.setLongitude(fix.longitude);l.setAccuracy(fix.accuracy);l.setMock(true);l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());return l;}
 private static void trace(String action,Location l){if(TRACE)System.out.println("SHIRAZ_TEST_LOCATION "+action+" latitude="+l.getLatitude()+" longitude="+l.getLongitude()+" mock=true elapsed_ns="+l.getElapsedRealtimeNanos());}
 public synchronized Location getLastKnownLocation(String provider){provider(provider);requireBackend(provider);Location result=new Location(last);trace("last-known",result);return result;}
 public void requestLocationUpdates(String provider,long minTime,float minDistance,LocationListener listener){requestLocationUpdates(provider,minTime,minDistance,listener,null);}
 public synchronized void requestLocationUpdates(String provider,long minTime,float minDistance,LocationListener listener,Looper looper){
  request(provider,minTime,minDistance,listener,looper,false);
 }
 public synchronized void requestSingleUpdate(String provider,LocationListener listener,Looper looper){
  request(provider,0,0,listener,looper,true);
 }
 private void request(String provider,long minTime,float minDistance,LocationListener listener,Looper looper,boolean single){
  provider(provider);if(listener==null)throw new IllegalArgumentException("listener is null");
  if(!Float.isFinite(minDistance))throw new IllegalArgumentException("Non-finite minimum distance");
  requireBackend(provider);
  Handler handler=new Handler(looper==null?Looper.myLooper():looper);
  removeUpdates(listener);
  Subscription subscription=new Subscription(listener,handler,Math.max(1000,minTime),Math.max(0,minDistance),single);
  if(TRACE)System.out.println("SHIRAZ_TEST_LOCATION subscribe interval_ms="+subscription.interval+" min_distance_m="+subscription.minDistance+" listener="+((Object)listener).getClass().getName());
  subscriptions.put(listener,subscription);if(foreground&&!handler.post(subscription))subscriptions.remove(listener);
 }
 public synchronized void removeUpdates(LocationListener listener){if(listener==null)throw new IllegalArgumentException("listener is null");Subscription subscription=subscriptions.remove(listener);if(subscription!=null)subscription.handler.removeCallbacks(subscription);}
 /** Host lifecycle pauses delivery without discarding registrations. */
 public synchronized void shirazSetForeground(boolean value){if(closed||foreground==value)return;foreground=value;for(Subscription s:subscriptions.values()){s.handler.removeCallbacks(s);if(value)s.handler.post(s);}}
 public synchronized void shirazSetTestLocation(TestLocationConfig value){if(value==null)throw new NullPointerException("fix");if(fix==null||closed)throw new IllegalStateException("Test backend is not active");fix=value;last=sample();}
 public synchronized void shirazClose(){if(closed)return;closed=true;for(Subscription s:subscriptions.values())s.handler.removeCallbacks(s);subscriptions.clear();last=null;}
 // Simulated coordinates provide no satellite measurements or assistance injection.
 public synchronized boolean registerGnssStatusCallback(GnssStatus.Callback callback,Handler handler){if(callback==null)throw new IllegalArgumentException("callback");requireBackend(GPS_PROVIDER);return false;}
 public void unregisterGnssStatusCallback(GnssStatus.Callback callback){if(callback==null)throw new IllegalArgumentException("callback");}
 public synchronized boolean sendExtraCommand(String provider,String command,android.os.Bundle extras){provider(provider);requireBackend(provider);return false;}
 private static double distance(Location a,Location b){double lat=Math.toRadians(b.getLatitude()-a.getLatitude()),lon=Math.toRadians(b.getLongitude()-a.getLongitude());double h=Math.sin(lat/2)*Math.sin(lat/2)+Math.cos(Math.toRadians(a.getLatitude()))*Math.cos(Math.toRadians(b.getLatitude()))*Math.sin(lon/2)*Math.sin(lon/2);return 6371000*2*Math.asin(Math.sqrt(Math.min(1,h)));}
 private final class Subscription implements Runnable {
  final LocationListener listener;final Handler handler;final long interval;final float minDistance;final boolean single;Location delivered;long deliveredAt;
  Subscription(LocationListener listener,Handler handler,long interval,float distance,boolean single){this.listener=listener;this.handler=handler;this.interval=interval;minDistance=distance;this.single=single;}
  public void run(){
   Location value=null;
   synchronized(LocationManager.this){
    if(closed||!foreground||subscriptions.get(listener)!=this)return;
    if(!permitted()){subscriptions.remove(listener);return;}
    long now=SystemClock.elapsedRealtime();last=sample();
    if(delivered==null||(now-deliveredAt>=interval&&distance(delivered,last)>=minDistance)){
     value=new Location(last);delivered=new Location(last);deliveredAt=now;
     // Retire before calling app code, which may register this listener again.
     if(single)subscriptions.remove(listener);
    }
   }
   // App callbacks may synchronously coordinate with another thread removing this listener.
   if(value!=null){listener.onLocationChanged(value);trace("callback-completed",delivered);}
   synchronized(LocationManager.this){
    if(!closed&&foreground&&subscriptions.get(listener)==this&&!handler.hasCallbacks(this)&&!handler.postDelayed(this,interval))subscriptions.remove(listener);
   }
  }
 }
}
