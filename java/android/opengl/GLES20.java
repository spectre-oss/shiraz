// SPDX-License-Identifier: GPL-3.0-only
package android.opengl;
import java.nio.*;
/** GLES2 calls on the current EGL context. Buffer positions are preserved. */
public class GLES20 {
 static { System.loadLibrary("shiraz-gles2"); }
 public static native void glActiveTexture(int texture);
 public static native void glAttachShader(int program,int shader);
 public static native void glBindBuffer(int target,int buffer);
 public static native void glBindFramebuffer(int target,int framebuffer);
 public static native void glBindRenderbuffer(int target,int renderbuffer);
 public static native void glBindTexture(int target,int texture);
 public static native void glBlendColor(float r,float g,float b,float a);
 public static native void glBlendEquation(int mode);
 public static native void glBlendEquationSeparate(int rgb,int alpha);
 public static native void glBlendFunc(int source,int destination);
 public static native void glBlendFuncSeparate(int srgb,int drgb,int sa,int da);
 public static native int glCheckFramebufferStatus(int target);
 public static native void glClear(int mask);
 public static native void glClearColor(float r,float g,float b,float a);
 public static native void glClearDepthf(float depth);
 public static native void glClearStencil(int stencil);
 public static native void glColorMask(boolean r,boolean g,boolean b,boolean a);
 public static native void glCompileShader(int shader);
 public static native int glCreateProgram();
 public static native int glCreateShader(int type);
 public static native void glCullFace(int mode);
 public static native void glDeleteProgram(int program);
 public static native void glDeleteShader(int shader);
 public static native void glDepthFunc(int function);
 public static native void glDepthMask(boolean mask);
 public static native void glDepthRangef(float near,float far);
 public static native void glDetachShader(int program,int shader);
 public static native void glDisable(int cap);
 public static native void glDisableVertexAttribArray(int index);
 public static native void glDrawArrays(int mode,int first,int count);
 public static native void glEnable(int cap);
 public static native void glEnableVertexAttribArray(int index);
 public static native void glFinish();
 public static native void glFlush();
 public static native void glFramebufferRenderbuffer(int target,int attachment,int renderbufferTarget,int renderbuffer);
 public static native void glFramebufferTexture2D(int target,int attachment,int textureTarget,int texture,int level);
 public static native void glFrontFace(int mode);
 public static native void glGenerateMipmap(int target);
 public static native int glGetError();
 public static native void glHint(int target,int mode);
 public static native void glLineWidth(float width);
 public static native void glLinkProgram(int program);
 public static native void glPixelStorei(int name,int value);
 public static native void glPolygonOffset(float factor,float units);
 public static native void glRenderbufferStorage(int target,int format,int width,int height);
 public static native void glSampleCoverage(float value,boolean invert);
 public static native void glScissor(int x,int y,int width,int height);
 public static native void glStencilFunc(int function,int reference,int mask);
 public static native void glStencilMask(int mask);
 public static native void glStencilOp(int fail,int depthFail,int pass);
 public static native void glTexParameterf(int target,int name,float value);
 public static native void glTexParameteri(int target,int name,int value);
 public static native void glUniform1f(int location,float x);
 public static native void glUniform2f(int location,float x,float y);
 public static native void glUniform3f(int location,float x,float y,float z);
 public static native void glUniform4f(int location,float x,float y,float z,float w);
 public static native void glUniform1i(int location,int x);
 public static native void glUniform2i(int location,int x,int y);
 public static native void glUniform3i(int location,int x,int y,int z);
 public static native void glUniform4i(int location,int x,int y,int z,int w);
 public static native void glUseProgram(int program);
 public static native void glValidateProgram(int program);
 public static native void glViewport(int x,int y,int width,int height);
 public static final int GL_VERTEX_SHADER=0x8b31,GL_FRAGMENT_SHADER=0x8b30,GL_COMPILE_STATUS=0x8b81,GL_LINK_STATUS=0x8b82,GL_ARRAY_BUFFER=0x8892,GL_ELEMENT_ARRAY_BUFFER=0x8893,GL_STATIC_DRAW=0x88e4,GL_FLOAT=0x1406,GL_UNSIGNED_BYTE=0x1401,GL_UNSIGNED_SHORT=0x1403,GL_RGBA=0x1908,GL_TRIANGLES=4,GL_COLOR_BUFFER_BIT=0x4000;
 private static void range(int length,int offset,int count){if(offset<0||count<0||offset>length-count)throw new IllegalArgumentException("GL array range");}
 private static int size(int count,int components){if(count<0||count>1048576/components)throw new IllegalArgumentException("GL element count");return count*components;}
 private static int[] ints(IntBuffer buffer,int count,boolean read){if(buffer==null)throw new IllegalArgumentException("null GL buffer");if(count<0||buffer.remaining()<count||(!read&&buffer.isReadOnly()))throw new IllegalArgumentException("GL buffer range");int[] values=new int[count];if(read)buffer.duplicate().get(values);return values;}
 private static void output(IntBuffer buffer,int[] values){int at=buffer.position();for(int i=0;i<values.length;i++)buffer.put(at+i,values[i]);}
 private static float[] floats(FloatBuffer buffer,int count){if(buffer==null||count<0||buffer.remaining()<count)throw new IllegalArgumentException("GL float buffer range");float[] values=new float[count];buffer.duplicate().get(values);return values;}
 public static native String glGetString(int name);
 public static native void glGetFloatv(int name,float[] values,int offset);
 public static void glGetFloatv(int name,FloatBuffer buffer){int count=nIntegerCount(name);if(buffer==null||buffer.isReadOnly()||buffer.remaining()<count)throw new IllegalArgumentException("GL float destination");float[] values=new float[count];glGetFloatv(name,values,0);int at=buffer.position();for(int i=0;i<count;i++)buffer.put(at+i,values[i]);}
 public static native void glShaderSource(int shader,String source);
 public static native int glGetAttribLocation(int program,String name);
 public static native int glGetUniformLocation(int program,String name);
 public static native void glBindAttribLocation(int program,int index,String name);
 private static native String nInfoLog(int object,boolean program);
 public static String glGetShaderInfoLog(int shader){return nInfoLog(shader,false);}
 public static String glGetProgramInfoLog(int program){return nInfoLog(program,true);}
 private static native int nIntegerCount(int name);
 public static native void glGetIntegerv(int name,int[] values,int offset);
 public static void glGetIntegerv(int name,IntBuffer buffer){int[] values=ints(buffer,nIntegerCount(name),false);glGetIntegerv(name,values,0);output(buffer,values);}
 public static native void glGetShaderiv(int shader,int name,int[] values,int offset);
 public static void glGetShaderiv(int shader,int name,IntBuffer buffer){int[] values=ints(buffer,1,false);glGetShaderiv(shader,name,values,0);output(buffer,values);}
 public static native void glGetProgramiv(int program,int name,int[] values,int offset);
 public static void glGetProgramiv(int program,int name,IntBuffer buffer){int[] values=ints(buffer,name==0x8267?3:1,false);glGetProgramiv(program,name,values,0);output(buffer,values);}
 public static native void glBufferData(int target,int bytes,Buffer data,int usage);
 public static native void glBufferSubData(int target,int offset,int bytes,Buffer data);
 public static native void glTexImage2D(int target,int level,int internalFormat,int width,int height,int border,int format,int type,Buffer pixels);
 public static native void glTexSubImage2D(int target,int level,int x,int y,int width,int height,int format,int type,Buffer pixels);
 public static native void glReadPixels(int x,int y,int width,int height,int format,int type,Buffer pixels);
 private static native void nDrawElementsOffset(int mode,int count,int type,int offset);
 private static native void nDrawElementsBuffer(int mode,int count,int type,Buffer data);
 public static void glDrawElements(int mode,int count,int type,int offset){nDrawElementsOffset(mode,count,type,offset);}
 public static void glDrawElements(int mode,int count,int type,Buffer data){nDrawElementsBuffer(mode,count,type,data);}
 private static native void nVertexAttribPointer(int index,int count,int type,boolean normalized,int stride,int offset);
 public static void glVertexAttribPointer(int index,int count,int type,boolean normalized,int stride,int offset){nVertexAttribPointer(index,count,type,normalized,stride,offset);}
 public static void glVertexAttribPointer(int index,int count,int type,boolean normalized,int stride,Buffer data){throw new UnsupportedOperationException("Client vertex arrays require retained-buffer lifetime support; use vertex buffers");}
 public static native void glGenBuffers(int count,int[] values,int offset);
 public static void glGenBuffers(int count,IntBuffer buffer){int[] values=ints(buffer,size(count,1),false);glGenBuffers(count,values,0);output(buffer,values);}
 public static native void glDeleteBuffers(int count,int[] values,int offset);
 public static void glDeleteBuffers(int count,IntBuffer buffer){int[] values=ints(buffer,size(count,1),true);glDeleteBuffers(count,values,0);}
 public static native void glGenTextures(int count,int[] values,int offset);
 public static void glGenTextures(int count,IntBuffer buffer){int[] values=ints(buffer,size(count,1),false);glGenTextures(count,values,0);output(buffer,values);}
 public static native void glDeleteTextures(int count,int[] values,int offset);
 public static void glDeleteTextures(int count,IntBuffer buffer){int[] values=ints(buffer,size(count,1),true);glDeleteTextures(count,values,0);}
 public static native void glGenFramebuffers(int count,int[] values,int offset);
 public static void glGenFramebuffers(int count,IntBuffer buffer){int[] values=ints(buffer,size(count,1),false);glGenFramebuffers(count,values,0);output(buffer,values);}
 public static native void glDeleteFramebuffers(int count,int[] values,int offset);
 public static void glDeleteFramebuffers(int count,IntBuffer buffer){int[] values=ints(buffer,size(count,1),true);glDeleteFramebuffers(count,values,0);}
 public static native void glGenRenderbuffers(int count,int[] values,int offset);
 public static void glGenRenderbuffers(int count,IntBuffer buffer){int[] values=ints(buffer,size(count,1),false);glGenRenderbuffers(count,values,0);output(buffer,values);}
 public static native void glDeleteRenderbuffers(int count,int[] values,int offset);
 public static void glDeleteRenderbuffers(int count,IntBuffer buffer){int[] values=ints(buffer,size(count,1),true);glDeleteRenderbuffers(count,values,0);}
 public static native void glUniform1fv(int location,int count,float[] values,int offset);
 public static void glUniform1fv(int location,int count,FloatBuffer buffer){glUniform1fv(location,count,floats(buffer,size(count,1)),0);}
 public static native void glUniform2fv(int location,int count,float[] values,int offset);
 public static void glUniform2fv(int location,int count,FloatBuffer buffer){glUniform2fv(location,count,floats(buffer,size(count,2)),0);}
 public static native void glUniform3fv(int location,int count,float[] values,int offset);
 public static void glUniform3fv(int location,int count,FloatBuffer buffer){glUniform3fv(location,count,floats(buffer,size(count,3)),0);}
 public static native void glUniform4fv(int location,int count,float[] values,int offset);
 public static void glUniform4fv(int location,int count,FloatBuffer buffer){glUniform4fv(location,count,floats(buffer,size(count,4)),0);}
 public static native void glUniform1iv(int location,int count,int[] values,int offset);
 public static void glUniform1iv(int location,int count,IntBuffer buffer){glUniform1iv(location,count,ints(buffer,size(count,1),true),0);}
 public static native void glUniform2iv(int location,int count,int[] values,int offset);
 public static void glUniform2iv(int location,int count,IntBuffer buffer){glUniform2iv(location,count,ints(buffer,size(count,2),true),0);}
 public static native void glUniform3iv(int location,int count,int[] values,int offset);
 public static void glUniform3iv(int location,int count,IntBuffer buffer){glUniform3iv(location,count,ints(buffer,size(count,3),true),0);}
 public static native void glUniform4iv(int location,int count,int[] values,int offset);
 public static void glUniform4iv(int location,int count,IntBuffer buffer){glUniform4iv(location,count,ints(buffer,size(count,4),true),0);}
 public static native void glUniformMatrix2fv(int location,int count,boolean transpose,float[] values,int offset);
 public static void glUniformMatrix2fv(int location,int count,boolean transpose,FloatBuffer buffer){glUniformMatrix2fv(location,count,transpose,floats(buffer,size(count,4)),0);}
 public static native void glUniformMatrix3fv(int location,int count,boolean transpose,float[] values,int offset);
 public static void glUniformMatrix3fv(int location,int count,boolean transpose,FloatBuffer buffer){glUniformMatrix3fv(location,count,transpose,floats(buffer,size(count,9)),0);}
 public static native void glUniformMatrix4fv(int location,int count,boolean transpose,float[] values,int offset);
 public static void glUniformMatrix4fv(int location,int count,boolean transpose,FloatBuffer buffer){glUniformMatrix4fv(location,count,transpose,floats(buffer,size(count,16)),0);}
 private static native String nActive(int program,int index,boolean uniform,int[] size,int[] type);
 private static String active(int program,int index,boolean uniform,int[] size,int so,int[] type,int to){range(size.length,so,1);range(type.length,to,1);int[] s=new int[1],t=new int[1];String name=nActive(program,index,uniform,s,t);size[so]=s[0];type[to]=t[0];return name;}
 public static String glGetActiveUniform(int program,int index,int[] size,int so,int[] type,int to){return active(program,index,true,size,so,type,to);}
 public static String glGetActiveAttrib(int program,int index,int[] size,int so,int[] type,int to){return active(program,index,false,size,so,type,to);}
 private static void activeBytes(int program,int index,boolean uniform,int capacity,int[] length,int lo,int[] size,int so,int[] type,int to,byte[] name,int no){range(name.length,no,capacity);if(length!=null)range(length.length,lo,1);String value=active(program,index,uniform,size,so,type,to);byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);int count=Math.min(bytes.length,Math.max(0,capacity-1));System.arraycopy(bytes,0,name,no,count);if(capacity>0)name[no+count]=0;if(length!=null)length[lo]=count;}
 public static void glGetActiveUniform(int p,int i,int cap,int[] len,int lo,int[] size,int so,int[] type,int to,byte[] name,int no){activeBytes(p,i,true,cap,len,lo,size,so,type,to,name,no);}
 public static void glGetActiveAttrib(int p,int i,int cap,int[] len,int lo,int[] size,int so,int[] type,int to,byte[] name,int no){activeBytes(p,i,false,cap,len,lo,size,so,type,to,name,no);}
}
