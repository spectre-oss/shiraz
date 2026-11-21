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
 public static final int GL_ACTIVE_ATTRIBUTES = 0x8B89;
 public static final int GL_ACTIVE_ATTRIBUTE_MAX_LENGTH = 0x8B8A;
 public static final int GL_ACTIVE_TEXTURE = 0x84E0;
 public static final int GL_ACTIVE_UNIFORMS = 0x8B86;
 public static final int GL_ACTIVE_UNIFORM_MAX_LENGTH = 0x8B87;
 public static final int GL_ALIASED_LINE_WIDTH_RANGE = 0x846E;
 public static final int GL_ALIASED_POINT_SIZE_RANGE = 0x846D;
 public static final int GL_ALPHA = 0x1906;
 public static final int GL_ALPHA_BITS = 0xD55;
 public static final int GL_ALWAYS = 0x207;
 public static final int GL_ARRAY_BUFFER_BINDING = 0x8894;
 public static final int GL_ATTACHED_SHADERS = 0x8B85;
 public static final int GL_BACK = 0x405;
 public static final int GL_BLEND = 0xBE2;
 public static final int GL_BLEND_COLOR = 0x8005;
 public static final int GL_BLEND_DST_ALPHA = 0x80CA;
 public static final int GL_BLEND_DST_RGB = 0x80C8;
 public static final int GL_BLEND_EQUATION = 0x8009;
 public static final int GL_BLEND_EQUATION_ALPHA = 0x883D;
 public static final int GL_BLEND_EQUATION_RGB = 0x8009;
 public static final int GL_BLEND_SRC_ALPHA = 0x80CB;
 public static final int GL_BLEND_SRC_RGB = 0x80C9;
 public static final int GL_BLUE_BITS = 0xD54;
 public static final int GL_BOOL = 0x8B56;
 public static final int GL_BOOL_VEC2 = 0x8B57;
 public static final int GL_BOOL_VEC3 = 0x8B58;
 public static final int GL_BOOL_VEC4 = 0x8B59;
 public static final int GL_BUFFER_SIZE = 0x8764;
 public static final int GL_BUFFER_USAGE = 0x8765;
 public static final int GL_BYTE = 0x1400;
 public static final int GL_CCW = 0x901;
 public static final int GL_CLAMP_TO_EDGE = 0x812F;
 public static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
 public static final int GL_COLOR_CLEAR_VALUE = 0xC22;
 public static final int GL_COLOR_WRITEMASK = 0xC23;
 public static final int GL_COMPRESSED_TEXTURE_FORMATS = 0x86A3;
 public static final int GL_CONSTANT_ALPHA = 0x8003;
 public static final int GL_CONSTANT_COLOR = 0x8001;
 public static final int GL_CULL_FACE = 0xB44;
 public static final int GL_CULL_FACE_MODE = 0xB45;
 public static final int GL_CURRENT_PROGRAM = 0x8B8D;
 public static final int GL_CURRENT_VERTEX_ATTRIB = 0x8626;
 public static final int GL_CW = 0x900;
 public static final int GL_DECR = 0x1E03;
 public static final int GL_DECR_WRAP = 0x8508;
 public static final int GL_DELETE_STATUS = 0x8B80;
 public static final int GL_DEPTH_ATTACHMENT = 0x8D00;
 public static final int GL_DEPTH_BITS = 0xD56;
 public static final int GL_DEPTH_BUFFER_BIT = 0x100;
 public static final int GL_DEPTH_CLEAR_VALUE = 0xB73;
 public static final int GL_DEPTH_COMPONENT = 0x1902;
 public static final int GL_DEPTH_COMPONENT16 = 0x81A5;
 public static final int GL_DEPTH_FUNC = 0xB74;
 public static final int GL_DEPTH_RANGE = 0xB70;
 public static final int GL_DEPTH_TEST = 0xB71;
 public static final int GL_DEPTH_WRITEMASK = 0xB72;
 public static final int GL_DITHER = 0xBD0;
 public static final int GL_DONT_CARE = 0x1100;
 public static final int GL_DST_ALPHA = 0x304;
 public static final int GL_DST_COLOR = 0x306;
 public static final int GL_DYNAMIC_DRAW = 0x88E8;
 public static final int GL_ELEMENT_ARRAY_BUFFER_BINDING = 0x8895;
 public static final int GL_EQUAL = 0x202;
 public static final int GL_EXTENSIONS = 0x1F03;
 public static final int GL_FALSE = 0;
 public static final int GL_FASTEST = 0x1101;
 public static final int GL_FIXED = 0x140C;
 public static final int GL_FLOAT_MAT2 = 0x8B5A;
 public static final int GL_FLOAT_MAT3 = 0x8B5B;
 public static final int GL_FLOAT_MAT4 = 0x8B5C;
 public static final int GL_FLOAT_VEC2 = 0x8B50;
 public static final int GL_FLOAT_VEC3 = 0x8B51;
 public static final int GL_FLOAT_VEC4 = 0x8B52;
 public static final int GL_FRAMEBUFFER = 0x8D40;
 public static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME = 0x8CD1;
 public static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE = 0x8CD0;
 public static final int GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_CUBE_MAP_FACE = 0x8CD3;
 public static final int GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL = 0x8CD2;
 public static final int GL_FRAMEBUFFER_BINDING = 0x8CA6;
 public static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
 public static final int GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT = 0x8CD6;
 public static final int GL_FRAMEBUFFER_INCOMPLETE_DIMENSIONS = 0x8CD9;
 public static final int GL_FRAMEBUFFER_INCOMPLETE_MISSING_ATTACHMENT = 0x8CD7;
 public static final int GL_FRAMEBUFFER_UNSUPPORTED = 0x8CDD;
 public static final int GL_FRONT = 0x404;
 public static final int GL_FRONT_AND_BACK = 0x408;
 public static final int GL_FRONT_FACE = 0xB46;
 public static final int GL_FUNC_ADD = 0x8006;
 public static final int GL_FUNC_REVERSE_SUBTRACT = 0x800B;
 public static final int GL_FUNC_SUBTRACT = 0x800A;
 public static final int GL_GENERATE_MIPMAP_HINT = 0x8192;
 public static final int GL_GEQUAL = 0x206;
 public static final int GL_GREATER = 0x204;
 public static final int GL_GREEN_BITS = 0xD53;
 public static final int GL_HIGH_FLOAT = 0x8DF2;
 public static final int GL_HIGH_INT = 0x8DF5;
 public static final int GL_IMPLEMENTATION_COLOR_READ_FORMAT = 0x8B9B;
 public static final int GL_IMPLEMENTATION_COLOR_READ_TYPE = 0x8B9A;
 public static final int GL_INCR = 0x1E02;
 public static final int GL_INCR_WRAP = 0x8507;
 public static final int GL_INFO_LOG_LENGTH = 0x8B84;
 public static final int GL_INT = 0x1404;
 public static final int GL_INT_VEC2 = 0x8B53;
 public static final int GL_INT_VEC3 = 0x8B54;
 public static final int GL_INT_VEC4 = 0x8B55;
 public static final int GL_INVALID_ENUM = 0x500;
 public static final int GL_INVALID_FRAMEBUFFER_OPERATION = 0x506;
 public static final int GL_INVALID_OPERATION = 0x502;
 public static final int GL_INVALID_VALUE = 0x501;
 public static final int GL_INVERT = 0x150A;
 public static final int GL_KEEP = 0x1E00;
 public static final int GL_LEQUAL = 0x203;
 public static final int GL_LESS = 0x201;
 public static final int GL_LINEAR = 0x2601;
 public static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
 public static final int GL_LINEAR_MIPMAP_NEAREST = 0x2701;
 public static final int GL_LINES = 1;
 public static final int GL_LINE_LOOP = 2;
 public static final int GL_LINE_STRIP = 3;
 public static final int GL_LINE_WIDTH = 0xB21;
 public static final int GL_LOW_FLOAT = 0x8DF0;
 public static final int GL_LOW_INT = 0x8DF3;
 public static final int GL_LUMINANCE = 0x1909;
 public static final int GL_LUMINANCE_ALPHA = 0x190A;
 public static final int GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS = 0x8B4D;
 public static final int GL_MAX_CUBE_MAP_TEXTURE_SIZE = 0x851C;
 public static final int GL_MAX_FRAGMENT_UNIFORM_VECTORS = 0x8DFD;
 public static final int GL_MAX_RENDERBUFFER_SIZE = 0x84E8;
 public static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872;
 public static final int GL_MAX_TEXTURE_SIZE = 0xD33;
 public static final int GL_MAX_VARYING_VECTORS = 0x8DFC;
 public static final int GL_MAX_VERTEX_ATTRIBS = 0x8869;
 public static final int GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS = 0x8B4C;
 public static final int GL_MAX_VERTEX_UNIFORM_VECTORS = 0x8DFB;
 public static final int GL_MAX_VIEWPORT_DIMS = 0xD3A;
 public static final int GL_MEDIUM_FLOAT = 0x8DF1;
 public static final int GL_MEDIUM_INT = 0x8DF4;
 public static final int GL_MIRRORED_REPEAT = 0x8370;
 public static final int GL_NEAREST = 0x2600;
 public static final int GL_NEAREST_MIPMAP_LINEAR = 0x2702;
 public static final int GL_NEAREST_MIPMAP_NEAREST = 0x2700;
 public static final int GL_NEVER = 0x200;
 public static final int GL_NICEST = 0x1102;
 public static final int GL_NONE = 0;
 public static final int GL_NOTEQUAL = 0x205;
 public static final int GL_NO_ERROR = 0;
 public static final int GL_NUM_COMPRESSED_TEXTURE_FORMATS = 0x86A2;
 public static final int GL_NUM_SHADER_BINARY_FORMATS = 0x8DF9;
 public static final int GL_ONE = 1;
 public static final int GL_ONE_MINUS_CONSTANT_ALPHA = 0x8004;
 public static final int GL_ONE_MINUS_CONSTANT_COLOR = 0x8002;
 public static final int GL_ONE_MINUS_DST_ALPHA = 0x305;
 public static final int GL_ONE_MINUS_DST_COLOR = 0x307;
 public static final int GL_ONE_MINUS_SRC_ALPHA = 0x303;
 public static final int GL_ONE_MINUS_SRC_COLOR = 0x301;
 public static final int GL_OUT_OF_MEMORY = 0x505;
 public static final int GL_PACK_ALIGNMENT = 0xD05;
 public static final int GL_POINTS = 0;
 public static final int GL_POLYGON_OFFSET_FACTOR = 0x8038;
 public static final int GL_POLYGON_OFFSET_FILL = 0x8037;
 public static final int GL_POLYGON_OFFSET_UNITS = 0x2A00;
 public static final int GL_RED_BITS = 0xD52;
 public static final int GL_RENDERBUFFER = 0x8D41;
 public static final int GL_RENDERBUFFER_ALPHA_SIZE = 0x8D53;
 public static final int GL_RENDERBUFFER_BINDING = 0x8CA7;
 public static final int GL_RENDERBUFFER_BLUE_SIZE = 0x8D52;
 public static final int GL_RENDERBUFFER_DEPTH_SIZE = 0x8D54;
 public static final int GL_RENDERBUFFER_GREEN_SIZE = 0x8D51;
 public static final int GL_RENDERBUFFER_HEIGHT = 0x8D43;
 public static final int GL_RENDERBUFFER_INTERNAL_FORMAT = 0x8D44;
 public static final int GL_RENDERBUFFER_RED_SIZE = 0x8D50;
 public static final int GL_RENDERBUFFER_STENCIL_SIZE = 0x8D55;
 public static final int GL_RENDERBUFFER_WIDTH = 0x8D42;
 public static final int GL_RENDERER = 0x1F01;
 public static final int GL_REPEAT = 0x2901;
 public static final int GL_REPLACE = 0x1E01;
 public static final int GL_RGB = 0x1907;
 public static final int GL_RGB565 = 0x8D62;
 public static final int GL_RGB5_A1 = 0x8057;
 public static final int GL_RGBA4 = 0x8056;
 public static final int GL_SAMPLER_2D = 0x8B5E;
 public static final int GL_SAMPLER_CUBE = 0x8B60;
 public static final int GL_SAMPLES = 0x80A9;
 public static final int GL_SAMPLE_ALPHA_TO_COVERAGE = 0x809E;
 public static final int GL_SAMPLE_BUFFERS = 0x80A8;
 public static final int GL_SAMPLE_COVERAGE = 0x80A0;
 public static final int GL_SAMPLE_COVERAGE_INVERT = 0x80AB;
 public static final int GL_SAMPLE_COVERAGE_VALUE = 0x80AA;
 public static final int GL_SCISSOR_BOX = 0xC10;
 public static final int GL_SCISSOR_TEST = 0xC11;
 public static final int GL_SHADER_BINARY_FORMATS = 0x8DF8;
 public static final int GL_SHADER_COMPILER = 0x8DFA;
 public static final int GL_SHADER_SOURCE_LENGTH = 0x8B88;
 public static final int GL_SHADER_TYPE = 0x8B4F;
 public static final int GL_SHADING_LANGUAGE_VERSION = 0x8B8C;
 public static final int GL_SHORT = 0x1402;
 public static final int GL_SRC_ALPHA = 0x302;
 public static final int GL_SRC_ALPHA_SATURATE = 0x308;
 public static final int GL_SRC_COLOR = 0x300;
 public static final int GL_STENCIL_ATTACHMENT = 0x8D20;
 public static final int GL_STENCIL_BACK_FAIL = 0x8801;
 public static final int GL_STENCIL_BACK_FUNC = 0x8800;
 public static final int GL_STENCIL_BACK_PASS_DEPTH_FAIL = 0x8802;
 public static final int GL_STENCIL_BACK_PASS_DEPTH_PASS = 0x8803;
 public static final int GL_STENCIL_BACK_REF = 0x8CA3;
 public static final int GL_STENCIL_BACK_VALUE_MASK = 0x8CA4;
 public static final int GL_STENCIL_BACK_WRITEMASK = 0x8CA5;
 public static final int GL_STENCIL_BITS = 0xD57;
 public static final int GL_STENCIL_BUFFER_BIT = 0x400;
 public static final int GL_STENCIL_CLEAR_VALUE = 0xB91;
 public static final int GL_STENCIL_FAIL = 0xB94;
 public static final int GL_STENCIL_FUNC = 0xB92;
 public static final int GL_STENCIL_INDEX = 0x1901;
 public static final int GL_STENCIL_INDEX8 = 0x8D48;
 public static final int GL_STENCIL_PASS_DEPTH_FAIL = 0xB95;
 public static final int GL_STENCIL_PASS_DEPTH_PASS = 0xB96;
 public static final int GL_STENCIL_REF = 0xB97;
 public static final int GL_STENCIL_TEST = 0xB90;
 public static final int GL_STENCIL_VALUE_MASK = 0xB93;
 public static final int GL_STENCIL_WRITEMASK = 0xB98;
 public static final int GL_STREAM_DRAW = 0x88E0;
 public static final int GL_SUBPIXEL_BITS = 0xD50;
 public static final int GL_TEXTURE = 0x1702;
 public static final int GL_TEXTURE0 = 0x84C0;
 public static final int GL_TEXTURE1 = 0x84C1;
 public static final int GL_TEXTURE10 = 0x84CA;
 public static final int GL_TEXTURE11 = 0x84CB;
 public static final int GL_TEXTURE12 = 0x84CC;
 public static final int GL_TEXTURE13 = 0x84CD;
 public static final int GL_TEXTURE14 = 0x84CE;
 public static final int GL_TEXTURE15 = 0x84CF;
 public static final int GL_TEXTURE16 = 0x84D0;
 public static final int GL_TEXTURE17 = 0x84D1;
 public static final int GL_TEXTURE18 = 0x84D2;
 public static final int GL_TEXTURE19 = 0x84D3;
 public static final int GL_TEXTURE2 = 0x84C2;
 public static final int GL_TEXTURE20 = 0x84D4;
 public static final int GL_TEXTURE21 = 0x84D5;
 public static final int GL_TEXTURE22 = 0x84D6;
 public static final int GL_TEXTURE23 = 0x84D7;
 public static final int GL_TEXTURE24 = 0x84D8;
 public static final int GL_TEXTURE25 = 0x84D9;
 public static final int GL_TEXTURE26 = 0x84DA;
 public static final int GL_TEXTURE27 = 0x84DB;
 public static final int GL_TEXTURE28 = 0x84DC;
 public static final int GL_TEXTURE29 = 0x84DD;
 public static final int GL_TEXTURE3 = 0x84C3;
 public static final int GL_TEXTURE30 = 0x84DE;
 public static final int GL_TEXTURE31 = 0x84DF;
 public static final int GL_TEXTURE4 = 0x84C4;
 public static final int GL_TEXTURE5 = 0x84C5;
 public static final int GL_TEXTURE6 = 0x84C6;
 public static final int GL_TEXTURE7 = 0x84C7;
 public static final int GL_TEXTURE8 = 0x84C8;
 public static final int GL_TEXTURE9 = 0x84C9;
 public static final int GL_TEXTURE_2D = 0xDE1;
 public static final int GL_TEXTURE_BINDING_2D = 0x8069;
 public static final int GL_TEXTURE_BINDING_CUBE_MAP = 0x8514;
 public static final int GL_TEXTURE_CUBE_MAP = 0x8513;
 public static final int GL_TEXTURE_CUBE_MAP_NEGATIVE_X = 0x8516;
 public static final int GL_TEXTURE_CUBE_MAP_NEGATIVE_Y = 0x8518;
 public static final int GL_TEXTURE_CUBE_MAP_NEGATIVE_Z = 0x851A;
 public static final int GL_TEXTURE_CUBE_MAP_POSITIVE_X = 0x8515;
 public static final int GL_TEXTURE_CUBE_MAP_POSITIVE_Y = 0x8517;
 public static final int GL_TEXTURE_CUBE_MAP_POSITIVE_Z = 0x8519;
 public static final int GL_TEXTURE_MAG_FILTER = 0x2800;
 public static final int GL_TEXTURE_MIN_FILTER = 0x2801;
 public static final int GL_TEXTURE_WRAP_S = 0x2802;
 public static final int GL_TEXTURE_WRAP_T = 0x2803;
 public static final int GL_TRIANGLE_FAN = 6;
 public static final int GL_TRIANGLE_STRIP = 5;
 public static final int GL_TRUE = 1;
 public static final int GL_UNPACK_ALIGNMENT = 0xCF5;
 public static final int GL_UNSIGNED_INT = 0x1405;
 public static final int GL_UNSIGNED_SHORT_4_4_4_4 = 0x8033;
 public static final int GL_UNSIGNED_SHORT_5_5_5_1 = 0x8034;
 public static final int GL_UNSIGNED_SHORT_5_6_5 = 0x8363;
 public static final int GL_VALIDATE_STATUS = 0x8B83;
 public static final int GL_VENDOR = 0x1F00;
 public static final int GL_VERSION = 0x1F02;
 public static final int GL_VERTEX_ATTRIB_ARRAY_BUFFER_BINDING = 0x889F;
 public static final int GL_VERTEX_ATTRIB_ARRAY_ENABLED = 0x8622;
 public static final int GL_VERTEX_ATTRIB_ARRAY_NORMALIZED = 0x886A;
 public static final int GL_VERTEX_ATTRIB_ARRAY_POINTER = 0x8645;
 public static final int GL_VERTEX_ATTRIB_ARRAY_SIZE = 0x8623;
 public static final int GL_VERTEX_ATTRIB_ARRAY_STRIDE = 0x8624;
 public static final int GL_VERTEX_ATTRIB_ARRAY_TYPE = 0x8625;
 public static final int GL_VIEWPORT = 0xBA2;
 public static final int GL_ZERO = 0;
}
