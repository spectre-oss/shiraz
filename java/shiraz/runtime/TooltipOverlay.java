// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.View;
import android.widget.TextView;
/** One anchored, noninteractive tooltip in the current host window. */
public final class TooltipOverlay {
 private static View anchor;
 private static TextView label;
 private static long deadline,revision;
 private TooltipOverlay(){}
 public static boolean show(View view,CharSequence text){
  if(!view.isAttachedToWindow()||!view.isShown()||!view.isEnabled()||text==null||text.length()==0)return false;
  if(android.os.Looper.myLooper()!=android.os.Looper.getMainLooper())throw new IllegalStateException("Tooltip requires main Looper");
  TextView next=new TextView(view.getContext());next.setText(text);next.setTextSize(14);next.setTextColor(0xffffffff);next.setBackgroundColor(0xee303030);next.setPadding(8,4,8,4);next.setMaxLines(3);
  anchor=view;label=next;deadline=SystemClock.uptimeMillis()+2500;revision++;view.invalidate();return true;
 }
 public static void cancel(View view){if(anchor==view)clear();}
 public static void clear(){if(anchor!=null){anchor.invalidate();anchor=null;label=null;revision++;}}
 public static long revision(){if(anchor!=null&&(!anchor.isAttachedToWindow()||!anchor.isShown()||!anchor.isEnabled()||SystemClock.uptimeMillis()>=deadline))clear();return revision;}
 public static void draw(Canvas canvas,int width,int height){
  revision();if(anchor==null||width<=0||height<=0)return;
  label.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.AT_MOST));
  int w=label.getMeasuredWidth(),h=label.getMeasuredHeight();int[] pos=new int[2];anchor.getLocationInWindow(pos);
  int x=Math.max(0,Math.min(width-w,pos[0]+anchor.getWidth()/2-w/2));int y=pos[1]-h-8;if(y<0)y=pos[1]+anchor.getHeight()+8;y=Math.max(0,Math.min(height-h,y));
  label.layout(0,0,w,h);int save=canvas.save();try{canvas.clipRect(0,0,width,height);canvas.translate(x,y);label.shirazDrawWithAlpha(canvas);}finally{canvas.restoreToCount(save);}
 }
}
