// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.content.ContextWrapper;
public class WindowFocusCallbackSmoke {
 public static void main(String[] args){
  android.os.Looper.prepareMainLooper();
  final java.util.List<Boolean> calls=new java.util.ArrayList<Boolean>();
  Window window=new Window(new ContextWrapper(null));window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0));
  Window.Callback callback=(Window.Callback)java.lang.reflect.Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class<?>[]{Window.Callback.class},(proxy,method,values)->{
   if(method.getName().equals("onWindowFocusChanged"))calls.add((Boolean)values[0]);
   return method.getReturnType()==boolean.class?false:null;
  });window.setCallback(callback);
  View decor=window.getDecorView();decor.shirazAttachTree(ViewTreeObserver.shirazCreateWindowObserver());
  decor.shirazSetWindowFocus(true);decor.shirazSetWindowFocus(true);decor.shirazSetWindowFocus(false);
  if(!calls.equals(java.util.Arrays.asList(true,false)))throw new AssertionError("focus transitions: "+calls);
  window.setCallback(null);decor.shirazSetWindowFocus(true);if(calls.size()!=2)throw new AssertionError("removed callback");
  decor.shirazDetachTree();window.shirazDestroy();
  System.out.println("WINDOW_FOCUS_CALLBACK_OK");
 }
}
