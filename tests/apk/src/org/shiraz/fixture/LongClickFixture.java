// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.view.*;import android.content.Context;
public final class LongClickFixture {
 private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
 private static class Timed extends View {Runnable pending;long delay;Timed(Context c){super(c);layout(0,0,40,40);}public boolean postDelayed(Runnable r,long d){pending=r;delay=d;return true;}public boolean removeCallbacks(Runnable r){if(pending==r)pending=null;return true;}}
 private static void event(View v,int action,float x){MotionEvent e=MotionEvent.obtain(0,0,action,x,10,0);try{v.dispatchTouchEvent(e);}finally{e.recycle();}}
 public static void check(Context c){Timed v=new Timed(c);final int[] longs={0},clicks={0};v.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View view){longs[0]++;return true;}});v.setOnClickListener(new View.OnClickListener(){public void onClick(View view){clicks[0]++;}});
  event(v,0,10);check(v.pending!=null&&v.delay==ViewConfiguration.getLongPressTimeout(),"Long press timeout");v.pending.run();event(v,1,10);check(longs[0]==1&&clicks[0]==0,"Handled long press suppresses click");
  event(v,0,10);Runnable stale=v.pending;event(v,3,10);stale.run();check(longs[0]==1&&v.pending==null,"Cancellation ignores stale callback");
  event(v,0,10);stale=v.pending;event(v,2,80);stale.run();event(v,1,10);check(longs[0]==1&&clicks[0]==0,"Moving outside cancels gesture");
  event(v,0,10);stale=v.pending;v.setEnabled(false);stale.run();check(longs[0]==1,"Disabled cancels long press");v.setEnabled(true);
  v.setOnLongClickListener(new View.OnLongClickListener(){public boolean onLongClick(View view){longs[0]++;return false;}});event(v,0,10);v.pending.run();event(v,1,10);check(longs[0]==2&&clicks[0]==1,"Unhandled long press permits click");
  event(v,0,10);stale=v.pending;v.setLongClickable(false);stale.run();event(v,1,10);check(longs[0]==2&&clicks[0]==2,"Long-click disable preserves click");
  check(!v.performLongClick(1,2)&&longs[0]==3,"Explicit long click dispatch");System.out.println("LONG_CLICK_DISPATCH_OK");
 }
}
