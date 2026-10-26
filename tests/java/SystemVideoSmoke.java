// SPDX-License-Identifier: GPL-3.0-only
/** Optional integration test: args[0] is an Annex-B H.264 clip with AUDs. */
public final class SystemVideoSmoke {
 public static void main(String[] args)throws Exception{
  byte[] stream=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0]));
  java.util.ArrayList<Integer> offsets=new java.util.ArrayList<Integer>();
  for(int i=0;i+5<stream.length;i++)if(stream[i]==0&&stream[i+1]==0&&stream[i+2]==0&&stream[i+3]==1&&(stream[i+4]&31)==9)offsets.add(i);
  if(offsets.isEmpty())throw new AssertionError("No H264 access units");offsets.add(stream.length);
  backpressure(stream,offsets);
  long decoder=shiraz.runtime.NativeVideo.open("video/avc",960,540,new byte[0]);
  android.view.Surface surface=new android.view.Surface();surface.shirazConfigure(960,540,1);int frames=0;long last=-1;java.util.HashSet<Integer> hashes=new java.util.HashSet<Integer>();
  try{
   for(int i=0;i+1<offsets.size();i++)shiraz.runtime.NativeVideo.queue(decoder,java.util.Arrays.copyOfRange(stream,offsets.get(i),offsets.get(i+1)),i*33333L,false);
   shiraz.runtime.NativeVideo.queue(decoder,new byte[0],0,true);
   // Gecko retains several outputs before presenting the first: a one-slot
   // implementation deadlocks despite passing a decode/release loop.
   java.util.ArrayList<long[]> retained=new java.util.ArrayList<long[]>();
   long holdDeadline=System.nanoTime()+10000000000L;
   while(retained.size()<4&&System.nanoTime()<holdDeadline){long[] f=shiraz.runtime.NativeVideo.poll(decoder);if(f==null)Thread.sleep(5);else retained.add(f);}
   if(retained.size()!=4)throw new AssertionError("Multiple output ownership stalled");
   for(long[] f:retained)shiraz.runtime.NativeVideo.releaseFrame(decoder,(int)f[3],0,false);
   long deadline=System.nanoTime()+10000000000L;
   while(frames<Math.min(8,offsets.size()-1)&&System.nanoTime()<deadline){long[] frame=shiraz.runtime.NativeVideo.poll(decoder);if(frame==null){Thread.sleep(5);continue;}if(frame[0]!=960||frame[1]!=540)throw new AssertionError("Decoded dimensions");if(frame[2]<last)throw new AssertionError("Decoded timestamp order");last=frame[2];shiraz.runtime.NativeVideo.releaseFrame(decoder,(int)frame[3],surface.shirazNativeWindow(),true);
    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(960,540,android.graphics.Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);surface.shirazDraw(canvas,960,540);int hash=0;for(int y=0;y<540;y+=50)for(int x=0;x<960;x+=50)hash=31*hash+bitmap.getPixel(x,y);hashes.add(hash);canvas.setBitmap(null);bitmap.recycle();frames++;
   }
   if(frames<3||hashes.size()<2)throw new AssertionError("Video frames missing/unchanged: "+frames+"/"+hashes.size());
   System.out.println("SYSTEM_VIDEO_DECODE_OK frames="+frames+" distinct="+hashes.size());
  }finally{shiraz.runtime.NativeVideo.close(decoder);surface.release();}
 }
 static void backpressure(byte[] stream,java.util.ArrayList<Integer> offsets)throws Exception{
  android.view.Surface surface=new android.view.Surface();surface.shirazConfigure(960,540,1);
  android.media.MediaCodec codec=android.media.MediaCodec.createDecoderByType("video/avc");
  codec.configure(android.media.MediaFormat.createVideoFormat("video/avc",960,540),surface,null,0);codec.start();
  try{
   int submitted=0;long deadline=System.nanoTime()+5000000000L;
   // A paused consumer must stop receiving input ownership before an overflow,
   // without blocking the thread that must eventually release output frames.
   while(submitted<500&&System.nanoTime()<deadline){
    int index=codec.dequeueInputBuffer(0);if(index<0)break;
    int packet=submitted%(offsets.size()-1),start=offsets.get(packet),end=offsets.get(packet+1);
    codec.getInputBuffer(index).put(stream,start,end-start);
    codec.queueInputBuffer(index,0,end-start,submitted*33333L,0);submitted++;
   }
   if(submitted==0||submitted>=500)throw new AssertionError("Paused decoder did not apply backpressure: "+submitted);
   android.media.MediaCodec.BufferInfo info=new android.media.MediaCodec.BufferInfo();boolean resumed=false;int released=0;
   deadline=System.nanoTime()+10000000000L;
   while(System.nanoTime()<deadline){
    int output=codec.dequeueOutputBuffer(info,0);
    if(output>=0){codec.releaseOutputBuffer(output,false);released++;}
    if(released>0&&codec.dequeueInputBuffer(0)>=0){resumed=true;break;}
    Thread.sleep(5);
   }
   if(!resumed)throw new AssertionError("Decoder did not resume after releasing output");
   System.out.println("SYSTEM_VIDEO_BACKPRESSURE_OK submitted="+submitted+" released="+released);
  }finally{codec.release();surface.release();}
 }

}
