// SPDX-License-Identifier: GPL-3.0-only
public final class RippleSmoke {
 public static void main(String[] args){
  try{shiraz.runtime.NativePath.ripple(new int[4],2,2,0,0,1,1,3,Float.NaN,0,2,2,0xff00ff00,0,1,1,3);throw new AssertionError("Nonfinite ripple accepted");}catch(IllegalArgumentException expected){}
  try{shiraz.runtime.NativePath.ripple(new int[3],2,2,0,0,1,1,3,.2f,0,2,2,0xff00ff00,0,1,1,3);throw new AssertionError("Short ripple buffer accepted");}catch(IllegalArgumentException expected){}
final android.content.res.Resources resources=android.content.res.Resources.shirazOpen(args[0],40,40);android.content.Context context=new android.content.ContextWrapper(null){public android.content.res.Resources getResources(){return resources;}public android.content.res.Resources.Theme getTheme(){return resources.newTheme();}};android.os.Looper.prepareMainLooper();try{org.shiraz.fixture.RippleFixture.immediate(context);final android.view.Choreographer frames=android.view.Choreographer.getInstance();org.shiraz.fixture.RippleFixture.timed(context,new org.shiraz.fixture.RippleFixture.Clock(){public void at(long time,Runnable action){frames.shirazDoFrame(time*1000000,action);}});}finally{android.os.Looper.shirazShutdownMain();resources.shirazClose();}}
}
