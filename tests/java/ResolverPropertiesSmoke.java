// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.net.LinkProperties;
public final class ResolverPropertiesSmoke {
 static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
 public static void main(String[] args)throws Exception{
  LinkProperties properties=HostConnectivity.resolver("# comment\nnameserver 192.0.2.1\nnameserver 2001:db8::1 ; note\nnameserver 192.0.2.1\nsearch example.invalid test.invalid\noptions timeout:2\n");
  check(properties.getDnsServers().size()==2,"deduplicate numeric servers");
  check(properties.getDnsServers().get(0).getHostAddress().equals("192.0.2.1"),"IPv4 bytes");
  check(properties.getDnsServers().get(1) instanceof java.net.Inet6Address,"IPv6 address");
  check("example.invalid test.invalid".equals(properties.getDomains()),"search domains");
  LinkProperties copy=new LinkProperties(properties);properties.setDnsServers(java.util.Collections.<java.net.InetAddress>emptyList());check(copy.getDnsServers().size()==2,"snapshot isolation");
  try{copy.getDnsServers().clear();throw new AssertionError("mutable DNS list");}catch(UnsupportedOperationException expected){}
  check(!copy.isPrivateDnsActive()&&copy.getPrivateDnsServerName()==null,"no Android Private DNS service");
  for(String address:new String[]{"resolver.invalid","999.1.2.3","1.2.3","::bad::address"}){
   try{HostConnectivity.resolver("nameserver "+address);throw new AssertionError("invalid numeric address accepted");}catch(java.io.IOException expected){}
  }
  check("last.invalid".equals(HostConnectivity.resolver("search first.invalid\ndomain last.invalid").getDomains()),"last domain directive");
  check(android.net.ConnectivityManager.shirazForHost().getLinkProperties(new android.net.Network(999))==null,"unknown network");
  android.os.Looper.prepare();
  final android.os.Looper loop=android.os.Looper.myLooper();
  final android.net.ConnectivityManager manager=android.net.ConnectivityManager.shirazForHost();
  final int[] calls={0};
  android.net.ConnectivityManager.NetworkCallback callback=new android.net.ConnectivityManager.NetworkCallback(){
   @Override public void onAvailable(android.net.Network network){check(loop.isCurrentThread(),"callback thread");calls[0]++;}
  };
  android.os.Handler handler=new android.os.Handler(loop);
  android.net.NetworkRequest request=new android.net.NetworkRequest.Builder().build();
  manager.registerNetworkCallback(request,callback,handler);
  check(calls[0]==0,"handler callback must be queued");
  manager.unregisterNetworkCallback(callback);
  loop.shirazDispatchDue(100);
  check(calls[0]==0,"cancel queued registration");
  manager.registerDefaultNetworkCallback(callback,handler);
  try{manager.registerNetworkCallback(request,callback,handler);throw new AssertionError("duplicate callback");}catch(IllegalArgumentException expected){}
  loop.shirazDispatchDue(100);
  check(calls[0]==(manager.getActiveNetwork()==null?0:1),"deliver initial snapshot");
  manager.unregisterNetworkCallback(callback);
  loop.quit();
  try{manager.registerNetworkCallback(request,callback,handler);throw new AssertionError("stopped handler");}catch(IllegalStateException expected){}
  try{manager.unregisterNetworkCallback(callback);throw new AssertionError("failed registration retained");}catch(IllegalArgumentException expected){}
  System.out.println("RESOLVER_LINK_PROPERTIES_OK");
 }
}
