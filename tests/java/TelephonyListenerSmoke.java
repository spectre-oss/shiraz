// SPDX-License-Identifier: GPL-3.0-only
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
public class TelephonyListenerSmoke {
 public static void main(String[] args){
  Looper.prepare();final int[] calls={0};final Thread owner=Thread.currentThread();
  TelephonyManager manager=new TelephonyManager();
  PhoneStateListener listener=new PhoneStateListener(){public void onCallStateChanged(int state,String number){
   if(state!=TelephonyManager.CALL_STATE_IDLE||!"".equals(number)||Thread.currentThread()!=owner)throw new AssertionError("call state");
   calls[0]++;
  }};
  manager.listen(listener,PhoneStateListener.LISTEN_CALL_STATE);
  if(calls[0]!=0)throw new AssertionError("callback must be queued");
  Looper.myLooper().shirazDispatchDue(10);
  if(calls[0]!=1)throw new AssertionError("initial idle callback");
  manager.listen(listener,PhoneStateListener.LISTEN_CALL_STATE);
  manager.listen(listener,PhoneStateListener.LISTEN_NONE);
  Looper.myLooper().shirazDispatchDue(10);
  if(calls[0]!=1)throw new AssertionError("unregistered callback");
  manager.listen(listener,PhoneStateListener.LISTEN_CALL_STATE);
  manager.listen(listener,PhoneStateListener.LISTEN_CALL_STATE);
  Looper.myLooper().shirazDispatchDue(10);
  if(calls[0]!=2)throw new AssertionError("duplicate registration");
  manager.listen(listener,PhoneStateListener.LISTEN_NONE);
  System.out.println("TELEPHONY_LISTENER_OK");
 }
}
