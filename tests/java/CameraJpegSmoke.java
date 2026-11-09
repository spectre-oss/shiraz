// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import android.hardware.Camera;
/** Synthetic raster encoding test; this is not physical camera acceptance. */
public final class CameraJpegSmoke {
 public static void main(String[] args)throws Exception{
  int[] frame=new int[32*16+2];frame[0]=32;frame[1]=16;for(int y=0;y<16;y++)for(int x=0;x<32;x++)frame[2+y*32+x]=x<16?Color.RED:Color.BLUE;
  java.lang.reflect.Method encode=Camera.class.getDeclaredMethod("encodePicture",int[].class,int.class,int.class);encode.setAccessible(true);byte[] jpeg=(byte[])encode.invoke(null,frame,95,90);
  if(jpeg.length<100||(jpeg[0]&255)!=255||(jpeg[1]&255)!=216)throw new AssertionError("JPEG container");
  Bitmap decoded=BitmapFactory.decodeByteArray(jpeg,0,jpeg.length);if(decoded.getWidth()!=16||decoded.getHeight()!=32)throw new AssertionError("JPEG rotation dimensions");
  int top=decoded.getPixel(8,4),bottom=decoded.getPixel(8,27);if(Color.red(top)<200||Color.blue(top)>40||Color.blue(bottom)<200||Color.red(bottom)>40)throw new AssertionError("JPEG rotation pixels");
  if(frame[2]!=Color.RED||frame[2+31]!=Color.BLUE)throw new AssertionError("Snapshot source mutated");decoded.recycle();
  System.out.println("CAMERA_JPEG_ROTATION_OK");
 }
}
