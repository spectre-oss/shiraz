// SPDX-License-Identifier: GPL-3.0-only
import android.view.*;
import android.graphics.*;
import android.os.*;
import android.opengl.GLES20;
import javax.microedition.khronos.egl.*;
public class PixelCopySmoke {
 public static void main(String[] args){
  Looper.prepare();Handler handler=new Handler();Surface source=new Surface();source.shirazConfigure(4,2,PixelFormat.RGBA_8888);
  Bitmap target=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);final int[] result={-1};
  PixelCopy.OnPixelCopyFinishedListener listener=new PixelCopy.OnPixelCopyFinishedListener(){public void onPixelCopyFinished(int value){result[0]=value;}};
  try{
   PixelCopy.request(source,target,listener,handler);if(result[0]!=-1)throw new AssertionError("inline callback");Looper.myLooper().shirazDispatchDue(100);if(result[0]!=PixelCopy.ERROR_SOURCE_NO_DATA)throw new AssertionError("no frame");
   source.shirazNativeWindow();PixelCopy.request(source,target,listener,handler);Looper.myLooper().shirazDispatchDue(100);if(result[0]!=PixelCopy.ERROR_SOURCE_NO_DATA)throw new AssertionError("initial blank buffer is not producer data");
   Canvas canvas=source.lockCanvas(null);canvas.drawColor(Color.RED);Paint paint=new Paint();paint.setColor(Color.BLUE);canvas.drawRect(2,0,4,2,paint);source.unlockCanvasAndPost(canvas);
   PixelCopy.request(source,new Rect(2,0,4,2),target,listener,handler);Looper.myLooper().shirazDispatchDue(100);if(result[0]!=PixelCopy.SUCCESS||target.getPixel(0,0)!=Color.BLUE||target.getPixel(1,1)!=Color.BLUE)throw new AssertionError("cropped pixels");
   try{PixelCopy.request(source,new Rect(),target,listener,handler);throw new AssertionError("empty crop");}catch(IllegalArgumentException expected){}
   Bitmap immutable=target.copy(Bitmap.Config.ARGB_8888,false);try{PixelCopy.request(source,immutable,listener,handler);throw new AssertionError("immutable target");}catch(IllegalArgumentException expected){}finally{immutable.recycle();}
   EGL10 egl=(EGL10)EGLContext.getEGL();EGLDisplay display=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);egl.eglInitialize(display,new int[2]);EGLConfig[] configs=new EGLConfig[1];egl.eglChooseConfig(display,new int[]{0x3040,4,EGL10.EGL_NONE},configs,1,new int[1]);
   EGLContext context=egl.eglCreateContext(display,configs[0],EGL10.EGL_NO_CONTEXT,new int[]{0x3098,2,EGL10.EGL_NONE});EGLSurface window=egl.eglCreateWindowSurface(display,configs[0],source,null);
   if(!egl.eglMakeCurrent(display,window,window,context))throw new AssertionError("native context");
   GLES20.glClearColor(0,1,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);if(!egl.eglSwapBuffers(display,window))throw new AssertionError("native post");
   PixelCopy.request(source,target,listener,handler);Looper.myLooper().shirazDispatchDue(100);if(result[0]!=PixelCopy.SUCCESS||target.getPixel(0,0)!=Color.GREEN)throw new AssertionError("native frame copy");
   egl.eglMakeCurrent(display,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_CONTEXT);egl.eglDestroySurface(display,window);egl.eglDestroyContext(display,context);
   source.release();try{PixelCopy.request(source,target,listener,handler);throw new AssertionError("released source");}catch(IllegalArgumentException expected){}
   System.out.println("PIXEL_COPY_SURFACE_OK");
  }finally{source.release();target.recycle();}
 }
}
