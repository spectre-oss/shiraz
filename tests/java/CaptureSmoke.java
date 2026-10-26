// SPDX-License-Identifier: GPL-3.0-only
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.media.*;
import shiraz.runtime.*;
import java.io.*;
import java.nio.*;
import java.util.*;
public final class CaptureSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 static final class Owner extends Activity {int calls;int[] results;void bind(Context c){attachBaseContext(c);}public int checkSelfPermission(String p){CapturePermissions policy=CapturePermissions.current();return policy!=null&&policy.granted(p)?0:-1;}public void onRequestPermissionsResult(int code,String[] names,int[] values){calls++;results=values;}}
 static Button find(View v,String label){if(v instanceof Button&&label.contentEquals(((Button)v).getText()))return (Button)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Button found=find(((ViewGroup)v).getChildAt(i),label);if(found!=null)return found;}return null;}
 static AlertDialog pending(Owner owner)throws Exception{java.lang.reflect.Field requests=Activity.class.getDeclaredField("permissionRequests");requests.setAccessible(true);java.lang.reflect.Field prompt=PermissionRequests.class.getDeclaredField("prompt");prompt.setAccessible(true);return (AlertDialog)prompt.get(requests.get(owner));}
 static void pump(){for(int i=0;i<3;i++)Looper.myLooper().shirazDispatchDue(100);}
 static void choose(Owner owner,CapturePermissions policy,String p,String label){final int[] calls={0};AlertDialog dialog=policy.prompt(owner,p,new Runnable(){public void run(){calls[0]++;}});check(!policy.granted(p),"grant requires consent");Button button=find(dialog.getWindow().getDecorView(),label);check(button!=null,"consent choice visible");button.performClick();check(calls[0]==1&&!dialog.isShowing(),"consent completes once");}
 public static void main(String[] args)throws Exception {
  Looper.prepareMainLooper();try{
   File data=new File(System.getProperty("shiraz.captureTestData"));data.mkdirs();Properties metadata=new Properties();metadata.setProperty("permission.count","2");metadata.setProperty("permission.0",CapturePermissions.CAMERA);metadata.setProperty("permission.1",CapturePermissions.MICROPHONE);
   CapturePermissions policy=new CapturePermissions(data,"Capture test",metadata);CapturePermissions.install(policy);
   Context context=new ContextWrapper(null){private final Resources.Theme theme=Resources.getSystem().newTheme();public Resources getResources(){return Resources.getSystem();}public Resources.Theme getTheme(){return theme;}public String getPackageName(){return "org.shiraz.capturetest";}};Owner owner=new Owner();owner.bind(context);
   Properties packageFacts=new Properties();packageFacts.setProperty("package","org.shiraz.capturetest");packageFacts.setProperty("activity.count","0");HostPackageManager packageManager=new HostPackageManager(packageFacts,context);
   check(new Intent("android.media.action.IMAGE_CAPTURE").resolveActivity(packageManager).getClassName().equals(HostCameraCapture.class.getName())&&new Intent("org.shiraz.unknown").resolveActivity(packageManager)==null,"camera intent resolution without an external Android registry");
   check(!policy.granted(CapturePermissions.CAMERA)&&!policy.granted(CapturePermissions.MICROPHONE),"default deny");
   try{NativeCapture.microphoneOpen(44100,1);throw new AssertionError("native consent bypass");}catch(SecurityException expected){}
   try{NativeCapture.cameraOpen();throw new AssertionError("native camera consent bypass");}catch(SecurityException expected){}
   owner.requestPermissions(new String[]{CapturePermissions.CAMERA},17);pump();AlertDialog before=pending(owner);check(before!=null&&before.isShowing()&&owner.calls==0,"request waits for visible consent");PermissionRequests requests=owner.shirazTakePermissionRequests();check(!before.isShowing(),"old consent detaches on recreation");Owner replacement=new Owner();replacement.bind(context);replacement.shirazRestorePermissionRequests(requests);owner.shirazSetDestroyed();pump();AlertDialog after=pending(replacement);check(after!=null&&after!=before&&after.isShowing(),"unanswered capture prompt recreated");find(after.getWindow().getDecorView(),"Don't allow").performClick();pump();check(owner.calls==0&&replacement.calls==1&&replacement.results[0]==-1,"only replacement receives denial");owner=replacement;
   choose(owner,policy,CapturePermissions.MICROPHONE,"Don't allow");check(!policy.granted(CapturePermissions.MICROPHONE)&&policy.wasDenied(CapturePermissions.MICROPHONE),"deny tracked");
   choose(owner,policy,CapturePermissions.MICROPHONE,"Only this time");check(policy.granted(CapturePermissions.MICROPHONE),"one-time grant");
   AudioRecord recorder=new AudioRecord(1,44100,16,2,8192);recorder.startRecording();check(recorder.getRecordingState()==3&&policy.activeSummary().contains("Microphone"),"capture indicator");byte[] pcm=new byte[2048];check(recorder.read(pcm,0,pcm.length)==pcm.length,"PCM read");
   if("1".equals(System.getenv("SHIRAZ_TEST_CAPTURE_SIGNAL"))){long energy=0;for(int i=0;i<pcm.length;i+=2){long sample=(short)((pcm[i]&255)|(pcm[i+1]<<8));energy+=sample*sample;}check(energy/(pcm.length/2)>1000000,"microphone source signal survives capture");}
   policy.revoke(CapturePermissions.MICROPHONE);check(recorder.getRecordingState()==1&&policy.activeSummary().isEmpty()&&recorder.read(pcm,0,pcm.length)==-3,"revocation closes capture");recorder.release();
   choose(owner,policy,CapturePermissions.MICROPHONE,"Only this time");policy.setForeground(false);policy.setForeground(true);check(!policy.granted(CapturePermissions.MICROPHONE),"one-time expires in background");
   choose(owner,policy,CapturePermissions.CAMERA,"While using app");CapturePermissions reloaded=new CapturePermissions(data,"Capture test",metadata);check(reloaded.granted(CapturePermissions.CAMERA),"durable app grant");reloaded.setForeground(false);check(!reloaded.granted(CapturePermissions.CAMERA),"background never granted");
   Properties none=new Properties();CapturePermissions undeclared=new CapturePermissions(new File(data,"undeclared"),"other",none);check(!undeclared.canRequest(CapturePermissions.CAMERA),"undeclared cannot prompt");
   String audit=new String(java.nio.file.Files.readAllBytes(new File(data,"privacy/access-history.log").toPath()),java.nio.charset.StandardCharsets.UTF_8);check(audit.contains("capture-start")&&audit.contains("capture-stop")&&audit.contains("decision:")&&audit.contains("one-time-expired")&&policy.history().contains("Capture started"),"durable metadata history and readable UI");
   MediaFormat format=MediaFormat.createAudioFormat("audio/mp4a-latm",44100,1);format.setInteger("bitrate",32000);MediaCodec codec=MediaCodec.createEncoderByType("audio/mp4a-latm");codec.configure(format,null,null,1);codec.start();MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();check(codec.dequeueOutputBuffer(info,0)==-2,"AAC output format");
   File recording=new File(data,"encoded-test.m4a");MediaMuxer muxer=new MediaMuxer(recording.getAbsolutePath(),0);muxer.addTrack(codec.getOutputFormat());muxer.start();int packets=0;
   for(int i=0;i<8;i++){check(codec.dequeueInputBuffer(0)==0,"AAC input buffer");codec.getInputBuffer(0).put(new byte[2048]);codec.queueInputBuffer(0,0,2048,i*1024L*1000000/44100,i==7?4:0);while(codec.dequeueOutputBuffer(info,0)>=0){if(info.size>0){muxer.writeSampleData(0,codec.getOutputBuffer(0),info);packets++;}codec.releaseOutputBuffer(0,false);}}
   muxer.stop();muxer.release();codec.release();byte[] encoded=java.nio.file.Files.readAllBytes(recording.toPath());String boxes=new String(encoded,java.nio.charset.StandardCharsets.ISO_8859_1);check(packets==8&&boxes.contains("ftypM4A ")&&boxes.contains("moov")&&boxes.contains("mp4a")&&boxes.contains("esds")&&encoded.length>500,"AAC MPEG-4 recording");
   MediaMetadataRetriever metadataReader=new MediaMetadataRetriever();metadataReader.setDataSource(recording.getAbsolutePath());check("185".equals(metadataReader.extractMetadata(9))&&"yes".equals(metadataReader.extractMetadata(16))&&metadataReader.getFrameAtTime()==null,"recorded AAC duration and audio metadata");metadataReader.release();
   try(FileInputStream recordingInput=new FileInputStream(recording)){recordingInput.getChannel().position(5);metadataReader.setDataSource(recordingInput.getFD(),0,recording.length());check(recordingInput.getChannel().position()==5&&"185".equals(metadataReader.extractMetadata(9)),"descriptor metadata preserves position and caller ownership");}metadataReader.release();
   System.out.println("CAPTURE_CONSENT_AUDIT_CODEC_OK");
  }finally{CapturePermissions.install(null);DialogOverlay.clear();Looper.shirazShutdownMain();}
 }
}
