// SPDX-License-Identifier: GPL-3.0-only
import android.location.*;
import android.content.*;
import android.os.*;
import shiraz.runtime.TestLocationConfig;
public final class LocationSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 private static class Listener implements LocationListener {
  int calls;Location value;Thread thread;
  public void onLocationChanged(Location l){calls++;value=l;thread=Thread.currentThread();}
  public void onStatusChanged(String p,int s,Bundle b){}public void onProviderEnabled(String p){}public void onProviderDisabled(String p){}
 }
 private static void pump(){Looper.myLooper().shirazDispatchDue(100);}
 public static void main(String[] args){
  Looper.prepare();
  final boolean[] granted={false};Context context=new ContextWrapper(null){public int checkCallingOrSelfPermission(String permission){return granted[0]?0:-1;}};
  check(Context.LOCATION_SERVICE.equals(context.getSystemServiceName(LocationManager.class)),"Typed service mapping");
  LocationManager absent=LocationManager.shirazForTest(context,null);
  check(absent.getAllProviders().isEmpty()&&!absent.isProviderEnabled("gps")&&!absent.isLocationEnabled(),"Unregistered backend advertised");
  Listener listener=new Listener();absent.removeUpdates(listener);absent.removeUpdates(listener);
  try{absent.getLastKnownLocation("gps");throw new AssertionError("Missing permission ignored");}catch(SecurityException expected){}
  granted[0]=true;try{absent.getLastKnownLocation("gps");throw new AssertionError("Missing backend hidden");}catch(UnsupportedOperationException expected){}
  check(TestLocationConfig.parse(null)==null,"Default location fabricated");
  for(String bad:new String[]{"","NaN,1","1,Infinity","91,1","1,-181","1,2,0","1,2,-1","1,2,NaN","1,2,3,4"}){
   try{TestLocationConfig.parse(bad);throw new AssertionError("Invalid test fix accepted");}catch(IllegalArgumentException expected){}
  }
  TestLocationConfig config=TestLocationConfig.parse("29.6257993,52.5559203");
  java.util.Properties metadata=new java.util.Properties();
  check(!config.grants("android.permission.ACCESS_FINE_LOCATION",metadata),"Undeclared permission granted");
  metadata.setProperty("permission.count","1");metadata.setProperty("permission.0","android.permission.ACCESS_FINE_LOCATION");
  check(config.grants("android.permission.ACCESS_FINE_LOCATION",metadata)&&!config.grants("android.permission.ACCESS_COARSE_LOCATION",metadata)&&!config.grants("android.permission.CAMERA",metadata),"Test permission scope");
  LocationManager manager=LocationManager.shirazForTest(context,config);
  check(manager.getAllProviders().size()==1&&manager.isProviderEnabled("gps")&&!manager.isProviderEnabled("network"),"Provider capabilities");
  manager.getAllProviders().clear();check(manager.getAllProviders().size()==1,"Provider list aliases service");
  Location last=manager.getLastKnownLocation("gps");
  check(last.getLatitude()==29.6257993&&last.getLongitude()==52.5559203&&last.isMock()&&last.hasAccuracy()&&!last.hasAltitude()&&last.getTime()>0&&last.getElapsedRealtimeNanos()>0,"Configured test fix fields");
  last.setLatitude(0);check(manager.getLastKnownLocation("gps").getLatitude()==29.6257993,"Last-known copy isolation");
  try{manager.getLastKnownLocation("network");throw new AssertionError("Unknown provider accepted");}catch(IllegalArgumentException expected){}
  granted[0]=false;try{manager.requestLocationUpdates("gps",0,0,listener);throw new AssertionError("Permission ignored");}catch(SecurityException expected){}
  granted[0]=true;
  manager.requestLocationUpdates("gps",0,0,listener);check(listener.calls==0,"Synchronous callback");pump();
  check(listener.calls==1&&listener.thread==Thread.currentThread()&&listener.value.getLatitude()==29.6257993,"Initial Looper delivery");
  listener.value.setLongitude(0);check(manager.getLastKnownLocation("gps").getLongitude()==52.5559203,"Callback value aliases cache");
  manager.removeUpdates(listener);SystemClock.sleep(1050);pump();check(listener.calls==1,"Removal left queued callback");
  Listener filtered=new Listener();manager.requestLocationUpdates("gps",0,100,filtered);pump();SystemClock.sleep(1050);pump();check(filtered.calls==1,"Distance filter ignored");
  manager.shirazSetTestLocation(TestLocationConfig.parse("29.6357993,52.5559203,8"));SystemClock.sleep(1050);pump();check(filtered.calls==2&&filtered.value.getAccuracy()==8,"Updated fix not delivered");
  manager.removeUpdates(filtered);
  Listener timed=new Listener();manager.requestLocationUpdates("gps",2000,0,timed);pump();SystemClock.sleep(1050);pump();check(timed.calls==1,"Minimum time ignored");
  SystemClock.sleep(1050);pump();check(timed.calls==2,"Periodic fix missing");manager.removeUpdates(timed);
  Listener paused=new Listener();manager.requestLocationUpdates("gps",0,0,paused);manager.shirazSetForeground(false);pump();check(paused.calls==0,"Background delivery");
  manager.shirazSetForeground(true);pump();check(paused.calls==1,"Resume failed");
  granted[0]=false;SystemClock.sleep(1050);pump();check(paused.calls==1,"Revoked permission delivery");granted[0]=true;
  manager.shirazSetForeground(false);manager.shirazSetForeground(true);pump();check(paused.calls==1,"Revoked registration retained");
  final Listener selfRemoving=new Listener(){public void onLocationChanged(Location l){super.onLocationChanged(l);manager.removeUpdates(this);}};
  manager.requestLocationUpdates("gps",0,0,selfRemoving);pump();SystemClock.sleep(1050);pump();check(selfRemoving.calls==1,"Reentrant cancellation failed");
  Listener crossThread=new Listener(){public void onLocationChanged(Location l){super.onLocationChanged(l);final LocationListener self=this;Thread remover=new Thread(new Runnable(){public void run(){manager.removeUpdates(self);}});remover.start();try{remover.join(1000);}catch(InterruptedException e){throw new AssertionError(e);}check(!remover.isAlive(),"Callback held service lock while app waited for removal");}};
  manager.requestLocationUpdates("gps",0,0,crossThread);pump();check(crossThread.calls==1,"Cross-thread listener not delivered");
  check(!manager.registerGnssStatusCallback(new GnssStatus.Callback(){},null),"Fabricated GNSS capability");
  Listener once=new Listener();manager.shirazSetForeground(false);
  manager.requestSingleUpdate("gps",once,Looper.myLooper());pump();check(once.calls==0,"Single fix delivered while paused");
  manager.shirazSetForeground(true);pump();check(once.calls==1,"Single fix missing after resume");
  SystemClock.sleep(1050);pump();check(once.calls==1,"Single fix repeated");
  Listener cancelledOnce=new Listener();manager.requestSingleUpdate("gps",cancelledOnce,null);manager.removeUpdates(cancelledOnce);pump();check(cancelledOnce.calls==0,"Cancelled single fix delivered");
  Listener renewed=new Listener(){public void onLocationChanged(Location l){super.onLocationChanged(l);if(calls==1)manager.requestSingleUpdate("gps",this,null);}};
  manager.requestSingleUpdate("gps",renewed,null);pump();check(renewed.calls==2,"Reentrant single registration lost");
  SystemClock.sleep(1050);pump();check(renewed.calls==2,"Retired single registration rescheduled");
  granted[0]=false;try{manager.requestSingleUpdate("gps",once,null);throw new AssertionError("Single fix bypassed permission");}catch(SecurityException expected){}granted[0]=true;
  Listener closed=new Listener();manager.requestLocationUpdates("gps",0,0,closed);manager.shirazClose();manager.shirazClose();pump();check(closed.calls==0&&!manager.isLocationEnabled(),"Close leaked callback");
  try{manager.requestLocationUpdates("gps",0,0,listener);throw new AssertionError("Closed service accepted request");}catch(IllegalStateException expected){}
  Looper.myLooper().quit();
  System.out.println("LOCATION_CONFIGURED_LIFECYCLE_OK");
 }
}
