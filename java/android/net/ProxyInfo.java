// SPDX-License-Identifier: GPL-3.0-only
package android.net;
/** Direct HTTP proxy value. Shiraz has no Android-managed global proxy. */
public final class ProxyInfo {
 private final String host;private final int port;private final String[] exclusions;
 private ProxyInfo(String host,int port,java.util.List<String> exclusions){if(host==null)throw new NullPointerException("host");if(port<1||port>65535)throw new IllegalArgumentException("port");this.host=host;this.port=port;this.exclusions=exclusions.toArray(new String[exclusions.size()]);}
 public static ProxyInfo buildDirectProxy(String host,int port){return new ProxyInfo(host,port,java.util.Collections.<String>emptyList());}
 public static ProxyInfo buildDirectProxy(String host,int port,java.util.List<String> exclusions){return new ProxyInfo(host,port,exclusions);}
 public String getHost(){return host;}public int getPort(){return port;}
 public String[] getExclusionList(){return exclusions.clone();}
 public Uri getPacFileUrl(){return Uri.EMPTY;}
}
