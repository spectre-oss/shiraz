// SPDX-License-Identifier: GPL-3.0-only
import android.media.*;
import java.io.File;
/** Tests configuration and consent failure without opening any host device. */
public final class MediaRecorderStateSmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  File output=new File(System.getProperty("java.io.tmpdir"),"shiraz-recorder-consent-test.m4a");if(output.exists())throw new AssertionError("Existing test output");
  MediaRecorder recorder=new MediaRecorder();try{boolean incomplete=false;try{recorder.prepare();}catch(IllegalStateException expected){incomplete=true;}check(incomplete,"incomplete recorder rejected");boolean video=false;try{recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);}catch(UnsupportedOperationException expected){video=true;}check(video&&!CamcorderProfile.hasProfile(0,CamcorderProfile.QUALITY_HIGH),"video recording not advertised");recorder.setAudioSource(MediaRecorder.AudioSource.MIC);recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);recorder.setAudioSamplingRate(44100);recorder.setAudioChannels(1);recorder.setOutputFile(output.getPath());recorder.prepare();check(!output.exists(),"prepare must not truncate/create recording");boolean denied=false;try{recorder.start();}catch(SecurityException expected){denied=true;}check(denied&&!output.exists(),"no-consent start must leave output untouched");recorder.reset();boolean rate=false;try{recorder.setAudioSamplingRate(12345);}catch(IllegalArgumentException expected){rate=true;}check(rate,"unsupported sample rate rejected");}finally{recorder.release();}boolean released=false;try{recorder.setAudioSource(1);}catch(IllegalStateException expected){released=true;}check(released,"released recorder rejected");
  System.out.println("MEDIA_RECORDER_STATE_CONSENT_OK");
 }
}
