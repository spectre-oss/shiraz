// SPDX-License-Identifier: GPL-3.0-only
package shiraz;
import android.graphics.*;
import shiraz.runtime.*;
/** Measures UI-thread composition separately from paced presentation. No snapshots/readbacks. */
public final class GpuAnimationBenchmark {
 public static void main(String[] args){
  final boolean controls=args.length>0 && args[0].equals("controls");
  final int warmup=120, measured=240;
  final long[] samples=new long[measured];final Bitmap frame=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);
  final Bitmap page=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);
  final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);Canvas background=new Canvas(page);background.drawColor(0xfffafafa);
  for(int i=0;i<18;i++){p.setColor(0xff335588+i*1234);background.drawRoundRect(15,i*72+5,705,i*72+67,12,12,p);}
  long start=System.nanoTime();
  try(final NativeSurface surface=new NativeSurface(720,1280)){
   surface.presentDispatched(warmup+measured,new NativeSurface.FrameCallback(){int index;
    public boolean onFrame(){long begin=System.nanoTime();frame.eraseColor(Color.BLACK);Canvas c=Canvas.shirazScreen(frame);p.setAlpha(255);c.drawBitmap(page,0,0,p);int saved=c.save();c.translate(72*(float)Math.sin(index*.08),0);p.setAlpha(128);c.drawBitmap(page,0,0,p);c.restoreToCount(saved);if(controls){p.setAlpha(255);for(int row=0;row<18;row++){float y=row*70+5;c.save();c.clipRect(0,y,720,y+68);p.setColor(0xffeeeeee);c.drawRoundRect(12,y,708,y+64,12,12,p);p.setColor(0xff336699);c.drawCircle(44,y+32,22,p);for(int line=0;line<6;line++)c.drawRect(80+line*95,y+20,160+line*95,y+44,p);c.restore();}}frame.shirazPostToSurface(surface);if(index>=warmup)samples[index-warmup]=System.nanoTime()-begin;index++;return true;}
    public void onKey(int a,int b,int c,int d,int e,int f,long g,long h){}public void onText(String s,long t){}public void onPointer(int a,float x,float y,int s,long t){}
   });
  }
  java.util.Arrays.sort(samples);System.out.println("ANIMATION_BENCH median_ms="+samples[measured/2]/1e6+" p95_ms="+samples[measured*95/100]/1e6+" paced_total_ms="+(System.nanoTime()-start)/1e6);
  frame.recycle();page.recycle();
 }
}
