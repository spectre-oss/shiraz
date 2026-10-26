// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.view.*;
public class ResizeActivity extends Activity {
 static{System.loadLibrary("shiraz-test-input");}
 static native void inject(int width,int height,int x,int y);
 private static int phase,creates,saves,restores,changes,clicks;private static ViewTreeObserver retired;
 private int value=93;private View view;private ViewTreeObserver observer;
 public static final class Handled extends ResizeActivity { }
 public static final class Requested extends ResizeActivity { }
 protected void onCreate(Bundle state){super.onCreate(state);ActivityIdentityFixture.check(this);FragmentFixture.install(this);if(retired!=null&&retired.isAlive())throw new AssertionError("old observer remains alive");creates++;TitleFixture.initial(this);setTitle("Resize instance "+creates);if(state!=null)value=state.getInt("value");if(value!=93+clicks)throw new AssertionError("resize saved value");
  view=new View(this){
   protected void onDraw(Canvas canvas){TitleFixture.nativeTitle(getTitle().toString());
    boolean landscape=getWidth()>getHeight();Configuration c=getResources().getConfiguration();
    if(c.orientation!=(landscape?2:1)||getResources().getDisplayMetrics().widthPixels!=getWidth())throw new AssertionError("resize resource/layout mismatch");
    canvas.drawColor(landscape?0xffff0000:0xff0000ff);
    if(phase==0){phase=1;if(ResizeActivity.this instanceof Requested)setRequestedOrientation(0);else inject(160,96,-1,-1);}
    else if(phase==1){if(getWidth()!=160||getHeight()!=96)throw new AssertionError("landscape layout");phase=2;inject(0,0,150,80);invalidate();}
    else if(phase==2){if(clicks!=1)throw new AssertionError("landscape input missing");phase=3;if(ResizeActivity.this instanceof Requested)setRequestedOrientation(1);else inject(96,160,-1,-1);}
    else if(phase==3){if(getWidth()!=96||getHeight()!=160)throw new AssertionError("portrait layout");phase=4;inject(0,0,86,150);invalidate();}
    else if(phase==4){if(clicks!=2)throw new AssertionError("portrait input missing");phase=5;finish();}
   }
   public boolean onTouchEvent(MotionEvent event){
    if(event.getAction()==MotionEvent.ACTION_UP){int x=getWidth()>getHeight()?150:86,y=getWidth()>getHeight()?80:150;if(Math.abs(event.getX()-x)>.01||Math.abs(event.getY()-y)>.01)throw new AssertionError("resized pointer mapping");clicks++;value++;invalidate();}return true;
   }
  };setContentView(view);AttachFixture.observe(view);InsetsFixture.observe(view);
  view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){if(isDestroyed())throw new AssertionError("destroyed activity observer retained");observer=view.getViewTreeObserver();}});
 }
 protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("value",value);saves++;}
 protected void onRestoreInstanceState(Bundle state){super.onRestoreInstanceState(state);restores++;}
 public void onConfigurationChanged(Configuration configuration){super.onConfigurationChanged(configuration);changes++;}
 protected void onDestroy(){super.onDestroy();if(isChangingConfigurations())retired=observer;if(!isChangingConfigurations()){
  if(phase!=5){System.out.println("SHIRAZ_RESIZE_INCOMPLETE "+phase);return;}
  boolean handled=this instanceof Handled;
  if(phase!=5||clicks!=2||creates!=(handled?1:3)||changes!=(handled?2:0)||saves!=(handled?0:2)||restores!=(handled?0:2))throw new AssertionError("resize lifecycle counts "+phase+":"+creates+":"+changes+":"+saves+":"+restores);
  if(this instanceof Requested&&getRequestedOrientation()!=1)throw new AssertionError("orientation request not retained");
  System.out.println("SHIRAZ_RESIZE_ACTIVITY_OK "+(this instanceof Requested?"requested":handled?"handled":"recreated"));
 }}
}
