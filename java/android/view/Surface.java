// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.*;
/** Software producer/presenter buffers. A native window can be taken from a configured surface. */
public class Surface implements android.os.Parcelable {
 public static class OutOfResourcesException extends RuntimeException {public OutOfResourcesException(){}public OutOfResourcesException(String message){super(message);}}
 private Bitmap front,back,spare;private Canvas locked;private Thread producer;private boolean valid,accepting;private int width,height,format,generation,lockedGeneration;private long nativeWindow;
 static { System.loadLibrary("android"); }
 private Surface delegate;private boolean proxy;private long posts,lastNativeRevision;
 public Surface(){}
 private Surface(long window,int w,int h){shirazConfigure(w,h,PixelFormat.RGBA_8888);nativeWindow=window;}
 public Surface(SurfaceTexture texture){if(texture==null)throw new NullPointerException("texture");delegate=texture.shirazSurface();proxy=true;}
 private Surface target(){if(delegate==null)throw new IllegalStateException("Surface released");return delegate;}
 private static native long nativeWindowSize(long window);
 private static native long nativeWindowRevision(long window);
 public synchronized long shirazFrameNumber(){return proxy?target().shirazFrameNumber():posts+(nativeWindow==0?0:nativeWindowRevision(nativeWindow));}

 public synchronized long shirazNativeWindow() { if(proxy)return delegate==null?0:delegate.shirazNativeWindow(); if (!valid || width < 1 || height < 1) return 0; if (nativeWindow == 0) {nativeWindow = nativeCreateWindow(width, height);if(nativeWindow!=0&&front!=null){int[] pixels=new int[width*height];front.getPixels(pixels,0,width,0,0,width,height);if(!nativePostArgb(nativeWindow,pixels)){nativeReleaseWindow(nativeWindow);nativeWindow=0;}}} return nativeWindow; }
 public int shirazWidth() { return width; }
 public int shirazHeight() { return height; }
 private static native long nativeCreateWindow(int width, int height);
 private static native boolean nativeResizeWindow(long window,int width,int height);
 private static native void nativeReleaseWindow(long window);
 private static native boolean nativePostArgb(long window, int[] pixels);
 private static native long nativeEglCreateContext();
 private static native void nativeEglDestroyContext(long context);
 private static native boolean nativeEglMakeCurrent(long window, long context);
 private static native boolean nativeEglSwap(long window);
 private static native void nativeEglForgetWindow(long window);
 public static long shirazEglCreateContext() { return nativeEglCreateContext(); }
 public static void shirazEglDestroyContext(long context) { if (context != 0) nativeEglDestroyContext(context); }
 public static boolean shirazEglRelease() { return nativeEglMakeCurrent(0, 0); }
 public boolean shirazEglMakeCurrent(long context) { long window = shirazNativeWindow(); return window != 0 && context != 0 && nativeEglMakeCurrent(window, context); }
 public boolean shirazEglSwap() { long window = shirazNativeWindow(); return window != 0 && nativeEglSwap(window); }
 public long shirazOffscreenWindow(int w, int h) { shirazConfigure(w, h, PixelFormat.RGBA_8888); return shirazNativeWindow(); }
 private static native boolean nativeCopyArgb(long window, int[] pixels);
 boolean shirazPostArgb(int[] pixels) { long window = shirazNativeWindow(); return window != 0 && nativePostArgb(window, pixels); }
 private int[] published;
 private void publishWindow() {
  if (nativeWindow == 0 || front == null) return;
  long revision=nativeWindowRevision(nativeWindow);if(revision==lastNativeRevision)return;
  long size=nativeWindowSize(nativeWindow);int w=(int)(size>>>32),h=(int)size;dimensions(w,h);
  if(front.getWidth()!=w||front.getHeight()!=h){front.recycle();if(spare!=null){spare.recycle();spare=null;}front=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);front.setDensity(Bitmap.DENSITY_NONE);width=w;height=h;generation++;}

