// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class CanvasColorModeSmoke {
 static void pixel(Bitmap b,int x,int y,int expected){int actual=b.getPixel(x,y);if(actual!=expected)throw new AssertionError(Integer.toHexString(actual)+" != "+Integer.toHexString(expected));}
 public static void main(String[] args){
  Bitmap b=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);
  c.drawColor(0xff123456);c.save();c.clipRect(2,2,6,6);c.translate(100,100);
  c.drawColor(0xffff0000,PorterDuff.Mode.CLEAR);pixel(b,3,3,0);pixel(b,1,1,0xff123456);
  c.drawColor(0x80402010,PorterDuff.Mode.SRC);pixel(b,3,3,0x80402010);
  c.drawColor(0xffabcdef,PorterDuff.Mode.DST);pixel(b,3,3,0x80402010);c.restore();
  c.drawColor(0xff0000ff,PorterDuff.Mode.SRC);c.drawColor(0x80ff0000,PorterDuff.Mode.SRC_OVER);pixel(b,0,0,0xff80007f);
  c.save();Path p=new Path();p.addCircle(4,4,2,Path.Direction.CW);c.clipPath(p);c.drawColor(0,PorterDuff.Mode.CLEAR);pixel(b,4,4,0);pixel(b,0,0,0xff80007f);c.restore();
  try{c.drawColor(0,(PorterDuff.Mode)null);throw new AssertionError("null mode accepted");}catch(NullPointerException expected){}
  b.recycle();System.out.println("CANVAS_COLOR_MODE_OK");
 }
}
