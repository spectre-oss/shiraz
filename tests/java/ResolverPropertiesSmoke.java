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
  System.out.println("RESOLVER_LINK_PROPERTIES_OK");
 }
}
