// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.net.*;
public final class DarwinConnectivitySmoke {
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 public static void main(String[] args){
  int[] masks={0,1,2,3,4,8,15};int[] types={8,1,9,8,0,8,8};
  for(int i=0;i<masks.length;i++){NetworkInfo n=DarwinConnectivity.decode(new int[]{1,1,masks[i]});check(n.isConnected()&&n.getType()==types[i],"transport "+masks[i]);}
  check(DarwinConnectivity.decode(new int[]{1,2,1})==null,"unsatisfied");
  check(DarwinConnectivity.decode(new int[]{1,3,1})==null,"requires connection");
  for(int[] bad:new int[][]{null,{}, {2,1,1},{1,0,1},{1,4,1},{1,1,16},{1,1,-1}}){
   try{DarwinConnectivity.decode(bad);throw new AssertionError("unknown observation accepted");}catch(IllegalStateException expected){}
  }
  if(HostOs.darwin()){
   long start=System.nanoTime();
   for(int i=0;i<8;i++){NetworkInfo value=DarwinConnectivity.active();if(value!=null)check(value.isConnected(),"live status");}
   System.out.println("NATIVE_PATH_SAMPLES=8 elapsed_ms="+(System.nanoTime()-start)/1000000);
  }
  System.out.println("DARWIN_CONNECTIVITY_OK");
 }
}
