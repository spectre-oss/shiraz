// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.content.Context;
import android.util.DisplayMetrics;
/** Host gesture policy using API 27 baseline values. No Android settings service or device overlays. */
public class ViewConfiguration {
 private final float density,sizeDensity;
 private final int drawingCache;
 public ViewConfiguration(){density=1;sizeDensity=1;drawingCache=480*800*4;}
 private ViewConfiguration(Context context){
  DisplayMetrics metrics=context.getResources().getDisplayMetrics();
  if(!Float.isFinite(metrics.density)||metrics.density<=0||metrics.density>64)throw new IllegalArgumentException("Display density");
  density=metrics.density;sizeDensity=density*((context.getResources().getConfiguration().screenLayout&15)>=4?1.5f:1);
  long pixels=(long)Math.max(0,metrics.widthPixels)*Math.max(0,metrics.heightPixels);
  drawingCache=(int)Math.min(Integer.MAX_VALUE,pixels>Integer.MAX_VALUE/4?Integer.MAX_VALUE:pixels*4);
 }
 /** A fresh snapshot avoids stale thresholds after density or window configuration changes. */
 public static ViewConfiguration get(Context context){if(context==null)throw new NullPointerException("context");return new ViewConfiguration(context);}
 private int dp(int value){return Math.round(value*density);}
 private int sized(int value){return Math.round(value*sizeDensity);}
 public static int getScrollBarSize(){return 4;}
 public static int getScrollBarFadeDuration(){return 250;}
 public static int getScrollDefaultDelay(){return 300;}
 public static int getFadingEdgeLength(){return 12;}
 public static int getPressedStateDuration(){return 64;}
 public static int getLongPressTimeout(){return 500;}
 public static int getMultiPressTimeout(){return 300;}
 public static int getKeyRepeatTimeout(){return 500;}
 public static int getKeyRepeatDelay(){return 50;}
 public static int getTapTimeout(){return 100;}
 public static int getJumpTapTimeout(){return 500;}
 public static int getDoubleTapTimeout(){return 300;}
 public static int getDoubleTapMinTime(){return 40;}
 public static int getHoverTapTimeout(){return 150;}
 public static int getHoverTapSlop(){return 20;}
 public static int getEdgeSlop(){return 12;}
 public static int getTouchSlop(){return 8;}
 public static int getDoubleTapSlop(){return 100;}
 public static int getWindowTouchSlop(){return 16;}
 public static int getMinimumFlingVelocity(){return 50;}
 public static int getMaximumFlingVelocity(){return 8000;}
 public static int getMaximumDrawingCacheSize(){return 1536000;}
 public static int getLongPressTooltipHideTimeout(){return 1500;}
 public static int getHoverTooltipShowTimeout(){return 500;}
 public static int getHoverTooltipHideTimeout(){return 15000;}
 public static int getHoverTooltipHideShortTimeout(){return 3000;}
 public static long getZoomControlsTimeout(){return 3000;}
 public static long getGlobalActionKeyTimeout(){return 500;}
 public static long getSendRecurringAccessibilityEventsInterval(){return 100;}
 public static long getDefaultActionModeHideDuration(){return 2000;}
 public int getScaledScrollBarSize(){return dp(4);}
 public int getScaledMinScrollbarTouchTarget(){return dp(48);}
 public int getScaledTouchSlop(){return dp(8);}
 public int getScaledDoubleTapTouchSlop(){return dp(8);}
 public int getScaledPagingTouchSlop(){return 2*getScaledTouchSlop();}
 public int getScaledMinimumFlingVelocity(){return dp(50);}
 public int getScaledMaximumFlingVelocity(){return dp(8000);}
 public int getScaledEdgeSlop(){return sized(12);}
 public int getScaledFadingEdgeLength(){return sized(12);}
 public int getScaledDoubleTapSlop(){return sized(100);}
 public int getScaledWindowTouchSlop(){return sized(16);}
 public int getScaledOverscrollDistance(){return sized(0);}
 public int getScaledOverflingDistance(){return sized(6);}
 public int getScaledMaximumDrawingCacheSize(){return drawingCache;}
 public float getScaledHorizontalScrollFactor(){return dp(64);}
 public float getScaledVerticalScrollFactor(){return dp(64);}
 public int getScaledScrollFactor(){return dp(64);}
 public static float getScrollFriction(){return 0.015f;}
 public long getDeviceGlobalActionKeyTimeout(){return 500;}
 public long getAccessibilityShortcutKeyTimeout(){return 3000;}
 public boolean hasPermanentMenuKey(){return false;}
 public boolean isFadingMarqueeEnabled(){return false;}
}
