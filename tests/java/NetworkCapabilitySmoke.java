// SPDX-License-Identifier: GPL-3.0-only
import android.net.*;
import android.os.*;
public final class NetworkCapabilitySmoke {
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  NetworkCapabilities host=NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_WIFI);
  check(host.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),"route capability");
  for(int capability:new int[]{11,14,15,16,18,20,21})check(!host.hasCapability(capability),"unobserved capability "+capability);
  check(host.getLinkDownstreamBandwidthKbps()==0&&host.getLinkUpstreamBandwidthKbps()==0,"invented bandwidth");
  NetworkRequest.Builder builder=new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(NetworkCapabilities.TRANSPORT_WIFI).addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
  NetworkRequest either=builder.build();
  check(either.canBeSatisfiedBy(host),"transport alternatives");
  check(!either.canBeSatisfiedBy(NetworkCapabilities.shirazHost(NetworkCapabilities.TRANSPORT_ETHERNET)),"wrong transport");
  check(!either.canBeSatisfiedBy(null),"null matches");
  NetworkRequest validated=builder.addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED).build();
  check(!validated.canBeSatisfiedBy(host),"unknown validation matched");
  check(either.canBeSatisfiedBy(host),"builder mutated prior request");
  NetworkCapabilities proven=new NetworkCapabilities(host).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
  check(validated.canBeSatisfiedBy(proven),"all required capabilities");
  check(!validated.canBeSatisfiedBy(host),"candidate copy mutated original");
  ConnectivityManager manager=ConnectivityManager.shirazForHost();
  check(manager.getNetworkInfo(-1)==null&&manager.getNetworkInfo(18)==null,"invalid legacy network type");
  for(int type=0;type<=17;type++){NetworkInfo info=manager.getNetworkInfo(type);check(info==null||info.getType()==type,"legacy query returned another transport");}
  NetworkInfo[] inventory=manager.getAllNetworkInfo();check(inventory.length<=1,"invented network inventory");
  if(inventory.length==1){inventory[0]=null;NetworkInfo[] fresh=manager.getAllNetworkInfo();check(fresh.length==0||fresh[0]!=null,"mutable inventory array");}
  check(manager.isActiveNetworkMetered(),"unknown metering must be conservative");
  final int[] calls={0};
  ConnectivityManager.NetworkCallback callback=new ConnectivityManager.NetworkCallback(){public void onAvailable(Network n){calls[0]++;}};
  manager.registerNetworkCallback(validated,callback);check(calls[0]==0,"unmatched callback");manager.unregisterNetworkCallback(callback);
  manager.requestNetwork(validated,callback);check(calls[0]==0,"unmatched request");manager.unregisterNetworkCallback(callback);
  try{manager.registerNetworkCallback(null,callback);throw new AssertionError("null request");}catch(NullPointerException expected){}
  try{manager.requestNetwork(null,callback);throw new AssertionError("null active request");}catch(NullPointerException expected){}
  Looper.prepare();Handler handler=new Handler(Looper.myLooper());
  manager.registerNetworkCallback(validated,callback,handler);Looper.myLooper().shirazDispatchDue(100);
  check(calls[0]==0,"unmatched queued callback");manager.unregisterNetworkCallback(callback);
  Network before=manager.getActiveNetwork();
  manager.registerDefaultNetworkCallback(callback,handler);
  long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
  while(calls[0]==0&&System.nanoTime()<deadline){Looper.myLooper().shirazDispatchDue(100);Thread.sleep(10);}
  Network after=manager.getActiveNetwork();
  if(before!=null&&after!=null)check(calls[0]==1,"default callback missing");
  manager.unregisterNetworkCallback(callback);manager.shirazClose();Looper.myLooper().quit();
  System.out.println("NETWORK_CAPABILITY_OK");
 }
}
