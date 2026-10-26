// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Application;
import android.content.*;
import android.content.res.Configuration;
/** SDK-compiled callback delivery, mutation and host lifecycle checks. */
public final class ComponentFixture {
 private static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
 private static class Callback implements ComponentCallbacks2 {
  int configurations,low,trim,level;Configuration seen;
  public void onConfigurationChanged(Configuration c){configurations++;seen=c;}
  public void onLowMemory(){low++;}
  public void onTrimMemory(int n){trim++;level=n;}
 }
 public static void check(final Application app) {
  final StringBuilder order=new StringBuilder();final Callback later=new Callback();
  final ComponentCallbacks simple=new ComponentCallbacks(){public void onConfigurationChanged(Configuration c){order.append('C');}public void onLowMemory(){order.append('L');}};
  final ComponentCallbacks first=new ComponentCallbacks(){public void onConfigurationChanged(Configuration c){order.append('A');app.unregisterComponentCallbacks(this);app.registerComponentCallbacks(later);}public void onLowMemory(){order.append('X');}};
  Context wrapper=new ContextWrapper(app);wrapper.registerComponentCallbacks(first);wrapper.registerComponentCallbacks(simple);
  Configuration c=new Configuration(app.getResources().getConfiguration());
  try {
   app.onConfigurationChanged(c);check(order.toString().equals("AC")&&later.configurations==0,"snapshot mutation");
   app.onConfigurationChanged(c);check(order.toString().equals("ACC")&&later.configurations==1&&later.seen==c,"configuration delivery");
   app.onLowMemory();check(order.toString().equals("ACCL")&&later.low==1,"low memory delivery");
   app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);check(later.trim==1&&later.level==10,"trim subtype/level");
   wrapper.unregisterComponentCallbacks(later);app.onTrimMemory(80);check(later.trim==1,"unregister");
   app.registerComponentCallbacks(later);app.registerComponentCallbacks(later);app.onLowMemory();check(later.low==3,"duplicate registrations");app.unregisterComponentCallbacks(later);app.onLowMemory();check(later.low==4,"single duplicate removal");
  }finally{app.unregisterComponentCallbacks(first);app.unregisterComponentCallbacks(simple);app.unregisterComponentCallbacks(later);app.unregisterComponentCallbacks(later);}
  final RuntimeException failure=new RuntimeException("callback failure");ComponentCallbacks broken=new ComponentCallbacks(){public void onConfigurationChanged(Configuration c){throw failure;}public void onLowMemory(){throw failure;}};
  app.registerComponentCallbacks(broken);try{try{app.onLowMemory();throw new AssertionError("callback failure swallowed");}catch(RuntimeException e){check(e==failure,"callback failure identity");}}finally{app.unregisterComponentCallbacks(broken);}
  try{app.registerComponentCallbacks(null);throw new AssertionError("null callback accepted");}catch(NullPointerException expected){}
  Callback bounded=new Callback();int installed=0;
  try{for(;installed<1024;installed++)app.registerComponentCallbacks(bounded);try{app.registerComponentCallbacks(bounded);throw new AssertionError("unbounded callback registry");}catch(IllegalStateException expected){}}
  finally{for(int i=0;i<installed;i++)app.unregisterComponentCallbacks(bounded);}
  app.registerComponentCallbacks(bounded);app.onLowMemory();app.unregisterComponentCallbacks(bounded);check(bounded.low==1,"registry restored after capacity failure");
  System.out.println("SHIRAZ_FIXTURE_COMPONENT_CALLBACKS_OK");
 }
 public static void live(final Application app){app.registerComponentCallbacks(new Callback(){public void onTrimMemory(int level){check(level==ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,"host hidden level");app.unregisterComponentCallbacks(this);System.out.println("SHIRAZ_FIXTURE_COMPONENT_HIDDEN_OK");}});}
}
