// SPDX-License-Identifier: GPL-3.0-only
import android.graphics.*;
import android.opengl.GLES20;
import android.view.Surface;
import javax.microedition.khronos.egl.*;
public final class GlesWindowFramebufferSmoke {
 public static void main(String[] args){
  EGL10 egl=(EGL10)EGLContext.getEGL();EGLDisplay display=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
  if(!egl.eglInitialize(display,null))throw new AssertionError("display");
  Surface holder=new Surface();holder.shirazConfigure(4,3,PixelFormat.RGBA_8888);
  EGLSurface surface=egl.eglCreateWindowSurface(display,null,holder,null);
  EGLContext context=egl.eglCreateContext(display,null,EGL10.EGL_NO_CONTEXT,new int[]{0x3098,3,EGL10.EGL_NONE});
  if(!egl.eglMakeCurrent(display,surface,surface,context))throw new AssertionError("current");
  int[] binding={0};GLES20.glGetIntegerv(0x8ca6,binding,0);int window=binding[0];
  if(window==0)throw new AssertionError("window adapter framebuffer");
  int[] offscreen={0};GLES20.glGenFramebuffers(1,offscreen,0);
  GLES20.glBindFramebuffer(0x8d40,offscreen[0]);
  GLES20.glGetIntegerv(0x8ca6,binding,0);if(binding[0]!=offscreen[0])throw new AssertionError("offscreen bind");
  GLES20.glBindFramebuffer(0x8d40,0);
  GLES20.glGetIntegerv(0x8ca6,binding,0);if(binding[0]!=window)throw new AssertionError("Java framebuffer zero bypassed window adapter");
  if(GLES20.glCheckFramebufferStatus(0x8d40)!=0x8cd5)throw new AssertionError("window completeness");
  GLES20.glViewport(0,0,4,3);GLES20.glClearColor(0,1,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
  if(GLES20.glGetError()!=0||!egl.eglSwapBuffers(display,surface))throw new AssertionError("clear/swap");
  Bitmap pixels=Bitmap.createBitmap(4,3,Bitmap.Config.ARGB_8888);holder.shirazDraw(new Canvas(pixels),4,3);
  if(pixels.getPixel(2,1)!=0xff00ff00)throw new AssertionError("window pixel");pixels.recycle();
  // Native producers must obey the consumer surface's opacity just as Canvas does.
  for(int format:new int[]{PixelFormat.RGBX_8888,PixelFormat.RGB_565,PixelFormat.OPAQUE,PixelFormat.RGBA_8888}){
   holder.shirazConfigure(4,3,format);
   GLES20.glClearColor(0,1,0,.5f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
   if(!egl.eglSwapBuffers(display,surface))throw new AssertionError("opacity swap");
   pixels=Bitmap.createBitmap(4,3,Bitmap.Config.ARGB_8888);
   holder.shirazDraw(new Canvas(pixels),4,3);
   int alpha=pixels.getPixel(2,1)>>>24;
   if(format==PixelFormat.RGBA_8888 ? alpha<126||alpha>129 : alpha!=255)
    throw new AssertionError("native surface alpha="+alpha+" format="+format);
   pixels.recycle();
  }
  GLES20.glDeleteFramebuffers(1,offscreen,0);
  egl.eglMakeCurrent(display,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_CONTEXT);
  egl.eglDestroySurface(display,surface);egl.eglDestroyContext(display,context);holder.release();
  System.out.println("GLES_JAVA_WINDOW_FRAMEBUFFER_PIXEL_OK");
 }
}
