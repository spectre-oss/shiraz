// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
public final class WebpSmoke {
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
  byte[] bytes=java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(args[0]));BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
  check(BitmapFactory.decodeByteArray(bytes,0,bytes.length,options)==null&&options.outWidth==2&&options.outHeight==1&&"image/webp".equals(options.outMimeType),"WebP bounds/mime");options.inJustDecodeBounds=false;
  Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);check(bitmap!=null&&bitmap.getPixel(0,0)==0xffff0000&&bitmap.getPixel(1,0)==0x8000ff00,"WebP RGBA pixels");
  byte[] padded=new byte[bytes.length+4];System.arraycopy(bytes,0,padded,2,bytes.length);check(BitmapFactory.decodeByteArray(padded,2,bytes.length).getPixel(0,0)==0xffff0000,"WebP byte slice");
  check(BitmapFactory.decodeByteArray(bytes,0,bytes.length/2)==null,"truncated image");try{BitmapFactory.decodeByteArray(bytes,1,Integer.MAX_VALUE);throw new AssertionError("overflow");}catch(ArrayIndexOutOfBoundsException expected){}
  System.out.println("WEBP_DECODE_OK");
 }
}
