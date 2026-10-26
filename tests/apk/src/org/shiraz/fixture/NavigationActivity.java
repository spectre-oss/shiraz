// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Canvas;
import android.view.*;
public class NavigationActivity extends Activity {
 private static int phase,creates,childDestroys;
 private static NavigationActivity original;
 private int value=17;
 public static class Handled extends NavigationActivity {}
 protected void onCreate(Bundle state){super.onCreate(state);creates++;if(original==null)original=this;if(state!=null)value=state.getInt("value");setContentView(new View(this){
  protected void onDraw(Canvas canvas){canvas.drawColor(0xff0000ff);
   if(phase==0){phase=1;ResizeActivity.inject(0,0,20,20);}
   else if(phase==3){if(value!=18||childDestroys!=2||getWidth()!=160||getHeight()!=96)throw new AssertionError("returned state/configuration "+value+":"+childDestroys+":"+getWidth());boolean handled=NavigationActivity.this instanceof Handled;if(creates!=(handled?1:2)||(NavigationActivity.this==original)!=handled)throw new AssertionError("covered recreation policy");phase=4;ResizeActivity.inject(0,0,140,70);invalidate();}
   else if(phase==5){if(value!=19)throw new AssertionError("return input");System.out.println("NAVIGATION_RUNNER_OK");finish();}
  }
  public boolean onTouchEvent(MotionEvent event){if(event.getAction()==MotionEvent.ACTION_UP){value++;if(phase==1){phase=2;startActivity(new Intent(NavigationActivity.this,Child.class));}else if(phase==4){phase=5;invalidate();}}return true;}
 });}
 protected void onSaveInstanceState(Bundle out){super.onSaveInstanceState(out);out.putInt("value",value);}
 public static class Child extends Activity {
  protected void onCreate(Bundle state){super.onCreate(state);setContentView(new View(this){protected void onDraw(Canvas canvas){canvas.drawColor(0xffff0000);if(phase==2){phase=3;ResizeActivity.inject(160,96,-1,-1);}else if(phase==3){if(getWidth()!=160||getHeight()!=96)throw new AssertionError("child resize");finish();}}});}
  protected void onDestroy(){super.onDestroy();childDestroys++;}
 }
}
