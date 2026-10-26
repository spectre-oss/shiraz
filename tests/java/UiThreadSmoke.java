// SPDX-License-Identifier: GPL-3.0-only
import android.app.Activity;import android.os.Looper;import java.util.*;
public final class UiThreadSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Looper.prepareMainLooper();final Activity activity=new Activity();final Thread owner=Thread.currentThread();final List<Integer> order=new ArrayList<Integer>();
  try{
   activity.runOnUiThread(new Runnable(){public void run(){check(Thread.currentThread()==owner,"Inline UI thread");order.add(1);}});check(order.equals(Arrays.asList(1)),"UI calls execute immediately");
   final Throwable[] failure={null};Thread worker=new Thread(new Runnable(){public void run(){try{for(int i=2;i<=3;i++){final int number=i;activity.runOnUiThread(new Runnable(){public void run(){check(Thread.currentThread()==owner,"Posted UI thread");order.add(number);}});}}catch(Throwable error){failure[0]=error;}}});worker.start();worker.join(2000);check(!worker.isAlive()&&failure[0]==null,"Worker posts return");check(order.equals(Arrays.asList(1)),"Worker callback waits for UI dispatch");Looper.myLooper().shirazDispatchDue(100);check(order.equals(Arrays.asList(1,2,3)),"Posted callback FIFO");
   final RuntimeException expected=new RuntimeException("callback");try{activity.runOnUiThread(new Runnable(){public void run(){throw expected;}});throw new AssertionError("Inline failure swallowed");}catch(RuntimeException actual){check(actual==expected,"Inline failure identity");}
   try{activity.runOnUiThread(null);throw new AssertionError("Null action");}catch(NullPointerException expectedNull){}
   android.os.Handler handler=new android.os.Handler(),other=new android.os.Handler();final int[] calls={0};Runnable callback=new Runnable(){public void run(){calls[0]++;}};Object token=new Object(),otherToken=new Object();
   android.os.Message tagged=android.os.Message.obtain(handler,callback);tagged.what=42;tagged.obj=token;handler.sendMessage(tagged);
   check(handler.hasMessages(42)&&handler.hasMessages(42,token)&&handler.hasCallbacks(callback)&&!handler.hasMessages(42,otherToken)&&!other.hasMessages(42),"Callback messages retain numeric ID, token and handler matching");
   handler.removeMessages(42,otherToken);check(handler.hasMessages(42),"Wrong token does not remove callback message");handler.removeMessages(42,token);check(!handler.hasMessages(42)&&!handler.hasCallbacks(callback),"Numeric removal cancels callback messages");
   for(int i=0;i<100;i++)if(!handler.hasMessages(77)){android.os.Message refresh=android.os.Message.obtain(handler,callback);refresh.what=77;handler.sendMessage(refresh);}
   Looper.myLooper().shirazDispatchDue(100);check(calls[0]==1&&!handler.hasMessages(77),"Tagged refresh callbacks coalesce instead of flooding queue");
   handler.post(callback);check(handler.hasMessages(0),"Posted runnable has message ID zero");handler.removeMessages(0);Looper.myLooper().shirazDispatchDue(100);check(calls[0]==1,"removeMessages zero also removes posted runnables");
   android.os.Message retained=android.os.Message.obtain(handler,42);handler.sendMessage(retained);handler.removeCallbacks(callback);check(handler.hasMessages(42),"Runnable removal does not remove numeric messages");handler.removeMessages(42);
   final int[] idle={0,0};
   android.os.MessageQueue.IdleHandler repeating=new android.os.MessageQueue.IdleHandler(){public boolean queueIdle(){idle[0]++;return true;}};
   Looper.myQueue().addIdleHandler(repeating);
   Looper.myQueue().addIdleHandler(new android.os.MessageQueue.IdleHandler(){public boolean queueIdle(){idle[1]++;Thread poster=new Thread(new Runnable(){public void run(){handler.post(callback);}});poster.start();try{poster.join(1000);}catch(InterruptedException e){throw new AssertionError(e);}check(!poster.isAlive(),"Idle callback does not hold queue lock");return false;}});
   handler.post(callback);Looper.myLooper().shirazDispatchDue(100);
   check(idle[0]==2&&idle[1]==1,"Idle callbacks recur after messages; one-shot removed");
   Looper.myLooper().shirazDispatchDue(100);check(idle[0]==2,"Idle host polling does not spin callbacks");
   Looper.myQueue().removeIdleHandler(repeating);handler.post(callback);Looper.myLooper().shirazDispatchDue(100);check(idle[0]==2,"Explicit idle removal");
   check(android.os.LocaleList.getAdjustedDefault().get(0).equals(java.util.Locale.getDefault()),"Adjusted locale follows default");
   android.hardware.input.InputManager input=android.hardware.input.InputManager.getInstance();
   check(input.getInputDeviceIds().length==1&&input.getInputDevice(0).isVirtual()&&input.getInputDevice(0).supportsSource(android.view.InputDevice.SOURCE_KEYBOARD)&&input.getInputDevice(7)==null,"Aggregate SDL device identity");
   final int[] deviceEvents={0};android.hardware.input.InputManager.InputDeviceListener listener=new android.hardware.input.InputManager.InputDeviceListener(){public void onInputDeviceAdded(int id){deviceEvents[0]++;}public void onInputDeviceRemoved(int id){deviceEvents[0]++;}public void onInputDeviceChanged(int id){deviceEvents[0]++;}};
   input.registerInputDeviceListener(listener,handler);input.registerInputDeviceListener(listener,handler);input.unregisterInputDeviceListener(listener);Looper.myLooper().shirazDispatchDue(100);check(deviceEvents[0]==0,"Registration does not invent device changes");
   check(!android.appwidget.AppWidgetManager.getInstance(activity).isRequestPinAppWidgetSupported(),"Widget hosting unavailable");
   android.net.ProxyInfo proxy=android.net.ProxyInfo.buildDirectProxy("localhost",8080,java.util.Arrays.asList("example.test"));String[] exclusions=proxy.getExclusionList();exclusions[0]="changed";
   check(proxy.getPort()==8080&&proxy.getHost().equals("localhost")&&proxy.getExclusionList()[0].equals("example.test")&&android.net.ConnectivityManager.shirazForHost().getDefaultProxy()==null,"Proxy values and absent system configuration");
   check(new android.os.UserManager().getSerialNumberForUser(android.os.UserHandle.SYSTEM)==0&&new android.os.UserManager().getSerialNumberForUser(android.os.UserHandle.of(99))==-1,"Single logical user's serial");
   android.os.Message nativeMessage=android.os.Message.obtain(handler,68);handler.sendMessage(nativeMessage);
   java.lang.reflect.Method next=android.os.MessageQueue.class.getDeclaredMethod("next");next.setAccessible(true);
   check(next.invoke(Looper.myQueue())==nativeMessage&&!handler.hasMessages(68),"Native queue next ABI consumes the due message");
   System.out.println("UI_THREAD_DISPATCH_OK");
  }finally{Looper.shirazShutdownMain();}
 }
}
