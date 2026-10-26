// SPDX-License-Identifier: GPL-3.0-only
import android.media.*;import android.os.*;import java.util.*;
public final class AudioFocusSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args){Looper.prepareMainLooper();final List<String> events=new ArrayList<String>();final Thread main=Thread.currentThread();AudioManager manager=AudioManager.shirazForProcess();
 AudioManager.OnAudioFocusChangeListener a=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int change){check(Thread.currentThread()==main,"callback main thread");events.add("a"+change);}};
 AudioManager.OnAudioFocusChangeListener b=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int change){events.add("b"+change);}};
 AudioManager.OnAudioFocusChangeListener c=new AudioManager.OnAudioFocusChangeListener(){public void onAudioFocusChange(int change){events.add("c"+change);}};
 AudioFocusRequest first=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setOnAudioFocusChangeListener(a).setWillPauseWhenDucked(true).build();check(manager.requestAudioFocus(first)==1&&events.isEmpty(),"initial focus synchronous grant");
 AudioFocusRequest speech=new AudioFocusRequest.Builder(3).setAudioAttributes(new AudioAttributes.Builder().setUsage(12).setContentType(1).build()).setOnAudioFocusChangeListener(b).build();manager.requestAudioFocus(speech);check(events.isEmpty(),"callback queued");Looper.myLooper().shirazDispatchDue(10);check(events.equals(Arrays.asList("a-2")),"speech pause instead of duck");
 manager.abandonAudioFocusRequest(new AudioFocusRequest.Builder(speech).build());Looper.myLooper().shirazDispatchDue(10);check(events.equals(Arrays.asList("a-2","a1")),"listener identity abandon restores previous");events.clear();
 manager.requestAudioFocus(new AudioFocusRequest.Builder(1).setOnAudioFocusChangeListener(c).build());Looper.myLooper().shirazDispatchDue(10);check(events.equals(Arrays.asList("a-1")),"permanent replacement loss");events.clear();manager.abandonAudioFocus(c);Looper.myLooper().shirazDispatchDue(10);check(events.isEmpty(),"permanently lost owner not restored");
 manager.requestAudioFocus(a,3,1);manager.requestAudioFocus(b,3,3);Looper.myLooper().shirazDispatchDue(10);check(events.equals(Arrays.asList("a-3")),"legacy duck callback");manager.abandonAudioFocus(b);Looper.myLooper().shirazDispatchDue(10);check(events.equals(Arrays.asList("a-3","a1")),"legacy restore");manager.abandonAudioFocus(a);
 check(!manager.isBluetoothScoAvailableOffCall()&&!manager.isBluetoothScoOn()&&manager.getMode()==0,"no unsupported host routing claims");try{new AudioFocusRequest.Builder(0);throw new AssertionError("bad gain");}catch(IllegalArgumentException expected){}try{new AudioFocusRequest.Builder(1).setAcceptsDelayedFocusGain(true).build();throw new AssertionError("missing listener");}catch(IllegalStateException expected){}
 Looper.shirazShutdownMain();System.out.println("AUDIO_FOCUS_POLICY_OK");}
}
