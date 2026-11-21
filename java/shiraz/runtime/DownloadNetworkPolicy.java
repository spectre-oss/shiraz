// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.net.*;
import java.util.Properties;

/** Persistent admission policy; absent host facts never satisfy a restriction. */
public final class DownloadNetworkPolicy {
 public interface Source { NetworkCapabilities read(); }
 public static final Source HOST=new Source(){public NetworkCapabilities read(){
  NetworkInfo info=HostConnectivity.activeNetwork();if(info==null||!info.isConnected())return null;
  int type=info.getType();return NetworkCapabilities.shirazHost(type==ConnectivityManager.TYPE_WIFI?NetworkCapabilities.TRANSPORT_WIFI:type==ConnectivityManager.TYPE_MOBILE?NetworkCapabilities.TRANSPORT_CELLULAR:type==ConnectivityManager.TYPE_ETHERNET?NetworkCapabilities.TRANSPORT_ETHERNET:-1);
 }};
 public static final DownloadNetworkPolicy ANY=new DownloadNetworkPolicy(-1,true,true);
 private final int networks;private final boolean metered,roaming;
 public DownloadNetworkPolicy(int networks,boolean metered,boolean roaming){
  if(networks!=-1&&(networks<0||(networks&~3)!=0))throw new IllegalArgumentException("Download network mask");
  // Since Android N Wi-Fi-only means unmetered, including wired paths.
  this.networks=networks==2?-1:networks;this.metered=metered&&networks!=2;this.roaming=roaming;
 }
 public int pauseReason(Source source){
  if((networks==-1||networks==3)&&metered&&roaming)return 0;
  final NetworkCapabilities value;try{value=source.read();}catch(RuntimeException unknown){return 2;}
  if(value==null)return 2;
  if(!metered&&!value.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))return 3;
  if(!roaming&&!value.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING))return 2;
  if(networks==0||networks==1&&!value.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return 2;
  return 0;
 }
 void write(Properties p){p.setProperty("network.version","1");p.setProperty("network.types",Integer.toString(networks));p.setProperty("network.metered",Boolean.toString(metered));p.setProperty("network.roaming",Boolean.toString(roaming));}
 static DownloadNetworkPolicy read(Properties p){
  if(p.getProperty("network.version")==null){for(String key:p.stringPropertyNames())if(key.startsWith("network."))throw new IllegalArgumentException("Incomplete download network policy");return ANY;}
  if(!"1".equals(p.getProperty("network.version")))throw new IllegalArgumentException("Download network policy version");
  return new DownloadNetworkPolicy(Integer.parseInt(p.getProperty("network.types")),flag(p,"network.metered"),flag(p,"network.roaming"));
 }
 private static boolean flag(Properties p,String key){String value=p.getProperty(key);if(!"true".equals(value)&&!"false".equals(value))throw new IllegalArgumentException("Download policy boolean");return Boolean.parseBoolean(value);}
}
