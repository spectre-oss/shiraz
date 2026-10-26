// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
import java.io.*;
public final class BitmapRegionDecoder {
 private Bitmap bitmap;
 private boolean recycled;
 private BitmapRegionDecoder(Bitmap bitmap){this.bitmap=bitmap;}
 private static BitmapRegionDecoder fromBitmap(Bitmap bitmap)throws IOException{
  if(bitmap==null)throw new IOException("Image could not be decoded");
  return new BitmapRegionDecoder(bitmap);
 }
 public static BitmapRegionDecoder newInstance(byte[] data,int offset,int length,boolean isShareable)throws IOException{
  return fromBitmap(BitmapFactory.decodeByteArray(data,offset,length));
 }
 public static BitmapRegionDecoder newInstance(byte[] data,int offset,int length)throws IOException{
  return newInstance(data,offset,length,false);
 }
 public static BitmapRegionDecoder newInstance(FileDescriptor fd,boolean isShareable)throws IOException{
  if(fd==null)throw new NullPointerException("fd");
  return newInstance(new FileInputStream(fd),isShareable);
 }
 public static BitmapRegionDecoder newInstance(FileDescriptor fd)throws IOException{
  return newInstance(fd,false);
 }
 public static BitmapRegionDecoder newInstance(InputStream is,boolean isShareable)throws IOException{
  return fromBitmap(BitmapFactory.decodeStream(is));
 }
 public static BitmapRegionDecoder newInstance(InputStream is)throws IOException{
  return newInstance(is,false);
 }
 public static BitmapRegionDecoder newInstance(String pathName,boolean isShareable)throws IOException{
  return fromBitmap(BitmapFactory.decodeFile(pathName));
 }
 public static BitmapRegionDecoder newInstance(String pathName)throws IOException{
  return newInstance(pathName,false);
 }
 private void checkRecycled(String message){if(recycled)throw new IllegalStateException(message);}
 public synchronized Bitmap decodeRegion(Rect rect,BitmapFactory.Options options){
  checkRecycled("Cannot decode region on recycled BitmapRegionDecoder");
  if(rect==null)throw new NullPointerException("rect");
  int w=bitmap.getWidth(),h=bitmap.getHeight();
  if(rect.width()<=0||rect.height()<=0||rect.left>=w||rect.top>=h||rect.right<=0||rect.bottom<=0)
   throw new IllegalArgumentException("rectangle is outside the image");
  int left=Math.max(0,rect.left),top=Math.max(0,rect.top),right=Math.min(w,rect.right),bottom=Math.min(h,rect.bottom);
  int cw=right-left,ch=bottom-top;
  if(cw<=0||ch<=0)return null;
  int sample=options!=null&&options.inSampleSize>1?options.inSampleSize:1;
  int outW=Math.max(1,(cw+sample-1)/sample),outH=Math.max(1,(ch+sample-1)/sample);
  Bitmap out=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
  int[] row=new int[cw];
  for(int y=0;y<outH;y++){
   int sy=Math.min(bottom-1,top+y*sample);
   bitmap.getPixels(row,0,cw,left,sy,cw,1);
   for(int x=0;x<outW;x++){
    int sx=Math.min(cw-1,x*sample);
    out.setPixel(x,y,row[sx]);
   }
  }
  if(options!=null){options.outWidth=outW;options.outHeight=outH;options.outConfig=Bitmap.Config.ARGB_8888;}
  return out;
 }
 public synchronized int getWidth(){checkRecycled("Cannot call getWidth() on recycled BitmapRegionDecoder");return bitmap.getWidth();}
 public synchronized int getHeight(){checkRecycled("Cannot call getHeight() on recycled BitmapRegionDecoder");return bitmap.getHeight();}
 public synchronized void recycle(){if(!recycled){recycled=true;if(bitmap!=null){bitmap.recycle();bitmap=null;}}}
 public synchronized boolean isRecycled(){return recycled;}
}
