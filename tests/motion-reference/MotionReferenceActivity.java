// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.motionreference;
import android.app.Activity;
import android.os.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
/** Self-running, permission-free probe for identical-APK Android/Shiraz motion comparison. */
public class MotionReferenceActivity extends Activity {
 private final Handler handler=new Handler();private View target;
 protected void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(1024,1024);target=new View(this){protected void onDraw(Canvas canvas){Paint p=new Paint();p.setColor(Color.BLACK);p.setTextSize(28);canvas.drawText("Native Android ripple reference",24,80,p);}};
  target.setBackground(new RippleDrawable(ColorStateList.valueOf(0x660066ff),new ColorDrawable(0xffeeeeee),new ColorDrawable(Color.WHITE)));target.setClickable(true);setContentView(target);
  handler.postDelayed(new Runnable(){public void run(){touch(0);}},2000);
  handler.postDelayed(new Runnable(){public void run(){touch(1);}},2040);
  handler.postDelayed(new Runnable(){public void run(){touch(0);}},3000);
  handler.postDelayed(new Runnable(){public void run(){touch(1);}},3700);
  handler.postDelayed(new Runnable(){public void run(){startActivity(new Intent(MotionReferenceActivity.this,Next.class));}},4500);
 }
 private long down;
 private void touch(int action){long now=SystemClock.uptimeMillis();if(action==0)down=now;MotionEvent event=MotionEvent.obtain(down,now,action,160,300,0);try{target.dispatchTouchEvent(event);}finally{event.recycle();}}
 protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
 public static class Next extends Activity {
  private final Handler handler=new Handler();
  protected void onCreate(Bundle state){super.onCreate(state);getWindow().setFlags(1024,1024);setContentView(new View(this){protected void onDraw(Canvas c){c.drawColor(0xff3366cc);Paint p=new Paint();p.setColor(Color.WHITE);p.setTextSize(28);c.drawText("Page two: Back follows",24,80,p);}});handler.postDelayed(new Runnable(){public void run(){finish();}},2200);}
  protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
 }
}
