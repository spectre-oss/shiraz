// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import java.io.*;
public final class WebpEncoderSmoke {
 private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  Bitmap bitmap=Bitmap.createBitmap(47,33,Bitmap.Config.ARGB_8888);
  for(int y=0;y<33;y++)for(int x=0;x<47;x++)bitmap.setPixel(x,y,((x*13+y*7)&255)<<24|((x*5)&255)<<16|((y*7)&255)<<8|0x35);
  for(Bitmap.CompressFormat format:new Bitmap.CompressFormat[]{Bitmap.CompressFormat.WEBP,Bitmap.CompressFormat.WEBP_LOSSY,Bitmap.CompressFormat.WEBP_LOSSLESS}){
   final boolean[] closed={false};ByteArrayOutputStream output=new ByteArrayOutputStream(){public void close(){closed[0]=true;}};
   check(bitmap.compress(format,80,output)&&!closed[0],"Encoding and stream ownership");
   byte[] bytes=output.toByteArray();check(bytes.length>12&&bytes[0]=='R'&&bytes[8]=='W',"Real WebP container");
   BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
   check(BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds)==null&&bounds.outWidth==47&&bounds.outHeight==33&&"image/webp".equals(bounds.outMimeType),"Encoded dimensions");
   Bitmap decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length);check(decoded!=null,"Decode output");
   for(int y=0;y<33;y++)for(int x=0;x<47;x++){
    int before=bitmap.getPixel(x,y),after=decoded.getPixel(x,y);check((before>>>24)==(after>>>24),"Alpha preservation");
    if(format==Bitmap.CompressFormat.WEBP_LOSSLESS)check(before==after,"Lossless pixels including transparent RGB");
   }decoded.recycle();
   check(!bitmap.compress(format,50,new OutputStream(){public void write(int b)throws IOException{throw new IOException("full");}}),"Output failure");
  }
  BitmapFactory.Options bad=new BitmapFactory.Options();bad.outWidth=123;bad.outHeight=456;bad.outMimeType="old";
  byte[] unknown="<svg>not a raster image</svg>".getBytes("UTF-8");
  check(BitmapFactory.decodeByteArray(unknown,0,unknown.length,bad)==null&&bad.outWidth==-1&&bad.outHeight==-1&&bad.outMimeType==null,"Unknown format lets caller try fallback");
  try{shiraz.runtime.NativeImage.webpEncode(new int[1],2,2,50,false);throw new AssertionError("Bad pixel count");}catch(IllegalArgumentException expected){}
  try{shiraz.runtime.NativeImage.webpEncode(new int[1],1,1,101,false);throw new AssertionError("Bad quality");}catch(IllegalArgumentException expected){}
  bitmap.recycle();System.out.println("WEBP_ENCODER_ROUNDTRIP_OK");
 }
}
