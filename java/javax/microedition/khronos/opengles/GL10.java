// SPDX-License-Identifier: GPL-3.0-only
package javax.microedition.khronos.opengles;
public interface GL10 extends GL {
 void glFlush();
 void glFinish();
 void glClear(int mask);
 void glClearColor(float red,float green,float blue,float alpha);
 void glColor4f(float red,float green,float blue,float alpha);
 void glBindTexture(int target,int texture);
 void glBlendFunc(int source,int destination);
 void glEnable(int capability);
 void glEnableClientState(int array);
 void glLoadIdentity();
 void glMatrixMode(int mode);
 void glOrthof(float left,float right,float bottom,float top,float near,float far);
 void glTexParameterf(int target,int name,float value);
 void glViewport(int x,int y,int width,int height);
 void glReadPixels(int x,int y,int width,int height,int format,int type,java.nio.Buffer pixels);
 void glGenTextures(int count,int[] textures,int offset);
 void glDeleteTextures(int count,int[] textures,int offset);
 void glDrawElements(int mode,int count,int type,java.nio.Buffer indices);
 void glTexCoordPointer(int size,int type,int stride,java.nio.Buffer pointer);
 void glVertexPointer(int size,int type,int stride,java.nio.Buffer pointer);
}
