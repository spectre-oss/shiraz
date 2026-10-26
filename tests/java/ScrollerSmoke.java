// SPDX-License-Identifier: GPL-3.0-only
public final class ScrollerSmoke {
 public static void main(String[] args){
  android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return android.content.res.Resources.getSystem();}public android.content.pm.ApplicationInfo getApplicationInfo(){android.content.pm.ApplicationInfo info=new android.content.pm.ApplicationInfo();info.targetSdkVersion=27;return info;}};
  org.shiraz.fixture.ScrollerFixture.immediate(context);
  android.os.Looper.prepareMainLooper();final android.view.Choreographer frames=android.view.Choreographer.getInstance();
  try{org.shiraz.fixture.ScrollerFixture.timed(context,new org.shiraz.fixture.ScrollerFixture.Clock(){public void at(long millis,Runnable action){frames.shirazDoFrame(millis*1000000,action);}});}finally{android.os.Looper.shirazShutdownMain();}
 }
}
