// SPDX-License-Identifier: GPL-3.0-only
import shiraz.runtime.OrientationRequest;
public final class OrientationSmoke {
 public static void main(String[] args)throws Exception {
  OrientationRequest request=new OrientationRequest(-1,96,160);if(request.get()!=-1||request.take(96,160)!=null)throw new AssertionError("default preference");
  request.request(0,96,160);int[] size=request.take(96,160);if(size[0]!=160||size[1]!=96||request.take(96,160)!=null)throw new AssertionError("landscape request consumption");
  request.request(0,96,160);if(request.take(96,160)!=null)throw new AssertionError("recreated activity re-armed consumed landscape request");
  request.request(1,160,96);size=request.take(160,96);if(size[0]!=96||size[1]!=160)throw new AssertionError("portrait request");
  request.request(1,160,96);if(request.take(160,96)!=null)throw new AssertionError("compositor-refused portrait request retried");
  OrientationRequest initial=new OrientationRequest(1,160,96);size=initial.take(160,96);if(size==null||size[0]!=96||size[1]!=160)throw new AssertionError("initial manifest portrait request");
  request.request(14,96,160);size=request.take(160,96);if(size[0]!=96||size[1]!=160)throw new AssertionError("locked aspect capture");
  request.request(14,160,96);if(request.take(160,96)!=null)throw new AssertionError("repeated LOCKED recaptures compositor aspect");
  request.request(-1,160,96);request.take(160,96);request.request(1,160,96);size=request.take(160,96);if(size==null||size[0]!=96||size[1]!=160)throw new AssertionError("genuine mode change must schedule a new request");
  for(int mode:new int[]{-1,2,5,13}){request.request(mode,96,160);if(request.take(160,96)!=null)throw new AssertionError("freeform user orientation");}
  request.request(6,96,160);size=request.take(96,160);if(size==null||size[0]!=160)throw new AssertionError("sensor landscape desktop aspect");
  request.request(7,160,96);size=request.take(160,96);if(size==null||size[0]!=96)throw new AssertionError("sensor portrait desktop aspect");
  request.request(13,96,160);
  for(int bad:new int[]{3,4,8,9,10}){try{request.request(bad,96,160);throw new AssertionError("unsupported orientation accepted");}catch(UnsupportedOperationException expected){}if(request.get()!=13)throw new AssertionError("rejection mutated request");}
  try{request.request(15,96,160);throw new AssertionError("invalid mode accepted");}catch(IllegalArgumentException expected){}
  final Throwable[] error={null};Thread t=new Thread(new Runnable(){public void run(){try{request.request(0,96,160);}catch(Throwable e){error[0]=e;}}});t.start();t.join();if(!(error[0] instanceof IllegalStateException))throw new AssertionError("off-thread request");
  System.out.println("ORIENTATION_OK");
 }
}
