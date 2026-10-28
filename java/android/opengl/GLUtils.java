// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;
import android.graphics.Bitmap;
/** Bitmap uploads to the current GLES context. */
public final class GLUtils {
 static { System.loadLibrary("shiraz-gles1"); }
 private GLUtils() {}
 private static void check(Bitmap bitmap){if(bitmap==null||bitmap.isRecycled())throw new IllegalArgumentException("bitmap is null or recycled");}
 public static int getInternalFormat(Bitmap bitmap){check(bitmap);return bitmap.getConfig()==Bitmap.Config.ALPHA_8?0x1906:bitmap.getConfig()==Bitmap.Config.RGB_565?0x1907:0x1908;}
 public static int getType(Bitmap bitmap){check(bitmap);return bitmap.getConfig()==Bitmap.Config.RGB_565?0x8363:0x1401;}
 public static void texImage2D(int target,int level,Bitmap bitmap,int border){texImage2D(target,level,getInternalFormat(bitmap),bitmap,getType(bitmap),border);}
 public static void texImage2D(int target,int level,int format,Bitmap bitmap,int border){texImage2D(target,level,format,bitmap,getType(bitmap),border);}
 public static void texImage2D(int target,int level,int format,Bitmap bitmap,int type,int border){upload(target,level,0,0,format,type,border,bitmap,false);}
 public static void texSubImage2D(int target,int level,int x,int y,Bitmap bitmap){texSubImage2D(target,level,x,y,bitmap,getInternalFormat(bitmap),getType(bitmap));}
 public static void texSubImage2D(int target,int level,int x,int y,Bitmap bitmap,int format,int type){upload(target,level,x,y,format,type,0,bitmap,true);}
 private static void upload(int target,int level,int x,int y,int format,int type,int border,Bitmap bitmap,boolean sub){
  check(bitmap);if(format<0)format=getInternalFormat(bitmap);if(type<0)type=getType(bitmap);
  if(border!=0)throw new IllegalArgumentException("border must be zero");
  int width=bitmap.getWidth(),height=bitmap.getHeight();
  int[] pixels=new int[Math.multiplyExact(width,height)];bitmap.getPixels(pixels,0,width,0,0,width,height);
  uploadNative(target,level,x,y,width,height,format,type,pixels,bitmap.isPremultiplied(),sub);
 }
 private static native void uploadNative(int target,int level,int x,int y,int width,int height,int format,int type,int[] pixels,boolean premultiplied,boolean sub);
 public static String getEGLErrorString(int error){String[] names={"EGL_SUCCESS","EGL_NOT_INITIALIZED","EGL_BAD_ACCESS","EGL_BAD_ALLOC","EGL_BAD_ATTRIBUTE","EGL_BAD_CONFIG","EGL_BAD_CONTEXT","EGL_BAD_CURRENT_SURFACE","EGL_BAD_DISPLAY","EGL_BAD_MATCH","EGL_BAD_NATIVE_PIXMAP","EGL_BAD_NATIVE_WINDOW","EGL_BAD_PARAMETER","EGL_BAD_SURFACE","EGL_CONTEXT_LOST"};int index=error-0x3000;return index>=0&&index<names.length?names[index]:"0x"+Integer.toHexString(error);}
}
