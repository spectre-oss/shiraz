// SPDX-License-Identifier: GPL-3.0-only
import android.media.MediaPlayer;
import android.os.Looper;
import java.io.*;
public class MediaPlayerSmoke {
 static void check(boolean v,String why){if(!v)throw new AssertionError(why);}
 static void le(ByteArrayOutputStream out,int v,int n){for(int i=0;i<n;i++)out.write(v>>(8*i));}
 static byte[] wav()throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();b.write("RIFF".getBytes("US-ASCII"));le(b,36+8820,4);b.write("WAVEfmt ".getBytes("US-ASCII"));le(b,16,4);le(b,1,2);le(b,1,2);le(b,44100,4);le(b,88200,4);le(b,2,2);le(b,16,2);b.write("data".getBytes("US-ASCII"));le(b,8820,4);for(int i=0;i<4410;i++)le(b,(int)(12000*Math.sin(i*2*Math.PI*440/44100)),2);return b.toByteArray();}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();final Thread owner=Thread.currentThread();final int[] done={0};byte[] bytes=wav();File file=new File(System.getProperty("shiraz.captureTestData"),"region.bin");
  try(FileOutputStream out=new FileOutputStream(file)){out.write(new byte[37]);out.write(bytes);out.write(new byte[17]);}
  final MediaPlayer p=new MediaPlayer();p.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer player){check(Thread.currentThread()==owner,"callback thread");done[0]++;player.release();}});
  p.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer player,int what,int extra){throw new AssertionError("playback error");}});
  try(FileInputStream in=new FileInputStream(file)){p.setDataSource(in.getFD(),37,bytes.length);}
  p.prepare();check(p.getDuration()==100,"region decoded with closed caller fd");p.setVolume(.5f,.5f);p.start();long end=System.currentTimeMillis()+4000;
  while(done[0]==0&&System.currentTimeMillis()<end){Thread.sleep(10);Looper.myLooper().shirazDispatchDue(100);}check(done[0]==1,"completion delivered once");p.release();
  MediaPlayer bad=new MediaPlayer();try(FileInputStream in=new FileInputStream(file)){bad.setDataSource(in.getFD(),0,16);}try{bad.prepare();throw new AssertionError("Malformed media accepted");}catch(IOException expected){}bad.reset();bad.release();
  MediaPlayer canceled=new MediaPlayer();try(FileInputStream in=new FileInputStream(file)){canceled.setDataSource(in.getFD(),37,bytes.length);}canceled.prepare();canceled.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer ignored){throw new AssertionError("callback after release");}});canceled.start();canceled.release();Thread.sleep(150);Looper.myLooper().shirazDispatchDue(100);
  final MediaPlayer async=new MediaPlayer();final int[] ready={0},seeks={0};final byte[] wave=bytes;
  async.setOnPreparedListener(new MediaPlayer.OnPreparedListener(){public void onPrepared(MediaPlayer player){check(Thread.currentThread()==owner,"prepared callback thread");ready[0]++;}});
  async.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener(){public void onSeekComplete(MediaPlayer player){seeks[0]++;}});
  async.setDataSource(new android.media.MediaDataSource(){public int readAt(long position,byte[] buffer,int offset,int size){if(position>=wave.length)return -1;int n=(int)Math.min(size,wave.length-position);System.arraycopy(wave,(int)position,buffer,offset,n);return n;}public long getSize(){return wave.length;}public void close(){}});
  async.prepareAsync();end=System.currentTimeMillis()+4000;
  while(ready[0]==0&&System.currentTimeMillis()<end){Thread.sleep(10);Looper.myLooper().shirazDispatchDue(100);}check(ready[0]==1&&async.getDuration()==100,"prepareAsync delivered onPrepared once");
  async.seekTo(50);Looper.myLooper().shirazDispatchDue(100);check(seeks[0]==1&&async.getCurrentPosition()==50,"seekTo position and callback");async.release();
  MediaPlayer remote=new MediaPlayer();try{remote.setDataSource("https://example.invalid/a.mp3");throw new AssertionError("Network stream accepted");}catch(IOException expected){}remote.release();
  MediaPlayer video=new MediaPlayer();try(FileInputStream in=new FileInputStream(file)){video.setDataSource(in.getFD(),37,bytes.length);}
  video.setDisplay((android.view.SurfaceHolder)java.lang.reflect.Proxy.newProxyInstance(MediaPlayerSmoke.class.getClassLoader(),new Class<?>[]{android.view.SurfaceHolder.class},new java.lang.reflect.InvocationHandler(){public Object invoke(Object proxy,java.lang.reflect.Method method,Object[] values){return null;}}));
  try{video.prepare();throw new AssertionError("Video output accepted");}catch(IOException expected){}video.release();
  String createApk=System.getProperty("shiraz.testCreateApk"),createRaw=System.getProperty("shiraz.testCreateRaw");if(createApk!=null&&createRaw!=null){
   final android.content.res.Resources r=android.content.res.Resources.shirazOpen(createApk,720,1280);String[] name=createRaw.split(":",2);int id=r.getIdentifier(name[1],"raw",name[0]);check(id!=0,"create raw resource resolved");
   android.content.Context context=new android.content.ContextWrapper(null){@Override public android.content.res.Resources getResources(){return r;}};
   MediaPlayer created=MediaPlayer.create(context,id);check(created!=null&&created.getDuration()>0,"MediaPlayer.create prepared the raw resource");
   final boolean[] finished={false};created.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer player){finished[0]=true;}});created.setVolume(.1f,.1f);created.start();
   end=System.currentTimeMillis()+5000;while(!finished[0]&&System.currentTimeMillis()<end){Thread.sleep(10);Looper.myLooper().shirazDispatchDue(100);}check(finished[0],"created player completion");created.release();
   check(MediaPlayer.create(context,0x7f7f0000)==null,"missing raw resource returns null");
  }
  String asset=System.getProperty("shiraz.testAudioAsset");if(asset!=null){MediaPlayer actual=new MediaPlayer();actual.setDataSource(asset);actual.prepare();check(actual.getDuration()>0,"APK audio decoded");final boolean[] played={false};actual.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer ignored){played[0]=true;}});actual.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer ignored,int what,int extra){throw new AssertionError("APK audio playback failed");}});actual.start();long deadline=System.currentTimeMillis()+5000;while(!played[0]&&System.currentTimeMillis()<deadline){Thread.sleep(10);Looper.myLooper().shirazDispatchDue(100);}check(played[0],"APK audio completed");actual.release();}
  String apk=System.getProperty("shiraz.testAudioApk");if(apk!=null){
   android.content.res.Resources r=android.content.res.Resources.shirazOpen(apk,720,1280);
   try{r.openRawResourceFd(0);throw new AssertionError("Missing resource accepted");}catch(android.content.res.Resources.NotFoundException expected){}
   int id=r.getIdentifier("zxing_beep","raw","chat.delta");check(id!=0,"Scanner resource ID resolved");
   android.content.res.AssetFileDescriptor fd=r.openRawResourceFd(id);check(fd!=null&&fd.getLength()>0&&fd.getStartOffset()>0,"APK resource descriptor region");
   ByteArrayOutputStream resourceBytes=new ByteArrayOutputStream();try(InputStream stream=r.openRawResource(id)){byte[] chunk=new byte[4096];int n;while((n=stream.read(chunk))!=-1)resourceBytes.write(chunk,0,n);}byte[] expected=resourceBytes.toByteArray();r.shirazClose();check(java.util.Arrays.equals(expected,fd.shirazReadRegion()),"Descriptor matches resource stream");
   final MediaPlayer beep=new MediaPlayer();beep.setAudioAttributes(new android.media.AudioAttributes.Builder().setContentType(2).build());
   final boolean[] finished={false};beep.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){public void onCompletion(MediaPlayer player){finished[0]=true;player.stop();player.reset();player.release();}});
   beep.setOnErrorListener(new MediaPlayer.OnErrorListener(){public boolean onError(MediaPlayer player,int what,int extra){throw new AssertionError("Scanner beep failed");}});
   beep.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());fd.close();beep.setVolume(.1f,.1f);beep.prepare();beep.start();
   long limit=System.currentTimeMillis()+5000;while(!finished[0]&&System.currentTimeMillis()<limit){Thread.sleep(10);Looper.myLooper().shirazDispatchDue(100);}check(finished[0],"Scanner resource playback completion");
  }
  Looper.shirazShutdownMain();System.out.println("MEDIA_PLAYER_OK");
 }
}
