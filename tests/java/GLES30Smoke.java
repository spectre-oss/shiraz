// SPDX-License-Identifier: GPL-3.0-only
import android.opengl.GLES20;
import android.opengl.GLES30;
import javax.microedition.khronos.egl.*;
import java.nio.*;
public class GLES30Smoke {
 private static int shader(int type,String source){int shader=GLES20.glCreateShader(type);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] status={0};GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,status,0);if(status[0]!=1)throw new AssertionError(GLES20.glGetShaderInfoLog(shader));return shader;}
 public static void main(String[] args){
  EGL10 egl=(EGL10)EGLContext.getEGL();EGLDisplay display=egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);if(!egl.eglInitialize(display,new int[2]))throw new AssertionError("display");
  EGLConfig[] configs=new EGLConfig[1];if(!egl.eglChooseConfig(display,new int[]{0x3040,0x40,EGL10.EGL_NONE},configs,1,new int[1]))throw new AssertionError("config");
  EGLContext context=egl.eglCreateContext(display,configs[0],EGL10.EGL_NO_CONTEXT,new int[]{0x3098,3,EGL10.EGL_NONE});
  EGLSurface surface=egl.eglCreatePbufferSurface(display,configs[0],new int[]{EGL10.EGL_WIDTH,8,EGL10.EGL_HEIGHT,8,EGL10.EGL_NONE});
  if(!egl.eglMakeCurrent(display,surface,surface,context))throw new AssertionError("current");
  IntBuffer arrays=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder()).asIntBuffer();arrays.put(0,123);arrays.position(1);
  GLES30.glGenVertexArrays(2,arrays);int first=arrays.get(1),second=arrays.get(2);
  if(first==0||second==0||first==second||arrays.position()!=1||arrays.get(0)!=123)throw new AssertionError("VAO generation/position");
  GLES30.glBindVertexArray(first);if(!GLES30.glIsVertexArray(first))throw new AssertionError("real VAO");
  int vertex=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 position;void main(){gl_Position=vec4(position,0.0,1.0);}");
  int fragment=shader(GLES20.GL_FRAGMENT_SHADER,"precision mediump float;uniform vec4 color;void main(){gl_FragColor=color;}");
  int program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glBindAttribLocation(program,0,"position");GLES20.glLinkProgram(program);
  IntBuffer status=IntBuffer.allocate(3);status.position(1);GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,status);if(status.position()!=1||status.get(1)!=1||status.get(0)!=0)throw new AssertionError("program status/position");
  GLES20.glUseProgram(program);IntBuffer buffers=IntBuffer.allocate(3);buffers.position(1);GLES20.glGenBuffers(1,buffers);int vbo=buffers.get(1);if(buffers.position()!=1||vbo==0)throw new AssertionError("buffer generation");
  FloatBuffer vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();vertices.put(new float[]{99,99,-1,-1,1,-1,0,1}).position(2);
  GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER,vbo);GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,24,vertices,GLES20.GL_STATIC_DRAW);
  if(vertices.position()!=2)throw new AssertionError("upload changed position");
  GLES20.glVertexAttribPointer(0,2,GLES20.GL_FLOAT,false,0,0);GLES20.glEnableVertexAttribArray(0);
  GLES30.glBindVertexArray(second);GLES20.glDisableVertexAttribArray(0);
  GLES30.glBindVertexArray(first);int[] state={-1};
  GLES20.glGetIntegerv(GLES30.GL_VERTEX_ARRAY_BINDING,state,0);if(state[0]!=first)throw new AssertionError("VAO binding query");
  FloatBuffer color=FloatBuffer.wrap(new float[]{99,0,0,1,1});color.position(1);GLES20.glUniform4fv(GLES20.glGetUniformLocation(program,"color"),1,color);
  GLES20.glViewport(0,0,8,8);GLES20.glClearColor(1,0,0,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,3);
  FloatBuffer clear=FloatBuffer.allocate(5);clear.position(1);GLES20.glGetFloatv(0x0c22,clear);if(clear.position()!=1||clear.get(1)!=1||clear.get(2)!=0||clear.get(4)!=1)throw new AssertionError("float query");
  ByteBuffer pixel=ByteBuffer.allocateDirect(8);pixel.position(4);GLES20.glReadPixels(4,3,1,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixel);
  if(pixel.get(4)!=0||pixel.get(5)!=0||(pixel.get(6)&255)!=255||pixel.get(0)!=0||pixel.position()!=4)throw new AssertionError("shader triangle/readback");
  try{GLES20.glReadPixels(0,0,2,1,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,pixel);throw new AssertionError("short pixel buffer");}catch(IllegalArgumentException expected){}
  try{GLES20.glGetIntegerv(0x0ba2,new int[1],0);throw new AssertionError("short viewport array");}catch(IllegalArgumentException expected){}
  try{GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER,28,vertices,GLES20.GL_STATIC_DRAW);throw new AssertionError("short vertex buffer");}catch(IllegalArgumentException expected){}
  int[] length={0},size={0},type={0};byte[] name=new byte[64];GLES20.glGetActiveUniform(program,0,name.length,length,0,size,0,type,0,name,0);if(!new String(name,0,length[0]).equals("color")||size[0]!=1||type[0]!=0x8b52)throw new AssertionError("active uniform");
  try{GLES30.glGenVertexArrays(4,arrays);throw new AssertionError("short VAO buffer");}catch(IllegalArgumentException expected){}
  try{GLES30.glGenVertexArrays(1,arrays.asReadOnlyBuffer());throw new AssertionError("readonly output");}catch(IllegalArgumentException expected){}
  try{GLES30.glDeleteVertexArrays(2,new int[1],0);throw new AssertionError("short VAO array");}catch(IllegalArgumentException expected){}
  GLES30.glDeleteVertexArrays(2,arrays);if(arrays.position()!=1||GLES30.glIsVertexArray(first)||GLES30.glIsVertexArray(second))throw new AssertionError("VAO deletion");
  int[] extra={123,0,456};GLES30.glGenVertexArrays(1,extra,1);GLES30.glBindVertexArray(extra[1]);
  if(extra[0]!=123||extra[2]!=456||!GLES30.glIsVertexArray(extra[1]))throw new AssertionError("array offset");
  GLES30.glDeleteVertexArrays(1,IntBuffer.wrap(new int[]{extra[1]}).asReadOnlyBuffer());
  GLES20.glDeleteBuffers(1,buffers);GLES20.glDeleteProgram(program);GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);
  if(GLES20.glGetError()!=0)throw new AssertionError("GL error");
  egl.eglMakeCurrent(display,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_SURFACE,EGL10.EGL_NO_CONTEXT);egl.eglDestroySurface(display,surface);egl.eglDestroyContext(display,context);
  System.out.println("GLES30_VAO_STATE_TRIANGLE_OK");
 }
}
