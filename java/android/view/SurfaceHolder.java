// SPDX-License-Identifier: GPL-3.0-only
package android.view;
import android.graphics.Canvas;
import android.graphics.Rect;
public interface SurfaceHolder {
 int SURFACE_TYPE_NORMAL=0,SURFACE_TYPE_HARDWARE=1,SURFACE_TYPE_GPU=2,SURFACE_TYPE_PUSH_BUFFERS=3;
 class BadSurfaceTypeException extends RuntimeException {public BadSurfaceTypeException(){}public BadSurfaceTypeException(String message){super(message);}}
 interface Callback {void surfaceCreated(SurfaceHolder holder);void surfaceChanged(SurfaceHolder holder,int format,int width,int height);void surfaceDestroyed(SurfaceHolder holder);}
 interface Callback2 extends Callback {void surfaceRedrawNeeded(SurfaceHolder holder);default void surfaceRedrawNeededAsync(SurfaceHolder holder,Runnable finished){surfaceRedrawNeeded(holder);finished.run();}}
 void addCallback(Callback callback);void removeCallback(Callback callback);boolean isCreating();void setType(int type);
 void setFixedSize(int width,int height);void setSizeFromLayout();void setFormat(int format);void setKeepScreenOn(boolean on);
 Canvas lockCanvas();Canvas lockCanvas(Rect dirty);Canvas lockHardwareCanvas();void unlockCanvasAndPost(Canvas canvas);Rect getSurfaceFrame();Surface getSurface();
}
