// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.ViewConfiguration;
public final class ViewConfigurationFixture {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void check(Context context){
  android.content.res.Configuration config=new android.content.res.Configuration();config.densityDpi=160;config.screenLayout=2;
  Context base=context.createConfigurationContext(config);ViewConfiguration first=ViewConfiguration.get(base);
  check(first.getScaledTouchSlop()==8&&first.getScaledPagingTouchSlop()==16,"Base gesture slop");
  check(first.getScaledMinimumFlingVelocity()==50&&first.getScaledMaximumFlingVelocity()==8000,"Fling bounds");
  DisplayMetrics metrics=base.getResources().getDisplayMetrics();check(first.getScaledMaximumDrawingCacheSize()==metrics.widthPixels*metrics.heightPixels*4,"Client surface bytes");
  config.densityDpi=208;ViewConfiguration scaled=ViewConfiguration.get(base.createConfigurationContext(config));
  check(scaled.getScaledTouchSlop()==10&&scaled.getScaledPagingTouchSlop()==20,"Rounded paging slop");
  check(scaled.getScaledHorizontalScrollFactor()==83&&scaled.getScaledVerticalScrollFactor()==83,"Wheel scale");
  check(first.getScaledTouchSlop()==8,"Snapshot mutated");
  config.densityDpi=320;config.screenLayout=4;ViewConfiguration large=ViewConfiguration.get(base.createConfigurationContext(config));
  check(large.getScaledTouchSlop()==16&&large.getScaledDoubleTapSlop()==300&&large.getScaledWindowTouchSlop()==48,"Large layout scaling");
  check(large.getScaledEdgeSlop()==36&&large.getScaledOverflingDistance()==18,"Large edge scaling");
  check(ViewConfiguration.getTapTimeout()<ViewConfiguration.getLongPressTimeout()&&ViewConfiguration.getDoubleTapTimeout()==300,"Gesture timing");
  check(!first.hasPermanentMenuKey()&&ViewConfiguration.getScrollFriction()==.015f,"Host policy");
  System.out.println("VIEW_CONFIGURATION_DENSITY_RESIZE_OK");
 }
}
