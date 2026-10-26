// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.os.Bundle;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.view.*;
/** A real APK verifies resource selection and pointer delivery through small resizes. */
public final class BucketResizeActivity extends Activity {
 private static int phase,creates,changes,saves,restores,clicks;
 private int value=93;
 private void check(boolean condition,String message){if(!condition)throw new AssertionError(message+" phase="+phase+" creates="+creates+" changes="+changes);}
 protected void onCreate(Bundle state){super.onCreate(state);creates++;if(state!=null)value=state.getInt("value");check(value==93+clicks,"Saved instance value");
  setContentView(new View(this){
   protected void onDraw(Canvas canvas){
    check(getResources().getDisplayMetrics().widthPixels==getWidth()&&getResources().getConfiguration().screenWidthDp==getWidth(),"Live resources match layout");canvas.drawColor(0xff0000ff);
    String bucket=getResources().getString(getResources().getIdentifier("resize_bucket","string","org.shiraz.fixture"));
    check(bucket.equals(getWidth()>=120?"wide":"base"),"Qualified resources match resized width");
    switch(phase){
     case 0:phase=1;ResizeActivity.inject(100,164,-1,-1);break;
     case 1:check(getWidth()==100&&creates==1&&changes==1,"First in-bucket relayout");phase=2;ResizeActivity.inject(0,0,90,150);invalidate();break;
     case 2:check(clicks==1,"Click after in-bucket resize");phase=3;ResizeActivity.inject(120,164,-1,-1);break;
     case 3:check(getWidth()==120&&creates==2&&changes==1,"Upward qualifier crossing");phase=4;ResizeActivity.inject(116,164,-1,-1);break;
     case 4:check(getWidth()==116&&creates==3&&changes==1,"Downward qualifier crossing");phase=5;ResizeActivity.inject(118,166,-1,-1);break;
     case 5:check(getWidth()==118&&creates==3&&changes==2,"Second in-bucket relayout");phase=6;ResizeActivity.inject(119,167,-1,-1);break;
     case 6:check(getWidth()==119&&creates==3&&changes==3,"Repeated in-bucket relayout");phase=7;ResizeActivity.inject(0,0,109,155);invalidate();break;
     case 7:check(clicks==2,"Click after repeated resizes");phase=8;finish();break;
    }
   }
   public boolean onTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_UP){check(Math.abs(event.getX()-(clicks==0?90:109))<.01&&Math.abs(event.getY()-(clicks==0?150:155))<.01,"Pointer uses current coordinates");clicks++;value++;invalidate();}return true;}
  });
 }
 public void onConfigurationChanged(Configuration config){super.onConfigurationChanged(config);changes++;check(config.screenWidthDp==getResources().getConfiguration().screenWidthDp,"Resource update before configuration callback");}
 protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("value",value);saves++;}
 protected void onRestoreInstanceState(Bundle state){super.onRestoreInstanceState(state);restores++;}
 protected void onDestroy(){super.onDestroy();if(!isChangingConfigurations()){check(phase==8&&creates==3&&changes==3&&saves==2&&restores==2&&clicks==2,"Lifecycle summary");System.out.println("BUCKET_RESIZE_RELAYOUT_RECREATION_AND_INPUT_OK");}}
}
