// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
/** Component timing only: no application startup or input-latency claim. */
public final class FillBenchmark {
 public static void main(String[] args){
  Bitmap b=Bitmap.createBitmap(720,1280,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0x80765432);
  for(int warm=0;warm<2;warm++){c.drawColor(0xff123456);c.drawRect(.25f,.5f,719.75f,1279.5f,p);}
  long[] samples=new long[5];for(int i=0;i<samples.length;i++){long start=System.nanoTime();c.drawColor(0xff123456);c.drawRect(.25f,.5f,719.75f,1279.5f,p);samples[i]=System.nanoTime()-start;}
  if(b.getPixel(360,640)!=0xff444444)throw new AssertionError("benchmark fill result");
  java.util.Arrays.sort(samples);System.out.println("FILL_PAIR_MEDIAN_NS="+samples[2]);System.out.println("FILL_CENTER="+Integer.toHexString(b.getPixel(360,640)));b.recycle();
 }
}
