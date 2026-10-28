// SPDX-License-Identifier: GPL-3.0-only
package shiraz.runtime;
/** Initial GLES1 subset backed by the current native EGL context. */
public final class HostGL10 implements javax.microedition.khronos.opengles.GL10 {
 public native String glGetString(int name);
 static { System.loadLibrary("shiraz-gles1"); }
 private java.nio.Buffer vertices,coordinates;
 public native void glFlush();
 public native void glFinish();
 public native void glClear(int mask);
 public native void glClearColor(float red,float green,float blue,float alpha);
 public native void glColor4f(float red,float green,float blue,float alpha);
 public native void glBindTexture(int target,int texture);
 public native void glBlendFunc(int source,int destination);
 public native void glEnable(int capability);
 public native void glEnableClientState(int array);
 public native void glLoadIdentity();
 public native void glMatrixMode(int mode);
 public native void glOrthof(float left,float right,float bottom,float top,float near,float far);
 public native void glTexParameterf(int target,int name,float value);
 public native void glViewport(int x,int y,int width,int height);
 public native void glReadPixels(int x,int y,int width,int height,int format,int type,java.nio.Buffer pixels);
 public native void glGenTextures(int count,int[] textures,int offset);
 public native void glDeleteTextures(int count,int[] textures,int offset);
 public native void glDrawElements(int mode,int count,int type,java.nio.Buffer indices);
 public void glVertexPointer(int size,int type,int stride,java.nio.Buffer pointer){ nativeglVertexPointer(size,type,stride,pointer); vertices=pointer; }
 private native void nativeglVertexPointer(int size,int type,int stride,java.nio.Buffer pointer);
 public void glTexCoordPointer(int size,int type,int stride,java.nio.Buffer pointer){ nativeglTexCoordPointer(size,type,stride,pointer); coordinates=pointer; }
 private native void nativeglTexCoordPointer(int size,int type,int stride,java.nio.Buffer pointer);
}
