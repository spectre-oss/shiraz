// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Choreographer;
public final class AnimationFixture {
 public static void install(final View root){
  final Choreographer scheduler=Choreographer.getInstance();final int[] stage={0};final boolean[] observed={false};final long[] time={0},currentTime={0};
  final Choreographer.FrameCallback clock=new Choreographer.FrameCallback(){public void doFrame(long stamp){currentTime[0]=stamp;if(stage[0]<2)scheduler.postFrameCallback(this);}};scheduler.postFrameCallback(clock);
  final Runnable canceled=new Runnable(){public void run(){throw new AssertionError("Canceled view animation ran");}};
  root.postOnAnimation(canceled);root.removeCallbacks(canceled);
  final Runnable second=new Runnable(){public void run(){if(stage[0]!=1||!observed[0]||currentTime[0]<=time[0])throw new AssertionError("Animation repost did not wait for a later frame/traversal");stage[0]=2;root.invalidate();}};
  final Runnable first=new Runnable(){public void run(){if(!root.isAttachedToWindow())throw new AssertionError("Unattached animation callback");stage[0]=1;time[0]=currentTime[0];root.postOnAnimation(second);root.invalidate();}};
  root.postOnAnimation(new Runnable(){public void run(){if(!root.isAttachedToWindow())throw new AssertionError("Pending action delivered before attach");root.postOnAnimation(first);}});
  root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){boolean reported;public boolean onPreDraw(){if(stage[0]==1){if(time[0]!=currentTime[0])throw new AssertionError("Animation and traversal frame time differ");observed[0]=true;}if(stage[0]==2&&!reported){reported=true;System.out.println("VIEW_ANIMATION_TRAVERSAL_OK");}return true;}});
  root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){view.postOnAnimationDelayed(canceled,Long.MAX_VALUE);}public void onViewDetachedFromWindow(View view){scheduler.removeFrameCallback(clock);view.removeCallbacks(canceled);view.removeCallbacks(first);view.removeCallbacks(second);}});
 }
}
