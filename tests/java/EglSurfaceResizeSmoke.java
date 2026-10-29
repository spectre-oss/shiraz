// SPDX-License-Identifier: GPL-3.0-only
import android.view.Surface;
import android.graphics.PixelFormat;
import javax.microedition.khronos.egl.*;
public final class EglSurfaceResizeSmoke {
 static{System.loadLibrary("shiraz-egl-resize-test");}
 private static native boolean currentSize(int width,int height);
 public static void main(String[] args)throws Exception{
  EGL10 egl=(EGL10)EGLContext.getEGL();EGLDisplay display=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
  if(!egl.eglInitialize(display,null))throw new AssertionError("display");
  Surface holder=new Surface();holder.shirazConfigure(4,3,PixelFormat.RGBA_8888);
  EGLSurface surface=egl.eglCreateWindowSurface(display,null,holder,null);
  EGLContext context=egl.eglCreateContext(display,null,EGL10.EGL_NO_CONTEXT,new int[]{0x3098,3,EGL10.EGL_NONE});
  if(!egl.eglMakeCurrent(display,surface,surface,context)||!egl.eglSwapBuffers(display,surface))throw new AssertionError("initial current/swap");
  for(final int width:new int[]{7,3,12,4}){
   final Throwable[] error={null};Thread ui=new Thread(new Runnable(){public void run(){try{holder.shirazConfigure(width,width+2,PixelFormat.RGBA_8888);}catch(Throwable t){error[0]=t;}}});ui.start();ui.join();
   if(error[0]!=null)throw new AssertionError(error[0]);
   if(!currentSize(width,width+2))throw new AssertionError("Host resize destroyed native EGL surface");
   if(!egl.eglSwapBuffers(display,surface))throw new AssertionError("Host resize invalidated current EGL surface");
   if(egl.eglGetCurrentContext()!=context||egl.eglGetCurrentSurface(EGL10.EGL_DRAW)!=surface)throw new AssertionError("Current identity changed");
  }
  egl.eglMakeCurrent(display,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_CONTEXT);
  egl.eglDestroySurface(display,surface);egl.eglDestroyContext(display,context);holder.release();
  System.out.println("EGL_SURFACE_HOST_RESIZE_PRESERVES_CURRENT_OK");
 }
}
