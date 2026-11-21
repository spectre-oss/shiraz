// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
/** Passive default path observation, shared with host job scheduling. */
final class DarwinConnectivity {
 private DarwinConnectivity() {}
 private static final class Native {static {System.loadLibrary("shiraz-network");}}
 private static native int[] snapshot();
 static NetworkInfo active(){
  try{new Native();return decode(snapshot());}
  catch(LinkageError unavailable){throw new IllegalStateException("Native host connectivity unavailable",unavailable);}
 }
 static NetworkInfo decode(int[] values){
  if(values==null||values.length!=3||values[0]!=1||values[1]<1||values[1]>3||(values[2]&~15)!=0)
   throw new IllegalStateException("Unknown host connectivity observation");
  if(values[1]!=1)return null;
  int type=values[2]==1?ConnectivityManager.TYPE_WIFI:values[2]==2?ConnectivityManager.TYPE_ETHERNET:values[2]==4?ConnectivityManager.TYPE_MOBILE:ConnectivityManager.TYPE_DUMMY;
  String name=type==ConnectivityManager.TYPE_WIFI?"WIFI":type==ConnectivityManager.TYPE_ETHERNET?"ETHERNET":type==ConnectivityManager.TYPE_MOBILE?"MOBILE":"UNKNOWN";
  NetworkInfo info=new NetworkInfo(type,0,name,"");info.setIsAvailable(true);info.setDetailedState(NetworkInfo.DetailedState.CONNECTED,null,null);return info;
 }
}