  int count = front.getWidth() * front.getHeight();
  if (published == null || published.length != count) published = new int[count];
  if (nativeCopyArgb(nativeWindow, published)&&revision==nativeWindowRevision(nativeWindow)){front.setPixels(published, 0, front.getWidth(), 0, 0, front.getWidth(), front.getHeight());lastNativeRevision=revision;}
 }
 private static void dimensions(int w,int h){if(w<1||h<1||w>8192||h>8192||(long)w*h>16*1024*1024)throw new IllegalArgumentException("Surface dimensions");}
 public synchronized void shirazConfigure(int w,int h,int pixelFormat){dimensions(w,h);if(nativeWindow!=0&&(width!=w||height!=h)){if(!nativeResizeWindow(nativeWindow,w,h))throw new OutOfResourcesException("Native surface resize failed");nativeEglForgetWindow(nativeWindow);}Bitmap replacement=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);replacement.setDensity(Bitmap.DENSITY_NONE);if(pixelFormat!=PixelFormat.RGBA_8888&&pixelFormat!=PixelFormat.TRANSLUCENT&&pixelFormat!=PixelFormat.TRANSPARENT)replacement.eraseColor(0xff000000);if(front!=null)front.recycle();if(spare!=null){spare.recycle();spare=null;}front=replacement;width=w;height=h;format=pixelFormat;generation++;valid=accepting=true;notifyAll();}
 public synchronized boolean isValid(){return proxy?delegate!=null&&delegate.isValid():valid;}
 synchronized void shirazStopProducer(){accepting=false;generation++;notifyAll();}
 public synchronized void release(){if(proxy){delegate=null;return;}lastNativeRevision=0;valid=accepting=false;generation++;if(nativeWindow!=0){nativeEglForgetWindow(nativeWindow);nativeReleaseWindow(nativeWindow);nativeWindow=0;}if(front!=null){front.recycle();front=null;}if(spare!=null){spare.recycle();spare=null;}published=null;notifyAll();}
 public Canvas lockCanvas(Rect dirty){return shirazLock(dirty,false);}
 synchronized Canvas shirazLock(Rect dirty,boolean nullable){if(proxy)return target().shirazLock(dirty,nullable);
  while(valid&&accepting&&locked!=null){if(producer==Thread.currentThread())throw new IllegalStateException("Surface already locked by this thread");try{wait();}catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("Surface lock interrupted",error);}}
  if(!valid||!accepting){if(nullable)return null;throw new IllegalStateException("Surface is invalid");}
  publishWindow();
  if(spare==null){back=front.copy(Bitmap.Config.ARGB_8888,true);}else{back=spare;spare=null;front.shirazCopyPixelsInto(back);}locked=new Canvas(back);producer=Thread.currentThread();lockedGeneration=generation;
  // The software buffer may require a full redraw, as permitted by lockCanvas(Rect).
  if(dirty!=null)dirty.set(0,0,width,height);return locked;
 }
 public void unlockCanvasAndPost(Canvas canvas){if(proxy){target().unlockCanvasAndPost(canvas);return;}
  synchronized(this){if(canvas==null||canvas!=locked||producer!=Thread.currentThread())throw new IllegalArgumentException("Canvas lock ownership");
   canvas.setBitmap(null);
   try{if(valid&&lockedGeneration==generation){
     if(format==PixelFormat.RGB_565||format==PixelFormat.RGBX_8888||format==PixelFormat.OPAQUE){int w=back.getWidth(),h=back.getHeight();int[] row=new int[w];for(int y=0;y<h;y++){back.getPixels(row,0,w,0,y,w,1);for(int x=0;x<w;x++){int c=row[x];if(format==PixelFormat.RGB_565){int r=(c>>>19)&31,g=(c>>>10)&63,b=(c>>>3)&31;c=((r<<3|r>>>2)<<16)|((g<<2|g>>>4)<<8)|(b<<3|b>>>2);}row[x]=0xff000000|c;}back.setPixels(row,0,w,0,y,w,1);}}
     spare=front;front=back;back=null;posts++;if(nativeWindow!=0){int count=width*height;if(published==null||published.length!=count)published=new int[count];front.getPixels(published,0,width,0,0,width,height);if(!nativePostArgb(nativeWindow,published))throw new IllegalStateException("Native surface post failed");lastNativeRevision=nativeWindowRevision(nativeWindow);}
   }}finally{if(back!=null)back.recycle();back=null;locked=null;producer=null;notifyAll();}
  }
 }
 public Canvas lockHardwareCanvas(){throw new UnsupportedOperationException("Hardware surface canvas requires EGL/native window support");}
 public synchronized void shirazDraw(Canvas canvas,int width,int height){if(proxy){target().shirazDraw(canvas,width,height);return;}if(valid&&front!=null&&width>0&&height>0){publishWindow();canvas.drawBitmap(front,null,new Rect(0,0,width,height),null);}}
 private static native int nativeExportWindow(long window);
 private static native long nativeImportWindow(int descriptor);
 public int describeContents(){return CONTENTS_FILE_DESCRIPTOR;}
 public synchronized void writeToParcel(android.os.Parcel out,int flags){
  if(out==null)throw new NullPointerException("parcel");
  long window=shirazNativeWindow();
  if(window==0){out.writeInt(0);return;}
  int fd=nativeExportWindow(window);
  if(fd<0)throw new OutOfResourcesException("Cannot export Surface descriptor: "+fd);
  try(android.os.ParcelFileDescriptor descriptor=android.os.ParcelFileDescriptor.adoptFd(fd)){
   out.writeInt(1);out.writeFileDescriptor(descriptor.getFileDescriptor());
  }catch(java.io.IOException error){throw new IllegalStateException("Cannot close Surface descriptor",error);}
  if((flags&PARCELABLE_WRITE_RETURN_VALUE)!=0)release();
 }
 public synchronized void readFromParcel(android.os.Parcel in){
  if(in==null)throw new NullPointerException("parcel");
  if(locked!=null)throw new IllegalStateException("Cannot replace a locked Surface");
  int present=in.readInt();
  if(present==0){release();proxy=false;delegate=null;return;}
  if(present!=1)throw new IllegalArgumentException("Surface parcel marker");
  long imported=0;
  try(android.os.ParcelFileDescriptor descriptor=in.readFileDescriptor()){
   if(descriptor==null)throw new IllegalArgumentException("Missing Surface descriptor");
   imported=nativeImportWindow(descriptor.getFd());
   if(imported==0)throw new IllegalArgumentException("Invalid Surface descriptor");
   long size=nativeWindowSize(imported);int w=(int)(size>>>32),h=(int)size;dimensions(w,h);
   release();proxy=false;delegate=null;shirazConfigure(w,h,PixelFormat.RGBA_8888);
   nativeWindow=imported;imported=0;
  }catch(java.io.IOException error){throw new IllegalStateException("Cannot close Surface descriptor",error);}
  finally{if(imported!=0)nativeReleaseWindow(imported);}
 }
 public static final android.os.Parcelable.Creator<Surface> CREATOR=new android.os.Parcelable.Creator<Surface>(){
  public Surface createFromParcel(android.os.Parcel in){Surface surface=new Surface();surface.readFromParcel(in);return surface;}
  public Surface[] newArray(int size){return new Surface[size];}
 };
}
