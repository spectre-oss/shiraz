// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class BitmapOpacitySmoke {
 private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  Bitmap source=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888,false);
  check(!source.hasAlpha()&&source.getPixel(0,0)==Color.BLACK,"opaque allocation");
  source.setPixel(0,0,0x12345678);check(source.getPixel(0,0)==0xff345678,"opaque write");
  Paint clear=new Paint();clear.setColor(Color.TRANSPARENT);clear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));new Canvas(source).drawRect(0,0,2,2,clear);
  Bitmap destination=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);destination.eraseColor(Color.RED);
  new Canvas(destination).drawBitmap(source,0,0,null);check(destination.getPixel(0,0)==Color.BLACK,"opaque source composition");
  check(!source.copy(Bitmap.Config.ARGB_8888,true).hasAlpha(),"opaque copy metadata");
  source.setHasAlpha(true);source.eraseColor(0x12345678);check(source.hasAlpha()&&source.getPixel(0,0)==0x12345678,"restore alpha");
  source.setHasAlpha(false);check(source.getPixel(0,0)==0xff345678,"opaque transition");
  System.out.println("BITMAP_OPACITY_OK");
 }
}
