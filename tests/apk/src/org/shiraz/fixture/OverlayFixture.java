// SPDX-License-Identifier: GPL-3.0-only
package org.shiraz.fixture;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
public final class OverlayFixture {
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 private static final class Host extends View {
  int invalidations,cancelled,scheduled;
  Host(Context c){super(c);}
  public void invalidate(){invalidations++;super.invalidate();}
  public void scheduleDrawable(Drawable d,Runnable r,long when){check(verifyDrawable(d),"Overlay callback ownership");scheduled++;}
  public void unscheduleDrawable(Drawable d){cancelled++;}
 }
 private static int pixel(View v,int x){Bitmap b=Bitmap.createBitmap(6,6,Bitmap.Config.ARGB_8888);try{v.draw(new Canvas(b));return b.getPixel(x,2);}finally{b.recycle();}}
 public static void check(Context context){
  Host host=new Host(context);host.layout(0,0,4,4);host.setBackgroundColor(Color.RED);ViewOverlay overlay=host.getOverlay();check(overlay==host.getOverlay(),"Stable overlay identity");
  ColorDrawable blue=new ColorDrawable(Color.BLUE);blue.setBounds(1,1,8,8);overlay.add(blue);overlay.add(blue);check(pixel(host,2)==Color.BLUE&&pixel(host,5)==0,"Overlay draws and clips to host");
  host.scrollTo(2,3);check(pixel(host,2)==Color.BLUE,"Overlay coordinates ignore content scroll");
  ColorDrawable green=new ColorDrawable(Color.GREEN);green.setBounds(0,0,3,3);overlay.add(green);check(pixel(host,2)==Color.GREEN,"Overlay insertion order");
  host.setForeground(new ColorDrawable(Color.WHITE));check(pixel(host,2)==Color.WHITE,"Foreground above overlay");host.setForeground(null);
  int before=host.invalidations;green.setColor(Color.YELLOW);check(host.invalidations>before,"Overlay invalidation reaches host");green.scheduleSelf(new Runnable(){public void run(){}},10);check(host.scheduled==1,"Overlay scheduling reaches host");
  int cancelledBefore=host.cancelled;overlay.remove(green);check(green.getCallback()==null&&host.cancelled==cancelledBefore+1&&pixel(host,2)==Color.BLUE,"Removal detaches and cancels");
  Host other=new Host(context);other.layout(0,0,4,4);other.getOverlay().add(blue);check(pixel(host,2)==Color.RED&&pixel(other,2)==Color.BLUE,"Transfer between overlays");other.getOverlay().clear();check(blue.getCallback()==null&&pixel(other,2)==0,"Clear detaches drawables");
  host.layout(0,0,6,6);overlay.add(blue);check(pixel(host,5)==Color.BLUE,"Host resize updates clipping");overlay.clear();
  System.out.println("VIEW_OVERLAY_OK");
 }
}
