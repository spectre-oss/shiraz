// SPDX-License-Identifier: GPL-3.0-only
import javax.microedition.khronos.egl.*;
import javax.microedition.khronos.opengles.GL10;
import java.nio.ByteBuffer;
public class GL10Smoke {
 public static void main(String[] args) {
  EGL10 egl=(EGL10)EGLContext.getEGL();
  EGLDisplay display=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
  if(!egl.eglInitialize(display,new int[2]))throw new AssertionError("display");
  EGLConfig[] configs=new EGLConfig[1];
  if(!egl.eglChooseConfig(display,new int[]{EGL10.EGL_NONE},configs,1,new int[1]))throw new AssertionError("config");
  EGLContext context=egl.eglCreateContext(display,configs[0],EGL10.EGL_NO_CONTEXT,new int[]{0x3098,1,EGL10.EGL_NONE});
  EGLSurface surface=egl.eglCreatePbufferSurface(display,configs[0],new int[]{EGL10.EGL_WIDTH,8,EGL10.EGL_HEIGHT,8,EGL10.EGL_NONE});
  if(!egl.eglMakeCurrent(display,surface,surface,context))throw new AssertionError("current "+egl.eglGetError());
  GL10 gl=(GL10)context.getGL();
  gl.glViewport(0,0,8,8);gl.glClearColor(1,0,0,1);gl.glClear(0x4000);
  ByteBuffer pixel=ByteBuffer.allocateDirect(8);pixel.position(4);
  gl.glReadPixels(0,0,1,1,0x1908,0x1401,pixel);
  if((pixel.get(4)&255)!=255||pixel.get(5)!=0||pixel.get(6)!=0||pixel.get(0)!=0)throw new AssertionError("red pixel/buffer offset");
  try{gl.glReadPixels(0,0,2,1,0x1908,0x1401,pixel);throw new AssertionError("bounds");}catch(IllegalArgumentException expected){}
  android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(2,2,android.graphics.Bitmap.Config.ARGB_8888);
  bitmap.eraseColor(0xff00ff00);
  int[] textures=new int[1];gl.glGenTextures(1,textures,0);gl.glBindTexture(0x0de1,textures[0]);
  gl.glTexParameterf(0x0de1,0x2801,0x2600);gl.glTexParameterf(0x0de1,0x2800,0x2600);
  android.opengl.GLUtils.texImage2D(0x0de1,0,bitmap,0);
  gl.glEnable(0x0de1);gl.glEnableClientState(0x8074);gl.glEnableClientState(0x8078);gl.glColor4f(1,1,1,1);
  gl.glMatrixMode(0x1701);gl.glLoadIdentity();gl.glOrthof(-1,1,-1,1,-1,1);gl.glMatrixMode(0x1700);gl.glLoadIdentity();
  gl.glVertexPointer(2,0x1406,0,floats(new float[]{-1,-1,1,-1,1,1,-1,1}));
  gl.glTexCoordPointer(2,0x1406,0,floats(new float[]{0,0,1,0,1,1,0,1}));
  ByteBuffer indices=ByteBuffer.allocateDirect(6);indices.put(new byte[]{0,1,2,0,2,3}).position(0);
  gl.glDrawElements(4,6,0x1401,indices);gl.glReadPixels(4,4,1,1,0x1908,0x1401,pixel);
  if(pixel.get(4)!=0||(pixel.get(5)&255)!=255||pixel.get(6)!=0)throw new AssertionError("green textured quad");
  bitmap.eraseColor(0xff0000ff);android.opengl.GLUtils.texSubImage2D(0x0de1,0,0,0,bitmap);
  gl.glDrawElements(4,6,0x1401,indices);gl.glReadPixels(4,4,1,1,0x1908,0x1401,pixel);
  if(pixel.get(4)!=0||pixel.get(5)!=0||(pixel.get(6)&255)!=255)throw new AssertionError("blue texture replacement");
  gl.glDeleteTextures(1,textures,0);bitmap.recycle();
  if(!egl.eglSwapBuffers(display,surface))throw new AssertionError("swap");
  egl.eglMakeCurrent(display,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_CONTEXT);
  egl.eglDestroySurface(display,surface);egl.eglDestroyContext(display,context);
  System.out.println("JAVA_GL10_PIXEL_OK");
 }
 private static java.nio.FloatBuffer floats(float[] values){java.nio.FloatBuffer buffer=ByteBuffer.allocateDirect(values.length*4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(values);buffer.position(0);return buffer;}
}
