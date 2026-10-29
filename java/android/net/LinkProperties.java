// SPDX-License-Identifier: GPL-3.0-only
package android.net;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
/** Resolver snapshot for the host default network; no Android Private DNS service. */
public class LinkProperties {
 private final ArrayList<InetAddress> dns=new ArrayList<InetAddress>();
 private String domains;
 public LinkProperties(){}
 public LinkProperties(LinkProperties other){dns.addAll(other.dns);domains=other.domains;}
 public List<InetAddress> getDnsServers(){return Collections.unmodifiableList(new ArrayList<InetAddress>(dns));}
 public boolean addDnsServer(InetAddress server){if(server==null||dns.contains(server))return false;dns.add(server);return true;}
 public void setDnsServers(java.util.Collection<InetAddress> servers){ArrayList<InetAddress> copy=new ArrayList<InetAddress>(servers);dns.clear();for(InetAddress server:copy)addDnsServer(server);}
 public String getDomains(){return domains;}
 public void setDomains(String value){domains=value;}
 public boolean isPrivateDnsActive(){return false;}
 public String getPrivateDnsServerName(){return null;}
}
