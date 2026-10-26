// SPDX-License-Identifier: GPL-3.0-only
package android.graphics;
/** Native-window consumer with EGL external-texture sampling. */
public class SurfaceTexture {
 public interface OnFrameAvailableListener {void onFrameAvailable(SurfaceTexture surfaceTexture);}
 public static class OutOfResourcesException extends Exception {public OutOfResourcesException(){}public OutOfResourcesException(String message){super(message);}}
 private final android.view.Surface surface=new android.view.Surface();private boolean released;private int width=1,height=1;
 private long consumer,notifiedFrame=-1;
 private final android.os.Looper creator=android.os.Looper.myLooper();
 private final Runnable poll=new Runnable(){public void run(){OnFrameAvailableListener callback; synchronized(SurfaceTexture.this){if(released||listener==null)return;long frame=surface.shirazFrameNumber();callback=frame!=notifiedFrame?listener:null;notifiedFrame=frame;handler.postDelayed(this,16);}if(callback!=null)callback.onFrameAvailable(SurfaceTexture.this);}};
 private OnFrameAvailableListener listener;private android.os.Handler handler;private long timestamp;
 public SurfaceTexture(int texName){this(texName,false);}
 public SurfaceTexture(int texName,boolean singleBufferMode){this(singleBufferMode);nativeRelease(consumer);consumer=nativeCreate(texName,true);}
 public SurfaceTexture(boolean singleBufferMode){if(singleBufferMode)throw new UnsupportedOperationException("Single-buffer texture consumers");surface.shirazConfigure(1,1,PixelFormat.RGBA_8888);consumer=nativeCreate(0,false);}
 public synchronized android.view.Surface shirazSurface(){if(released)throw new IllegalStateException("SurfaceTexture released");return surface;}
 public synchronized void setDefaultBufferSize(int w,int h){if(released)throw new IllegalStateException("SurfaceTexture released");if(w==width&&h==height)return;surface.shirazConfigure(w,h,PixelFormat.RGBA_8888);width=w;height=h;}
 public synchronized void setOnFrameAvailableListener(OnFrameAvailableListener value){setOnFrameAvailableListener(value,null);}
 public synchronized void setOnFrameAvailableListener(OnFrameAvailableListener value,android.os.Handler target){if(handler!=null)handler.removeCallbacks(poll);listener=value;handler=null;if(value!=null){android.os.Looper looper=creator!=null?creator:android.os.Looper.getMainLooper();handler=target!=null?target:new android.os.Handler(looper);notifiedFrame=surface.shirazFrameNumber();handler.postDelayed(poll,16);}}
 public void shirazFrameAvailable(){final OnFrameAvailableListener callback;android.os.Handler target;synchronized(this){if(released)return;long frame=surface.shirazFrameNumber();if(frame==notifiedFrame)return;notifiedFrame=frame;callback=listener;target=handler;}if(callback!=null){if(target==null)callback.onFrameAvailable(this);else target.post(new Runnable(){public void run(){synchronized(SurfaceTexture.this){if(released||listener!=callback)return;}callback.onFrameAvailable(SurfaceTexture.this);}});}}
 public synchronized long getTimestamp(){return timestamp;}
 public synchronized boolean isReleased(){return released;}
 public synchronized void release(){if(released)return;released=true;listener=null;if(handler!=null)handler.removeCallbacks(poll);handler=null;nativeRelease(consumer);consumer=0;surface.release();}
 public void getTransformMatrix(float[] matrix){if(matrix==null||matrix.length!=16)throw new IllegalArgumentException("16-entry transform required");java.util.Arrays.fill(matrix,0);matrix[0]=matrix[10]=matrix[15]=1;matrix[5]=-1;matrix[13]=1;}
 private void live(){if(released)throw new IllegalStateException("SurfaceTexture released");}
 private static native long nativeCreate(int name,boolean attached);
 private static native void nativeAttach(long consumer,int name);
 private static native void nativeDetach(long consumer);
 private static native boolean nativeUpdate(long consumer,long window);
 private static native void nativeRelease(long consumer);
 public synchronized void updateTexImage(){live();if(nativeUpdate(consumer,surface.shirazNativeWindow()))timestamp=System.nanoTime();}
 public synchronized void releaseTexImage(){live();/* Multi-buffer consumers retain the last image until the next update. */}
 public synchronized void attachToGLContext(int name){live();nativeAttach(consumer,name);}
 public synchronized void detachFromGLContext(){live();nativeDetach(consumer);}
}
