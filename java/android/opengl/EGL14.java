// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;
import java.util.concurrent.atomic.AtomicLong;
/** Virtual EGL 1.4: handles are tracked and calls succeed, but no GPU context exists and nothing is rendered. */
public final class EGL14 {
 public static final int EGL_SUCCESS=0x3000,EGL_NOT_INITIALIZED=0x3001,EGL_BAD_DISPLAY=0x3008;
 public static final EGLContext EGL_NO_CONTEXT=new EGLContext(0);
 public static final EGLDisplay EGL_NO_DISPLAY=new EGLDisplay(0);
 public static final EGLSurface EGL_NO_SURFACE=new EGLSurface(0);
 private static final AtomicLong NEXT=new AtomicLong(16);
 private static final EGLDisplay DISPLAY=new EGLDisplay(1);
 private static final EGLConfig CONFIG=new EGLConfig(1);
 private static final ThreadLocal<EGLContext> CURRENT=new ThreadLocal<EGLContext>();
 private static volatile boolean initialized;
 private EGL14(){}
 public static EGLDisplay eglGetDisplay(int displayId){return DISPLAY;}
 public static boolean eglInitialize(EGLDisplay display,int[] major,int majorOffset,int[] minor,int minorOffset){if(!DISPLAY.equals(display))return false;initialized=true;if(major!=null&&major.length>majorOffset)major[majorOffset]=1;if(minor!=null&&minor.length>minorOffset)minor[minorOffset]=4;return true;}
 public static boolean eglTerminate(EGLDisplay display){initialized=false;return DISPLAY.equals(display);}
 public static int eglGetError(){return EGL_SUCCESS;}
 public static String eglQueryString(EGLDisplay display,int name){return "";}
 public static boolean eglChooseConfig(EGLDisplay display,int[] attributes,int attributesOffset,EGLConfig[] configs,int configsOffset,int configSize,int[] count,int countOffset){
  if(!initialized||!DISPLAY.equals(display))return false;
  int n=configSize>0&&configs!=null&&configs.length>configsOffset?1:0;
  if(n==1)configs[configsOffset]=CONFIG;
  if(count!=null&&count.length>countOffset)count[countOffset]=1;
  return true;
 }
 public static EGLContext eglCreateContext(EGLDisplay display,EGLConfig config,EGLContext shared,int[] attributes,int offset){return initialized?new EGLContext(NEXT.getAndIncrement()):EGL_NO_CONTEXT;}
 public static EGLSurface eglCreatePbufferSurface(EGLDisplay display,EGLConfig config,int[] attributes,int offset){return initialized?new EGLSurface(NEXT.getAndIncrement()):EGL_NO_SURFACE;}
 public static EGLSurface eglCreateWindowSurface(EGLDisplay display,EGLConfig config,Object window,int[] attributes,int offset){return initialized?new EGLSurface(NEXT.getAndIncrement()):EGL_NO_SURFACE;}
 public static boolean eglDestroyContext(EGLDisplay display,EGLContext context){return initialized;}
 public static boolean eglDestroySurface(EGLDisplay display,EGLSurface surface){return initialized;}
 public static EGLContext eglGetCurrentContext(){EGLContext value=CURRENT.get();return value==null?EGL_NO_CONTEXT:value;}
 public static boolean eglMakeCurrent(EGLDisplay display,EGLSurface draw,EGLSurface read,EGLContext context){if(!initialized)return false;CURRENT.set(context);return true;}
 public static boolean eglQuerySurface(EGLDisplay display,EGLSurface surface,int attribute,int[] value,int offset){if(value!=null&&value.length>offset)value[offset]=0;return initialized;}
 public static boolean eglReleaseThread(){CURRENT.remove();return true;}
 public static boolean eglSwapBuffers(EGLDisplay display,EGLSurface surface){return initialized;}
}
