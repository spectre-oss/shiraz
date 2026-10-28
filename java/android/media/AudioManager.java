// SPDX-License-Identifier: GPL-3.0-only
package android.media;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
/** Cooperative APK-local focus. Does not claim control of other desktop applications. */
public final class AudioManager {
 public static final int STREAM_VOICE_CALL=0,STREAM_SYSTEM=1,STREAM_RING=2,STREAM_MUSIC=3,STREAM_ALARM=4,STREAM_NOTIFICATION=5,STREAM_DTMF=8;
 public static final int AUDIOFOCUS_GAIN=1,AUDIOFOCUS_GAIN_TRANSIENT=2,AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK=3,AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE=4;
 public static final int AUDIOFOCUS_LOSS=-1,AUDIOFOCUS_LOSS_TRANSIENT=-2,AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK=-3;
 public static final int AUDIOFOCUS_REQUEST_FAILED=0,AUDIOFOCUS_REQUEST_GRANTED=1,AUDIOFOCUS_REQUEST_DELAYED=2,MODE_NORMAL=0,MODE_RINGTONE=1,MODE_IN_CALL=2,MODE_IN_COMMUNICATION=3;
 public interface OnAudioFocusChangeListener {void onAudioFocusChange(int focusChange);}
 private final ArrayList<AudioFocusRequest> owners=new ArrayList<AudioFocusRequest>();
 private AudioManager(){}public static AudioManager shirazForProcess(){return new AudioManager();}
 /** Optional system UI cues are muted: Shiraz has no configured desktop sound-effect theme. */
 public void playSoundEffect(int effectType){}
 public void playSoundEffect(int effectType,float volume){}
 private void notifyChange(final AudioFocusRequest request,final int change){if(request.listener==null)return;Handler handler=request.handler;if(handler==null){Looper main=Looper.getMainLooper();if(main==null)throw new IllegalStateException("Audio focus needs a main Looper");handler=new Handler(main);}if(!handler.post(new Runnable(){public void run(){request.listener.onAudioFocusChange(change);}}))throw new IllegalStateException("Audio focus callback looper stopped");}
 public synchronized int requestAudioFocus(AudioFocusRequest request){
  if(request==null)throw new NullPointerException("request");
  AudioFocusRequest previous=owners.isEmpty()?null:owners.get(owners.size()-1);
  if(previous!=null&&previous.listener==request.listener&&previous.gain==request.gain){owners.set(owners.size()-1,request);return AUDIOFOCUS_REQUEST_GRANTED;}
  if(owners.size()>=64)return AUDIOFOCUS_REQUEST_FAILED;
  for(int i=owners.size()-1;i>=0;i--)if(owners.get(i).listener==request.listener)owners.remove(i);
  if(request.gain==AUDIOFOCUS_GAIN){for(AudioFocusRequest owner:owners)notifyChange(owner,AUDIOFOCUS_LOSS);owners.clear();}
  else if(previous!=null&&previous.listener!=request.listener)notifyChange(previous,request.gain==AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK&&!previous.pause?AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:AUDIOFOCUS_LOSS_TRANSIENT);
  owners.add(request);return AUDIOFOCUS_REQUEST_GRANTED;
 }
 public int requestAudioFocus(OnAudioFocusChangeListener listener,int stream,int gain){if(stream<0||stream>10)throw new IllegalArgumentException("stream");AudioFocusRequest.Builder b=new AudioFocusRequest.Builder(gain);if(listener!=null)b.setOnAudioFocusChangeListener(listener);return requestAudioFocus(b.build());}
 public int abandonAudioFocusRequest(AudioFocusRequest request){if(request==null)throw new NullPointerException("request");return abandonAudioFocus(request.listener);}
 public synchronized int abandonAudioFocus(OnAudioFocusChangeListener listener){boolean top=!owners.isEmpty()&&owners.get(owners.size()-1).listener==listener;for(int i=owners.size()-1;i>=0;i--)if(owners.get(i).listener==listener)owners.remove(i);if(top&&!owners.isEmpty())notifyChange(owners.get(owners.size()-1),AUDIOFOCUS_GAIN);return AUDIOFOCUS_REQUEST_GRANTED;}
 public static final int GET_DEVICES_INPUTS=1,GET_DEVICES_OUTPUTS=2,GET_DEVICES_ALL=3;
 private final java.util.IdentityHashMap<AudioDeviceCallback,Handler> deviceCallbacks=new java.util.IdentityHashMap<>();
 public AudioDeviceInfo[] getDevices(int flags){return (flags&2)==0?new AudioDeviceInfo[0]:new AudioDeviceInfo[]{new AudioDeviceInfo()};}
 public void registerAudioDeviceCallback(final AudioDeviceCallback callback,Handler handler){if(callback==null)throw new IllegalArgumentException("callback");final Handler target=handler==null?new Handler(Looper.getMainLooper()):handler;synchronized(deviceCallbacks){deviceCallbacks.put(callback,target);}target.post(new Runnable(){public void run(){synchronized(deviceCallbacks){if(deviceCallbacks.get(callback)!=target)return;}callback.onAudioDevicesAdded(getDevices(GET_DEVICES_ALL));}});}
 public void unregisterAudioDeviceCallback(AudioDeviceCallback callback){synchronized(deviceCallbacks){deviceCallbacks.remove(callback);}}
 public String getProperty(String key){return "android.media.property.OUTPUT_SAMPLE_RATE".equals(key)?"48000":"android.media.property.OUTPUT_FRAMES_PER_BUFFER".equals(key)?"1024":null;}
 /** Conservative output latency in milliseconds, matching the native playback buffer. */
 public int getOutputLatency(int stream){return 80;}
 public String getParameters(String key){return "";}
 public boolean isVolumeFixed(){return true;}public int getStreamVolume(int stream){return 1;}public int getStreamMaxVolume(int stream){return 1;}public boolean isStreamMute(int stream){return false;}
 public boolean isOffloadedPlaybackSupported(AudioFormat format,AudioAttributes attributes){return false;}
 public int generateAudioSessionId(){return 1;}
 public boolean isBluetoothScoAvailableOffCall(){return false;}public boolean isBluetoothScoOn(){return false;}
 public void setBluetoothScoOn(boolean enabled){if(enabled)throw new UnsupportedOperationException("Bluetooth SCO routing is unavailable");}
 public void startBluetoothSco(){throw new UnsupportedOperationException("Bluetooth SCO routing is unavailable");}public void stopBluetoothSco(){}
 public int getMode(){return MODE_NORMAL;}public void setMode(int mode){if(mode!=MODE_NORMAL)throw new UnsupportedOperationException("Telephony audio modes are unavailable");}
}
